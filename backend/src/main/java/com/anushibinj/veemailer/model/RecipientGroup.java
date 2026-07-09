package com.anushibinj.veemailer.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "recipient_groups",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_recipient_groups_ws_name",
                columnNames = {"workspace_id", "name"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecipientGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "workspace_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_recipient_groups_workspace"))
    private Workspace workspace;

    @Column(nullable = false)
    private String name;

    @Column(length = 500)
    private String description;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "recipient_group_members",
            joinColumns = @JoinColumn(name = "group_id"),
            foreignKey = @ForeignKey(name = "fk_recipient_group_members_group"))
    @Column(name = "member_email")
    @Builder.Default
    private Set<String> memberEmails = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by")
    private String createdBy;
}
