import React, { useEffect, useState, useCallback } from 'react';
import { Plus, Pencil, Trash2, Loader2, KeyRound, CheckCircle, XCircle, Shield, Plug } from 'lucide-react';
import toast from 'react-hot-toast';
import type { WorkspaceAdmin } from '../../services/apiService';
import {
  adminFetchWorkspaces,
  adminDeleteWorkspace,
  adminTestWorkspaceConnection,
  fetchWorkspaces,
} from '../../services/apiService';
import WorkspaceFormModal from '../../components/WorkspaceFormModal';
import WorkspaceAdminManager from './WorkspaceAdminManager';
import ConfirmDialog from '../../components/ConfirmDialog';
import { useAuth } from '../../hooks/useAuth';

const WorkspaceManagementPage: React.FC = () => {
  const { isAdmin } = useAuth();
  const [workspaces, setWorkspaces] = useState<WorkspaceAdmin[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  // Create / edit modal
  const [formOpen, setFormOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<WorkspaceAdmin | null>(null);

  // Delete confirm dialog
  const [deleteTarget, setDeleteTarget] = useState<WorkspaceAdmin | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [testingWorkspaceId, setTestingWorkspaceId] = useState<string | null>(null);

  // Workspace admin management (ADMIN only)
  const [adminManageTarget, setAdminManageTarget] = useState<WorkspaceAdmin | null>(null);

  const loadWorkspaces = useCallback(async () => {
    setIsLoading(true);
    try {
      // ADMIN uses /all endpoint; WORKSPACE_ADMIN uses normal /workspaces which is filtered server-side
      const data = isAdmin
        ? await adminFetchWorkspaces()
        : (await fetchWorkspaces()) as unknown as WorkspaceAdmin[];
      setWorkspaces(data);
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string } } };
      toast.error(
        axiosErr.response?.data?.message ?? 'Failed to load workspaces. Please try again.'
      );
    } finally {
      setIsLoading(false);
    }
  }, [isAdmin]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    loadWorkspaces();
  }, [loadWorkspaces]);

  const handleOpenCreate = () => {
    setEditTarget(null);
    setFormOpen(true);
  };

  const handleOpenEdit = (ws: WorkspaceAdmin) => {
    setEditTarget(ws);
    setFormOpen(true);
  };

  const handleFormSuccess = (saved: WorkspaceAdmin) => {
    setFormOpen(false);
    setEditTarget(null);
    setWorkspaces(prev => {
      const idx = prev.findIndex(w => w.id === saved.id);
      if (idx >= 0) {
        const next = [...prev];
        next[idx] = saved;
        return next;
      }
      return [...prev, saved];
    });
  };

  const handleDeleteConfirm = async () => {
    if (!deleteTarget) return;
    setIsDeleting(true);
    try {
      await adminDeleteWorkspace(deleteTarget.id);
      setWorkspaces(prev => prev.filter(w => w.id !== deleteTarget.id));
      toast.success(`"${deleteTarget.title}" deleted`);
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string } } };
      toast.error(axiosErr.response?.data?.message ?? 'Failed to delete workspace');
    } finally {
      setIsDeleting(false);
      setDeleteTarget(null);
    }
  };

  const handleTestConnection = async (workspace: WorkspaceAdmin) => {
    setTestingWorkspaceId(workspace.id);
    try {
      const response = await adminTestWorkspaceConnection({
        workspaceRecordId: workspace.id,
        sharedSpaceId: workspace.sharedSpaceId,
        workspaceId: workspace.workspaceId,
        clientId: workspace.clientId,
        clientKey: workspace.clientKey,
        rootUrl: workspace.rootUrl,
      });
      toast.success(
        response.workspaceName
          ? `Connection successful: ${response.workspaceName} (${response.workspaceId})`
          : `Connection successful for workspace ${response.workspaceId}`
      );
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string } } };
      toast.error(axiosErr.response?.data?.message ?? 'Connection test failed');
    } finally {
      setTestingWorkspaceId(null);
    }
  };

  return (
    <div className="max-w-7xl mx-auto py-8 px-4 sm:px-6 lg:px-8">
      {/* Page header */}
      <div className="mb-8 flex items-center justify-between gap-4 flex-wrap">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">Workspace Management</h1>
          <p className="mt-1 text-sm text-gray-500">
            {isAdmin ? 'Create and manage ValueEdge workspace connections.' : 'Manage your assigned workspaces.'}
          </p>
        </div>
        {isAdmin && (
          <button
            onClick={handleOpenCreate}
            className="inline-flex items-center gap-2 px-4 py-2 border border-transparent rounded-lg shadow-sm text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 transition-all"
          >
            <Plus className="h-4 w-4" />
            Add Workspace
          </button>
        )}
      </div>

      {/* Content */}
      {isLoading ? (
        <div className="flex flex-col items-center justify-center py-24">
          <Loader2 className="h-8 w-8 animate-spin text-blue-500 mb-4" />
          <p className="text-gray-500">Loading workspaces…</p>
        </div>
      ) : workspaces.length === 0 ? (
        <div className="text-center bg-white rounded-lg border border-gray-200 shadow-sm py-20">
          <div className="flex items-center justify-center h-14 w-14 rounded-full bg-blue-50 mx-auto mb-4">
            <Plus className="h-7 w-7 text-blue-500" />
          </div>
          <h3 className="text-lg font-medium text-gray-900 mb-1">No workspaces yet</h3>
          <p className="text-sm text-gray-500 mb-5">
            Add your first ValueEdge workspace to get started.
          </p>
          <button
            onClick={handleOpenCreate}
            className="inline-flex items-center gap-2 px-4 py-2 border border-transparent rounded-lg text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 transition-colors"
          >
            <Plus className="h-4 w-4" />
            Add Workspace
          </button>
        </div>
      ) : (
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-gray-200">
              <thead className="bg-gray-50">
                <tr>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Title
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Shared Space ID
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Workspace ID
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Client ID
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Client Key
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Root URL
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Status
                  </th>
                  <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Actions
                  </th>
                </tr>
              </thead>
              <tbody className="bg-white divide-y divide-gray-200">
                {workspaces.map(ws => (
                  <tr key={ws.id} className="hover:bg-gray-50 transition-colors">
                    <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900">
                      {ws.title}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500 font-mono">
                      {ws.sharedSpaceId}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500 font-mono">
                      {ws.workspaceId}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500 font-mono">
                      {ws.clientId}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm">
                      <div className="inline-flex items-center gap-1.5">
                        <KeyRound className="h-3.5 w-3.5 text-gray-400" />
                        {ws.clientKeyConfigured ? (
                          <span className="inline-flex items-center gap-1 text-green-700 font-medium">
                            <CheckCircle className="h-3.5 w-3.5" />
                            Configured
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1 text-red-600 font-medium">
                            <XCircle className="h-3.5 w-3.5" />
                            Not set
                          </span>
                        )}
                      </div>
                    </td>
                    <td className="px-6 py-4 text-sm text-gray-500 max-w-xs truncate" title={ws.rootUrl}>
                      {ws.rootUrl || <span className="text-gray-300">—</span>}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm">
                      {ws.status === 'ENABLED' && (
                        <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-green-50 text-green-700 border border-green-200">
                          Enabled
                        </span>
                      )}
                      {ws.status === 'DRAFT' && (
                        <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-amber-50 text-amber-700 border border-amber-200">
                          Draft
                        </span>
                      )}
                      {ws.status === 'DISABLED' && (
                        <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-gray-100 text-gray-500 border border-gray-200">
                          Disabled
                        </span>
                      )}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-right">
                      <div className="inline-flex items-center gap-2">
                        <button
                          onClick={() => handleOpenEdit(ws)}
                          className="inline-flex items-center gap-1.5 px-3 py-1.5 border border-gray-300 rounded-md text-xs font-medium text-gray-700 bg-white hover:bg-gray-50 hover:border-blue-300 transition-colors"
                        >
                          <Pencil className="h-3.5 w-3.5" />
                          Edit
                        </button>
                        <button
                          onClick={() => handleTestConnection(ws)}
                          disabled={testingWorkspaceId === ws.id}
                          className="inline-flex items-center gap-1.5 px-3 py-1.5 border border-indigo-200 rounded-md text-xs font-medium text-indigo-600 bg-white hover:bg-indigo-50 hover:border-indigo-400 disabled:opacity-50 transition-colors"
                        >
                          {testingWorkspaceId === ws.id ? (
                            <Loader2 className="h-3.5 w-3.5 animate-spin" />
                          ) : (
                            <Plug className="h-3.5 w-3.5" />
                          )}
                          Test connection
                        </button>
                        {isAdmin && (
                          <>
                            <button
                              onClick={() => setAdminManageTarget(adminManageTarget?.id === ws.id ? null : ws)}
                              className="inline-flex items-center gap-1.5 px-3 py-1.5 border border-violet-200 rounded-md text-xs font-medium text-violet-600 bg-white hover:bg-violet-50 hover:border-violet-400 transition-colors"
                            >
                              <Shield className="h-3.5 w-3.5" />
                              Admins
                            </button>
                            <button
                              onClick={() => setDeleteTarget(ws)}
                              className="inline-flex items-center gap-1.5 px-3 py-1.5 border border-red-200 rounded-md text-xs font-medium text-red-600 bg-white hover:bg-red-50 hover:border-red-400 transition-colors"
                            >
                              <Trash2 className="h-3.5 w-3.5" />
                              Delete
                            </button>
                          </>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Create / Edit Modal */}
      <WorkspaceFormModal
        isOpen={formOpen}
        workspace={editTarget}
        onClose={() => {
          setFormOpen(false);
          setEditTarget(null);
        }}
        onSuccess={handleFormSuccess}
      />

      {/* Workspace Admin Manager (shown below table when ADMIN clicks "Admins") */}
      {isAdmin && adminManageTarget && (
        <WorkspaceAdminManager
          workspaceId={adminManageTarget.id}
          workspaceTitle={adminManageTarget.title}
        />
      )}

      {/* Delete Confirmation */}
      <ConfirmDialog
        isOpen={!!deleteTarget}
        title="Delete Workspace"
        message={
          deleteTarget
            ? `Are you sure you want to delete "${deleteTarget.title}"? This action cannot be undone.`
            : ''
        }
        confirmLabel="Delete"
        onConfirm={handleDeleteConfirm}
        onCancel={() => setDeleteTarget(null)}
        isLoading={isDeleting}
        variant="danger"
      />
    </div>
  );
};

export default WorkspaceManagementPage;
