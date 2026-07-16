import React, { useState, useEffect, useRef, useCallback } from 'react';
import {
  createSubscription,
  createGroupSubscription,
  fetchWorkspaceUsers,
  fetchRecipientGroups,
  type Filter,
  type Schedule,
  type UserSummary,
  type RecipientGroup,
  type SubscriptionCreatePayload,
} from '../services/apiService';
import { formatHourLabel } from '../services/scheduleUtils';
import { useAuth } from '../hooks/useAuth';
import { Loader2, X, Plus, Bell, ChevronDown, Check, Users } from 'lucide-react';
import toast from 'react-hot-toast';

interface SubscriptionFormModalProps {
  isOpen: boolean;
  workspaceId: string;
  filters: Filter[];
  canManage?: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

const selectClass =
  'w-full px-3.5 py-2.5 border border-slate-200 dark:border-slate-600 rounded-xl text-sm ' +
  'text-slate-900 dark:text-slate-100 bg-white dark:bg-slate-800/60 ' +
  'focus:outline-none focus:border-indigo-500 dark:focus:border-indigo-400 ' +
  'focus:ring-2 focus:ring-indigo-500/15 dark:focus:ring-indigo-400/15 transition-all appearance-none';

const TRIAGE_SLA_FIELD = 'Triage SLA';
const TRIAGE_SLA_OPTIONS = [
  { value: 'GREEN', label: '🟢 Green (0-2 days old)' },
  { value: 'YELLOW', label: '🟡 Yellow (3 days old)' },
  { value: 'RED', label: '🔴 Red (4+ days old)' },
] as const;

/** Returns the display label for a user: "Name (email)" */
function userLabel(u: UserSummary) {
  return `${u.name} (${u.email})`;
}

function filterHasTriageSla(filter?: Filter): boolean {
  if (!filter?.fields) return false;
  try {
    const parsed = JSON.parse(filter.fields);
    return Array.isArray(parsed) && parsed.includes(TRIAGE_SLA_FIELD);
  } catch {
    return false;
  }
}

const SubscriptionFormModal: React.FC<SubscriptionFormModalProps> = ({
  isOpen, workspaceId, filters, canManage = false, onClose, onSuccess,
}) => {
  const { user } = useAuth();

  // --- mode: 'individual' or 'group' ---
  const [mode, setMode] = useState<'individual' | 'group'>('individual');

  // --- core form state ---
  const [selectedFilter, setSelectedFilter] = useState('');
  const [scheduleType, setScheduleType] = useState<'DAILY' | 'WEEKLY'>('DAILY');
  const [scheduledHours, setScheduledHours] = useState<number[]>([]);
  const [hourToAdd, setHourToAdd] = useState<number>(9);
  const [triageSlaThreshold, setTriageSlaThreshold] = useState<'GREEN' | 'YELLOW' | 'RED'>('GREEN');
  const [isSubmitting, setIsSubmitting] = useState(false);

  // --- recipient combobox state (admin-only, individual mode) ---
  const [users, setUsers] = useState<UserSummary[]>([]);
  const [usersLoading, setUsersLoading] = useState(false);
  // null = "Myself"; a UserSummary = subscribe that person
  const [selectedRecipient, setSelectedRecipient] = useState<UserSummary | null>(null);
  const [query, setQuery] = useState('');
  const [dropdownOpen, setDropdownOpen] = useState(false);
  const comboboxRef = useRef<HTMLDivElement>(null);

  // --- group picker state (admin-only, group mode) ---
  const [groups, setGroups] = useState<RecipientGroup[]>([]);
  const [groupsLoading, setGroupsLoading] = useState(false);
  const [selectedGroupId, setSelectedGroupId] = useState('');

  const selectedFilterMeta = filters.find(filter => filter.id === selectedFilter);
  const triageEnabled = filterHasTriageSla(selectedFilterMeta);

  // Fetch users once when modal opens (only for admins, individual mode)
  useEffect(() => {
    if (!isOpen || !canManage) return;
    const loadUsers = () => {
      setUsersLoading(true);
      fetchWorkspaceUsers(workspaceId)
        .then(data => setUsers(data))
        .catch(() => toast.error('Could not load users list.'))
        .finally(() => setUsersLoading(false));
    };
    loadUsers();
  }, [isOpen, canManage, workspaceId]);

  // Fetch groups when modal opens in group mode
  useEffect(() => {
    if (!isOpen || !canManage) return;
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setGroupsLoading(true);
    fetchRecipientGroups(workspaceId)
      .then(data => setGroups(data))
      .catch(() => toast.error('Could not load recipient groups.'))
      .finally(() => setGroupsLoading(false));
  }, [isOpen, canManage, workspaceId]);

  // Close dropdown on outside click
  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (comboboxRef.current && !comboboxRef.current.contains(e.target as Node)) {
        setDropdownOpen(false);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  // "Myself" synthetic entry shown at top of list
  const myselfLabel = `${user?.name ?? 'Myself'} (${user?.email ?? ''}) — you`;

  // Filter: empty query shows all; otherwise search through display label
  const filteredUsers = query.trim()
    ? users.filter(u => userLabel(u).toLowerCase().includes(query.trim().toLowerCase()))
    : users;

  const displayValue = selectedRecipient
    ? userLabel(selectedRecipient)
    : query;

  const isFormValid = selectedFilter !== '' && scheduledHours.length > 0
    && (mode === 'individual' || (mode === 'group' && selectedGroupId !== ''));

  const handleAddHour = () => {
    if (!scheduledHours.includes(hourToAdd)) {
      setScheduledHours(prev => [...prev, hourToAdd].sort((a, b) => a - b));
    }
  };

  const resetForm = useCallback(() => {
    setSelectedFilter('');
    setScheduleType('DAILY');
    setScheduledHours([]);
    setHourToAdd(9);
    setTriageSlaThreshold('GREEN');
    setSelectedRecipient(null);
    setQuery('');
    setDropdownOpen(false);
    setSelectedGroupId('');
    setMode('individual');
  }, []);

  const handleClose = () => { resetForm(); onClose(); };

  const handleSelectUser = (u: UserSummary | null) => {
    setSelectedRecipient(u);
    setQuery('');
    setDropdownOpen(false);
  };

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setSelectedRecipient(null); // clear selection when typing
    setQuery(e.target.value);
    setDropdownOpen(true);
  };

  const handleInputFocus = () => setDropdownOpen(true);

  const handleClearRecipient = () => {
    setSelectedRecipient(null);
    setQuery('');
    setDropdownOpen(false);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!isFormValid) return;
    setIsSubmitting(true);
    try {
      const schedule: Schedule = { type: scheduleType, hours: scheduledHours };

      if (mode === 'group' && selectedGroupId) {
        // Create a single group subscription row — members are expanded at send time
        const payload: SubscriptionCreatePayload = {
          filterId: selectedFilter,
          schedule,
          ...(triageEnabled ? { triageSlaThreshold } : {}),
        };
        const result = await createGroupSubscription(workspaceId, selectedGroupId, payload);
        const groupName = result.groupName ?? groups.find(g => g.id === selectedGroupId)?.name ?? 'group';
        const count = result.groupMemberCount ?? 0;
        toast.success(`Group "${groupName}" subscribed (${count} member${count !== 1 ? 's' : ''} will be notified)!`);
      } else {
        // Individual subscription
        const payload: SubscriptionCreatePayload = {
          filterId: selectedFilter,
          schedule,
          ...(triageEnabled ? { triageSlaThreshold } : {}),
        };
        // Send recipientEmail only when admin explicitly picked someone other than themselves
        if (canManage && selectedRecipient && selectedRecipient.email.toLowerCase() !== user?.email?.toLowerCase()) {
          payload.recipientEmail = selectedRecipient.email;
        }
        await createSubscription(workspaceId, payload);
        toast.success('Subscribed successfully!');
      }

      resetForm();
      onSuccess();
    } catch (err: unknown) {
      const message =
        err instanceof Error
          ? err.message
          : (err as { response?: { data?: { message?: string } } })?.response?.data?.message
            ?? 'Failed to create subscription. Please try again.';
      toast.error(message);
    } finally {
      setIsSubmitting(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4" role="dialog" aria-modal="true" aria-labelledby="sub-form-title">
      {/* Backdrop */}
      <div
        className="absolute inset-0 bg-slate-900/60 dark:bg-slate-950/70 backdrop-blur-sm animate-fade-in"
        onClick={handleClose}
        aria-hidden="true"
      />

      <div className="relative bg-white dark:bg-slate-900 rounded-2xl shadow-2xl dark:shadow-slate-950/60 border border-slate-100/80 dark:border-slate-700/50 w-full max-w-md overflow-hidden animate-scale-in">
        <div className="h-1 bg-gradient-to-r from-indigo-500 via-violet-500 to-purple-500" />

        <div className="px-6 pt-5 pb-6">
          {/* Header */}
          <div className="flex items-center justify-between mb-5">
            <div className="flex items-center gap-2.5">
              <div className="h-8 w-8 rounded-lg bg-indigo-50 dark:bg-indigo-500/10 flex items-center justify-center">
                <Bell className="h-4 w-4 text-indigo-600 dark:text-indigo-400" />
              </div>
              <h3 id="sub-form-title" className="text-base font-semibold text-slate-900 dark:text-white">
                New Subscription
              </h3>
            </div>
            <button
              onClick={handleClose}
              className="p-1.5 rounded-lg text-slate-400 dark:text-slate-500 hover:text-slate-600 dark:hover:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors cursor-pointer"
              aria-label="Close"
            >
              <X className="h-4 w-4" />
            </button>
          </div>

          <form onSubmit={handleSubmit} className="space-y-4">
            {/* Mode toggle — admins can choose individual or group */}
            {canManage && (
              <div className="flex rounded-xl border border-slate-200 dark:border-slate-700 overflow-hidden">
                <button
                  type="button"
                  onClick={() => setMode('individual')}
                  className={`flex-1 flex items-center justify-center gap-1.5 py-2 text-xs font-semibold transition-colors cursor-pointer ${
                    mode === 'individual'
                      ? 'bg-indigo-600 text-white'
                      : 'bg-white dark:bg-slate-800 text-slate-500 dark:text-slate-400 hover:bg-slate-50 dark:hover:bg-slate-700'
                  }`}
                >
                  <Bell className="h-3.5 w-3.5" />
                  Individual
                </button>
                <button
                  type="button"
                  onClick={() => setMode('group')}
                  className={`flex-1 flex items-center justify-center gap-1.5 py-2 text-xs font-semibold transition-colors cursor-pointer ${
                    mode === 'group'
                      ? 'bg-indigo-600 text-white'
                      : 'bg-white dark:bg-slate-800 text-slate-500 dark:text-slate-400 hover:bg-slate-50 dark:hover:bg-slate-700'
                  }`}
                >
                  <Users className="h-3.5 w-3.5" />
                  Group
                </button>
              </div>
            )}

            {/* Group picker (group mode, admin only) */}
            {canManage && mode === 'group' && (
              <div className="space-y-1.5">
                <label className="block text-sm font-medium text-slate-700 dark:text-slate-300">
                  Recipient Group
                </label>
                {groupsLoading ? (
                  <div className="flex items-center gap-2 text-slate-400 text-sm py-2">
                    <Loader2 className="h-4 w-4 animate-spin" /> Loading groups…
                  </div>
                ) : groups.length === 0 ? (
                  <p className="text-xs text-slate-400 dark:text-slate-500 py-1">
                    No groups found. Create one from the workspace dashboard.
                  </p>
                ) : (
                  <select
                    required={mode === 'group'}
                    value={selectedGroupId}
                    onChange={e => setSelectedGroupId(e.target.value)}
                    className={selectClass}
                  >
                    <option value="" disabled>Select a group…</option>
                    {groups.map(g => (
                      <option key={g.id} value={g.id}>
                        {g.name} ({g.memberCount} member{g.memberCount !== 1 ? 's' : ''})
                      </option>
                    ))}
                  </select>
                )}
                <p className="text-xs text-slate-400 dark:text-slate-500">
                  All members of the group will be subscribed to the selected filter.
                </p>
              </div>
            )}

            {/* Recipient picker — admins can subscribe any user (individual mode) */}
            {canManage && mode === 'individual' && (
              <div className="space-y-1.5">
                <label className="block text-sm font-medium text-slate-700 dark:text-slate-300">
                  Recipient
                </label>
                <div ref={comboboxRef} className="relative">
                  <div className="relative flex items-center">
                    <input
                      type="text"
                      autoComplete="off"
                      value={displayValue}
                      onChange={handleInputChange}
                      onFocus={handleInputFocus}
                      placeholder={`${user?.name ?? 'Myself'} (${user?.email ?? ''}) — you`}
                      className="w-full pl-3.5 pr-16 py-2.5 border border-slate-200 dark:border-slate-600 rounded-xl text-sm text-slate-900 dark:text-slate-100 placeholder:text-slate-400 dark:placeholder:text-slate-500 bg-white dark:bg-slate-800/60 focus:outline-none focus:border-indigo-500 dark:focus:border-indigo-400 focus:ring-2 focus:ring-indigo-500/15 dark:focus:ring-indigo-400/15 transition-all"
                    />
                    <div className="absolute right-2 flex items-center gap-1">
                      {selectedRecipient && (
                        <button
                          type="button"
                          onClick={handleClearRecipient}
                          className="p-1 text-slate-400 hover:text-slate-600 dark:hover:text-slate-300 transition-colors"
                          aria-label="Clear selection"
                        >
                          <X className="h-3.5 w-3.5" />
                        </button>
                      )}
                      {usersLoading
                        ? <Loader2 className="h-4 w-4 text-slate-400 animate-spin" />
                        : (
                          <button
                            type="button"
                            onMouseDown={e => { e.preventDefault(); setDropdownOpen(prev => !prev); }}
                            className="p-1 text-slate-400 hover:text-slate-600 dark:hover:text-slate-300 transition-colors"
                            aria-label={dropdownOpen ? 'Close dropdown' : 'Open dropdown'}
                          >
                            <ChevronDown className={`h-4 w-4 transition-transform ${dropdownOpen ? 'rotate-180' : ''}`} />
                          </button>
                        )
                      }
                    </div>
                  </div>

                  {/* Dropdown */}
                  {dropdownOpen && !usersLoading && (
                    <div className="absolute z-10 mt-1.5 w-full bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-700 shadow-lg overflow-hidden max-h-52 overflow-y-auto">
                      {/* Myself option */}
                      <button
                        type="button"
                        onMouseDown={e => { e.preventDefault(); handleSelectUser(null); }}
                        className={`w-full text-left px-3.5 py-2.5 text-sm flex items-center justify-between gap-2 transition-colors
                          ${!selectedRecipient
                            ? 'bg-indigo-50 dark:bg-indigo-500/10 text-indigo-700 dark:text-indigo-300'
                            : 'text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-800'}`}
                      >
                        <span className="truncate">{myselfLabel}</span>
                        {!selectedRecipient && <Check className="h-3.5 w-3.5 flex-shrink-0" />}
                      </button>

                      {/* Divider */}
                      {filteredUsers.length > 0 && (
                        <div className="border-t border-slate-100 dark:border-slate-800" />
                      )}

                      {filteredUsers.length === 0 && query.trim() ? (
                        <p className="px-3.5 py-3 text-sm text-slate-400 dark:text-slate-500">
                          No users match "{query}".
                        </p>
                      ) : (
                        filteredUsers.map(u => {
                          const isSelected = selectedRecipient?.email === u.email;
                          return (
                            <button
                              key={u.id}
                              type="button"
                              onMouseDown={e => { e.preventDefault(); handleSelectUser(u); }}
                              className={`w-full text-left px-3.5 py-2.5 text-sm flex items-center justify-between gap-2 transition-colors
                                ${isSelected
                                  ? 'bg-indigo-50 dark:bg-indigo-500/10 text-indigo-700 dark:text-indigo-300'
                                  : 'text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-800'}`}
                            >
                              <span className="truncate">{userLabel(u)}</span>
                              {isSelected && <Check className="h-3.5 w-3.5 flex-shrink-0" />}
                            </button>
                          );
                        })
                      )}
                    </div>
                  )}
                </div>
                <p className="text-xs text-slate-400 dark:text-slate-500">
                  Leave as yourself or pick another user to subscribe on their behalf.
                </p>
              </div>
            )}

            <div className="space-y-1.5">
              <label htmlFor="sub-filter" className="block text-sm font-medium text-slate-700 dark:text-slate-300">Filter Template</label>
              <select
                id="sub-filter" required value={selectedFilter}
                onChange={e => {
                  setSelectedFilter(e.target.value);
                  setTriageSlaThreshold('GREEN');
                }}
                className={selectClass}
              >
                <option value="" disabled>Select a filter…</option>
                {filters.map(f => <option key={f.id} value={f.id}>{f.title}</option>)}
              </select>
            </div>

            {triageEnabled && (
              <div className="space-y-1.5">
                <label className="block text-sm font-medium text-slate-700 dark:text-slate-300">
                  Triage SLA threshold
                </label>
                <div
                  role="group"
                  aria-label="Triage SLA threshold"
                  className="flex rounded-xl border border-slate-200 dark:border-slate-700 overflow-hidden"
                >
                  {TRIAGE_SLA_OPTIONS.map(option => (
                    <button
                      key={option.value}
                      type="button"
                      onClick={() => setTriageSlaThreshold(option.value)}
                      className={`flex-1 px-3 py-2 text-sm font-semibold transition-colors cursor-pointer ${
                        triageSlaThreshold === option.value
                          ? 'bg-indigo-600 text-white'
                          : 'bg-white dark:bg-slate-800 text-slate-600 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-700'
                      }`}
                    >
                      {option.label}
                    </button>
                  ))}
                </div>
                <p className="text-xs text-slate-400 dark:text-slate-500">
                  Choose the minimum Triage SLA color that should trigger this subscription (default: 🟢 Green).
                </p>
              </div>
            )}

            <div className="space-y-1.5">
              <label htmlFor="sub-schedule-type" className="block text-sm font-medium text-slate-700 dark:text-slate-300">Schedule</label>
              <select
                id="sub-schedule-type" value={scheduleType}
                onChange={e => setScheduleType(e.target.value as 'DAILY' | 'WEEKLY')}
                className={selectClass}
              >
                <option value="DAILY">Daily</option>
                <option value="WEEKLY">Weekly (every Monday)</option>
              </select>
            </div>

            <div className="space-y-1.5">
              <label className="block text-sm font-medium text-slate-700 dark:text-slate-300">Notification Hours</label>
              <div className="flex gap-2">
                <select
                  value={hourToAdd}
                  onChange={e => setHourToAdd(Number(e.target.value))}
                  aria-label="Hour to add"
                  className={`flex-1 ${selectClass}`}
                >
                  {Array.from({ length: 24 }, (_, i) => (
                    <option key={i} value={i}>{formatHourLabel(i)}</option>
                  ))}
                </select>
                <button
                  type="button"
                  onClick={handleAddHour}
                  disabled={scheduledHours.includes(hourToAdd)}
                  className="inline-flex items-center gap-1.5 px-4 py-2.5 border border-indigo-200 dark:border-indigo-500/30 rounded-xl text-sm font-medium text-indigo-600 dark:text-indigo-400 bg-indigo-50 dark:bg-indigo-500/10 hover:bg-indigo-100 dark:hover:bg-indigo-500/20 disabled:opacity-40 disabled:cursor-not-allowed transition-colors cursor-pointer"
                >
                  <Plus className="h-4 w-4" />
                  Add
                </button>
              </div>
              {scheduledHours.length === 0 ? (
                <p className="text-xs text-slate-400 dark:text-slate-500">No hours added yet — add at least one.</p>
              ) : (
                <div className="flex flex-wrap gap-1.5 mt-1">
                  {scheduledHours.map(h => (
                    <span key={h} className="inline-flex items-center gap-1 px-2.5 py-1 bg-indigo-50 dark:bg-indigo-500/10 text-indigo-700 dark:text-indigo-300 border border-indigo-100 dark:border-indigo-500/20 rounded-full text-xs font-medium">
                      {formatHourLabel(h)}
                      <button
                        type="button"
                        onClick={() => setScheduledHours(prev => prev.filter(x => x !== h))}
                        className="hover:text-indigo-900 dark:hover:text-indigo-100 transition-colors ml-0.5 cursor-pointer"
                        aria-label={`Remove ${formatHourLabel(h)}`}
                      >
                        <X className="h-3 w-3" />
                      </button>
                    </span>
                  ))}
                </div>
              )}
            </div>

            <button
              type="submit"
              disabled={!isFormValid || isSubmitting}
              className="w-full flex items-center justify-center gap-2 py-2.5 px-4 bg-indigo-600 hover:bg-indigo-500 dark:bg-indigo-500 dark:hover:bg-indigo-400 text-white text-sm font-semibold rounded-xl shadow-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 dark:focus:ring-offset-slate-900 disabled:opacity-50 disabled:cursor-not-allowed transition-all mt-2 cursor-pointer"
            >
              {isSubmitting ? <Loader2 className="h-4 w-4 animate-spin" /> : 'Subscribe'}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
};

export default SubscriptionFormModal;
