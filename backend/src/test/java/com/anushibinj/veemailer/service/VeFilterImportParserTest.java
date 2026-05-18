package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.dto.FilterDto;
import com.anushibinj.veemailer.dto.VeFilterImportDto;
import com.anushibinj.veemailer.model.FilterCriteriaClause;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VeFilterImportParserTest {

    private VeFilterImportParser parser;

    @BeforeEach
    void setUp() {
        parser = new VeFilterImportParser(new ObjectMapper());
    }

    // -------------------------------------------------------------------------
    // Happy-path
    // -------------------------------------------------------------------------

    @Test
    void parse_typicalExport_extractsCriteriaAndFields() throws IOException {
        String json = """
                {
                  "params": {
                    "contentFilter": [
                      {
                        "operator": "AND",
                        "filters": [
                          {
                            "lExpression": { "operator": "PROPERTY", "value": "product_udf" },
                            "operator": "IN",
                            "rExpression": [
                              { "operator": "LITERAL", "value": { "id": "42z3y3le5o36qfqqzv0oxyj5w", "path": null } }
                            ],
                            "isIncomplete": false
                          }
                        ]
                      }
                    ],
                    "columns": "[\\"id\\",\\"name\\",\\"owner\\",\\"phase\\",\\"product_udf\\"]"
                  }
                }
                """;

        VeFilterImportDto dto = VeFilterImportDto.builder()
                .rawJson(json)
                .title("My Filter")
                .description("Test description")
                .entityType("defect")
                .build();

        FilterDto result = parser.parse(dto);

        assertEquals("My Filter", result.getTitle());
        assertEquals("Test description", result.getDescription());
        assertEquals("defect", result.getEntityType());

        List<String> fields = result.getFields();
        assertEquals(List.of("id", "name", "owner", "phase", "product_udf"), fields);

        List<FilterCriteriaClause> criteria = result.getCriteria();
        assertEquals(1, criteria.size());
        FilterCriteriaClause clause = criteria.get(0);
        assertEquals("product_udf", clause.getField());
        assertEquals("IN", clause.getOperator());
        assertEquals(List.of("42z3y3le5o36qfqqzv0oxyj5w"), clause.getValues());
    }

    @Test
    void parse_notInOperator_preserved() throws IOException {
        String json = """
                {
                  "params": {
                    "contentFilter": [
                      {
                        "operator": "AND",
                        "filters": [
                          {
                            "lExpression": { "operator": "PROPERTY", "value": "phase" },
                            "operator": "NOT_IN",
                            "rExpression": [
                              { "operator": "LITERAL", "value": { "id": "closed_id", "path": null } }
                            ],
                            "isIncomplete": false
                          }
                        ]
                      }
                    ],
                    "columns": "[\\"id\\",\\"name\\"]"
                  }
                }
                """;

        VeFilterImportDto dto = baseDto(json);
        FilterDto result = parser.parse(dto);

        assertEquals("NOT_IN", result.getCriteria().get(0).getOperator());
    }

    @Test
    void parse_multipleCriteria_allIncluded() throws IOException {
        String json = """
                {
                  "params": {
                    "contentFilter": [
                      {
                        "operator": "AND",
                        "filters": [
                          {
                            "lExpression": { "operator": "PROPERTY", "value": "phase" },
                            "operator": "IN",
                            "rExpression": [
                              { "operator": "LITERAL", "value": { "id": "phase_id_1", "path": null } },
                              { "operator": "LITERAL", "value": { "id": "phase_id_2", "path": null } }
                            ],
                            "isIncomplete": false
                          },
                          {
                            "lExpression": { "operator": "PROPERTY", "value": "severity" },
                            "operator": "IN",
                            "rExpression": [
                              { "operator": "LITERAL", "value": { "id": "sev_high", "path": null } }
                            ],
                            "isIncomplete": false
                          }
                        ]
                      }
                    ],
                    "columns": "[\\"id\\",\\"name\\",\\"phase\\",\\"severity\\"]"
                  }
                }
                """;

        FilterDto result = parser.parse(baseDto(json));

        assertEquals(2, result.getCriteria().size());
        assertEquals(List.of("phase_id_1", "phase_id_2"), result.getCriteria().get(0).getValues());
        assertEquals("severity", result.getCriteria().get(1).getField());
    }

    // -------------------------------------------------------------------------
    // Incomplete clauses are skipped
    // -------------------------------------------------------------------------

    @Test
    void parse_incompleteClausesSkipped() throws IOException {
        String json = """
                {
                  "params": {
                    "contentFilter": [
                      {
                        "operator": "AND",
                        "filters": [
                          {
                            "lExpression": { "operator": "PROPERTY", "value": "phase" },
                            "operator": "IN",
                            "rExpression": [
                              { "operator": "LITERAL", "value": { "id": "phase_id_1", "path": null } }
                            ],
                            "isIncomplete": true
                          },
                          {
                            "lExpression": { "operator": "PROPERTY", "value": "severity" },
                            "operator": "IN",
                            "rExpression": [
                              { "operator": "LITERAL", "value": { "id": "sev_high", "path": null } }
                            ],
                            "isIncomplete": false
                          }
                        ]
                      }
                    ],
                    "columns": "[\\"id\\",\\"name\\"]"
                  }
                }
                """;

        FilterDto result = parser.parse(baseDto(json));

        assertEquals(1, result.getCriteria().size());
        assertEquals("severity", result.getCriteria().get(0).getField());
    }

    // -------------------------------------------------------------------------
    // Edge cases
    // -------------------------------------------------------------------------

    @Test
    void parse_emptyContentFilter_returnsCriteriaEmptyList() throws IOException {
        String json = """
                {
                  "params": {
                    "contentFilter": [],
                    "columns": "[\\"id\\"]"
                  }
                }
                """;

        FilterDto result = parser.parse(baseDto(json));
        assertTrue(result.getCriteria().isEmpty());
        assertEquals(List.of("id"), result.getFields());
    }

    @Test
    void parse_missingColumnsNode_returnsEmptyFields() throws IOException {
        String json = """
                {
                  "params": {
                    "contentFilter": []
                  }
                }
                """;

        FilterDto result = parser.parse(baseDto(json));
        assertTrue(result.getFields().isEmpty());
    }

    @Test
    void parse_missingParamsNode_throwsIllegalArgument() {
        String json = "{ \"other\": {} }";
        VeFilterImportDto dto = baseDto(json);

        assertThrows(IllegalArgumentException.class, () -> parser.parse(dto));
    }

    @Test
    void parse_invalidJson_throwsIOException() {
        VeFilterImportDto dto = baseDto("not-valid-json{{{");

        assertThrows(IOException.class, () -> parser.parse(dto));
    }

    @Test
    void parse_metadataPassedThrough() throws IOException {
        String json = """
                {
                  "params": {
                    "contentFilter": [],
                    "columns": "[\\"id\\"]"
                  }
                }
                """;

        VeFilterImportDto dto = VeFilterImportDto.builder()
                .rawJson(json)
                .title("Imported Title")
                .description("Some desc")
                .entityType("story")
                .build();

        FilterDto result = parser.parse(dto);
        assertEquals("Imported Title", result.getTitle());
        assertEquals("Some desc", result.getDescription());
        assertEquals("story", result.getEntityType());
        assertNull(result.getWorkspaceId()); // caller must set this
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private VeFilterImportDto baseDto(String rawJson) {
        return VeFilterImportDto.builder()
                .rawJson(rawJson)
                .title("Test Filter")
                .entityType("defect")
                .build();
    }
}
