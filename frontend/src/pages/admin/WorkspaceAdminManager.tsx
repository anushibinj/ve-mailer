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
  // WORKSPACE_ADMIN actors may only pick users who already carry the WORKSPACE_ADMIN role —
  // only a global ADMIN can promote a plain USER/MEMBER into an admin.
  const availableUsers = users.filter(u => {
    if (admins.some(a => a.userId === u.id) || u.roles.includes('ADMIN')) return false;
    if (!isAdmin && !u.roles.includes('WORKSPACE_ADMIN')) return false;
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
    <div className="mt-6 border-t border-gray-200 pt-6">
      <div className="flex items-center gap-2 mb-4">
        <Shield className="h-4 w-4 text-violet-600" />
        <h3 className="text-sm font-semibold text-gray-900">
          Workspace Admins — {workspaceTitle}
        </h3>
      </div>

      {/* Assign form */}
      {!isAdmin && (
        <p className="text-xs text-gray-500 mb-2">
          As a workspace admin, you can only assign other users who already have the WORKSPACE_ADMIN role.
        </p>
      )}
      <div className="flex items-center gap-2 mb-4">
        <select
          value={selectedUserId}
          onChange={(e) => setSelectedUserId(e.target.value)}
          className="flex-1 px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
        >
          <option value="">Select a user to assign…</option>
          {availableUsers.map(u => (
            <option key={u.id} value={u.id}>
              {u.name} ({u.email})
            </option>
          ))}
        </select>
        <button
          onClick={handleAssign}
          disabled={!selectedUserId || isAssigning}
          className="inline-flex items-center gap-1.5 px-3 py-2 bg-indigo-600 hover:bg-indigo-500 text-white text-sm font-medium rounded-lg disabled:opacity-50 transition-colors"
        >
          {isAssigning ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Plus className="h-3.5 w-3.5" />}
          Assign
        </button>
      </div>

      {/* Current admins list */}
      {admins.length === 0 ? (
        <p className="text-sm text-gray-500">No workspace admins assigned yet.</p>
      ) : (
        <ul className="divide-y divide-gray-100 border border-gray-200 rounded-lg overflow-hidden">
          {admins.map(admin => (
            <li key={admin.id} className="flex items-center justify-between px-4 py-3 bg-white hover:bg-gray-50">
              <div>
                <span className="text-sm font-medium text-gray-900">{admin.userName}</span>
                <span className="ml-2 text-xs text-gray-500">{admin.userEmail}</span>
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
