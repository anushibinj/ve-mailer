package com.anushibinj.veemailer.service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.anushibinj.veemailer.dto.FilterDto;
import com.anushibinj.veemailer.model.Filter;
import com.anushibinj.veemailer.model.FilterCriteriaClause;
import com.anushibinj.veemailer.model.Workspace;
import com.anushibinj.veemailer.repository.FilterRepository;
import com.anushibinj.veemailer.repository.WorkspaceRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hpe.adm.nga.sdk.Octane;
import com.hpe.adm.nga.sdk.entities.OctaneCollection;
import com.hpe.adm.nga.sdk.model.EntityModel;
import com.hpe.adm.nga.sdk.query.Query;
import com.hpe.adm.nga.sdk.query.QueryMethod;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

@Slf4j
@Service
@RequiredArgsConstructor
public class FilterService {

    private final FilterRepository filterRepository;
    private final WorkspaceRepository workspaceRepository;
    private final OctaneCacheService octaneCacheService;
    private final ObjectMapper objectMapper;

    @Value("${veemailer.query.limit:25}")
    private int queryLimit;

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

            OctaneCollection<EntityModel> result = octaneClient
                    .entityList("work_items")
                    .get()
                    .query(query)
                    .addFields(effectiveFetchFields.toArray(new String[0]))
                    .limit(queryLimit) // configurable via veemailer.query.limit
                    .execute();

            return result.stream().toList();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize filter data", e);
        }
    }

    /** Returns the configured maximum number of results returned per filter execution. */
    public int getQueryLimit() {
        return queryLimit;
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
        }
        return false;
    }
}
