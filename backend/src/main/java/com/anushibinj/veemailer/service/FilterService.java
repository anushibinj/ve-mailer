package com.anushibinj.veemailer.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.anushibinj.veemailer.dto.FilterDto;
import com.anushibinj.veemailer.dto.ParsedFilterQueryResponse;
import com.anushibinj.veemailer.dto.PreviewResponse;
import com.anushibinj.veemailer.model.Filter;
import com.anushibinj.veemailer.model.FilterCriteriaClause;
import com.anushibinj.veemailer.model.Workspace;
import com.anushibinj.veemailer.model.WorkspaceStatus;
import com.anushibinj.veemailer.repository.EmailSubscriberRepository;
import com.anushibinj.veemailer.repository.FilterRepository;
import com.anushibinj.veemailer.repository.WorkspaceRepository;
import com.anushibinj.veemailer.service.extractor.FieldExtractorRegistry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hpe.adm.nga.sdk.Octane;
import com.hpe.adm.nga.sdk.entities.OctaneCollection;
import com.hpe.adm.nga.sdk.entities.get.GetEntities;
import com.hpe.adm.nga.sdk.model.BooleanFieldModel;
import com.hpe.adm.nga.sdk.model.DateFieldModel;
import com.hpe.adm.nga.sdk.model.EntityModel;
import com.hpe.adm.nga.sdk.model.FieldModel;
import com.hpe.adm.nga.sdk.model.FloatFieldModel;
import com.hpe.adm.nga.sdk.model.LongFieldModel;
import com.hpe.adm.nga.sdk.model.MultiReferenceFieldModel;
import com.hpe.adm.nga.sdk.model.ReferenceFieldModel;
import com.hpe.adm.nga.sdk.model.StringFieldModel;
import com.hpe.adm.nga.sdk.query.Query;
import com.hpe.adm.nga.sdk.query.QueryMethod;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class FilterService {
    private static final Pattern FILTER_QUERY_CLAUSE_PATTERN = Pattern.compile(
            "^([A-Za-z0-9_]+)\\s+(EQ|NEQ|IN|NOT_IN)\\s+(.+)$",
            Pattern.CASE_INSENSITIVE);


    private final FilterRepository filterRepository;
    private final WorkspaceRepository workspaceRepository;
    private final EmailSubscriberRepository emailSubscriberRepository;
    private final OctaneCacheService octaneCacheService;
    private final ObjectMapper objectMapper;
    private final GeneralSettingsService generalSettingsService;
    private final AiSummaryService aiSummaryService;
    private final FieldExtractorRegistry fieldExtractorRegistry;

    /**
     * Persist a new filter template associated with a workspace.
     */
    public Filter createFilter(FilterDto dto) {
        Workspace workspace = workspaceRepository.findById(dto.getWorkspaceId())
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + dto.getWorkspaceId()));
        FilterDto resolvedDto = resolveFilterDefinition(dto);
        try {
            String fieldsJson = objectMapper.writeValueAsString(resolvedDto.getFields());
            String criteriaJson = objectMapper.writeValueAsString(resolvedDto.getCriteria());

            Filter filter = Filter.builder()
                    .title(resolvedDto.getTitle())
                    .description(resolvedDto.getDescription())
                    .workspace(workspace)
                    .entityType(resolvedDto.getEntityType())
                    .fields(fieldsJson)
                    .criteria(criteriaJson)
                    .build();

            return filterRepository.save(filter);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize filter data", e);
        }
    }

    public ParsedFilterQueryResponse parseFilterQueryString(String filterQueryString) {
        String normalizedInput = filterQueryString == null ? "" : filterQueryString.trim();
        if (normalizedInput.isEmpty()) {
            throw new IllegalArgumentException("filterQueryString must not be blank");
        }

        Map<String, String> params = parseFilterQueryParams(normalizedInput);
        String fieldsRaw = params.get("fields");
        String queryRaw = params.get("query");

        if (fieldsRaw == null || fieldsRaw.isBlank()) {
            throw new IllegalArgumentException("Invalid filter query string: missing fields parameter");
        }
        if (queryRaw == null || queryRaw.isBlank()) {
            throw new IllegalArgumentException("Invalid filter query string: missing query parameter");
        }

        List<String> parsedFields = Arrays.stream(fieldsRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());
        if (parsedFields.isEmpty()) {
            throw new IllegalArgumentException("Invalid filter query string: fields parameter is empty");
        }

        List<FilterCriteriaClause> parsedCriteria = parseCriteriaExpression(queryRaw);
        String normalized = buildFilterQueryString(parsedFields, parsedCriteria);

        return ParsedFilterQueryResponse.builder()
                .fields(parsedFields)
                .criteria(parsedCriteria)
                .filterQueryString(normalized)
                .build();
    }

    public String getFilterQueryString(UUID filterId) {
        Filter filter = filterRepository.findById(filterId)
                .orElseThrow(() -> new IllegalArgumentException("Filter not found: " + filterId));
        try {
            List<String> fields = objectMapper.readValue(filter.getFields(), new TypeReference<>() {});
            List<FilterCriteriaClause> criteria = objectMapper.readValue(filter.getCriteria(), new TypeReference<>() {});
            return buildFilterQueryString(fields, criteria);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize filter data", e);
        }
    }

    /**
     * Deletes a filter template and all associated subscriptions.
     * The mail audit log references the filter only by its UUID and title (stored as plain
     * columns, not as a FK), so historical audit records are preserved after deletion.
     */
    @Transactional
    public void deleteFilter(UUID filterId) {
        if (!filterRepository.existsById(filterId)) {
            throw new IllegalArgumentException("Filter not found: " + filterId);
        }
        emailSubscriberRepository.deleteByFilter_Id(filterId);
        filterRepository.deleteById(filterId);
    }

    /**
     * Returns a deep-copy DTO of the given filter's configurable fields, with no ID,
     * workspace reference or audit metadata. The title is prefixed with "Clone of ".
     * The caller is responsible for persisting the result via {@link #createFilter(FilterDto)}.
     */
    public FilterDto cloneFilter(UUID filterId) {
        Filter filter = filterRepository.findById(filterId)
                .orElseThrow(() -> new IllegalArgumentException("Filter not found: " + filterId));
        try {
            List<String> fields = objectMapper.readValue(filter.getFields(), new TypeReference<>() {});
            List<FilterCriteriaClause> criteria = objectMapper.readValue(filter.getCriteria(), new TypeReference<>() {});
            return FilterDto.builder()
                    .title("Clone of " + filter.getTitle())
                    .description(filter.getDescription())
                    .entityType(filter.getEntityType())
                    .fields(fields)
                    .criteria(criteria)
                    .filterQueryString(buildFilterQueryString(fields, criteria))
                    .build();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize filter data", e);
        }
    }

    /**
     * Update an existing filter template.
     */
    public Filter updateFilter(UUID filterId, FilterDto dto) {
        Filter filter = filterRepository.findById(filterId)
                .orElseThrow(() -> new IllegalArgumentException("Filter not found: " + filterId));
        FilterDto resolvedDto = resolveFilterDefinition(dto);
        try {
            String fieldsJson = objectMapper.writeValueAsString(resolvedDto.getFields());
            String criteriaJson = objectMapper.writeValueAsString(resolvedDto.getCriteria());

            filter.setTitle(resolvedDto.getTitle());
            filter.setDescription(resolvedDto.getDescription());
            filter.setEntityType(resolvedDto.getEntityType());
            filter.setFields(fieldsJson);
            filter.setCriteria(criteriaJson);

            return filterRepository.save(filter);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize filter data", e);
        }
    }

    /**
     * Returns the ordered list of field names stored in the given filter template.
     * Used by notification senders to know which columns to render.
     */
    public List<String> getFilterFields(UUID filterId) {
        Filter filter = filterRepository.findById(filterId)
                .orElseThrow(() -> new IllegalArgumentException("Filter not found: " + filterId));
        try {
            return objectMapper.readValue(filter.getFields(), new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize filter fields", e);
        }
    }

    /**
     * Load a saved filter, build an Octane query dynamically, execute it
     * against the given workspace and return the results.
     */
    public List<EntityModel> executeFilter(UUID filterId, UUID workspaceId) {
        Filter filter = filterRepository.findById(filterId)
                .orElseThrow(() -> new IllegalArgumentException("Filter not found"));
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found"));
        enforceWorkspaceNotDisabled(workspace);

        try {
            List<String> fields = objectMapper.readValue(filter.getFields(), new TypeReference<>() {});
            List<FilterCriteriaClause> clauses = objectMapper.readValue(filter.getCriteria(), new TypeReference<>() {});

            // Compute the effective fields to fetch — strips pseudo-fields, adds silent
            // dependencies (AI Summary → name+description, always → id).
            List<String> effectiveFetchFields = computeEffectiveFetchFields(fields);

            Octane octaneClient = octaneCacheService.getOctaneClient(
                    workspace.getRootUrl(),
                    workspace.getClientId(),
                    workspace.getClientKey(),
                    Integer.parseInt(workspace.getSharedSpaceId()),
                    Integer.parseInt(workspace.getWorkspaceId()));

            Query query = buildQuery(filter.getEntityType(), clauses);

            int effectiveLimit = generalSettingsService.getQueryLimit();
            GetEntities getEntities = octaneClient
                    .entityList("work_items")
                    .get()
                    .query(query)
                    .addFields(effectiveFetchFields.toArray(new String[0]));
            // Apply LIMIT only when a positive integer is configured; -1 means unlimited.
            if (effectiveLimit > 0) {
                getEntities = getEntities.limit(effectiveLimit);
            }
            OctaneCollection<EntityModel> result = getEntities.execute();

            return result.stream().toList();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize filter data", e);
        }
    }

    /** Returns the configured maximum number of results returned per filter execution. */
    public int getQueryLimit() {
        return generalSettingsService.getQueryLimit();
    }

    /**
     * Preview mode: executes the filter with a hard limit (independent of the global
     * query limit setting) and fully replicates real email-generation behaviour,
     * including AI summary generation when the AI Summary pseudo-field is selected.
     * Used by the UI to show an accurate sample of results before sending.
     */
    public PreviewResponse previewFilter(UUID filterId, UUID workspaceId, int limit) {
        int effectivePreviewLimit = Math.max(1, Math.min(limit, 50)); // cap between 1 and 50
        Filter filter = filterRepository.findById(filterId)
                .orElseThrow(() -> new IllegalArgumentException("Filter not found"));
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found"));
        enforceWorkspaceNotDisabled(workspace);

        try {
            List<String> fields = objectMapper.readValue(filter.getFields(), new TypeReference<>() {});
            List<FilterCriteriaClause> clauses = objectMapper.readValue(filter.getCriteria(), new TypeReference<>() {});
            List<String> effectiveFetchFields = computeEffectiveFetchFields(fields);

            Octane octaneClient = octaneCacheService.getOctaneClient(
                    workspace.getRootUrl(),
                    workspace.getClientId(),
                    workspace.getClientKey(),
                    Integer.parseInt(workspace.getSharedSpaceId()),
                    Integer.parseInt(workspace.getWorkspaceId()));

            Query query = buildQuery(filter.getEntityType(), clauses);

            GetEntities getEntities = octaneClient
                    .entityList("work_items")
                    .get()
                    .query(query)
                    .addFields(effectiveFetchFields.toArray(new String[0]))
                    .limit(effectivePreviewLimit);

            OctaneCollection<EntityModel> result = getEntities.execute();
            List<EntityModel> entities = result.stream().toList();

            // AI Summary generation — matches the real email-send flow exactly.
            boolean aiSummaryEnabled = fields.contains(AiSummaryService.AI_SUMMARY_FIELD);
            String[] aiSummaries = null;
            if (aiSummaryEnabled) {
                aiSummaries = new String[entities.size()];
                for (int i = 0; i < entities.size(); i++) {
                    EntityModel entity = entities.get(i);
                    String name = extractFieldValue("name", entity.getValue("name"));
                    String description = extractFieldValue("description", entity.getValue("description"));
                    String ticketId = extractFieldValue("id", entity.getValue("id"));
                    String comments = aiSummaryService.fetchComments(ticketId, workspace);
                    aiSummaries[i] = aiSummaryService.generateSummary(name, description, comments);
                }
            }

            // Display fields = user-selected fields minus the AI Summary pseudo-field.
            List<String> displayFields = fields.stream()
                    .filter(f -> !AiSummaryService.AI_SUMMARY_FIELD.equals(f))
                    .collect(Collectors.toList());

            // Flatten each EntityModel into a human-readable Map<fieldName, displayValue>.
            List<Map<String, String>> records = new ArrayList<>(entities.size());
            for (int i = 0; i < entities.size(); i++) {
                EntityModel entity = entities.get(i);
                Map<String, String> record = new LinkedHashMap<>();
                for (String field : displayFields) {
                    record.put(field, extractFieldValue(field, entity.getValue(field)));
                }
                if (aiSummaryEnabled && aiSummaries != null) {
                    record.put(AiSummaryService.AI_SUMMARY_FIELD, aiSummaries[i]);
                }
                records.add(record);
            }

            return PreviewResponse.builder()
                    .records(records)
                    .aiSummaryGenerated(aiSummaryEnabled)
                    .build();

        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize filter data", e);
        }
    }

    /**
     * Computes the effective set of fields to fetch from Octane for a given set of
     * user-selected field names.
     *
     * <p>Differences from the raw user-selected list:
     * <ol>
     *   <li>The AI Summary pseudo-field is stripped — it is never a real Octane field.</li>
     *   <li>When AI Summary is enabled, {@code name} and {@code description} are added as
     *       silent internal dependencies for AI generation, even if not chosen for display.</li>
     *   <li>{@code id} is always added for ticket hyperlink generation, regardless of whether
     *       the user selected it as a visible column.</li>
     * </ol>
     *
     * <p>Package-private to allow direct unit testing without mocking the Octane client.
     */
    List<String> computeEffectiveFetchFields(List<String> fields) {
        List<String> effectiveFetchFields = fields.stream()
                .filter(f -> !AiSummaryService.AI_SUMMARY_FIELD.equals(f))
                .collect(Collectors.toList());
        if (fields.contains(AiSummaryService.AI_SUMMARY_FIELD)) {
            // name and description are fetched silently for AI generation
            // regardless of what the user chose to show in the final email output.
            for (String dep : List.of("name", "description")) {
                if (!effectiveFetchFields.contains(dep)) {
                    effectiveFetchFields.add(dep);
                }
            }
        }
        // id is always fetched for ticket hyperlink generation (id and global_id_udf fields).
        if (!effectiveFetchFields.contains("id")) {
            effectiveFetchFields.add("id");
        }
        return effectiveFetchFields;
    }

    /**
     * Dynamically builds an Octane SDK Query from the entity type and a list
     * of criteria clauses.
     *
     * Each clause becomes a sub-query joined with AND.
     * Reference fields (those whose values look like IDs rather than phases)
     * use a nested Query.statement("id", IN, values) pattern.
     */
    private Query buildQuery(String entityType, List<FilterCriteriaClause> clauses) {
        // Start with subtype filter
        Query.QueryBuilder combined = Query.statement("subtype", QueryMethod.EqualTo, entityType);

        for (FilterCriteriaClause clause : clauses) {
            Query.QueryBuilder clauseBuilder = buildClause(clause);
            combined = combined.and(clauseBuilder);
        }

        return combined.build();
    }

    private Query.QueryBuilder buildClause(FilterCriteriaClause clause) {
        String[] values = clause.getValues().toArray(new String[0]);
        boolean negate = "NOT_IN".equalsIgnoreCase(clause.getOperator());

        if (isReferenceField(values)) {
            // Reference fields: field EqualTo (id IN [...])  or  NOT(field EqualTo (id IN [...]))
            Query.QueryBuilder inner = Query.statement(clause.getField(), QueryMethod.EqualTo,
                    Query.statement("id", QueryMethod.In, values));
            return negate ? Query.not(clause.getField(), QueryMethod.EqualTo,
                    Query.statement("id", QueryMethod.In, values)) : inner;
        } else {
            // Literal fields: field IN [...]  or  NOT(field IN [...])
            if (negate) {
                return Query.not(clause.getField(), QueryMethod.In, values);
            }
            return Query.statement(clause.getField(), QueryMethod.In, values);
        }
    }

    /**
     * Heuristic: values that contain a dot (like "phase.defect.closed") or are
     * long alphanumeric strings (like "pgxw2gl93dd60aldlqq5w7596") are reference IDs.
     */
    private boolean isReferenceField(String[] values) {
        if (values.length == 0) return false;
        for (String v : values) {
            if (v.contains(".") || v.length() > 15) return true;
            if (v.matches("^[0-9]+$")) return true; // IDs like "10001234567" are also references, not phases
        }
        return false;
    }

    /**
     * Extracts a display-friendly string from any FieldModel type.
     *
     * <p>For reference fields, delegates to {@link FieldExtractorRegistry}
     * so that field-specific sub-field preferences are applied automatically.
     *
     * @param fieldName the Octane field name (used for the extractor registry lookup)
     * @param fm        the raw field model (may be {@code null})
     */
    private String extractFieldValue(String fieldName, FieldModel<?> fm) {
        if (fm == null || !fm.hasValue() || fm.getValue() == null) {
            return "";
        }
        if (fm instanceof StringFieldModel sfm) {
            return sfm.getValue() != null ? sfm.getValue() : "";
        }
        if (fm instanceof LongFieldModel lfm) {
            return String.valueOf(lfm.getValue());
        }
        if (fm instanceof FloatFieldModel ffm) {
            return String.valueOf(ffm.getValue());
        }
        if (fm instanceof BooleanFieldModel bfm) {
            return String.valueOf(bfm.getValue());
        }
        if (fm instanceof DateFieldModel dfm) {
            return dfm.getValue() != null ? dfm.getValue().toString() : "";
        }
        if (fm instanceof MultiReferenceFieldModel mrfm) {
            return mrfm.getValue().stream()
                    .map(ref -> resolveRefName(fieldName, ref))
                    .collect(Collectors.joining(", "));
        }
        if (fm instanceof ReferenceFieldModel) {
            return fieldExtractorRegistry.forField(fieldName).extract(fm);
        }
        return fm.getValue().toString();
    }

    /**
     * Resolves the display name of one entity inside a multi-reference field,
     * using the same registry lookup as single references.
     */
    private String resolveRefName(String fieldName, EntityModel ref) {
        if (ref == null) return "";
        ReferenceFieldModel synthetic = new ReferenceFieldModel(fieldName, ref);
        return fieldExtractorRegistry.forField(fieldName).extract(synthetic);
    }

    private void enforceWorkspaceNotDisabled(Workspace workspace) {
        if (workspace.getStatus() == WorkspaceStatus.DISABLED) {
            throw new IllegalArgumentException("Workspace is disabled and cannot execute filters");
        }
    }

    private FilterDto resolveFilterDefinition(FilterDto dto) {
        boolean hasFilterQueryString = dto.getFilterQueryString() != null && !dto.getFilterQueryString().isBlank();
        if (hasFilterQueryString) {
            ParsedFilterQueryResponse parsed = parseFilterQueryString(dto.getFilterQueryString());
            return FilterDto.builder()
                    .workspaceId(dto.getWorkspaceId())
                    .title(dto.getTitle())
                    .description(dto.getDescription())
                    .entityType(dto.getEntityType())
                    .fields(parsed.getFields())
                    .criteria(parsed.getCriteria())
                    .filterQueryString(parsed.getFilterQueryString())
                    .build();
        }

        if (dto.getFields() == null || dto.getFields().isEmpty()) {
            throw new IllegalArgumentException("fields must not be empty");
        }
        if (dto.getCriteria() == null || dto.getCriteria().isEmpty()) {
            throw new IllegalArgumentException("criteria must not be empty");
        }
        for (FilterCriteriaClause clause : dto.getCriteria()) {
            validateClause(clause);
        }
        return dto;
    }

    private List<FilterCriteriaClause> parseCriteriaExpression(String queryRaw) {
        String normalizedQuery = stripWrappingDoubleQuotes(queryRaw == null ? "" : queryRaw.trim());
        if (normalizedQuery.isEmpty()) {
            throw new IllegalArgumentException("Invalid filter query string: query parameter is empty");
        }

        List<FilterCriteriaClause> clauses = new ArrayList<>();
        for (String rawClause : splitTopLevelClauses(normalizedQuery, false)) {
            String clauseToken = stripOuterParentheses(rawClause.trim());
            if (clauseToken.isEmpty()) {
                continue;
            }
            FilterCriteriaClause clause = parseClauseOrOrGroup(clauseToken);
            validateClause(clause);
            clauses.add(clause);
        }
        if (clauses.isEmpty()) {
            throw new IllegalArgumentException("Invalid filter query string: query parameter is empty");
        }
        return clauses;
    }

    private FilterCriteriaClause parseClause(String rawClause) {
        Matcher matcher = FILTER_QUERY_CLAUSE_PATTERN.matcher(rawClause);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid filter query clause: " + rawClause);
        }

        String field = matcher.group(1).trim();
        String operatorToken = matcher.group(2).trim().toUpperCase();
        String rawValues = matcher.group(3).trim();
        List<String> values = parseValueTokens(rawValues, rawClause);

        String mappedOperator;
        if ("EQ".equals(operatorToken) || "IN".equals(operatorToken)) {
            mappedOperator = "IN";
        } else if ("NEQ".equals(operatorToken) || "NOT_IN".equals(operatorToken)) {
            mappedOperator = "NOT_IN";
        } else {
            throw new IllegalArgumentException("Unsupported operator in clause: " + rawClause);
        }

        if (("EQ".equals(operatorToken) || "NEQ".equals(operatorToken)) && values.size() > 1) {
            throw new IllegalArgumentException("EQ/NEQ clauses must contain exactly one value: " + rawClause);
        }

        return FilterCriteriaClause.builder()
                .field(field)
                .operator(mappedOperator)
                .values(values)
                .build();
    }

    private FilterCriteriaClause parseClauseOrOrGroup(String rawClause) {
        List<String> orParts = splitTopLevelClauses(rawClause, true);
        if (orParts.size() == 1) {
            return parseClause(orParts.get(0).trim());
        }

        String resolvedField = null;
        String resolvedOperator = null;
        Set<String> mergedValues = new LinkedHashSet<>();

        for (String token : orParts) {
            FilterCriteriaClause part = parseClause(stripOuterParentheses(token.trim()));
            if ("NOT_IN".equalsIgnoreCase(part.getOperator())) {
                throw new IllegalArgumentException("OR groups do not support NOT_IN clauses: " + rawClause);
            }
            if (resolvedField == null) {
                resolvedField = part.getField();
                resolvedOperator = part.getOperator();
            } else {
                if (!resolvedField.equalsIgnoreCase(part.getField())) {
                    throw new IllegalArgumentException("OR group must use a single field: " + rawClause);
                }
                if (!resolvedOperator.equalsIgnoreCase(part.getOperator())) {
                    throw new IllegalArgumentException("OR group must use a single operator: " + rawClause);
                }
            }
            mergedValues.addAll(part.getValues());
        }

        return FilterCriteriaClause.builder()
                .field(resolvedField)
                .operator(resolvedOperator)
                .values(new ArrayList<>(mergedValues))
                .build();
    }

    private List<String> parseValueTokens(String rawValues, String rawClause) {
        String trimmed = rawValues.trim();
        String unwrapped = unwrapCarets(trimmed);
        if (unwrapped.startsWith("{") && unwrapped.endsWith("}")) {
            return parseReferenceIdValues(unwrapped, rawClause);
        }
        List<String> values = Arrays.stream(unwrapped.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Invalid filter query clause values: " + rawClause);
        }
        return values;
    }

    private List<String> parseReferenceIdValues(String rawReference, String rawClause) {
        String inner = stripOuterParentheses(rawReference.substring(1, rawReference.length() - 1).trim());
        if (inner.isEmpty() || "null".equalsIgnoreCase(inner)) {
            throw new IllegalArgumentException("Invalid filter query clause values: " + rawClause);
        }
        Set<String> values = new LinkedHashSet<>();
        for (String token : splitTopLevelClauses(inner, true)) {
            Matcher matcher = FILTER_QUERY_CLAUSE_PATTERN.matcher(stripOuterParentheses(token.trim()));
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Invalid reference filter in clause: " + rawClause);
            }
            String innerField = matcher.group(1).trim();
            String innerOperator = matcher.group(2).trim().toUpperCase();
            if (!"ID".equalsIgnoreCase(innerField)) {
                throw new IllegalArgumentException("Reference filters must use id field: " + rawClause);
            }
            if (!"EQ".equals(innerOperator) && !"IN".equals(innerOperator)) {
                throw new IllegalArgumentException("Reference filters support EQ/IN only: " + rawClause);
            }
            String innerRawValue = matcher.group(3).trim();
            values.addAll(Arrays.stream(unwrapCarets(innerRawValue).split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList()));
        }
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Invalid filter query clause values: " + rawClause);
        }
        return new ArrayList<>(values);
    }

    private List<String> splitTopLevelClauses(String input, boolean splitOr) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int parenDepth = 0;
        int braceDepth = 0;
        boolean inCaret = false;

        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (ch == '^' && (i == 0 || input.charAt(i - 1) != '\\')) {
                inCaret = !inCaret;
                current.append(ch);
                continue;
            }
            if (inCaret) {
                current.append(ch);
                continue;
            }
            if (ch == '(') {
                parenDepth++;
                current.append(ch);
                continue;
            }
            if (ch == ')') {
                parenDepth = Math.max(0, parenDepth - 1);
                current.append(ch);
                continue;
            }
            if (ch == '{') {
                braceDepth++;
                current.append(ch);
                continue;
            }
            if (ch == '}') {
                braceDepth = Math.max(0, braceDepth - 1);
                current.append(ch);
                continue;
            }

            if (parenDepth == 0 && braceDepth == 0) {
                if (splitOr) {
                    if (ch == '|' && i + 1 < input.length() && input.charAt(i + 1) == '|') {
                        addToken(parts, current);
                        i++;
                        continue;
                    }
                    if (startsWithWordIgnoreCase(input, i, "OR")) {
                        addToken(parts, current);
                        i = i + 1;
                        continue;
                    }
                } else {
                    if (ch == ';') {
                        addToken(parts, current);
                        continue;
                    }
                    if (startsWithWordIgnoreCase(input, i, "AND")) {
                        addToken(parts, current);
                        i = i + 2;
                        continue;
                    }
                }
            }
            current.append(ch);
        }
        addToken(parts, current);
        return parts;
    }

    private void addToken(List<String> parts, StringBuilder token) {
        String value = token.toString().trim();
        if (!value.isEmpty()) {
            parts.add(value);
        }
        token.setLength(0);
    }

    private boolean startsWithWordIgnoreCase(String input, int index, String word) {
        int end = index + word.length();
        if (end > input.length()) {
            return false;
        }
        if (!input.regionMatches(true, index, word, 0, word.length())) {
            return false;
        }
        char before = index > 0 ? input.charAt(index - 1) : ' ';
        char after = end < input.length() ? input.charAt(end) : ' ';
        return !Character.isLetterOrDigit(before) && before != '_'
                && !Character.isLetterOrDigit(after) && after != '_';
    }

    private String stripOuterParentheses(String value) {
        String trimmed = value.trim();
        while (trimmed.startsWith("(") && trimmed.endsWith(")") && isSingleWrappedExpression(trimmed)) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    private boolean isSingleWrappedExpression(String expr) {
        int parenDepth = 0;
        int braceDepth = 0;
        boolean inCaret = false;
        for (int i = 0; i < expr.length(); i++) {
            char ch = expr.charAt(i);
            if (ch == '^' && (i == 0 || expr.charAt(i - 1) != '\\')) {
                inCaret = !inCaret;
                continue;
            }
            if (inCaret) {
                continue;
            }
            if (ch == '{') {
                braceDepth++;
            } else if (ch == '}') {
                braceDepth = Math.max(0, braceDepth - 1);
            } else if (ch == '(') {
                parenDepth++;
            } else if (ch == ')') {
                parenDepth--;
                if (parenDepth == 0 && i < expr.length() - 1 && braceDepth == 0) {
                    return false;
                }
            }
        }
        return parenDepth == 0;
    }

    private String stripWrappingDoubleQuotes(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1).trim();
        }
        return value;
    }

    private String unwrapCarets(String valueToken) {
        if (valueToken.length() >= 2 && valueToken.startsWith("^") && valueToken.endsWith("^")) {
            return valueToken.substring(1, valueToken.length() - 1);
        }
        return valueToken;
    }

    String buildFilterQueryString(List<String> fields, List<FilterCriteriaClause> criteria) {
        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException("fields must not be empty");
        }
        if (criteria == null || criteria.isEmpty()) {
            throw new IllegalArgumentException("criteria must not be empty");
        }

        String fieldsPart = fields.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(","));
        if (fieldsPart.isEmpty()) {
            throw new IllegalArgumentException("fields must not be empty");
        }

        String queryPart = criteria.stream()
                .peek(this::validateClause)
                .map(this::serializeClause)
                .collect(Collectors.joining(" AND "));

        return "fields=" + fieldsPart + "&query=" + queryPart;
    }

    private String serializeClause(FilterCriteriaClause clause) {
        List<String> values = clause.getValues().stream().map(String::trim).collect(Collectors.toList());
        String valueLiteral = "^" + String.join(",", values) + "^";
        String operator;
        if ("NOT_IN".equalsIgnoreCase(clause.getOperator())) {
            operator = values.size() == 1 ? "NEQ" : "NOT_IN";
        } else {
            operator = values.size() == 1 ? "EQ" : "IN";
        }
        return clause.getField().trim() + " " + operator + " " + valueLiteral;
    }

    private void validateClause(FilterCriteriaClause clause) {
        if (clause == null) {
            throw new IllegalArgumentException("criteria contains an empty clause");
        }
        if (clause.getField() == null || clause.getField().isBlank()) {
            throw new IllegalArgumentException("criteria field must not be blank");
        }
        if (clause.getOperator() == null || clause.getOperator().isBlank()) {
            throw new IllegalArgumentException("criteria operator must not be blank");
        }
        String operator = clause.getOperator().toUpperCase();
        if (!"IN".equals(operator) && !"NOT_IN".equals(operator)) {
            throw new IllegalArgumentException("criteria operator must be IN or NOT_IN");
        }
        if (clause.getValues() == null || clause.getValues().isEmpty()) {
            throw new IllegalArgumentException("criteria values must not be empty");
        }
        if (clause.getValues().stream().anyMatch(v -> v == null || v.isBlank())) {
            throw new IllegalArgumentException("criteria values must not contain blanks");
        }
    }

    private Map<String, String> parseFilterQueryParams(String filterQueryString) {
        Map<String, String> params = new LinkedHashMap<>();
        for (String part : filterQueryString.split("&")) {
            String pair = part.trim();
            if (pair.isEmpty()) {
                continue;
            }
            int separator = pair.indexOf('=');
            if (separator <= 0 || separator == pair.length() - 1) {
                throw new IllegalArgumentException("Invalid filter query segment: " + pair);
            }
            String key = decodeQueryComponent(pair.substring(0, separator)).trim().toLowerCase();
            String value = decodeQueryComponent(pair.substring(separator + 1)).trim();
            if (!"fields".equals(key) && !"query".equals(key)) {
                throw new IllegalArgumentException("Unsupported filter query parameter: " + key);
            }
            params.put(key, value);
        }
        return params;
    }

    private String decodeQueryComponent(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
