import React from 'react';
import { AlertTriangle, ExternalLink } from 'lucide-react';

// Shared across WorkspaceFormModal (edit) and WorkspaceCreationWizard (create) so both
// present duplicate-conflict and status-restriction messaging identically.

export const CLIENT_KEY_PLACEHOLDER = '(unchanged)';

/** Mirrors the backend's WorkspaceService.SHORTCODE_UNKNOWN_ENABLE_BLOCKED_MESSAGE verbatim. */
export const SHORTCODE_UNKNOWN_ENABLE_BLOCKED_MESSAGE =
  'This workspace cannot be moved to the Enabled state because the workspace shortcode ' +
  'could not be identified automatically during creation. Please contact a Super Admin to ' +
  'review and correct the workspace metadata.';

// eslint-disable-next-line react-refresh/only-export-components -- shared helper, not a component
export const isShortcodeUnknown = (shortcode: string | null | undefined): boolean =>
  !shortcode || shortcode.trim().toUpperCase() === 'UNKNOWN';

export const inputClass =
  'w-full px-3.5 py-2.5 border rounded-xl text-sm transition-all ' +
  'placeholder:text-slate-400 dark:placeholder:text-slate-500 ' +
  'focus:outline-none focus:ring-2';

// eslint-disable-next-line react-refresh/only-export-components -- shared helper, not a component
export const fieldInputClass = (hasError: boolean, readOnly = false): string =>
  `${inputClass} ${readOnly
    ? 'bg-slate-100 dark:bg-slate-800 text-slate-500 dark:text-slate-400 cursor-not-allowed'
    : 'text-slate-900 dark:text-slate-100 bg-white dark:bg-slate-800/60'
  } ${hasError
    ? 'border-red-400 dark:border-red-500/50 bg-red-50 dark:bg-red-500/5 focus:border-red-500 focus:ring-red-500/20'
    : 'border-slate-200 dark:border-slate-600 focus:border-indigo-500 dark:focus:border-indigo-400 focus:ring-indigo-500/15 dark:focus:ring-indigo-400/15'
  }`;

export interface ExistingWorkspaceConflict {
  id: string;
  name: string;
  rootUrl?: string;
  sharedSpaceId?: string;
  workspaceId?: string;
}

/**
 * Duplicate-workspace conflict banner shown when the backend rejects a (Root URL, Shared
 * Space ID, Workspace ID) combination as already in use (409). Reused verbatim by both the
 * edit modal and Step 1 of the creation wizard so the messaging never drifts between them.
 */
export const DuplicateWorkspaceConflictBanner: React.FC<{ existingWorkspace: ExistingWorkspaceConflict }> = ({
  existingWorkspace,
}) => (
  <div className="flex gap-3 rounded-xl border border-amber-300 dark:border-amber-600/50 bg-amber-50 dark:bg-amber-900/20 px-4 py-3 animate-fade-in">
    <AlertTriangle className="h-5 w-5 flex-shrink-0 text-amber-500 dark:text-amber-400 mt-0.5" />
    <div className="min-w-0 flex-1 text-sm text-amber-800 dark:text-amber-200">
      <p className="font-medium break-words">
        A workspace with the same Root URL, Shared Space ID, and Workspace ID already exists.
      </p>
      <p className="mt-1 break-words">
        Existing workspace: <span className="font-semibold">{existingWorkspace.name}</span>
      </p>
      <a
        href={`/workspace/${existingWorkspace.id}`}
        target="_blank"
        rel="noopener noreferrer"
        className="mt-1.5 inline-flex items-center gap-1 font-medium text-amber-900 dark:text-amber-100 underline hover:no-underline"
      >
        Open existing workspace
        <ExternalLink className="h-3.5 w-3.5" />
      </a>
    </div>
  </div>
);
