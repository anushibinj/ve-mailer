package com.anushibinj.veemailer.repository;

import com.anushibinj.veemailer.model.Filter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FilterRepository extends JpaRepository<Filter, UUID> {

    List<Filter> findByWorkspace_Id(UUID workspaceId);

    Optional<Filter> findByIdAndWorkspace_Id(UUID filterId, UUID workspaceId);

    @Query("""
            SELECT f
            FROM Filter f
            WHERE f.workspace.id = :workspaceId
              AND (f.ownerEmail IS NULL OR LOWER(f.ownerEmail) = LOWER(:email))
            """)
    List<Filter> findVisibleForUser(@Param("workspaceId") UUID workspaceId, @Param("email") String email);
}
