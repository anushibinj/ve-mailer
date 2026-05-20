package com.anushibinj.veemailer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single comment on a ticket, including metadata and nested replies.
 *
 * <p>Octane comments are currently flat (no parent–child relationships), so
 * the {@code children} list is always empty today. The field is retained so
 * the service layer is already structured for future threaded/nested comment
 * support without requiring a DTO change.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketCommentDto {

    private String id;
    private String authorName;
    private String authorEmail;
    private String text;
    private String creationTime;
    private Integer orderNumber;

    /** Nested replies — empty today (Octane comments are flat). */
    @Builder.Default
    private List<TicketCommentDto> children = new ArrayList<>();
}
