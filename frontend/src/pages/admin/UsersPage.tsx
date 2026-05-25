import { useEffect, useState, useCallback, useMemo } from 'react';
import { Loader2, Search, ChevronUp, ChevronDown, ChevronsUpDown, Users } from 'lucide-react';
import toast from 'react-hot-toast';
import type { UserSummary } from '../../services/apiService';
import { adminGetUsers } from '../../services/apiService';

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
  const [users, setUsers] = useState<UserSummary[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [sortKey, setSortKey] = useState<SortKey>('name');
  const [sortDir, setSortDir] = useState<SortDir>('asc');

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
  }, [load]);

  const handleSort = (key: SortKey) => {
    if (sortKey === key) {
      setSortDir(d => (d === 'asc' ? 'desc' : 'asc'));
    } else {
      setSortKey(key);
      setSortDir('asc');
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

  return (
    <div className="space-y-4">
      {/* Search + count */}
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
        <span className="text-sm text-slate-400">
          {filtered.length} of {users.length} user{users.length !== 1 ? 's' : ''}
        </span>
      </div>

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
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
