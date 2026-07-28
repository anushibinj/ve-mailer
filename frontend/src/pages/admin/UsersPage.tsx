import { useEffect, useState, useCallback, useMemo } from 'react';
import { Loader2, Search, ChevronUp, ChevronDown, ChevronsUpDown, Users, UserPlus, X, AlertCircle, Trash2, RotateCcw, AlertTriangle, ArrowLeft, Mail } from 'lucide-react';
import toast from 'react-hot-toast';
import type { ApiErrorResponse } from '../../types/auth';
import type { UserSummary } from '../../services/apiService';
import { adminDeleteUser, adminGetNonAppUsers, adminGetUsers, adminOnboardUser, adminResendInvite } from '../../services/apiService';
import ConfirmDialog from '../../components/ConfirmDialog';
import { useAuth } from '../../hooks/useAuth';

type SortKey = 'name' | 'email' | 'subscribedFilterCount';
type SortDir = 'asc' | 'desc';

function formatRole(role: string): string {
  return role.replace(/^ROLE_/, '');
}

function roleBadgeClass(role: string): string {
  const name = formatRole(role).toUpperCase();
  if (name === 'ADMIN') return 'bg-violet-50 text-violet-700 border-violet-100';
  if (name === 'MEMBER') return 'bg-indigo-50 text-indigo-700 border-indigo-100';
  return 'bg-slate-50 text-slate-600 border-slate-100';
}

function SortIcon({ active, dir }: { active: boolean; dir: SortDir }) {
  if (!active) return <ChevronsUpDown className="h-3.5 w-3.5 text-slate-400 inline ml-1" />;
  return dir === 'asc'
    ? <ChevronUp className="h-3.5 w-3.5 text-indigo-600 inline ml-1" />
    : <ChevronDown className="h-3.5 w-3.5 text-indigo-600 inline ml-1" />;
}

export default function UsersPage() {
  const { user: currentUser } = useAuth();
  const isSuperAdmin = (currentUser?.roles?.includes('ADMIN') ?? false)
    || (currentUser?.roles?.includes('ROLE_ADMIN') ?? false);
  const [users, setUsers] = useState<UserSummary[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [sortKey, setSortKey] = useState<SortKey>('name');
  const [sortDir, setSortDir] = useState<SortDir>('asc');

  // Non-app users panel state
  type PageView = 'list' | 'non-app-users';
  const [pageView, setPageView] = useState<PageView>('list');
  const [nonAppUsers, setNonAppUsers] = useState<string[]>([]);
  const [nonAppUsersLoading, setNonAppUsersLoading] = useState(false);

  // Onboard modal state
  const [isOnboardOpen, setIsOnboardOpen] = useState(false);
  const [onboardName, setOnboardName] = useState('');
  const [onboardEmail, setOnboardEmail] = useState('');
  const [onboardEmailReadonly, setOnboardEmailReadonly] = useState(false);
  const [isOnboarding, setIsOnboarding] = useState(false);
  const [onboardError, setOnboardError] = useState('');
  const [userToDelete, setUserToDelete] = useState<UserSummary | null>(null);
  const [isDeletingUser, setIsDeletingUser] = useState(false);
  const [resendingInviteUserId, setResendingInviteUserId] = useState<string | null>(null);

  const loadNonAppUsers = useCallback(async () => {
    setNonAppUsersLoading(true);
    try {
      const data = await adminGetNonAppUsers();
      setNonAppUsers(data);
    } catch {
      // Non-critical — banner simply won't appear
    } finally {
      setNonAppUsersLoading(false);
    }
  }, []);

  const load = useCallback(async () => {
    setIsLoading(true);
    try {
      const data = await adminGetUsers();
      setUsers(data);
    } catch {
      toast.error('Failed to load users.');
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    load();
    loadNonAppUsers();
  }, [load, loadNonAppUsers]);

  const handleSort = (key: SortKey) => {
    if (sortKey === key) {
      setSortDir(d => (d === 'asc' ? 'desc' : 'asc'));
    } else {
      setSortKey(key);
      setSortDir('asc');
    }
  };

  const handleOnboard = async (e: React.FormEvent) => {
    e.preventDefault();
    setOnboardError('');
    setIsOnboarding(true);
    const emailTrimmed = onboardEmail.trim();
    try {
      await adminOnboardUser(onboardName.trim(), emailTrimmed);
      toast.success(`Invite sent to ${emailTrimmed}!`);
      setIsOnboardOpen(false);
      setOnboardName('');
      setOnboardEmail('');
      setOnboardEmailReadonly(false);
      if (onboardEmailReadonly) {
        // Remove from non-app users list without a full refetch
        setNonAppUsers(prev => prev.filter(e => e !== emailTrimmed.toLowerCase()));
      } else {
        load();
      }
    } catch (err: unknown) {
      const axiosError = err as { response?: { data?: ApiErrorResponse } };
      setOnboardError(axiosError.response?.data?.message || 'Failed to onboard user.');
    } finally {
      setIsOnboarding(false);
    }
  };

  const openNonAppInviteModal = (email: string) => {
    setOnboardEmail(email);
    setOnboardName('');
    setOnboardEmailReadonly(true);
    setOnboardError('');
    setIsOnboardOpen(true);
  };

  const closeOnboardModal = () => {
    setIsOnboardOpen(false);
    setOnboardName('');
    setOnboardEmail('');
    setOnboardEmailReadonly(false);
    setOnboardError('');
  };

  const handleDeleteUser = async () => {
    if (!userToDelete) return;
    setIsDeletingUser(true);
    try {
      const response = await adminDeleteUser(userToDelete.id);
      setUsers(prev => prev.filter(u => u.id !== userToDelete.id));
      toast.success(response.message || `Deleted ${userToDelete.email}`);
      setUserToDelete(null);
    } catch (err: unknown) {
      const axiosError = err as { response?: { data?: ApiErrorResponse } };
      toast.error(axiosError.response?.data?.message || 'Failed to delete user.');
    } finally {
      setIsDeletingUser(false);
    }
  };

  const handleResendInvite = async (user: UserSummary) => {
    setResendingInviteUserId(user.id);
    try {
      const response = await adminResendInvite(user.id);
      toast.success(response.message || `Invite resent to ${user.email}.`);
    } catch (err: unknown) {
      const axiosError = err as { response?: { data?: ApiErrorResponse } };
      toast.error(axiosError.response?.data?.message || 'Failed to resend invite.');
    } finally {
      setResendingInviteUserId(null);
    }
  };

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return users.filter(u => !q || u.name.toLowerCase().includes(q) || u.email.toLowerCase().includes(q));
  }, [users, search]);

  const sorted = useMemo(() => {
    return [...filtered].sort((a, b) => {
      let cmp = 0;
      if (sortKey === 'name') cmp = a.name.localeCompare(b.name);
      else if (sortKey === 'email') cmp = a.email.localeCompare(b.email);
      else cmp = a.subscribedFilterCount - b.subscribedFilterCount;
      return sortDir === 'asc' ? cmp : -cmp;
    });
  }, [filtered, sortKey, sortDir]);

  if (isLoading) {
    return (
      <div className="flex flex-col items-center justify-center py-20">
        <Loader2 className="h-7 w-7 animate-spin text-indigo-500 mb-3" />
        <p className="text-sm text-slate-400">Loading users…</p>
      </div>
    );
  }

  /* ── Non-app users panel ─────────────────────────────────────────────── */
  if (pageView === 'non-app-users') {
    return (
      <div className="space-y-4">
        <div className="flex items-center gap-3">
          <button
            onClick={() => setPageView('list')}
            className="inline-flex items-center gap-1.5 text-sm font-medium text-indigo-600 hover:text-indigo-500 transition-colors"
          >
            <ArrowLeft className="h-4 w-4" />
            Back to Users
          </button>
        </div>

        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-base font-bold text-slate-900">Non-application users</h2>
            <p className="text-sm text-slate-500 mt-0.5">
              These email addresses appear in recipient group memberships but do not have an application account.
              Send an onboarding invite to create accounts for them.
            </p>
          </div>
          {nonAppUsersLoading && <Loader2 className="h-5 w-5 animate-spin text-slate-400" />}
        </div>

        {nonAppUsers.length === 0 ? (
          <div className="bg-white rounded-2xl border border-slate-100 shadow-sm p-12 text-center">
            <div className="h-12 w-12 rounded-2xl bg-green-50 flex items-center justify-center mx-auto mb-4">
              <Users className="h-6 w-6 text-green-400" />
            </div>
            <p className="text-slate-500 text-sm">All group members have application accounts. 🎉</p>
          </div>
        ) : (
          <div className="bg-white rounded-2xl border border-slate-100 shadow-sm overflow-hidden">
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-slate-100 text-sm">
                <thead>
                  <tr className="bg-slate-50/60">
                    <th scope="col" className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">
                      Email
                    </th>
                    <th scope="col" className="px-5 py-3 text-right text-xs font-semibold text-slate-500 uppercase tracking-wider">
                      Action
                    </th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-50">
                  {nonAppUsers.map(email => (
                    <tr key={email} className="hover:bg-slate-50/70 transition-colors">
                      <td className="px-5 py-3.5 text-slate-700">
                        <div className="flex items-center gap-2.5">
                          <div className="h-7 w-7 rounded-full bg-gradient-to-br from-amber-300 to-orange-400 flex items-center justify-center flex-shrink-0">
                            <Mail className="h-3.5 w-3.5 text-white" />
                          </div>
                          {email}
                        </div>
                      </td>
                      <td className="px-5 py-3.5 text-right">
                        <button
                          type="button"
                          onClick={() => openNonAppInviteModal(email)}
                          className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold rounded-lg bg-indigo-50 text-indigo-700 border border-indigo-100 hover:bg-indigo-100 transition-colors"
                        >
                          <UserPlus className="h-3.5 w-3.5" />
                          Send Invite
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* Onboard modal (shared) */}
        {isOnboardOpen && (
          <OnboardModal
            onboardName={onboardName}
            setOnboardName={setOnboardName}
            onboardEmail={onboardEmail}
            setOnboardEmail={setOnboardEmail}
            onboardEmailReadonly={onboardEmailReadonly}
            isOnboarding={isOnboarding}
            onboardError={onboardError}
            onSubmit={handleOnboard}
            onClose={closeOnboardModal}
          />
        )}
      </div>
    );
  }

  /* ── Main users list ─────────────────────────────────────────────────── */
  return (
    <div className="space-y-4">
      {/* Non-app users banner */}
      {nonAppUsers.length > 0 && (
        <div className="flex items-start gap-3 bg-amber-50 border border-amber-200 rounded-xl px-4 py-3 text-sm text-amber-800">
          <AlertTriangle className="h-4 w-4 mt-0.5 flex-shrink-0 text-amber-500" />
          <span>
            At least one non-application user was found in the system.{' '}
            <button
              type="button"
              onClick={() => setPageView('non-app-users')}
              className="font-semibold underline underline-offset-2 hover:text-amber-900 transition-colors"
            >
              Click here
            </button>{' '}
            to send onboarding invites to them.
          </span>
        </div>
      )}

      {/* Search + Onboard button */}
      <div className="flex items-center justify-between gap-4 flex-wrap">
        <div className="relative max-w-xs w-full">
          <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400 pointer-events-none" />
          <input
            type="text"
            value={search}
            onChange={e => setSearch(e.target.value)}
            placeholder="Search by name or email…"
            className="w-full pl-10 pr-4 py-2.5 border border-slate-200 rounded-xl text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/10 transition-all bg-white"
          />
        </div>
        <div className="flex items-center gap-3">
          <span className="text-sm text-slate-400">
            {filtered.length} of {users.length} user{users.length !== 1 ? 's' : ''}
          </span>
          <button
            onClick={() => { setIsOnboardOpen(true); setOnboardError(''); setOnboardEmailReadonly(false); }}
            className="flex items-center gap-1.5 px-3.5 py-2 bg-indigo-600 hover:bg-indigo-500 text-white text-sm font-semibold rounded-xl shadow-sm shadow-indigo-500/20 transition-all"
          >
            <UserPlus className="h-4 w-4" />
            Onboard User
          </button>
        </div>
      </div>

      {/* Onboard User Modal */}
      {isOnboardOpen && (
        <OnboardModal
          onboardName={onboardName}
          setOnboardName={setOnboardName}
          onboardEmail={onboardEmail}
          setOnboardEmail={setOnboardEmail}
          onboardEmailReadonly={onboardEmailReadonly}
          isOnboarding={isOnboarding}
          onboardError={onboardError}
          onSubmit={handleOnboard}
          onClose={closeOnboardModal}
        />
      )}

      {sorted.length === 0 ? (
        <div className="bg-white rounded-2xl border border-slate-100 shadow-sm p-12 text-center">
          <div className="h-12 w-12 rounded-2xl bg-slate-50 flex items-center justify-center mx-auto mb-4">
            <Users className="h-6 w-6 text-slate-300" />
          </div>
          <p className="text-slate-500 text-sm">
            {search.trim() ? 'No users match your search.' : 'No users found.'}
          </p>
        </div>
      ) : (
        <div className="bg-white rounded-2xl border border-slate-100 shadow-sm overflow-hidden">
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-slate-100 text-sm">
              <thead>
                <tr className="bg-slate-50/60">
                  <th
                    scope="col"
                    className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider whitespace-nowrap cursor-pointer select-none hover:text-indigo-600 transition-colors"
                    onClick={() => handleSort('name')}
                  >
                    Name <SortIcon active={sortKey === 'name'} dir={sortDir} />
                  </th>
                  <th
                    scope="col"
                    className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider whitespace-nowrap cursor-pointer select-none hover:text-indigo-600 transition-colors"
                    onClick={() => handleSort('email')}
                  >
                    Email <SortIcon active={sortKey === 'email'} dir={sortDir} />
                  </th>
                  <th scope="col" className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider whitespace-nowrap">
                    Roles
                  </th>
                  <th
                    scope="col"
                    className="px-5 py-3 text-right text-xs font-semibold text-slate-500 uppercase tracking-wider whitespace-nowrap cursor-pointer select-none hover:text-indigo-600 transition-colors"
                    onClick={() => handleSort('subscribedFilterCount')}
                  >
                    Subscriptions <SortIcon active={sortKey === 'subscribedFilterCount'} dir={sortDir} />
                  </th>
                  {isSuperAdmin && (
                    <th scope="col" className="px-5 py-3 text-right text-xs font-semibold text-slate-500 uppercase tracking-wider whitespace-nowrap">
                      Actions
                    </th>
                  )}
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-50">
                {sorted.map((user) => (
                  <tr key={user.id} className="hover:bg-slate-50/70 transition-colors">
                    <td className="px-5 py-3.5 font-medium text-slate-900 whitespace-nowrap">
                      <div className="flex items-center gap-2.5">
                        <div className="h-7 w-7 rounded-full bg-gradient-to-br from-indigo-400 to-violet-500 flex items-center justify-center flex-shrink-0">
                          <span className="text-white text-xs font-semibold">
                            {user.name?.charAt(0)?.toUpperCase() ?? '?'}
                          </span>
                        </div>
                        {user.name || <span className="text-slate-400">—</span>}
                        {user.mustSetPassword && (
                          <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-amber-50 text-amber-700 border border-amber-200">
                            Pending invite
                          </span>
                        )}
                      </div>
                    </td>
                    <td className="px-5 py-3.5 text-slate-500 whitespace-nowrap">{user.email}</td>
                    <td className="px-5 py-3.5">
                      <div className="flex flex-wrap gap-1">
                        {user.roles.length === 0 ? (
                          <span className="text-slate-400 text-xs">—</span>
                        ) : (
                          user.roles.map(role => (
                            <span
                              key={role}
                              className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium border ${roleBadgeClass(role)}`}
                            >
                              {formatRole(role)}
                            </span>
                          ))
                        )}
                      </div>
                    </td>
                    <td className="px-5 py-3.5 text-right">
                      <span className="inline-flex items-center justify-center min-w-[2rem] px-2 py-0.5 rounded-full bg-indigo-50 text-indigo-700 border border-indigo-100 text-xs font-semibold tabular-nums">
                        {user.subscribedFilterCount}
                      </span>
                    </td>
                    {isSuperAdmin && (
                      <td className="px-5 py-3.5 text-right">
                        <div className="inline-flex items-center gap-2">
                          {user.mustSetPassword && (
                            <button
                              type="button"
                              onClick={() => handleResendInvite(user)}
                              disabled={!!resendingInviteUserId || isDeletingUser}
                              className="inline-flex items-center gap-1.5 px-2.5 py-1.5 text-xs font-semibold rounded-lg border border-indigo-200 text-indigo-600 hover:bg-indigo-50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                              title={`Resend invite to ${user.email}`}
                            >
                              {resendingInviteUserId === user.id ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <RotateCcw className="h-3.5 w-3.5" />}
                              Resend Invite
                            </button>
                          )}
                          <button
                            type="button"
                            onClick={() => setUserToDelete(user)}
                            disabled={isDeletingUser || user.email === currentUser?.email || !!resendingInviteUserId}
                            className="inline-flex items-center gap-1.5 px-2.5 py-1.5 text-xs font-semibold rounded-lg border border-rose-200 text-rose-600 hover:bg-rose-50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                            title={user.email === currentUser?.email ? 'You cannot delete your own account' : `Delete ${user.email}`}
                          >
                            {isDeletingUser && userToDelete?.id === user.id ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Trash2 className="h-3.5 w-3.5" />}
                            Delete
                          </button>
                        </div>
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
      <ConfirmDialog
        isOpen={!!userToDelete}
        title="Delete user permanently?"
        message={userToDelete
          ? `This will permanently remove ${userToDelete.email} from the system. This action cannot be undone.`
          : ''}
        confirmLabel="Delete User"
        cancelLabel="Cancel"
        onConfirm={handleDeleteUser}
        onCancel={() => { if (!isDeletingUser) setUserToDelete(null); }}
        isLoading={isDeletingUser}
        variant="danger"
      />
    </div>
  );
}

/* ── Shared onboard modal ──────────────────────────────────────────────── */
interface OnboardModalProps {
  onboardName: string;
  setOnboardName: (v: string) => void;
  onboardEmail: string;
  setOnboardEmail: (v: string) => void;
  onboardEmailReadonly: boolean;
  isOnboarding: boolean;
  onboardError: string;
  onSubmit: (e: React.FormEvent) => void;
  onClose: () => void;
}

function OnboardModal({
  onboardName, setOnboardName,
  onboardEmail, setOnboardEmail,
  onboardEmailReadonly,
  isOnboarding, onboardError,
  onSubmit, onClose,
}: OnboardModalProps) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm p-4">
      <div className="bg-white dark:bg-slate-900 rounded-2xl shadow-xl border border-slate-100 dark:border-slate-700 w-full max-w-md p-6 space-y-5">
        <div className="flex items-center justify-between">
          <h3 className="text-lg font-bold text-slate-900 dark:text-white">Onboard New User</h3>
          <button
            onClick={onClose}
            className="text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 transition-colors"
          >
            <X className="h-5 w-5" />
          </button>
        </div>
        <p className="text-sm text-slate-500 dark:text-slate-400">
          Creates an account and sends a secure magic link to the user's email. They'll set their own password from that link.
        </p>

        {onboardError && (
          <div className="flex items-center gap-2.5 bg-red-50 dark:bg-red-500/10 border border-red-200 dark:border-red-500/20 text-red-600 dark:text-red-400 px-4 py-3 rounded-xl text-sm">
            <AlertCircle className="h-4 w-4 flex-shrink-0" />
            <span>{onboardError}</span>
          </div>
        )}

        <form onSubmit={onSubmit} className="space-y-4">
          <div className="space-y-1.5">
            <label className="block text-sm font-medium text-slate-700 dark:text-slate-300">Full Name</label>
            <input
              type="text"
              required
              minLength={2}
              maxLength={100}
              value={onboardName}
              onChange={e => setOnboardName(e.target.value)}
              placeholder="Jane Smith"
              className="w-full px-4 py-2.5 border border-slate-200 dark:border-slate-600 rounded-xl text-sm text-slate-900 dark:text-slate-100 placeholder:text-slate-400 bg-white dark:bg-slate-800/60 focus:outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/15 transition-all"
            />
          </div>
          <div className="space-y-1.5">
            <label className="block text-sm font-medium text-slate-700 dark:text-slate-300">Email</label>
            <input
              type="email"
              required
              readOnly={onboardEmailReadonly}
              value={onboardEmail}
              onChange={e => !onboardEmailReadonly && setOnboardEmail(e.target.value)}
              placeholder="jane@company.com"
              className={`w-full px-4 py-2.5 border rounded-xl text-sm placeholder:text-slate-400 focus:outline-none focus:ring-2 transition-all
                ${onboardEmailReadonly
                  ? 'border-slate-200 dark:border-slate-600 bg-slate-50 dark:bg-slate-800/40 text-slate-500 dark:text-slate-400 cursor-not-allowed focus:border-slate-200 focus:ring-slate-200/10'
                  : 'border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-800/60 text-slate-900 dark:text-slate-100 focus:border-indigo-500 focus:ring-indigo-500/15'
                }`}
            />
          </div>
          <div className="flex gap-3 pt-1">
            <button
              type="button"
              onClick={onClose}
              className="flex-1 py-2.5 px-4 border border-slate-200 dark:border-slate-600 text-slate-600 dark:text-slate-300 text-sm font-semibold rounded-xl hover:bg-slate-50 dark:hover:bg-slate-700/50 transition-all"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isOnboarding}
              className="flex-1 flex items-center justify-center gap-2 py-2.5 px-4 bg-indigo-600 hover:bg-indigo-500 text-white text-sm font-semibold rounded-xl disabled:opacity-50 disabled:cursor-not-allowed transition-all"
            >
              {isOnboarding ? <><Loader2 className="h-4 w-4 animate-spin" />Sending…</> : 'Send Invite'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
