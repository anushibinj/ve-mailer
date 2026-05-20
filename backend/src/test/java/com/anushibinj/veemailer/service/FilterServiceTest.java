package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.repository.FilterRepository;
import com.anushibinj.veemailer.repository.WorkspaceRepository;
import com.anushibinj.veemailer.service.ve.ValueEdgeProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

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
    @Mock private OctaneCacheService octaneCacheService;
    @Mock private ValueEdgeProperties valueEdgeProperties;

    private FilterService filterService;

    @BeforeEach
    void setUp() {
        filterService = new FilterService(
                filterRepository, workspaceRepository, octaneCacheService,
                valueEdgeProperties, new ObjectMapper());
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
    void testComputeEffectiveFetchFields_DoesNotMutateInput() {
        // The input list must not be modified.
        List<String> input = new ArrayList<>(List.of("phase", "owner"));
        filterService.computeEffectiveFetchFields(input);
        assertEquals(List.of("phase", "owner"), input, "input list must not be mutated");
    }
}
