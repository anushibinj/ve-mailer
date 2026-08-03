import React, { useEffect, useState } from 'react';
import { Plus, Trash2, Loader2, Shield } from 'lucide-react';
import toast from 'react-hot-toast';
import type { WorkspaceAdminEntry, UserSummary } from '../../services/apiService';
import { fetchWorkspaceAdmins, assignWorkspaceAdmin, removeWorkspaceAdmin, fetchWorkspaceUsers } from '../../services/apiService';
import { useAuth } from '../../hooks/useAuth';
import { TableActionButton } from '../../components/ui';

interface WorkspaceAdminManagerProps {
  workspaceId: string;
  workspaceTitle: string;
}

const WorkspaceAdminManager: React.FC<WorkspaceAdminManagerProps> = ({ workspaceId, workspaceTitle }) => {
  const { isAdmin } = useAuth();
  const [admins, setAdmins] = useState<WorkspaceAdminEntry[]>([]);
  const [users, setUsers] = useState<UserSummary[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [selectedUserId, setSelectedUserId] = useState('');
  const [isAssigning, setIsAssigning] = useState(false);
  const [removingId, setRemovingId] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setIsLoading(true);
      try {
        const [adminsData, usersData] = await Promise.all([
          fetchWorkspaceAdmins(workspaceId),
          fetchWorkspaceUsers(workspaceId),
        ]);
        if (!cancelled) {
          setAdmins(adminsData);
          setUsers(usersData);
        }
      } catch {
        if (!cancelled) toast.error('Failed to load workspace admin data');
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    })();
    return () => { cancelled = true; };
  }, [workspaceId]);

  const handleAssign = async () => {
    if (!selectedUserId) return;
    setIsAssigning(true);
    try {
      const newAdmin = await assignWorkspaceAdmin(workspaceId, selectedUserId);
      setAdmins(prev => [...prev, newAdmin]);
      setSelectedUserId('');
      toast.success('Workspace admin assigned successfully');
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string } } };
      toast.error(axiosErr.response?.data?.message ?? 'Failed to assign workspace admin');
    } finally {
      setIsAssigning(false);
    }
  };

  const handleRemove = async (userId: string) => {
    setRemovingId(userId);
    try {
      await removeWorkspaceAdmin(workspaceId, userId);
      setAdmins(prev => prev.filter(a => a.userId !== userId));
      toast.success('Workspace admin removed');
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string } } };
      toast.error(axiosErr.response?.data?.message ?? 'Failed to remove workspace admin');
    } finally {
      setRemovingId(null);
    }
  };

  // Users who are not already admins of this workspace (exclude global ADMINs too).
  // Both global ADMINs and WORKSPACE_ADMINs may promote a plain MEMBER user into an admin of
  // this workspace — the backend auto-upgrades the target's role to WORKSPACE_ADMIN.
  const availableUsers = users.filter(u => {
    if (admins.some(a => a.userId === u.id) || u.roles.includes('ADMIN')) return false;
    return true;
  });

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-8">
        <Loader2 className="h-5 w-5 animate-spin text-indigo-500" />
      </div>
    );
  }

  return (
    <div className="mt-6 border-t border-slate-200 dark:border-slate-700 pt-6">
      <div className="flex items-center gap-2 mb-4">
        <Shield className="h-4 w-4 text-violet-600 dark:text-violet-400" />
        <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-100">
          Workspace Admins — {workspaceTitle}
        </h3>
      </div>

      {/* Assign form */}
      {!isAdmin && (
        <p className="text-xs text-slate-500 dark:text-slate-400 mb-2">
          As a workspace admin, you can assign any user here — they will be promoted to
          WORKSPACE_ADMIN automatically if they aren't already.
        </p>
      )}
      <div className="flex items-center gap-2 mb-4">
        <select
          value={selectedUserId}
          onChange={(e) => setSelectedUserId(e.target.value)}
          className="flex-1 px-3 py-2 border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 text-slate-900 dark:text-slate-100 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
        >
          <option value="">Select a user to assign…</option>
          {availableUsers.map(u => (
            <option key={u.id} value={u.id}>
              {u.name} ({u.email})
            </option>
          ))}
        </select>
        <button
          type="button"
          onClick={handleAssign}
          disabled={!selectedUserId || isAssigning}
          className="inline-flex items-center gap-1.5 px-3 py-2 bg-indigo-600 hover:bg-indigo-500 text-white text-sm font-medium rounded-lg disabled:opacity-50 transition-colors cursor-pointer"
        >
          {isAssigning ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Plus className="h-3.5 w-3.5" />}
          Assign
        </button>
      </div>

      {/* Current admins list */}
      {admins.length === 0 ? (
        <p className="text-sm text-slate-500 dark:text-slate-400">No workspace admins assigned yet.</p>
      ) : (
        <ul className="divide-y divide-slate-100 dark:divide-slate-700 border border-slate-200 dark:border-slate-700 rounded-lg overflow-hidden">
          {admins.map(admin => (
            <li key={admin.id} className="flex items-center justify-between px-4 py-3 bg-white dark:bg-slate-800 hover:bg-slate-50 dark:hover:bg-slate-700">
              <div>
                <span className="text-sm font-medium text-slate-900 dark:text-slate-100">{admin.userName}</span>
                <span className="ml-2 text-xs text-slate-500 dark:text-slate-400">{admin.userEmail}</span>
              </div>
              <TableActionButton
                icon={<Trash2 className="h-3.5 w-3.5" />}
                label={`Remove ${admin.userName} as Workspace Admin`}
                variant="danger"
                loading={removingId === admin.userId}
                onClick={() => handleRemove(admin.userId)}
              />
            </li>
          ))}
        </ul>
      )}
    </div>
  );
};

export default WorkspaceAdminManager;
