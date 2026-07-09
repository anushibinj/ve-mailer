import React, { useEffect, useState, useCallback } from 'react';
import {
  fetchWorkspaces,
  adminFetchWorkspaces,
  fetchRecipientGroups,
  createRecipientGroup,
  updateRecipientGroup,
  deleteRecipientGroup,
  addRecipientGroupMember,
  removeRecipientGroupMember,
  type Workspace,
  type RecipientGroup,
} from '../../services/apiService';
import {
  Loader2,
  Plus,
  Users,
  Pencil,
  Trash2,
  UserPlus,
  UserMinus,
  ChevronDown,
  ChevronRight,
  X,
} from 'lucide-react';
import toast from 'react-hot-toast';
import ConfirmDialog from '../../components/ConfirmDialog';
import { useAuth } from '../../hooks/useAuth';

// ── Shared input class ────────────────────────────────────────────────────────

const inputClass =
  'w-full px-3.5 py-2.5 border border-slate-200 dark:border-slate-600 rounded-xl text-sm ' +
  'text-slate-900 dark:text-slate-100 bg-white dark:bg-slate-800/60 ' +
  'focus:outline-none focus:border-indigo-500 dark:focus:border-indigo-400 ' +
  'focus:ring-2 focus:ring-indigo-500/15 dark:focus:ring-indigo-400/15 transition-all placeholder:text-slate-400 dark:placeholder:text-slate-500';

const selectClass =
  'w-full px-3.5 py-2.5 border border-slate-200 dark:border-slate-600 rounded-xl text-sm ' +
  'text-slate-900 dark:text-slate-100 bg-white dark:bg-slate-800/60 ' +
  'focus:outline-none focus:border-indigo-500 dark:focus:border-indigo-400 ' +
  'focus:ring-2 focus:ring-indigo-500/15 dark:focus:ring-indigo-400/15 transition-all appearance-none';

// ── Group form modal ─────────────────────────────────────────────────────────

interface GroupFormModalProps {
  isOpen: boolean;
  initial?: RecipientGroup | null;
  onClose: () => void;
  onSave: (name: string, description: string, memberEmails: string[]) => Promise<void>;
}

function GroupFormModal({ isOpen, initial, onClose, onSave }: GroupFormModalProps) {
  const [name, setName] = useState(initial?.name ?? '');
  const [description, setDescription] = useState(initial?.description ?? '');
  const [membersText, setMembersText] = useState(
    initial ? Array.from(initial.memberEmails).join('\n') : ''
  );
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setName(initial?.name ?? '');
    setDescription(initial?.description ?? '');
    setMembersText(initial ? Array.from(initial.memberEmails).join('\n') : '');
  }, [initial, isOpen]);

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const emails = membersText
      .split(/[\n,;]+/)
      .map(e => e.trim().toLowerCase())
      .filter(Boolean);
    setSaving(true);
    try {
      await onSave(name.trim(), description.trim(), emails);
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4" role="dialog" aria-modal="true">
      <div
        className="absolute inset-0 bg-slate-900/60 backdrop-blur-sm"
        onClick={onClose}
        aria-hidden="true"
      />
      <div className="relative bg-white dark:bg-slate-900 rounded-2xl shadow-2xl border border-slate-100 dark:border-slate-700/50 w-full max-w-lg overflow-hidden">
        <div className="h-1 bg-gradient-to-r from-indigo-500 via-violet-500 to-purple-500" />
        <div className="px-6 pt-5 pb-6">
          <div className="flex items-center justify-between mb-5">
            <div className="flex items-center gap-2.5">
              <div className="h-8 w-8 rounded-lg bg-indigo-50 dark:bg-indigo-500/10 flex items-center justify-center">
                <Users className="h-4 w-4 text-indigo-600 dark:text-indigo-400" />
              </div>
              <h3 className="text-base font-semibold text-slate-900 dark:text-white">
                {initial ? 'Edit Group' : 'New Recipient Group'}
              </h3>
            </div>
            <button onClick={onClose} className="p-1.5 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition-colors cursor-pointer">
              <X className="h-4 w-4" />
            </button>
          </div>

          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="space-y-1.5">
              <label className="block text-sm font-medium text-slate-700">Group Name <span className="text-rose-500">*</span></label>
              <input type="text" required value={name} onChange={e => setName(e.target.value)} placeholder="e.g. QA Team" className={inputClass} />
            </div>
            <div className="space-y-1.5">
              <label className="block text-sm font-medium text-slate-700">Description</label>
              <input type="text" value={description} onChange={e => setDescription(e.target.value)} placeholder="Optional" className={inputClass} />
            </div>
            <div className="space-y-1.5">
              <label className="block text-sm font-medium text-slate-700">Member Emails</label>
              <textarea rows={5} value={membersText} onChange={e => setMembersText(e.target.value)} placeholder="One email per line" className={`${inputClass} resize-none`} />
              <p className="text-xs text-slate-400">Comma or newline separated. Members can also be managed individually.</p>
            </div>
            <div className="flex gap-3 pt-1">
              <button type="button" onClick={onClose} className="flex-1 py-2.5 px-4 border border-slate-200 text-slate-600 text-sm font-semibold rounded-xl hover:bg-slate-50 transition-all cursor-pointer">Cancel</button>
              <button type="submit" disabled={saving || !name.trim()} className="flex-1 flex items-center justify-center gap-2 py-2.5 px-4 bg-indigo-600 hover:bg-indigo-500 text-white text-sm font-semibold rounded-xl disabled:opacity-50 disabled:cursor-not-allowed transition-all cursor-pointer">
                {saving ? <Loader2 className="h-4 w-4 animate-spin" /> : (initial ? 'Save Changes' : 'Create Group')}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
}

// ── Add member inline form ────────────────────────────────────────────────────

function AddMemberRow({ onAdd }: { onAdd: (email: string) => Promise<void> }) {
  const [email, setEmail] = useState('');
  const [adding, setAdding] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email.trim()) return;
    setAdding(true);
    try { await onAdd(email.trim().toLowerCase()); setEmail(''); }
    finally { setAdding(false); }
  };

  return (
    <form onSubmit={handleSubmit} className="flex gap-2 mt-3">
      <input type="email" value={email} onChange={e => setEmail(e.target.value)} placeholder="Add member email…" className={`flex-1 ${inputClass}`} />
      <button type="submit" disabled={adding || !email.trim()} className="inline-flex items-center gap-1.5 px-4 py-2.5 border border-indigo-200 rounded-xl text-sm font-medium text-indigo-600 bg-indigo-50 hover:bg-indigo-100 disabled:opacity-40 disabled:cursor-not-allowed transition-colors cursor-pointer">
        {adding ? <Loader2 className="h-4 w-4 animate-spin" /> : <UserPlus className="h-4 w-4" />}
        Add
      </button>
    </form>
  );
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function RecipientGroupsPage() {
  const { isAdmin } = useAuth();
  const [workspaces, setWorkspaces] = useState<Workspace[]>([]);
  const [selectedWsId, setSelectedWsId] = useState('');
  const [wsLoading, setWsLoading] = useState(true);

  const [groups, setGroups] = useState<RecipientGroup[]>([]);
  const [groupsLoading, setGroupsLoading] = useState(false);
  const [expandedGroups, setExpandedGroups] = useState<Set<string>>(new Set());
  const [formOpen, setFormOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<RecipientGroup | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<RecipientGroup | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  // Load workspaces
  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setWsLoading(true);
    const fetchFn = isAdmin ? adminFetchWorkspaces : fetchWorkspaces;
    (fetchFn as () => Promise<Workspace[]>)()
      .then(data => {
        setWorkspaces(data);
        if (data.length > 0) setSelectedWsId(data[0].id);
      })
      .catch(() => toast.error('Failed to load workspaces.'))
      .finally(() => setWsLoading(false));
  }, [isAdmin]);

  // Load groups for selected workspace
  const loadGroups = useCallback(async () => {
    if (!selectedWsId) return;
    setGroupsLoading(true);
    try {
      const data = await fetchRecipientGroups(selectedWsId);
      setGroups(data);
    } catch {
      toast.error('Failed to load recipient groups.');
    } finally {
      setGroupsLoading(false);
    }
  }, [selectedWsId]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    loadGroups();
    setExpandedGroups(new Set());
  }, [loadGroups]);

  const toggleExpand = (id: string) => {
    setExpandedGroups(prev => {
      const n = new Set(prev);
      if (n.has(id)) { n.delete(id); } else { n.add(id); }
      return n;
    });
  };

  const handleCreate = async (name: string, description: string, memberEmails: string[]) => {
    try {
      await createRecipientGroup(selectedWsId, { name, description, memberEmails });
      toast.success(`Group "${name}" created!`);
      setFormOpen(false);
      await loadGroups();
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? 'Failed to create group.';
      toast.error(msg);
    }
  };

  const handleEdit = async (name: string, description: string, memberEmails: string[]) => {
    if (!editTarget) return;
    try {
      await updateRecipientGroup(selectedWsId, editTarget.id, { name, description, memberEmails });
      toast.success(`Group "${name}" updated!`);
      setEditTarget(null);
      await loadGroups();
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? 'Failed to update group.';
      toast.error(msg);
    }
  };

  const handleDelete = async () => {
    if (!deleteTarget) return;
    setIsDeleting(true);
    try {
      await deleteRecipientGroup(selectedWsId, deleteTarget.id);
      toast.success(`Group "${deleteTarget.name}" deleted.`);
      setDeleteTarget(null);
      await loadGroups();
    } catch { toast.error('Failed to delete group.'); }
    finally { setIsDeleting(false); }
  };

  const handleAddMember = async (groupId: string, email: string) => {
    try {
      const updated = await addRecipientGroupMember(selectedWsId, groupId, email);
      setGroups(prev => prev.map(g => g.id === groupId ? updated : g));
      toast.success(`${email} added.`);
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? 'Failed to add member.';
      toast.error(msg);
    }
  };

  const handleRemoveMember = async (groupId: string, email: string) => {
    try {
      const updated = await removeRecipientGroupMember(selectedWsId, groupId, email);
      setGroups(prev => prev.map(g => g.id === groupId ? updated : g));
      toast.success(`${email} removed.`);
    } catch { toast.error('Failed to remove member.'); }
  };

  if (wsLoading) {
    return (
      <div className="flex flex-col items-center justify-center py-20">
        <Loader2 className="h-7 w-7 animate-spin text-indigo-500 mb-3" />
        <p className="text-sm text-slate-400">Loading…</p>
      </div>
    );
  }

  if (workspaces.length === 0) {
    return (
      <div className="text-center py-16 text-slate-400 text-sm">
        No workspaces found. Create a workspace first.
      </div>
    );
  }

  return (
    <div className="space-y-5">
      {/* Group form modal */}
      <GroupFormModal
        isOpen={formOpen || editTarget !== null}
        initial={editTarget}
        onClose={() => { setFormOpen(false); setEditTarget(null); }}
        onSave={editTarget ? handleEdit : handleCreate}
      />

      {/* Delete confirm */}
      <ConfirmDialog
        isOpen={deleteTarget !== null}
        title="Delete Group"
        message={`Delete "${deleteTarget?.name}"? Existing subscriptions are not affected.`}
        confirmLabel="Delete"
        variant="danger"
        isLoading={isDeleting}
        onConfirm={handleDelete}
        onCancel={() => setDeleteTarget(null)}
      />

      {/* Workspace selector + New Group button */}
      <div className="flex items-center justify-between gap-4 flex-wrap">
        <div className="flex items-center gap-3">
          <label className="text-sm font-medium text-slate-600 whitespace-nowrap">Workspace:</label>
          <select
            value={selectedWsId}
            onChange={e => setSelectedWsId(e.target.value)}
            className={`${selectClass} max-w-xs`}
          >
            {workspaces.map(ws => (
              <option key={ws.id} value={ws.id}>{ws.title}</option>
            ))}
          </select>
        </div>
        <button
          onClick={() => { setEditTarget(null); setFormOpen(true); }}
          disabled={!selectedWsId}
          className="flex items-center gap-1.5 px-3.5 py-2 bg-indigo-600 hover:bg-indigo-500 text-white text-sm font-semibold rounded-xl shadow-sm shadow-indigo-500/20 transition-all disabled:opacity-50 cursor-pointer"
        >
          <Plus className="h-4 w-4" />
          New Group
        </button>
      </div>

      {/* Groups list */}
      {groupsLoading ? (
        <div className="flex items-center justify-center py-12">
          <Loader2 className="h-6 w-6 animate-spin text-indigo-500" />
        </div>
      ) : groups.length === 0 ? (
        <div className="bg-white rounded-2xl border border-slate-100 shadow-sm p-12 text-center">
          <div className="h-12 w-12 rounded-2xl bg-slate-50 flex items-center justify-center mx-auto mb-4">
            <Users className="h-6 w-6 text-slate-300" />
          </div>
          <p className="text-slate-500 text-sm mb-1 font-medium">No groups yet</p>
          <p className="text-slate-400 text-xs mb-5">Create a group to bulk-subscribe teams to filter notifications.</p>
          <button
            onClick={() => setFormOpen(true)}
            className="inline-flex items-center gap-2 px-4 py-2 bg-indigo-600 hover:bg-indigo-500 text-white text-sm font-semibold rounded-xl shadow-sm cursor-pointer transition-all"
          >
            <Plus className="h-4 w-4" />
            Create first group
          </button>
        </div>
      ) : (
        <div className="space-y-3">
          {groups.map(group => {
            const isExpanded = expandedGroups.has(group.id);
            return (
              <div key={group.id} className="bg-white rounded-2xl border border-slate-100 shadow-sm overflow-hidden">
                {/* Header */}
                <div className="px-5 py-4 flex items-center gap-3">
                  <button
                    onClick={() => toggleExpand(group.id)}
                    className="p-1 rounded-lg text-slate-400 hover:text-indigo-500 hover:bg-indigo-50 transition-colors cursor-pointer flex-shrink-0"
                  >
                    {isExpanded ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}
                  </button>

                  <div className="h-9 w-9 rounded-xl bg-indigo-50 flex items-center justify-center flex-shrink-0">
                    <Users className="h-4 w-4 text-indigo-600" />
                  </div>

                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="text-sm font-semibold text-slate-900 truncate">{group.name}</span>
                      <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-slate-100 text-slate-500 border border-slate-200 flex-shrink-0">
                        {group.memberCount} member{group.memberCount !== 1 ? 's' : ''}
                      </span>
                    </div>
                    {group.description && (
                      <p className="text-xs text-slate-400 mt-0.5 truncate">{group.description}</p>
                    )}
                  </div>

                  <div className="flex items-center gap-2 flex-shrink-0">
                    <button
                      onClick={() => setEditTarget(group)}
                      className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium text-slate-600 bg-white border border-slate-200 hover:bg-slate-50 hover:border-indigo-200 hover:text-indigo-600 transition-colors cursor-pointer"
                    >
                      <Pencil className="h-3.5 w-3.5" /> Edit
                    </button>
                    <button
                      onClick={() => setDeleteTarget(group)}
                      className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium text-rose-600 bg-rose-50 border border-rose-100 hover:bg-rose-100 transition-colors cursor-pointer"
                    >
                      <Trash2 className="h-3.5 w-3.5" /> Delete
                    </button>
                  </div>
                </div>

                {/* Members panel */}
                {isExpanded && (
                  <div className="border-t border-slate-100 px-5 py-4 bg-slate-50/50">
                    <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-3">Members</p>

                    {group.memberEmails.length === 0 ? (
                      <p className="text-sm text-slate-400 mb-3">No members yet.</p>
                    ) : (
                      <div className="space-y-1.5 mb-3">
                        {Array.from(group.memberEmails).map(email => (
                          <div key={email} className="flex items-center justify-between gap-2 bg-white border border-slate-100 rounded-xl px-3 py-2">
                            <span className="text-sm text-slate-700 truncate">{email}</span>
                            <button
                              onClick={() => handleRemoveMember(group.id, email)}
                              className="p-1 text-slate-400 hover:text-rose-500 transition-colors cursor-pointer flex-shrink-0"
                              title="Remove member"
                            >
                              <UserMinus className="h-3.5 w-3.5" />
                            </button>
                          </div>
                        ))}
                      </div>
                    )}

                    <AddMemberRow onAdd={email => handleAddMember(group.id, email)} />
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
