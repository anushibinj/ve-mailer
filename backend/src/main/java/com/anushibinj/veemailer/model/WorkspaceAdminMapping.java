package com.anushibinj.veemailer.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "workspace_admins",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_workspace_admins_ws_user",
        columnNames = {"workspace_id", "user_id"}
    )
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkspaceAdminMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_workspace_admins_workspace"))
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_workspace_admins_user"))
    private AppUser user;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private String createdBy;
}
