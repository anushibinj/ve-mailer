package com.anushibinj.veemailer.exception;

import com.anushibinj.veemailer.model.Workspace;
import lombok.Getter;

/**
 * Thrown when attempting to create (or update) a workspace whose combination of
 * Root URL + Shared Space ID + Workspace ID already matches an existing workspace.
 * Carries the conflicting existing workspace so callers (e.g. the global exception
 * handler) can surface its identity to the client.
 */
@Getter
public class DuplicateWorkspaceException extends RuntimeException {

    private final Workspace existingWorkspace;

    public DuplicateWorkspaceException(Workspace existingWorkspace) {
        super("A workspace with the same Root URL, Shared Space ID, and Workspace ID already exists: "
                + existingWorkspace.getTitle());
        this.existingWorkspace = existingWorkspace;
    }
}
