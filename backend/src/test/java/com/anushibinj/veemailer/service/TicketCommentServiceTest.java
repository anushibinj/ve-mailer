package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.dto.TicketCommentDto;
import com.anushibinj.veemailer.model.Workspace;
import com.hpe.adm.nga.sdk.Octane;
import com.hpe.adm.nga.sdk.entities.EntityList;
import com.hpe.adm.nga.sdk.entities.OctaneCollection;
import com.hpe.adm.nga.sdk.entities.get.GetEntities;
import com.hpe.adm.nga.sdk.model.EntityModel;
import com.hpe.adm.nga.sdk.model.ReferenceFieldModel;
import com.hpe.adm.nga.sdk.model.StringFieldModel;
import com.hpe.adm.nga.sdk.query.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketCommentServiceTest {

    @Mock
    private OctaneCacheService octaneCacheService;

    @Mock
    private Octane octaneClient;

    @Mock
    private EntityList entityList;

    @Mock
    private GetEntities getEntities;

    private TicketCommentService ticketCommentService;

    private Workspace workspace;

    @BeforeEach
    void setUp() {
        ticketCommentService = new TicketCommentService(octaneCacheService);
        ReflectionTestUtils.setField(ticketCommentService, "queryLimit", 25);

        workspace = new Workspace();
        workspace.setId(UUID.randomUUID());
        workspace.setClientId("client-id");
        workspace.setClientKey("client-key");
        workspace.setSharedSpaceId("1001");
        workspace.setWorkspaceId("2001");
        workspace.setRootUrl("https://octane.example.com");
    }

    @SuppressWarnings("unchecked")
    private OctaneCollection<EntityModel> mockCollection(EntityModel... entities) {
        OctaneCollection<EntityModel> collection = mock(OctaneCollection.class);
        List<EntityModel> list = Arrays.asList(entities);
        when(collection.iterator()).thenReturn(list.iterator());
        return collection;
    }

    @Test
    void testFetchComments_OctaneException_ReturnsEmptyList() {
        when(octaneCacheService.getOctaneClient(anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("Connection refused"));

        List<TicketCommentDto> result = ticketCommentService.fetchComments("100", workspace);

        assertTrue(result.isEmpty());
    }

    @Test
    void testFetchComments_Success_MapsEntitiesToDtos() {
        when(octaneCacheService.getOctaneClient(anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(octaneClient);

        // Build a mock comment entity
        EntityModel authorEntity = new EntityModel(Set.of(
                new StringFieldModel("full_name", "John Doe"),
                new StringFieldModel("email", "john@example.com")
        ));
        EntityModel commentEntity = new EntityModel(Set.of(
                new StringFieldModel("id", "5001"),
                new StringFieldModel("text", "This needs to be fixed ASAP."),
                new StringFieldModel("creation_time", "2026-05-15T10:00:00Z"),
                new ReferenceFieldModel("author", authorEntity)
        ));

        OctaneCollection<EntityModel> collection = mockCollection(commentEntity);

        when(octaneClient.entityList("comments")).thenReturn(entityList);
        when(entityList.get()).thenReturn(getEntities);
        when(getEntities.query(any(Query.class))).thenReturn(getEntities);
        when(getEntities.addFields(any(String[].class))).thenReturn(getEntities);
        when(getEntities.addOrderBy(anyString(), anyBoolean())).thenReturn(getEntities);
        when(getEntities.limit(anyInt())).thenReturn(getEntities);
        when(getEntities.execute()).thenReturn(collection);

        List<TicketCommentDto> result = ticketCommentService.fetchComments("100", workspace);

        assertEquals(1, result.size());
        TicketCommentDto dto = result.get(0);
        assertEquals("5001", dto.getId());
        assertEquals("John Doe", dto.getAuthorName());
        assertEquals("john@example.com", dto.getAuthorEmail());
        assertEquals("This needs to be fixed ASAP.", dto.getText());
        assertEquals("2026-05-15T10:00:00Z", dto.getCreationTime());
        assertNotNull(dto.getChildren());
        assertTrue(dto.getChildren().isEmpty());
    }

    @Test
    void testFetchComments_UsesCorrectQueryLimit() {
        ReflectionTestUtils.setField(ticketCommentService, "queryLimit", 50);
        when(octaneCacheService.getOctaneClient(anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(octaneClient);

        OctaneCollection<EntityModel> emptyCollection = mockCollection();

        when(octaneClient.entityList("comments")).thenReturn(entityList);
        when(entityList.get()).thenReturn(getEntities);
        when(getEntities.query(any(Query.class))).thenReturn(getEntities);
        when(getEntities.addFields(any(String[].class))).thenReturn(getEntities);
        when(getEntities.addOrderBy(anyString(), anyBoolean())).thenReturn(getEntities);
        when(getEntities.limit(50)).thenReturn(getEntities);
        when(getEntities.execute()).thenReturn(emptyCollection);

        ticketCommentService.fetchComments("100", workspace);

        verify(getEntities).limit(50);
    }

    @Test
    void testFetchComments_OrdersByOrderNumberDescending() {
        when(octaneCacheService.getOctaneClient(anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(octaneClient);

        OctaneCollection<EntityModel> emptyCollection = mockCollection();

        when(octaneClient.entityList("comments")).thenReturn(entityList);
        when(entityList.get()).thenReturn(getEntities);
        when(getEntities.query(any(Query.class))).thenReturn(getEntities);
        when(getEntities.addFields(any(String[].class))).thenReturn(getEntities);
        when(getEntities.addOrderBy("order_number", false)).thenReturn(getEntities);
        when(getEntities.limit(anyInt())).thenReturn(getEntities);
        when(getEntities.execute()).thenReturn(emptyCollection);

        ticketCommentService.fetchComments("100", workspace);

        verify(getEntities).addOrderBy("order_number", false);
    }

    // ── formatCommentsForAi ──────────────────────────────────────────────────

    @Test
    void testFormatCommentsForAi_EmptyList_ReturnsEmpty() {
        String result = ticketCommentService.formatCommentsForAi(Collections.emptyList());
        assertEquals("", result);
    }

    @Test
    void testFormatCommentsForAi_NullList_ReturnsEmpty() {
        String result = ticketCommentService.formatCommentsForAi(null);
        assertEquals("", result);
    }

    @Test
    void testFormatCommentsForAi_SingleComment() {
        TicketCommentDto comment = TicketCommentDto.builder()
                .authorName("Alice")
                .text("Verified the fix works.")
                .build();

        String result = ticketCommentService.formatCommentsForAi(List.of(comment));

        assertEquals("Comment by Alice:\nVerified the fix works.", result);
    }

    @Test
    void testFormatCommentsForAi_MultipleComments_PreservesOrdering() {
        TicketCommentDto comment1 = TicketCommentDto.builder()
                .authorName("Alice")
                .text("First comment")
                .build();
        TicketCommentDto comment2 = TicketCommentDto.builder()
                .authorName("Bob")
                .text("Second comment")
                .build();

        String result = ticketCommentService.formatCommentsForAi(List.of(comment1, comment2));

        assertTrue(result.startsWith("Comment by Alice:"));
        assertTrue(result.contains("Comment by Bob:"));
        assertTrue(result.indexOf("Alice") < result.indexOf("Bob"));
    }

    @Test
    void testFormatCommentsForAi_NullAuthor_ShowsUnknown() {
        TicketCommentDto comment = TicketCommentDto.builder()
                .authorName(null)
                .text("Anonymous feedback")
                .build();

        String result = ticketCommentService.formatCommentsForAi(List.of(comment));

        assertTrue(result.contains("Comment by Unknown:"));
        assertTrue(result.contains("Anonymous feedback"));
    }

    @Test
    void testFormatCommentsForAi_NullText_ShowsEmpty() {
        TicketCommentDto comment = TicketCommentDto.builder()
                .authorName("Charlie")
                .text(null)
                .build();

        String result = ticketCommentService.formatCommentsForAi(List.of(comment));

        assertTrue(result.contains("Comment by Charlie:"));
    }
}
