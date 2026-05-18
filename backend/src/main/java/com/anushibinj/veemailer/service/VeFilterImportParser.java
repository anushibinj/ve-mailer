package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.dto.FilterDto;
import com.anushibinj.veemailer.dto.VeFilterImportDto;
import com.anushibinj.veemailer.model.FilterCriteriaClause;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the JSON format exported by ValueEdge (Microfocus ALM Octane) into a
 * {@link FilterDto} that can be persisted via {@link FilterService#createFilter}.
 *
 * <p>Expected top-level structure:
 * <pre>
 * {
 *   "params": {
 *     "contentFilter": [
 *       {
 *         "operator": "AND",
 *         "filters": [
 *           {
 *             "lExpression": { "operator": "PROPERTY", "value": "<fieldName>" },
 *             "operator": "IN" | "NOT_IN",
 *             "rExpression": [
 *               { "operator": "LITERAL", "value": { "id": "<entityId>", "path": null } },
 *               ...
 *             ],
 *             "isIncomplete": false
 *           },
 *           ...
 *         ]
 *       }
 *     ],
 *     "columns": "[\"id\",\"name\",\"owner\"]"
 *   }
 * }
 * </pre>
 *
 * <p>Clauses where {@code isIncomplete} is {@code true} are silently skipped.
 */
@Service
public class VeFilterImportParser {

    private final ObjectMapper objectMapper;

    public VeFilterImportParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Parses {@code dto.rawJson} and builds a {@link FilterDto} ready for persistence.
     *
     * @param dto the import payload carrying the raw JSON and user-supplied metadata
     * @return a populated {@link FilterDto} (workspaceId is NOT set here — the caller must set it)
     * @throws IllegalArgumentException if the JSON is missing required structure
     * @throws IOException              if the JSON cannot be parsed at all
     */
    public FilterDto parse(VeFilterImportDto dto) throws IOException {
        JsonNode root = objectMapper.readTree(dto.getRawJson());

        JsonNode params = root.path("params");
        if (params.isMissingNode()) {
            throw new IllegalArgumentException("Import JSON is missing the top-level 'params' object.");
        }

        List<FilterCriteriaClause> criteria = parseCriteria(params);
        List<String> fields = parseColumns(params);

        return FilterDto.builder()
                .title(dto.getTitle())
                .description(dto.getDescription())
                .entityType(dto.getEntityType())
                .fields(fields)
                .criteria(criteria)
                .build();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private List<FilterCriteriaClause> parseCriteria(JsonNode params) {
        List<FilterCriteriaClause> result = new ArrayList<>();

        JsonNode contentFilter = params.path("contentFilter");
        if (contentFilter.isMissingNode() || !contentFilter.isArray() || contentFilter.isEmpty()) {
            return result; // no criteria — the caller's @NotEmpty on FilterDto will catch this
        }

        JsonNode filters = contentFilter.get(0).path("filters");
        if (filters.isMissingNode() || !filters.isArray()) {
            return result;
        }

        for (JsonNode filterNode : filters) {
            // Skip incomplete clauses
            if (filterNode.path("isIncomplete").asBoolean(false)) {
                continue;
            }

            String field = filterNode.path("lExpression").path("value").asText(null);
            String operator = filterNode.path("operator").asText(null);

            if (field == null || field.isBlank() || operator == null || operator.isBlank()) {
                continue; // skip malformed clause
            }

            List<String> values = new ArrayList<>();
            JsonNode rExpression = filterNode.path("rExpression");
            if (rExpression.isArray()) {
                for (JsonNode rNode : rExpression) {
                    JsonNode valueNode = rNode.path("value");
                    // Reference values carry an "id" field; plain literals are text nodes
                    if (valueNode.isObject()) {
                        String id = valueNode.path("id").asText(null);
                        if (id != null && !id.isBlank()) {
                            values.add(id);
                        }
                    } else if (valueNode.isTextual()) {
                        String text = valueNode.asText();
                        if (!text.isBlank()) {
                            values.add(text);
                        }
                    }
                }
            }

            if (values.isEmpty()) {
                continue; // nothing to filter on — skip
            }

            result.add(FilterCriteriaClause.builder()
                    .field(field)
                    .operator(operator)
                    .values(values)
                    .build());
        }

        return result;
    }

    private List<String> parseColumns(JsonNode params) throws IOException {
        JsonNode columnsNode = params.path("columns");

        if (columnsNode.isMissingNode()) {
            return List.of();
        }

        // "columns" is a JSON-encoded string, e.g. "[\"id\",\"name\"]"
        String columnsRaw = columnsNode.asText();
        JsonNode columnsArray = objectMapper.readTree(columnsRaw);

        List<String> fields = new ArrayList<>();
        if (columnsArray.isArray()) {
            for (JsonNode col : columnsArray) {
                String colName = col.asText(null);
                if (colName != null && !colName.isBlank()) {
                    fields.add(colName);
                }
            }
        }

        return fields;
    }
}
