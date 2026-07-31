package com.anushibinj.veemailer.repository;

import com.anushibinj.veemailer.model.Workspace;
import com.anushibinj.veemailer.model.WorkspaceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {

    List<Workspace> findByStatusIn(List<WorkspaceStatus> statuses);

    List<Workspace> findByIdInAndStatusIn(List<UUID> ids, List<WorkspaceStatus> statuses);

    /**
     * Looks up a workspace by the exact combination of Root URL, Shared Space ID, and
     * Workspace ID. Used to detect duplicates: a workspace is only considered a duplicate
     * when ALL THREE values match — sharing just one or two of the fields is allowed.
     */
    Optional<Workspace> findFirstByRootUrlAndSharedSpaceIdAndWorkspaceId(
            String rootUrl, String sharedSpaceId, String workspaceId);
}
