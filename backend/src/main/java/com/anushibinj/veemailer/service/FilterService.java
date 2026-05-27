package com.anushibinj.veemailer.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.anushibinj.veemailer.dto.FilterDto;
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
        try {
            String fieldsJson = objectMapper.writeValueAsString(dto.getFields());
            String criteriaJson = objectMapper.writeValueAsString(dto.getCriteria());

            Filter filter = Filter.builder()
                    .title(dto.getTitle())
                    .description(dto.getDescription())
                    .workspace(workspace)
                    .entityType(dto.getEntityType())
                    .fields(fieldsJson)
                    .criteria(criteriaJson)
                    .build();

            return filterRepository.save(filter);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize filter data", e);
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
        try {
            String fieldsJson = objectMapper.writeValueAsString(dto.getFields());
            String criteriaJson = objectMapper.writeValueAsString(dto.getCriteria());

            filter.setTitle(dto.getTitle());
            filter.setDescription(dto.getDescription());
            filter.setEntityType(dto.getEntityType());
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
}
