import { useEffect, useState, useCallback, useMemo } from 'react';
import { Loader2, Search, ChevronUp, ChevronDown, ChevronsUpDown } from 'lucide-react';
import toast from 'react-hot-toast';
import type { UserSummary } from '../../services/apiService';
import { adminGetUsers } from '../../services/apiService';

type SortKey = 'name' | 'email' | 'subscribedFilterCount';
type SortDir = 'asc' | 'desc';

// Strip "ROLE_" prefix for display, e.g. "ROLE_ADMIN" → "ADMIN"
function formatRole(role: string): string {
  return role.replace(/^ROLE_/, '');
}

// Color mapping for known role names
function roleBadgeClass(role: string): string {
  const name = formatRole(role).toUpperCase();
  if (name === 'ADMIN') return 'bg-purple-100 text-purple-800 border-purple-200';
  if (name === 'MEMBER') return 'bg-blue-100 text-blue-800 border-blue-200';
  return 'bg-gray-100 text-gray-700 border-gray-200';
}

function SortIcon({ active, dir }: { active: boolean; dir: SortDir }) {
  if (!active) return <ChevronsUpDown className="h-3.5 w-3.5 text-gray-400 inline ml-1" />;
  return dir === 'asc'
    ? <ChevronUp className="h-3.5 w-3.5 text-blue-600 inline ml-1" />
    : <ChevronDown className="h-3.5 w-3.5 text-blue-600 inline ml-1" />;
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
    return users.filter(u =>
      !q ||
      u.name.toLowerCase().includes(q) ||
      u.email.toLowerCase().includes(q)
    );
  }, [users, search]);

  const sorted = useMemo(() => {
    return [...filtered].sort((a, b) => {
      let cmp = 0;
      if (sortKey === 'name') {
        cmp = a.name.localeCompare(b.name);
      } else if (sortKey === 'email') {
        cmp = a.email.localeCompare(b.email);
      } else {
        cmp = a.subscribedFilterCount - b.subscribedFilterCount;
      }
      return sortDir === 'asc' ? cmp : -cmp;
    });
  }, [filtered, sortKey, sortDir]);

  return (
    <div>
      {/* Page header */}
      <div className="mb-6 flex items-center justify-between gap-4 flex-wrap">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">Users</h1>
          <p className="mt-1 text-sm text-gray-500">
            All registered users and their subscription activity.
          </p>
        </div>
        {!isLoading && (
          <span className="text-sm text-gray-400">
            {filtered.length} of {users.length} user{users.length !== 1 ? 's' : ''}
          </span>
        )}
      </div>

      {/* Search */}
      <div className="mb-4 relative max-w-sm">
        <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400 pointer-events-none" />
        <input
          type="text"
          value={search}
          onChange={e => setSearch(e.target.value)}
          placeholder="Search by name or email…"
          className="w-full pl-9 pr-3 py-2 border border-gray-300 rounded-lg text-sm shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
        />
      </div>

      {/* Loading */}
      {isLoading ? (
        <div className="flex flex-col items-center justify-center py-20">
          <Loader2 className="h-8 w-8 animate-spin text-blue-500 mb-3" />
          <p className="text-sm text-gray-500">Loading users…</p>
        </div>
      ) : sorted.length === 0 ? (
        /* Empty state */
        <div className="bg-white border border-gray-200 rounded-lg p-12 text-center shadow-sm">
          <p className="text-gray-500 text-sm">
            {search.trim() ? 'No users match your search.' : 'No users found.'}
          </p>
        </div>
      ) : (
        /* Table */
        <div className="bg-white border border-gray-200 rounded-lg shadow-sm overflow-hidden">
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-gray-200 text-sm">
              <thead className="bg-gray-50">
                <tr>
                  <th
                    scope="col"
                    className="px-5 py-3 text-left font-medium text-gray-600 uppercase tracking-wider whitespace-nowrap cursor-pointer select-none hover:bg-gray-100 transition-colors"
                    onClick={() => handleSort('name')}
                  >
                    Name
                    <SortIcon active={sortKey === 'name'} dir={sortDir} />
                  </th>
                  <th
                    scope="col"
                    className="px-5 py-3 text-left font-medium text-gray-600 uppercase tracking-wider whitespace-nowrap cursor-pointer select-none hover:bg-gray-100 transition-colors"
                    onClick={() => handleSort('email')}
                  >
                    Email
                    <SortIcon active={sortKey === 'email'} dir={sortDir} />
                  </th>
                  <th
                    scope="col"
                    className="px-5 py-3 text-left font-medium text-gray-600 uppercase tracking-wider whitespace-nowrap"
                  >
                    Roles
                  </th>
                  <th
                    scope="col"
                    className="px-5 py-3 text-right font-medium text-gray-600 uppercase tracking-wider whitespace-nowrap cursor-pointer select-none hover:bg-gray-100 transition-colors"
                    onClick={() => handleSort('subscribedFilterCount')}
                  >
                    Subscribed Filters
                    <SortIcon active={sortKey === 'subscribedFilterCount'} dir={sortDir} />
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {sorted.map((user, idx) => (
                  <tr
                    key={user.id}
                    className={idx % 2 === 0 ? 'bg-white hover:bg-gray-50' : 'bg-gray-50 hover:bg-gray-100'}
                  >
                    <td className="px-5 py-3 font-medium text-gray-900 whitespace-nowrap">
                      {user.name || <span className="text-gray-400">—</span>}
                    </td>
                    <td className="px-5 py-3 text-gray-600 whitespace-nowrap">
                      {user.email}
                    </td>
                    <td className="px-5 py-3">
                      <div className="flex flex-wrap gap-1">
                        {user.roles.length === 0 ? (
                          <span className="text-gray-400 text-xs">—</span>
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
                    <td className="px-5 py-3 text-right text-gray-700 font-medium tabular-nums whitespace-nowrap">
                      {user.subscribedFilterCount}
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
