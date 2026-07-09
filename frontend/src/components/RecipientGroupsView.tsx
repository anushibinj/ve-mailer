import React, { useEffect, useState, useCallback, useRef } from 'react';
import {
  fetchRecipientGroups,
  createRecipientGroup,
  updateRecipientGroup,
  deleteRecipientGroup,
  addRecipientGroupMember,
  removeRecipientGroupMember,
  fetchWorkspaceUsers,
  type RecipientGroup,
  type UserSummary,
} from '../services/apiService';
import {
  ArrowLeft,
  Loader2,
  Plus,
  Users,
  Pencil,
  Trash2,
  UserMinus,
  X,
  ChevronDown,
  ChevronRight,
} from 'lucide-react';
import toast from 'react-hot-toast';
import ConfirmDialog from './ConfirmDialog';

interface RecipientGroupsViewProps {
  workspaceId: string;
  onBack: () => void;
}

const inputClass =
  'w-full px-3.5 py-2.5 border border-slate-200 dark:border-slate-600 rounded-xl text-sm ' +
  'text-slate-900 dark:text-slate-100 bg-white dark:bg-slate-800/60 ' +
  'focus:outline-none focus:border-indigo-500 dark:focus:border-indigo-400 ' +
  'focus:ring-2 focus:ring-indigo-500/15 dark:focus:ring-indigo-400/15 transition-all placeholder:text-slate-400 dark:placeholder:text-slate-500';

// ── Group form modal ─────────────────────────────────────────────────────────

interface GroupFormModalProps {
  isOpen: boolean;
  initial?: RecipientGroup | null;
  users: UserSummary[];
  onClose: () => void;
  onSave: (name: string, description: string, memberEmails: string[]) => Promise<void>;
}

function GroupFormModal({ isOpen, initial, users, onClose, onSave }: GroupFormModalProps) {
  const [name, setName] = useState(initial?.name ?? '');
  const [description, setDescription] = useState(initial?.description ?? '');
  const [selectedEmails, setSelectedEmails] = useState<string[]>(
    initial ? Array.from(initial.memberEmails) : []
  );
  const [query, setQuery] = useState('');
  const [pickerOpen, setPickerOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const pickerRef = useRef<HTMLDivElement>(null);

  // Sync state when modal opens or initial group changes
  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setName(initial?.name ?? '');
    setDescription(initial?.description ?? '');
    setSelectedEmails(initial ? Array.from(initial.memberEmails) : []);
    setQuery('');
    setPickerOpen(false);
  }, [initial, isOpen]);

  // Close picker on outside click
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

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    try {
      await onSave(name.trim(), description.trim(), selectedEmails);
    } finally {
      setSaving(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4"
      role="dialog"
      aria-modal="true"
    >
      <div
        className="absolute inset-0 bg-slate-900/60 dark:bg-slate-950/70 backdrop-blur-sm animate-fade-in"
        onClick={onClose}
        aria-hidden="true"
      />
      <div className="relative bg-white dark:bg-slate-900 rounded-2xl shadow-2xl border border-slate-100/80 dark:border-slate-700/50 w-full max-w-lg overflow-hidden animate-scale-in">
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
            <button
              onClick={onClose}
              className="p-1.5 rounded-lg text-slate-400 hover:text-slate-600 dark:hover:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors cursor-pointer"
            >
              <X className="h-4 w-4" />
            </button>
          </div>

          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="space-y-1.5">
              <label className="block text-sm font-medium text-slate-700 dark:text-slate-300">
                Group Name <span className="text-rose-500">*</span>
              </label>
              <input
                type="text"
                required
                value={name}
                onChange={e => setName(e.target.value)}
                placeholder="e.g. QA Team"
                className={inputClass}
              />
            </div>

            <div className="space-y-1.5">
              <label className="block text-sm font-medium text-slate-700 dark:text-slate-300">
                Description
              </label>
              <input
                type="text"
                value={description}
                onChange={e => setDescription(e.target.value)}
                placeholder="Optional description"
                className={inputClass}
              />
            </div>

            <div className="space-y-1.5">
              <label className="block text-sm font-medium text-slate-700 dark:text-slate-300">
                Members
              </label>

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

              {/* Searchable user picker */}
              <div ref={pickerRef} className="relative">
                <input
                  type="text"
                  value={query}
                  onChange={e => { setQuery(e.target.value); setPickerOpen(true); }}
                  onFocus={() => setPickerOpen(true)}
                  placeholder={users.length === 0 ? 'Loading users…' : 'Search users to add…'}
                  disabled={users.length === 0}
                  className={`${inputClass} ${users.length === 0 ? 'opacity-50 cursor-not-allowed' : ''}`}
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
                    <p className="text-sm text-slate-400 dark:text-slate-500">No users match "{query}".</p>
                  </div>
                )}
              </div>
            </div>

            <div className="flex gap-3 pt-1">
              <button
                type="button"
                onClick={onClose}
                className="flex-1 py-2.5 px-4 border border-slate-200 dark:border-slate-600 text-slate-600 dark:text-slate-300 text-sm font-semibold rounded-xl hover:bg-slate-50 dark:hover:bg-slate-700/50 transition-all cursor-pointer"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={saving || !name.trim()}
                className="flex-1 flex items-center justify-center gap-2 py-2.5 px-4 bg-indigo-600 hover:bg-indigo-500 dark:bg-indigo-500 dark:hover:bg-indigo-400 text-white text-sm font-semibold rounded-xl disabled:opacity-50 disabled:cursor-not-allowed transition-all cursor-pointer"
              >
                {saving ? <Loader2 className="h-4 w-4 animate-spin" /> : (initial ? 'Save Changes' : 'Create Group')}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
}

// ── Add member user picker ────────────────────────────────────────────────────

interface AddMemberPickerProps {
  users: UserSummary[];
  existingEmails: Set<string>;
  onAdd: (email: string) => Promise<void>;
}

function AddMemberPicker({ users, existingEmails, onAdd }: AddMemberPickerProps) {
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
          placeholder={adding ? 'Adding…' : 'Search users to add…'}
          disabled={adding}
          className={`w-full px-3.5 py-2.5 border border-slate-200 dark:border-slate-600 rounded-xl text-sm text-slate-900 dark:text-slate-100 bg-white dark:bg-slate-800/60 focus:outline-none focus:border-indigo-500 dark:focus:border-indigo-400 focus:ring-2 focus:ring-indigo-500/15 dark:focus:ring-indigo-400/15 transition-all placeholder:text-slate-400 dark:placeholder:text-slate-500 ${adding ? 'opacity-50 cursor-not-allowed' : ''}`}
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
          <p className="text-sm text-slate-400 dark:text-slate-500">No users match "{query}".</p>
        </div>
      )}
    </div>
  );
}

// ── Main view ────────────────────────────────────────────────────────────────

const RecipientGroupsView: React.FC<RecipientGroupsViewProps> = ({ workspaceId, onBack }) => {
  const [groups, setGroups] = useState<RecipientGroup[]>([]);
  const [users, setUsers] = useState<UserSummary[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [expandedGroups, setExpandedGroups] = useState<Set<string>>(new Set());
  const [formOpen, setFormOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<RecipientGroup | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<RecipientGroup | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  const load = useCallback(async () => {
    setIsLoading(true);
    try {
      const [groupsData, usersData] = await Promise.all([
        fetchRecipientGroups(workspaceId),
        fetchWorkspaceUsers(workspaceId),
      ]);
      setGroups(groupsData);
      setUsers(usersData);
    } catch {
      toast.error('Failed to load recipient groups.');
    } finally {
      setIsLoading(false);
    }
  }, [workspaceId]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    load();
  }, [load]);

  const toggleExpand = (id: string) => {
    setExpandedGroups(prev => {
      const next = new Set(prev);
      if (next.has(id)) { next.delete(id); } else { next.add(id); }
      return next;
    });
  };

  const handleCreate = async (name: string, description: string, memberEmails: string[]) => {
    try {
      await createRecipientGroup(workspaceId, { name, description, memberEmails });
      toast.success(`Group "${name}" created!`);
      setFormOpen(false);
      await load();
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message
        ?? 'Failed to create group.';
      toast.error(msg);
    }
  };

  const handleEdit = async (name: string, description: string, memberEmails: string[]) => {
    if (!editTarget) return;
    try {
      await updateRecipientGroup(workspaceId, editTarget.id, { name, description, memberEmails });
      toast.success(`Group "${name}" updated!`);
      setEditTarget(null);
      await load();
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message
        ?? 'Failed to update group.';
      toast.error(msg);
    }
  };

  const handleDelete = async () => {
    if (!deleteTarget) return;
    setIsDeleting(true);
    try {
      await deleteRecipientGroup(workspaceId, deleteTarget.id);
      toast.success(`Group "${deleteTarget.name}" deleted.`);
      setDeleteTarget(null);
      await load();
    } catch {
      toast.error('Failed to delete group.');
    } finally {
      setIsDeleting(false);
    }
  };

  const handleAddMember = async (groupId: string, email: string) => {
    try {
      const updated = await addRecipientGroupMember(workspaceId, groupId, email);
      setGroups(prev => prev.map(g => g.id === groupId ? updated : g));
      toast.success(`${email} added to group.`);
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message
        ?? 'Failed to add member.';
      toast.error(msg);
    }
  };

  const handleRemoveMember = async (groupId: string, email: string) => {
    try {
      const updated = await removeRecipientGroupMember(workspaceId, groupId, email);
      setGroups(prev => prev.map(g => g.id === groupId ? updated : g));
      toast.success(`${email} removed from group.`);
    } catch {
      toast.error('Failed to remove member.');
    }
  };

  return (
    <div className="max-w-4xl mx-auto py-8 px-4 sm:px-6 lg:px-8">
      {/* Header */}
      <div className="mb-8 flex items-center justify-between gap-4 flex-wrap animate-fade-in">
        <div className="flex items-center gap-3">
          <button
            onClick={onBack}
            className="p-2 rounded-xl hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors text-slate-500 dark:text-slate-400 hover:text-slate-700 dark:hover:text-slate-200 cursor-pointer"
            aria-label="Back to dashboard"
          >
            <ArrowLeft className="h-5 w-5" />
          </button>
          <div>
            <h1 className="text-2xl font-bold text-slate-900 dark:text-white">Recipient Groups</h1>
            <p className="text-slate-400 dark:text-slate-500 text-sm mt-0.5">
              Manage teams for bulk subscription notifications
            </p>
          </div>
        </div>

        <button
          onClick={() => { setEditTarget(null); setFormOpen(true); }}
          className="inline-flex items-center gap-2 px-4 py-2 border border-transparent rounded-xl text-sm font-semibold text-white bg-indigo-600 hover:bg-indigo-500 dark:bg-indigo-500 dark:hover:bg-indigo-400 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2 dark:focus-visible:ring-offset-slate-950 transition-all shadow-sm shadow-indigo-500/20 cursor-pointer"
        >
          <Plus className="h-4 w-4" />
          New Group
        </button>
      </div>

      {/* Group form modal */}
      <GroupFormModal
        isOpen={formOpen || editTarget !== null}
        initial={editTarget}
        users={users}
        onClose={() => { setFormOpen(false); setEditTarget(null); }}
        onSave={editTarget ? handleEdit : handleCreate}
      />

      {/* Delete confirm */}
      <ConfirmDialog
        isOpen={deleteTarget !== null}
        title="Delete Group"
        message={`Are you sure you want to delete "${deleteTarget?.name}"? This does not affect existing subscriptions.`}
        confirmLabel="Delete"
        variant="danger"
        isLoading={isDeleting}
        onConfirm={handleDelete}
        onCancel={() => setDeleteTarget(null)}
      />

      {/* Groups list */}
      {isLoading ? (
        <div className="flex flex-col items-center justify-center py-20">
          <Loader2 className="h-7 w-7 animate-spin text-indigo-500 mb-3" />
          <p className="text-sm text-slate-400">Loading groups…</p>
        </div>
      ) : groups.length === 0 ? (
        <div className="bg-white dark:bg-slate-900 rounded-2xl border border-slate-100 dark:border-slate-800 shadow-sm p-14 text-center animate-slide-up">
          <div className="h-14 w-14 rounded-2xl bg-slate-50 dark:bg-slate-800 flex items-center justify-center mx-auto mb-5">
            <Users className="h-7 w-7 text-slate-300 dark:text-slate-600" />
          </div>
          <p className="text-slate-600 dark:text-slate-400 text-sm mb-1 font-medium">No groups yet</p>
          <p className="text-slate-400 dark:text-slate-500 text-xs mb-6">
            Create a group to bulk-subscribe a team to filter notifications.
          </p>
          <button
            onClick={() => setFormOpen(true)}
            className="inline-flex items-center gap-2 px-4 py-2 border border-transparent rounded-xl text-sm font-semibold text-white bg-indigo-600 hover:bg-indigo-500 transition-all shadow-sm shadow-indigo-500/20 cursor-pointer"
          >
            <Plus className="h-4 w-4" />
            Create your first group
          </button>
        </div>
      ) : (
        <div className="space-y-3 animate-slide-up">
          {groups.map((group, idx) => {
            const isExpanded = expandedGroups.has(group.id);
            return (
              <div
                key={group.id}
                style={{ animationDelay: `${idx * 40}ms` }}
                className="bg-white dark:bg-slate-900 rounded-2xl border border-slate-100 dark:border-slate-800 shadow-sm overflow-hidden animate-fade-in"
              >
                {/* Group header row */}
                <div className="px-5 py-4 flex items-center gap-3">
                  <button
                    onClick={() => toggleExpand(group.id)}
                    className="p-1 rounded-lg text-slate-400 hover:text-indigo-500 dark:hover:text-indigo-400 hover:bg-indigo-50 dark:hover:bg-indigo-500/10 transition-colors cursor-pointer flex-shrink-0"
                    aria-label={isExpanded ? 'Collapse' : 'Expand'}
                  >
                    {isExpanded
                      ? <ChevronDown className="h-4 w-4" />
                      : <ChevronRight className="h-4 w-4" />
                    }
                  </button>

                  <div className="h-9 w-9 rounded-xl bg-indigo-50 dark:bg-indigo-500/10 flex items-center justify-center flex-shrink-0">
                    <Users className="h-4.5 w-4.5 text-indigo-600 dark:text-indigo-400 h-4 w-4" />
                  </div>

                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="text-sm font-semibold text-slate-900 dark:text-white truncate">
                        {group.name}
                      </span>
                      <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-slate-100 dark:bg-slate-800 text-slate-500 dark:text-slate-400 border border-slate-200 dark:border-slate-700 flex-shrink-0">
                        {group.memberCount} member{group.memberCount !== 1 ? 's' : ''}
                      </span>
                    </div>
                    {group.description && (
                      <p className="text-xs text-slate-400 dark:text-slate-500 mt-0.5 truncate">
                        {group.description}
                      </p>
                    )}
                  </div>

                  {/* Actions */}
                  <div className="flex items-center gap-2 flex-shrink-0">
                    <button
                      onClick={() => setEditTarget(group)}
                      className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium text-slate-600 dark:text-slate-300 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 hover:bg-slate-50 dark:hover:bg-slate-700/60 hover:border-indigo-200 dark:hover:border-indigo-700 hover:text-indigo-600 dark:hover:text-indigo-400 transition-colors cursor-pointer"
                    >
                      <Pencil className="h-3.5 w-3.5" />
                      Edit
                    </button>
                    <button
                      onClick={() => setDeleteTarget(group)}
                      className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium text-rose-600 dark:text-rose-400 bg-rose-50 dark:bg-rose-500/10 border border-rose-100 dark:border-rose-500/20 hover:bg-rose-100 dark:hover:bg-rose-500/20 transition-colors cursor-pointer"
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                      Delete
                    </button>
                  </div>
                </div>

                {/* Members panel (expanded) */}
                {isExpanded && (
                  <div className="border-t border-slate-100 dark:border-slate-800 px-5 py-4 bg-slate-50/50 dark:bg-slate-800/30">
                    <p className="text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider mb-3">
                      Members
                    </p>

                    {group.memberEmails.length === 0 ? (
                      <p className="text-sm text-slate-400 dark:text-slate-500 mb-3">
                        No members yet.
                      </p>
                    ) : (
                      <div className="space-y-1.5 mb-3">
                        {Array.from(group.memberEmails).map(email => (
                          <div
                            key={email}
                            className="flex items-center justify-between gap-2 bg-white dark:bg-slate-900 border border-slate-100 dark:border-slate-800 rounded-xl px-3 py-2"
                          >
                            <span className="text-sm text-slate-700 dark:text-slate-300 truncate">
                              {email}
                            </span>
                            <button
                              onClick={() => handleRemoveMember(group.id, email)}
                              className="p-1 text-slate-400 hover:text-rose-500 dark:hover:text-rose-400 transition-colors cursor-pointer flex-shrink-0"
                              aria-label={`Remove ${email}`}
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
};

export default RecipientGroupsView;
