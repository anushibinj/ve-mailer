package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.repository.EmailSubscriberRepository;
import com.anushibinj.veemailer.repository.FilterRepository;
import com.anushibinj.veemailer.repository.WorkspaceRepository;
import com.anushibinj.veemailer.dto.ParsedFilterQueryResponse;
import com.anushibinj.veemailer.model.FilterCriteriaClause;
import com.anushibinj.veemailer.service.extractor.FieldExtractorRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hpe.adm.nga.sdk.model.EntityModel;
import com.hpe.adm.nga.sdk.model.StringFieldModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link FilterService#computeEffectiveFetchFields(List)}.
 *
 * <p>This method isolates the field-computation logic so it can be tested
 * without mocking the Octane SDK or database layer.
 */
@ExtendWith(MockitoExtension.class)
class FilterServiceTest {

    @Mock private FilterRepository filterRepository;
    @Mock private WorkspaceRepository workspaceRepository;
    @Mock private EmailSubscriberRepository emailSubscriberRepository;
    @Mock private OctaneCacheService octaneCacheService;
    @Mock private GeneralSettingsService generalSettingsService;
    @Mock private AiSummaryService aiSummaryService;
    @Mock private FieldExtractorRegistry fieldExtractorRegistry;
    @Mock private WorkspaceService workspaceService;

    private FilterService filterService;

    @BeforeEach
    void setUp() {
        filterService = new FilterService(
                filterRepository, workspaceRepository, emailSubscriberRepository,
                octaneCacheService, new ObjectMapper(), generalSettingsService,
                aiSummaryService, fieldExtractorRegistry, workspaceService);
    }

    @Test
    void testComputeEffectiveFetchFields_AlwaysIncludesId() {
        // id must be present even when the user did not select it.
        List<String> result = filterService.computeEffectiveFetchFields(List.of("name", "phase"));
        assertTrue(result.contains("id"), "id must always be in effective fetch fields for hyperlink generation");
    }

    @Test
    void testComputeEffectiveFetchFields_DeduplicatesId() {
        // If the user explicitly selected id, it must not appear twice.
        List<String> result = filterService.computeEffectiveFetchFields(List.of("id", "phase"));
        assertEquals(1, result.stream().filter("id"::equals).count(),
                "id must appear exactly once even when user selected it");
    }

    @Test
    void testComputeEffectiveFetchFields_RemovesAiSummaryPseudoField() {
        // The AI Summary pseudo-field must never be forwarded to Octane.
        List<String> result = filterService.computeEffectiveFetchFields(
                List.of(AiSummaryService.AI_SUMMARY_FIELD, "phase"));
        assertFalse(result.contains(AiSummaryService.AI_SUMMARY_FIELD),
                "AI Summary pseudo-field must be stripped from effective fetch fields");
    }

    @Test
    void testComputeEffectiveFetchFields_AiSummaryAddsNameAndDescription() {
        // When AI Summary is enabled, name and description must be silently added.
        List<String> result = filterService.computeEffectiveFetchFields(
                List.of(AiSummaryService.AI_SUMMARY_FIELD, "phase"));
        assertTrue(result.contains("name"), "name must be added when AI Summary is enabled");
        assertTrue(result.contains("description"), "description must be added when AI Summary is enabled");
    }

    @Test
    void testComputeEffectiveFetchFields_AiSummary_DeduplicatesNameAndDescription() {
        // name and description must not be duplicated if user already selected them.
        List<String> result = filterService.computeEffectiveFetchFields(
                List.of(AiSummaryService.AI_SUMMARY_FIELD, "name", "description", "phase"));
        assertEquals(1, result.stream().filter("name"::equals).count(),
                "name must appear exactly once");
        assertEquals(1, result.stream().filter("description"::equals).count(),
                "description must appear exactly once");
    }

    @Test
    void testComputeEffectiveFetchFields_NoAiSummary_DoesNotAddNameDescription() {
        // Without AI Summary, name and description are not silently added.
        List<String> result = filterService.computeEffectiveFetchFields(List.of("phase", "owner"));
        assertFalse(result.contains("name"), "name must not be added when AI Summary is not enabled");
        assertFalse(result.contains("description"), "description must not be added when AI Summary is not enabled");
        // id is still always added
        assertTrue(result.contains("id"), "id must still be present");
    }

    @Test
    void testComputeEffectiveFetchFields_TriageSlaAddsCreationTimeAndRemovesPseudoField() {
        List<String> result = filterService.computeEffectiveFetchFields(
                List.of(TriageSlaPolicy.TRIAGE_SLA_FIELD, "phase"));
        assertFalse(result.contains(TriageSlaPolicy.TRIAGE_SLA_FIELD),
                "Triage SLA pseudo-field must be stripped from effective fetch fields");
        assertTrue(result.contains(TriageSlaPolicy.CREATION_TIME_FIELD),
                "creation_time must be fetched when Triage SLA is enabled");
    }

    @Test
    void testSortByTriageSlaAgeIfEnabled_SortsDescendingByDaysOld() {
        EntityModel newest = new EntityModel(Set.of(
                new StringFieldModel("id", "1"),
                new StringFieldModel("creation_time", "2099-01-01T00:00:00Z")
        ));
        EntityModel middle = new EntityModel(Set.of(
                new StringFieldModel("id", "2"),
                new StringFieldModel("creation_time", "2026-07-10T00:00:00Z")
        ));
        EntityModel oldest = new EntityModel(Set.of(
                new StringFieldModel("id", "3"),
                new StringFieldModel("creation_time", "2026-07-01T00:00:00Z")
        ));

        List<EntityModel> sorted = filterService.sortByTriageSlaAgeIfEnabled(
                List.of(newest, middle, oldest),
                List.of("id", TriageSlaPolicy.TRIAGE_SLA_FIELD),
                null);

        assertEquals("3", ((StringFieldModel) sorted.get(0).getValue("id")).getValue(),
                "oldest ticket must come first");
        assertEquals("2", ((StringFieldModel) sorted.get(1).getValue("id")).getValue(),
                "middle-aged ticket must come second");
        assertEquals("1", ((StringFieldModel) sorted.get(2).getValue("id")).getValue(),
                "newest ticket must come last");
    }

    @Test
    void testComputeEffectiveFetchFields_DoesNotMutateInput() {
        // The input list must not be modified.
        List<String> input = new ArrayList<>(List.of("phase", "owner"));
        filterService.computeEffectiveFetchFields(input);
        assertEquals(List.of("phase", "owner"), input, "input list must not be mutated");
    }

    @Test
    void testParseFilterQueryString_ParsesEqAndNormalizes() {
        ParsedFilterQueryResponse parsed = filterService.parseFilterQueryString("fields=id,name&query=name EQ ^*Case360*^");
        assertEquals(List.of("id", "name"), parsed.getFields());
        assertEquals(1, parsed.getCriteria().size());
        assertEquals("name", parsed.getCriteria().get(0).getField());
        assertEquals("IN", parsed.getCriteria().get(0).getOperator());
        assertEquals(List.of("*Case360*"), parsed.getCriteria().get(0).getValues());
        assertEquals("fields=id,name&query=name EQ ^*Case360*^", parsed.getFilterQueryString());
    }

    @Test
    void testParseFilterQueryString_ParsesAndClauses() {
        ParsedFilterQueryResponse parsed = filterService.parseFilterQueryString(
                "fields=id,name,phase&query=name EQ ^*Case360*^ AND phase NOT_IN ^phase.defect.closed,phase.defect.rejected^");
        assertEquals(2, parsed.getCriteria().size());
        assertEquals("IN", parsed.getCriteria().get(0).getOperator());
        assertEquals("NOT_IN", parsed.getCriteria().get(1).getOperator());
        assertEquals(List.of("phase.defect.closed", "phase.defect.rejected"),
                parsed.getCriteria().get(1).getValues());
    }

    @Test
    void testParseFilterQueryString_ParsesQuotedOctaneQueryWithCrossFilterAndOr() {
        ParsedFilterQueryResponse parsed = filterService.parseFilterQueryString(
                "fields=id,name,owner,phase&query=\"owner EQ {id EQ 8666};(phase EQ {id EQ ^pgxw2gll8xe6du9y1jx87596z^}||phase EQ {id EQ ^dk9y4yv0r3w6dcy1r8ny94xv8^})\"");

        assertEquals(List.of("id", "name", "owner", "phase"), parsed.getFields());
        assertEquals(2, parsed.getCriteria().size());

        assertEquals("owner", parsed.getCriteria().get(0).getField());
        assertEquals("IN", parsed.getCriteria().get(0).getOperator());
        assertEquals(List.of("8666"), parsed.getCriteria().get(0).getValues());
        assertEquals(Boolean.TRUE, parsed.getCriteria().get(0).getReferenceValues());

        assertEquals("phase", parsed.getCriteria().get(1).getField());
        assertEquals("IN", parsed.getCriteria().get(1).getOperator());
        assertEquals(List.of("pgxw2gll8xe6du9y1jx87596z", "dk9y4yv0r3w6dcy1r8ny94xv8"),
                parsed.getCriteria().get(1).getValues());
        assertEquals(Boolean.TRUE, parsed.getCriteria().get(1).getReferenceValues());
    }

    @Test
    void testParseFilterQueryString_RejectsOrAcrossDifferentFields() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> filterService.parseFilterQueryString(
                        "fields=id,name&query=(owner EQ {id EQ 1}||phase EQ {id EQ ^phase.defect.new^})"));
        assertTrue(ex.getMessage().contains("OR group must use a single field"));
    }

    @Test
    void testParseFilterQueryString_InvalidSegmentThrows() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> filterService.parseFilterQueryString("fields=id,name&query"));
        assertTrue(ex.getMessage().contains("Invalid filter query segment"));
    }

    @Test
    void testBuildFilterQueryString_SerializesCriteria() {
        String output = filterService.buildFilterQueryString(
                List.of("id", "name"),
                List.of(
                        FilterCriteriaClause.builder().field("name").operator("IN").values(List.of("*Case360*")).build(),
                        FilterCriteriaClause.builder().field("phase").operator("NOT_IN")
                                .values(List.of("phase.defect.closed", "phase.defect.rejected")).build()
                ));
        assertEquals("fields=id,name&query=name EQ ^*Case360*^ AND phase NEQ {id IN phase.defect.closed,phase.defect.rejected}",
                output);
    }

    @Test
    void testBuildFilterQueryString_SerializesReferenceCriteriaAsIdExpressions() {
        String output = filterService.buildFilterQueryString(
                List.of("id", "name"),
                List.of(FilterCriteriaClause.builder()
                        .field("code_review_owner_udf")
                        .operator("IN")
                        .values(List.of("8666"))
                        .referenceValues(true)
                        .build()));
        assertEquals("fields=id,name&query=code_review_owner_udf EQ {id IN 8666}", output);
    }

    @Test
    void testParseFilterQueryString_ParsesOrderBy() {
        ParsedFilterQueryResponse parsed = filterService.parseFilterQueryString(
                "fields=id,name&query=name EQ ^*Case360*^&order_by=creation_time");
        assertEquals("creation_time", parsed.getOrderBy());
        assertEquals("ASC", parsed.getOrderByDirection());
        assertEquals("fields=id,name&query=name EQ ^*Case360*^&order_by=creation_time&order_by_direction=ASC",
                parsed.getFilterQueryString());
    }

    @Test
    void testParseFilterQueryString_ParsesOrderByDirectionDesc() {
        ParsedFilterQueryResponse parsed = filterService.parseFilterQueryString(
                "fields=id,name&query=name EQ ^*Case360*^&order_by=creation_time&order_by_direction=desc");
        assertEquals("creation_time", parsed.getOrderBy());
        assertEquals("DESC", parsed.getOrderByDirection());
        assertEquals("fields=id,name&query=name EQ ^*Case360*^&order_by=creation_time&order_by_direction=DESC",
                parsed.getFilterQueryString());
    }

    @Test
    void testParseFilterQueryString_RejectsOrderByDirectionWithoutOrderBy() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> filterService.parseFilterQueryString(
                        "fields=id,name&query=name EQ ^*Case360*^&order_by_direction=DESC"));
        assertTrue(ex.getMessage().contains("order_by_direction requires order_by"));
    }
}
