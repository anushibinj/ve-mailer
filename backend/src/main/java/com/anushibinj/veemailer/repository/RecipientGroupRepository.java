package com.anushibinj.veemailer.repository;

import com.anushibinj.veemailer.model.RecipientGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecipientGroupRepository extends JpaRepository<RecipientGroup, UUID> {

    List<RecipientGroup> findByWorkspaceIdOrderByNameAsc(UUID workspaceId);

    boolean existsByWorkspaceIdAndNameIgnoreCase(UUID workspaceId, String name);

    Optional<RecipientGroup> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
