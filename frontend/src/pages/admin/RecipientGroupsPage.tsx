import React, { useEffect, useState, useCallback, useRef } from 'react';
import {
  adminFetchWorkspaces,
  fetchRecipientGroups,
  createRecipientGroup,
  updateRecipientGroup,
  deleteRecipientGroup,
  addRecipientGroupMember,
  removeRecipientGroupMember,
  fetchWorkspaceUsers,
  fetchAllowedDomains,
  type Workspace,
  type RecipientGroup,
  type UserSummary,
} from '../../services/apiService';
import {
  Loader2,
  Plus,
  Users,
  Pencil,
  Trash2,
  UserMinus,
  ChevronDown,
  ChevronRight,
  X,
} from 'lucide-react';
import toast from 'react-hot-toast';
import ConfirmDialog from '../../components/ConfirmDialog';
import { TableActionButton } from '../../components/ui';

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

/** Returns true when the query is a valid email ending with one of the allowed domains. */
function isValidCustomEmail(query: string, allowedDomains: string[]): boolean {
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  if (!emailRegex.test(query.trim())) return false;
  const domain = query.trim().split('@')[1].toLowerCase();
  return allowedDomains.some(d => d.trim().toLowerCase() === domain);
}

// ── Group form modal ─────────────────────────────────────────────────────────

interface GroupFormModalProps {
  isOpen: boolean;
  initial?: RecipientGroup | null;
  users: UserSummary[];
  allowedDomains: string[];
  onClose: () => void;
  onSave: (name: string, description: string, memberEmails: string[]) => Promise<void>;
}

function GroupFormModal({ isOpen, initial, users, allowedDomains, onClose, onSave }: GroupFormModalProps) {
  const [name, setName] = useState(initial?.name ?? '');
  const [description, setDescription] = useState(initial?.description ?? '');
  const [selectedEmails, setSelectedEmails] = useState<string[]>(
    initial ? Array.from(initial.memberEmails) : []
  );
  const [query, setQuery] = useState('');
  const [pickerOpen, setPickerOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const pickerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setName(initial?.name ?? '');
    setDescription(initial?.description ?? '');
    setSelectedEmails(initial ? Array.from(initial.memberEmails) : []);
    setQuery('');
    setPickerOpen(false);
  }, [initial, isOpen]);

  useEffect(() => {
    const h = (e: MouseEvent) => {
      if (pickerRef.current && !pickerRef.current.contains(e.target as Node)) {
        setPickerOpen(false);
      }
    };
    document.addEventListener('mousedown', h);
    return () => document.removeEventListener('mousedown', h);
  }, []);

  const available = users.filter(u => {
    const lc = query.toLowerCase();
    return (
      !selectedEmails.includes(u.email.toLowerCase()) &&
      (lc === '' || u.name.toLowerCase().includes(lc) || u.email.toLowerCase().includes(lc))
    );
  });

  const addEmail = (email: string) => {
    const lc = email.toLowerCase();
    if (!selectedEmails.includes(lc)) {
      setSelectedEmails(prev => [...prev, lc]);
    }
    setQuery('');
    setPickerOpen(false);
  };

  const removeEmail = (email: string) => {
    setSelectedEmails(prev => prev.filter(e => e !== email));
  };

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    try {
      await onSave(name.trim(), description.trim(), selectedEmails);
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4" role="dialog" aria-modal="true">
      <div
        className="absolute inset-0 bg-slate-900/60 dark:bg-slate-950/70 backdrop-blur-sm"
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
            <button onClick={onClose} className="p-1.5 rounded-lg text-slate-400 hover:text-slate-600 dark:hover:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors cursor-pointer">
              <X className="h-4 w-4" />
            </button>
          </div>

          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="space-y-1.5">
              <label className="block text-sm font-medium text-slate-700 dark:text-slate-300">Group Name <span className="text-rose-500">*</span></label>
              <input type="text" required value={name} onChange={e => setName(e.target.value)} placeholder="e.g. QA Team" className={inputClass} />
            </div>
            <div className="space-y-1.5">
              <label className="block text-sm font-medium text-slate-700 dark:text-slate-300">Description</label>
              <input type="text" value={description} onChange={e => setDescription(e.target.value)} placeholder="Optional" className={inputClass} />
            </div>
            <div className="space-y-1.5">
              <label className="block text-sm font-medium text-slate-700 dark:text-slate-300">Members</label>

              {/* Selected members as pills */}
              {selectedEmails.length > 0 && (
                <div className="flex flex-wrap gap-1.5 mb-2">
                  {selectedEmails.map(email => {
                    const user = users.find(u => u.email.toLowerCase() === email);
                    return (
                      <span
                        key={email}
                        className="inline-flex items-center gap-1 pl-2.5 pr-1.5 py-1 bg-indigo-50 dark:bg-indigo-500/10 text-indigo-700 dark:text-indigo-300 border border-indigo-100 dark:border-indigo-500/20 rounded-full text-xs font-medium"
                      >
                        {user ? user.name : email}
                        <button
                          type="button"
                          onClick={() => removeEmail(email)}
                          className="hover:text-indigo-900 dark:hover:text-indigo-100 transition-colors ml-0.5 cursor-pointer"
                          aria-label={`Remove ${email}`}
                        >
                          <X className="h-3 w-3" />
                        </button>
                      </span>
                    );
                  })}
                </div>
              )}

              {/* Searchable picker */}
              <div ref={pickerRef} className="relative">
                <input
                  type="text"
                  value={query}
                  onChange={e => { setQuery(e.target.value); setPickerOpen(true); }}
                  onFocus={() => setPickerOpen(true)}
                  placeholder="Search users or enter a custom email…"
                  className={inputClass}
                />
                {pickerOpen && available.length > 0 && (
                  <div className="absolute z-20 mt-1 w-full bg-white dark:bg-slate-800 rounded-xl border border-slate-200 dark:border-slate-700 shadow-lg max-h-44 overflow-y-auto">
                    {available.map(u => (
                      <button
                        key={u.email}
                        type="button"
                        onMouseDown={e => { e.preventDefault(); addEmail(u.email); }}
                        className="w-full px-3 py-2.5 text-left hover:bg-slate-50 dark:hover:bg-slate-700/50 transition-colors"
                      >
                        <div className="text-sm font-medium text-slate-900 dark:text-slate-100">{u.name}</div>
                        <div className="text-xs text-slate-400 dark:text-slate-500">{u.email}</div>
                      </button>
                    ))}
                  </div>
                )}
                {pickerOpen && query && available.length === 0 && (
                  <div className="absolute z-20 mt-1 w-full bg-white dark:bg-slate-800 rounded-xl border border-slate-200 dark:border-slate-700 shadow-lg px-3 py-2.5">
                    {isValidCustomEmail(query, allowedDomains) && !selectedEmails.includes(query.trim().toLowerCase()) ? (
                      <button
                        type="button"
                        onMouseDown={e => { e.preventDefault(); addEmail(query.trim()); }}
                        className="w-full text-left text-sm text-indigo-600 dark:text-indigo-400 font-medium hover:underline"
                      >
                        Add &quot;{query.trim()}&quot; as custom email
                      </button>
                    ) : (
                      <p className="text-sm text-slate-400 dark:text-slate-500">
                        No users match &quot;{query}&quot;.{allowedDomains.length > 0 && (
                          <> Custom emails must end with: <span className="font-medium">{allowedDomains.join(', ')}</span>.</>
                        )}
                      </p>
                    )}
                  </div>
                )}
              </div>
            </div>
            <div className="flex gap-3 pt-1">
              <button type="button" onClick={onClose} className="flex-1 py-2.5 px-4 border border-slate-200 dark:border-slate-600 text-slate-600 dark:text-slate-300 text-sm font-semibold rounded-xl hover:bg-slate-50 dark:hover:bg-slate-700/50 transition-all cursor-pointer">Cancel</button>
              <button type="submit" disabled={saving || !name.trim()} className="flex-1 flex items-center justify-center gap-2 py-2.5 px-4 bg-indigo-600 hover:bg-indigo-500 dark:bg-indigo-500 dark:hover:bg-indigo-400 text-white text-sm font-semibold rounded-xl disabled:opacity-50 disabled:cursor-not-allowed transition-all cursor-pointer">
                {saving ? <Loader2 className="h-4 w-4 animate-spin" /> : (initial ? 'Save Changes' : 'Create Group')}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
}

// ── Add member picker (search + custom email) ─────────────────────────────────

interface AddMemberPickerProps {
  users: UserSummary[];
  existingEmails: Set<string>;
  allowedDomains: string[];
  onAdd: (email: string) => Promise<void>;
}

function AddMemberPicker({ users, existingEmails, allowedDomains, onAdd }: AddMemberPickerProps) {
  const [query, setQuery] = useState('');
  const [open, setOpen] = useState(false);
  const [adding, setAdding] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const h = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', h);
    return () => document.removeEventListener('mousedown', h);
  }, []);

  const available = users.filter(u => {
    const lc = query.toLowerCase();
    return (
      !existingEmails.has(u.email.toLowerCase()) &&
      (lc === '' || u.name.toLowerCase().includes(lc) || u.email.toLowerCase().includes(lc))
    );
  });

  const handleSelect = async (email: string) => {
    setAdding(true);
    setOpen(false);
    setQuery('');
    try {
      await onAdd(email.toLowerCase());
    } finally {
      setAdding(false);
    }
  };

  return (
    <div ref={ref} className="relative mt-3">
      <div className="relative">
        <input
          type="text"
          value={query}
          onChange={e => { setQuery(e.target.value); setOpen(true); }}
          onFocus={() => setOpen(true)}
          placeholder={adding ? 'Adding…' : 'Search users or enter a custom email…'}
          disabled={adding}
          className={`${inputClass} ${adding ? 'opacity-50 cursor-not-allowed' : ''}`}
        />
        {adding && (
          <div className="absolute right-3 top-1/2 -translate-y-1/2">
            <Loader2 className="h-4 w-4 animate-spin text-slate-400" />
          </div>
        )}
      </div>
      {open && available.length > 0 && (
        <div className="absolute z-20 mt-1 w-full bg-white dark:bg-slate-800 rounded-xl border border-slate-200 dark:border-slate-700 shadow-lg max-h-44 overflow-y-auto">
          {available.map(u => (
            <button
              key={u.email}
              type="button"
              onMouseDown={e => { e.preventDefault(); handleSelect(u.email); }}
              className="w-full px-3 py-2.5 text-left hover:bg-slate-50 dark:hover:bg-slate-700/50 transition-colors"
            >
              <div className="text-sm font-medium text-slate-900 dark:text-slate-100">{u.name}</div>
              <div className="text-xs text-slate-400 dark:text-slate-500">{u.email}</div>
            </button>
          ))}
        </div>
      )}
      {open && query && available.length === 0 && (
        <div className="absolute z-20 mt-1 w-full bg-white dark:bg-slate-800 rounded-xl border border-slate-200 dark:border-slate-700 shadow-lg px-3 py-2.5">
          {isValidCustomEmail(query, allowedDomains) && !existingEmails.has(query.trim().toLowerCase()) ? (
            <button
              type="button"
              onMouseDown={e => { e.preventDefault(); handleSelect(query.trim()); }}
              className="w-full text-left text-sm text-indigo-600 dark:text-indigo-400 font-medium hover:underline"
            >
              Add &quot;{query.trim()}&quot; as custom email
            </button>
          ) : (
            <p className="text-sm text-slate-400 dark:text-slate-500">
              No users match &quot;{query}&quot;.{allowedDomains.length > 0 && (
                <> Custom emails must end with: <span className="font-medium">{allowedDomains.join(', ')}</span>.</>
              )}
            </p>
          )}
        </div>
      )}
    </div>
  );
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function RecipientGroupsPage() {
  const [workspaces, setWorkspaces] = useState<Workspace[]>([]);
  const [selectedWsId, setSelectedWsId] = useState('');
  const [wsLoading, setWsLoading] = useState(true);

  const [groups, setGroups] = useState<RecipientGroup[]>([]);
  const [users, setUsers] = useState<UserSummary[]>([]);
  const [allowedDomains, setAllowedDomains] = useState<string[]>([]);
  const [groupsLoading, setGroupsLoading] = useState(false);
  const [expandedGroups, setExpandedGroups] = useState<Set<string>>(new Set());
  const [formOpen, setFormOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<RecipientGroup | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<RecipientGroup | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  // Load allowed domains once on mount
  useEffect(() => {
    fetchAllowedDomains().then(setAllowedDomains).catch(() => { /* non-critical */ });
  }, []);

  // Load workspaces (the admin `/all` endpoint already scopes results to only the workspaces
  // this caller administers when they're a WORKSPACE_ADMIN, and to every workspace for a
  // global ADMIN — see WorkspaceController#getAllWorkspaces).
  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setWsLoading(true);
    adminFetchWorkspaces()
      .then(data => {
        setWorkspaces(data);
        if (data.length > 0) setSelectedWsId(data[0].id);
      })
      .catch(() => toast.error('Failed to load workspaces.'))
      .finally(() => setWsLoading(false));
  }, []);

  // Load groups and workspace users for selected workspace
  const loadGroups = useCallback(async () => {
    if (!selectedWsId) return;
    setGroupsLoading(true);
    try {
      const [data, usersData] = await Promise.all([
        fetchRecipientGroups(selectedWsId),
        fetchWorkspaceUsers(selectedWsId),
      ]);
      setGroups(data);
      setUsers(usersData);
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
        users={users}
        allowedDomains={allowedDomains}
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
          <label className="text-sm font-medium text-slate-600 dark:text-slate-400 whitespace-nowrap">Workspace:</label>
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
        <div className="bg-white dark:bg-slate-900 rounded-2xl border border-slate-100 dark:border-slate-800 shadow-sm p-12 text-center">
          <div className="h-12 w-12 rounded-2xl bg-slate-50 dark:bg-slate-800 flex items-center justify-center mx-auto mb-4">
            <Users className="h-6 w-6 text-slate-300 dark:text-slate-600" />
          </div>
          <p className="text-slate-500 dark:text-slate-400 text-sm mb-1 font-medium">No groups yet</p>
          <p className="text-slate-400 dark:text-slate-500 text-xs mb-5">Create a group to bulk-subscribe teams to filter notifications.</p>
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
              <div key={group.id} className="bg-white dark:bg-slate-900 rounded-2xl border border-slate-100 dark:border-slate-800 shadow-sm">
                {/* Header */}
                <div className="px-5 py-4 flex items-center gap-3">
                  <button
                    onClick={() => toggleExpand(group.id)}
                    className="p-1 rounded-lg text-slate-400 hover:text-indigo-500 dark:hover:text-indigo-400 hover:bg-indigo-50 dark:hover:bg-indigo-500/10 transition-colors cursor-pointer flex-shrink-0"
                  >
                    {isExpanded ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}
                  </button>

                  <div className="h-9 w-9 rounded-xl bg-indigo-50 dark:bg-indigo-500/10 flex items-center justify-center flex-shrink-0">
                    <Users className="h-4 w-4 text-indigo-600 dark:text-indigo-400" />
                  </div>

                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="text-sm font-semibold text-slate-900 dark:text-white truncate">{group.name}</span>
                      <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-slate-100 dark:bg-slate-800 text-slate-500 dark:text-slate-400 border border-slate-200 dark:border-slate-700 flex-shrink-0">
                        {group.memberCount} member{group.memberCount !== 1 ? 's' : ''}
                      </span>
                    </div>
                    {group.description && (
                      <p className="text-xs text-slate-400 dark:text-slate-500 mt-0.5 truncate">{group.description}</p>
                    )}
                  </div>

                  <div className="flex items-center gap-2 flex-shrink-0">
                    <TableActionButton
                      icon={<Pencil className="h-3.5 w-3.5" />}
                      label={`Edit ${group.name}`}
                      variant="secondary"
                      onClick={() => setEditTarget(group)}
                    />
                    <TableActionButton
                      icon={<Trash2 className="h-3.5 w-3.5" />}
                      label={`Delete ${group.name}`}
                      variant="danger"
                      onClick={() => setDeleteTarget(group)}
                    />
                  </div>
                </div>

                {/* Members panel */}
                {isExpanded && (
                  <div className="border-t border-slate-100 dark:border-slate-800 px-5 py-4 bg-slate-50/50 dark:bg-slate-800/30 rounded-b-2xl">
                    <p className="text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider mb-3">Members</p>

                    {group.memberEmails.length === 0 ? (
                      <p className="text-sm text-slate-400 dark:text-slate-500 mb-3">No members yet.</p>
                    ) : (
                      <div className="space-y-1.5 mb-3">
                        {Array.from(group.memberEmails).map(email => (
                          <div key={email} className="flex items-center justify-between gap-2 bg-white dark:bg-slate-800 border border-slate-100 dark:border-slate-700 rounded-xl px-3 py-2">
                            <span className="text-sm text-slate-700 dark:text-slate-300 truncate">{email}</span>
                            <button
                              onClick={() => handleRemoveMember(group.id, email)}
                              className="p-1 text-slate-400 hover:text-rose-500 dark:hover:text-rose-400 transition-colors cursor-pointer flex-shrink-0"
                              title="Remove member"
                            >
                              <UserMinus className="h-3.5 w-3.5" />
                            </button>
                          </div>
                        ))}
                      </div>
                    )}

                    <AddMemberPicker
                      users={users}
                      existingEmails={new Set(Array.from(group.memberEmails).map(e => e.toLowerCase()))}
                      allowedDomains={allowedDomains}
                      onAdd={email => handleAddMember(group.id, email)}
                    />
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
