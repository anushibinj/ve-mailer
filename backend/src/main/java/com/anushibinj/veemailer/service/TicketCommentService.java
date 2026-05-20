package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.dto.TicketCommentDto;
import com.anushibinj.veemailer.model.Workspace;
import com.anushibinj.veemailer.repository.WorkspaceRepository;
import com.anushibinj.veemailer.service.ve.ValueEdgeProperties;
import com.hpe.adm.nga.sdk.Octane;
import com.hpe.adm.nga.sdk.entities.OctaneCollection;
import com.hpe.adm.nga.sdk.model.EntityModel;
import com.hpe.adm.nga.sdk.model.ReferenceFieldModel;
import com.hpe.adm.nga.sdk.model.StringFieldModel;
import com.hpe.adm.nga.sdk.query.Query;
import com.hpe.adm.nga.sdk.query.QueryMethod;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Fetches ticket comments from the Octane ticketing server.
 *
 * <p>Follows the same Octane client usage pattern as {@link FilterService}:
 * workspace/shared-space resolution, cached Octane client, query building,
 * and configurable limit via {@code veemailer.query.limit}.
 *
 * <p>Octane comments are flat (no parent–child hierarchy). The returned DTOs
 * include a {@code children} list for forward-compatibility with threaded APIs,
 * but it is always empty today.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketCommentService {

    private static final String[] COMMENT_FIELDS = {
            "id", "author", "text", "order_number", "creation_time"
    };

    private final OctaneCacheService octaneCacheService;
    private final ValueEdgeProperties valueEdgeProperties;
    private final WorkspaceRepository workspaceRepository;

    @Value("${veemailer.query.limit:25}")
    private int queryLimit;

    /**
     * Fetches comments for the given ticket from the Octane workspace.
     *
     * @param ticketId    the numeric ID of the work item
     * @param workspaceId the internal UUID of the workspace containing this ticket
     * @return ordered list of comments (newest first), or empty list on failure
     */
    public List<TicketCommentDto> fetchComments(String ticketId, UUID workspaceId) {
        log.debug("Fetching comments for ticket {} in workspace {}", ticketId, workspaceId);

        Workspace workspace = workspaceRepository.findById(workspaceId).orElse(null);
        if (workspace == null) {
            log.warn("Workspace {} not found — cannot fetch comments for ticket {}", workspaceId, ticketId);
            return Collections.emptyList();
        }

        try {
            Octane octaneClient = octaneCacheService.getOctaneClient(
                    valueEdgeProperties.getServerUrl(),
                    workspace.getClientId(),
                    workspace.getClientKey(),
                    Integer.parseInt(workspace.getSharedSpaceId()),
                    Integer.parseInt(workspace.getWorkspaceId()));

            // Query: owner_work_item={id=<ticketId>}
            Query query = Query.statement("owner_work_item", QueryMethod.EqualTo,
                    Query.statement("id", QueryMethod.EqualTo, ticketId)).build();

            OctaneCollection<EntityModel> result = octaneClient
                    .entityList("comments")
                    .get()
                    .query(query)
                    .addFields(COMMENT_FIELDS)
                    .addOrderBy("order_number", false) // descending — newest first
                    .limit(queryLimit)
                    .execute();

            List<TicketCommentDto> comments = new ArrayList<>();
            for (EntityModel entity : result) {
                comments.add(mapToDto(entity));
            }

            log.debug("Fetched {} comments for ticket {}", comments.size(), ticketId);
            return comments;
        } catch (Exception e) {
            log.error("Failed to fetch comments for ticket {} in workspace {}: {}",
                    ticketId, workspaceId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Formats a list of comment DTOs into a single text block suitable for AI prompt input.
     * Preserves ordering (newest first as returned by {@link #fetchComments}).
     */
    public String formatCommentsForAi(List<TicketCommentDto> comments) {
        if (comments == null || comments.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (TicketCommentDto comment : comments) {
            String author = comment.getAuthorName() != null ? comment.getAuthorName() : "Unknown";
            sb.append("Comment by ").append(author).append(":\n");
            sb.append(comment.getText() != null ? comment.getText() : "").append("\n\n");
        }
        return sb.toString().trim();
    }

    /**
     * Maps a raw Octane EntityModel to a structured TicketCommentDto.
     * Extracts author name/email from the nested reference field.
     */
    private TicketCommentDto mapToDto(EntityModel entity) {
        String id = getStringValue(entity, "id");
        String text = getStringValue(entity, "text");
        String creationTime = getStringValue(entity, "creation_time");
        Integer orderNumber = getIntegerValue(entity, "order_number");

        // Author is a reference field containing full_name and email
        String authorName = null;
        String authorEmail = null;
        if (entity.getValue("author") instanceof ReferenceFieldModel refModel) {
            EntityModel authorEntity = (EntityModel) refModel.getValue();
            if (authorEntity != null) {
                authorName = getStringValue(authorEntity, "full_name");
                authorEmail = getStringValue(authorEntity, "email");
            }
        }

        return TicketCommentDto.builder()
                .id(id)
                .authorName(authorName)
                .authorEmail(authorEmail)
                .text(text)
                .creationTime(creationTime)
                .orderNumber(orderNumber)
                .build();
    }

    private String getStringValue(EntityModel entity, String field) {
        if (entity.getValue(field) instanceof StringFieldModel sfm) {
            return sfm.getValue();
        }
        return null;
    }

    private Integer getIntegerValue(EntityModel entity, String field) {
        var fm = entity.getValue(field);
        if (fm != null && fm.hasValue() && fm.getValue() != null) {
            try {
                return Integer.parseInt(fm.getValue().toString());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
