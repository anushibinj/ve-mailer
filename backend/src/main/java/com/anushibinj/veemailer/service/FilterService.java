package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.dto.FilterDto;
import com.anushibinj.veemailer.model.Filter;
import com.anushibinj.veemailer.model.FilterCriteriaClause;
import com.anushibinj.veemailer.model.Workspace;
import com.anushibinj.veemailer.repository.FilterRepository;
import com.anushibinj.veemailer.repository.WorkspaceRepository;
import com.anushibinj.veemailer.service.ve.ValueEdgeProperties;
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
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FilterService {

    private final FilterRepository filterRepository;
    private final WorkspaceRepository workspaceRepository;
    private final OctaneCacheService octaneCacheService;
    private final ValueEdgeProperties valueEdgeProperties;
    private final ObjectMapper objectMapper;

    /**
     * Persist a new filter template.
     */
    public Filter createFilter(FilterDto dto) {
        try {
            String fieldsJson = objectMapper.writeValueAsString(dto.getFields());
            String criteriaJson = objectMapper.writeValueAsString(dto.getCriteria());

            Filter filter = Filter.builder()
                    .title(dto.getTitle())
                    .description(dto.getDescription())
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

            Octane octaneClient = octaneCacheService.getOctaneClient(
                    valueEdgeProperties.getServerUrl(),
                    workspace.getClientId(),
                    workspace.getClientKey(),
                    Integer.parseInt(workspace.getSharedSpaceId()),
                    Integer.parseInt(workspace.getWorkspaceId()));

            Query query = buildQuery(filter.getEntityType(), clauses);

            OctaneCollection<EntityModel> result = octaneClient
                    .entityList("work_items")
                    .get()
                    .query(query)
                    .addFields(fields.toArray(new String[0]))
                    .execute();

            return result.stream().toList();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize filter data", e);
        }
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
        QueryMethod method = resolveMethod(clause.getOperator());

        // Reference fields use nested id query: field EqualTo (id IN [...])
        // We detect this by the operator or let the caller decide; for simplicity
        // we check if values look like Octane IDs (contain dots or long alphanumeric).
        // The Octane SDK pattern for reference fields is always:
        //   Query.statement(field, EqualTo, Query.statement("id", In, values))
        Query.QueryBuilder inner;
        if (isReferenceField(values)) {
            inner = buildReferenceClause(clause.getField(), method, values);
        } else {
            inner = buildLiteralClause(clause.getField(), method, values);
        }

        if (clause.isNegate()) {
            return Query.not(clause.getField(), QueryMethod.EqualTo,
                    Query.statement("id", QueryMethod.In, values));
        }
        return inner;
    }

    /**
     * Builds a clause for a reference field:
     *   field EqualTo (id IN [val1, val2, ...])
     */
    private Query.QueryBuilder buildReferenceClause(String field, QueryMethod method, String[] values) {
        return Query.statement(field, QueryMethod.EqualTo,
                Query.statement("id", QueryMethod.In, values));
    }

    /**
     * Builds a clause for a literal / simple field:
     *   field IN [val1, val2, ...]   or   field EqualTo val
     */
    private Query.QueryBuilder buildLiteralClause(String field, QueryMethod method, String[] values) {
        if (values.length == 1) {
            return Query.statement(field, method, values[0]);
        }
        return Query.statement(field, QueryMethod.In, values);
    }

    private QueryMethod resolveMethod(String operator) {
        return switch (operator.toUpperCase()) {
            case "EQUAL_TO" -> QueryMethod.EqualTo;
            case "IN" -> QueryMethod.In;
            default -> throw new IllegalArgumentException("Unsupported operator: " + operator);
        };
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
