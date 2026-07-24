package com.anushibinj.veemailer.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "filters")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Filter {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String title;
    private String description;

    /** The workspace this filter template belongs to. Not serialised — the workspaceId FK is sufficient for the frontend. */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_filters_workspace_id"))
    private Workspace workspace;

    /** Octane subtype, e.g. "defect", "story", "feature" */
    private String entityType;

    /** JSON array of Octane field names to fetch, e.g. ["id","name","phase"] */
    @Column(columnDefinition = "TEXT")
    private String fields;

    /** JSON array of FilterCriteriaClause objects */
    @Column(columnDefinition = "TEXT")
    private String criteria;

    /** Optional Octane field name used to sort query results. */
    @Column(name = "order_by")
    private String orderBy;

    /** Optional direction for orderBy: ASC or DESC. */
    @Column(name = "order_by_direction")
    private String orderByDirection;

    /**
     * Null for admin-created shared templates.
     * Non-null for user-private templates owned by the given email address.
     */
    @Column(name = "owner_email")
    private String ownerEmail;

    /** Computed per request; true when current user can edit/delete this filter. */
    @Transient
    private Boolean editable;

    /** Computed per request; true when this is an admin-created shared template. */
    @Transient
    private Boolean adminManaged;
}
