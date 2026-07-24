package com.anushibinj.veemailer.repository;

import com.anushibinj.veemailer.model.WorkspaceAdminMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WorkspaceAdminRepository extends JpaRepository<WorkspaceAdminMapping, UUID> {

    boolean existsByWorkspace_IdAndUser_Id(UUID workspaceId, UUID userId);

    boolean existsByWorkspace_IdAndUser_Email(UUID workspaceId, String email);

    List<WorkspaceAdminMapping> findByWorkspace_Id(UUID workspaceId);

    List<WorkspaceAdminMapping> findByUser_Id(UUID userId);

    List<WorkspaceAdminMapping> findByUser_Email(String email);

    void deleteByWorkspace_IdAndUser_Id(UUID workspaceId, UUID userId);

    void deleteByUser_Id(UUID userId);
}
