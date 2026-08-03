import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { fetchWorkspaces, type Workspace } from '../services/apiService';
import { ChevronRight, LayoutGrid, LayoutList, Plus } from 'lucide-react';
import { ConnectivityBadge, Badge, SkeletonCardGrid, ErrorState, EmptyState, SearchInput } from './ui';
import { useAuth } from '../hooks/useAuth';

const ICON_GRADIENTS = [
  'from-indigo-500 to-violet-600',
  'from-blue-500 to-indigo-600',
  'from-violet-500 to-purple-600',
  'from-sky-500 to-blue-600',
  'from-purple-500 to-pink-600',
  'from-cyan-500 to-sky-600',
];

const ICON_SHADOWS = [
  'shadow-indigo-500/30',
  'shadow-blue-500/30',
  'shadow-violet-500/30',
  'shadow-sky-500/30',
  'shadow-purple-500/30',
  'shadow-cyan-500/30',
];

type WorkspaceViewMode = 'grid' | 'list';
const VIEW_MODE_KEY = 've-mailer-workspace-view-mode';

const LandingView: React.FC = () => {
  const navigate = useNavigate();
  const { isAdmin, isWorkspaceAdmin } = useAuth();
  const canManage = isAdmin || isWorkspaceAdmin;

  const [workspaces, setWorkspaces] = useState<Workspace[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState('');
  const [viewMode, setViewMode] = useState<WorkspaceViewMode>(() => {
    const saved = localStorage.getItem(VIEW_MODE_KEY);
    return saved === 'list' ? 'list' : 'grid';
  });

  useEffect(() => {
    const load = async () => {
      try {
        const data = await fetchWorkspaces();
        setWorkspaces(data);
      } catch (err: unknown) {
        const axiosErr = err as { response?: { data?: { message?: string } } };
        setError(axiosErr.response?.data?.message ?? 'Network error. Check that the backend is running.');
      } finally {
        setIsLoading(false);
      }
    };
    load();
  }, []);

  useEffect(() => {
    localStorage.setItem(VIEW_MODE_KEY, viewMode);
  }, [viewMode]);

  const filtered = search.trim()
    ? workspaces.filter(w =>
        w.title.toLowerCase().includes(search.toLowerCase()) ||
        w.workspaceShortcode?.toLowerCase().includes(search.toLowerCase())
      )
    : workspaces;

  return (
    <div className="min-h-screen bg-slate-50 dark:bg-slate-950">
      {/* Page header */}
      <div className="bg-white dark:bg-slate-900 border-b border-slate-100 dark:border-slate-800">
        <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
          <h1 className="text-2xl font-bold text-slate-900 dark:text-white tracking-tight mb-1">
            Workspaces
          </h1>
          <p className="text-sm text-slate-500 dark:text-slate-400">
            Select a workspace to manage your email notification subscriptions.
          </p>
        </div>
      </div>

      <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {/* Toolbar */}
        <div className="flex items-center gap-3 mb-6">
          <SearchInput
            value={search}
            onChange={e => setSearch(e.target.value)}
            onClear={() => setSearch('')}
            placeholder="Search workspaces..."
            className="flex-1 max-w-xs"
          />
          <div className="ml-auto flex items-center gap-2">
            {canManage && (
              <button
                type="button"
                onClick={() => navigate('/admin?tab=workspaces')}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold border border-transparent rounded-lg text-white bg-indigo-600 hover:bg-indigo-500 dark:bg-indigo-500 dark:hover:bg-indigo-400 transition-all shadow-sm shadow-indigo-500/20 cursor-pointer"
              >
                <Plus className="h-3.5 w-3.5" />
                New Workspace
              </button>
            )}
            <div className="inline-flex items-center rounded-xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 p-1">
              <button
                type="button"
                onClick={() => setViewMode('grid')}
                className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold transition-colors cursor-pointer ${
                  viewMode === 'grid'
                    ? 'bg-indigo-600 text-white'
                    : 'text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800'
                }`}
                aria-pressed={viewMode === 'grid'}
                aria-label="Grid view"
              >
                <LayoutGrid className="h-3.5 w-3.5" />
                <span className="hidden sm:inline">Grid</span>
              </button>
              <button
                type="button"
                onClick={() => setViewMode('list')}
                className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold transition-colors cursor-pointer ${
                  viewMode === 'list'
                    ? 'bg-indigo-600 text-white'
                    : 'text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800'
                }`}
                aria-pressed={viewMode === 'list'}
                aria-label="List view"
              >
                <LayoutList className="h-3.5 w-3.5" />
                <span className="hidden sm:inline">List</span>
              </button>
            </div>
          </div>
        </div>

        {/* States */}
        {isLoading && <SkeletonCardGrid count={6} />}

        {!isLoading && error && (
          <ErrorState message={error} onRetry={() => window.location.reload()} />
        )}

        {!isLoading && !error && filtered.length === 0 && (
          <EmptyState
            icon={<LayoutGrid className="h-7 w-7" />}
            title={search ? 'No workspaces match your search' : 'No workspaces available'}
            description={search ? 'Try a different search term.' : 'Contact your admin to get access to a workspace.'}
          />
        )}

        {!isLoading && !error && filtered.length > 0 && (
          <div className={viewMode === 'grid'
            ? 'grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3'
            : 'space-y-2'
          }>
            {filtered.map((workspace, idx) => {
              const gradient = ICON_GRADIENTS[idx % ICON_GRADIENTS.length];
              const shadow   = ICON_SHADOWS[idx % ICON_SHADOWS.length];
              const avatarShortcode = (workspace.workspaceShortcode || workspace.title || '?')
                .trim()
                .toUpperCase()
                .slice(0, 4);

              return (
                <button
                  key={workspace.id}
                  onClick={() => navigate(`/workspace/${workspace.id}`)}
                  style={{ animationDelay: `${idx * 40}ms` }}
                  className={`group animate-fade-in bg-white dark:bg-slate-900 rounded-2xl border border-slate-100 dark:border-slate-800 shadow-[var(--shadow-card)] hover:shadow-[var(--shadow-card-hover)] hover:border-indigo-200 dark:hover:border-indigo-700/50 transition-all duration-200 text-left flex items-center gap-4 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2 dark:focus-visible:ring-offset-slate-950 cursor-pointer ${
                    viewMode === 'grid'
                      ? 'p-4 hover:-translate-y-0.5'
                      : 'px-5 py-4 w-full'
                  }`}
                >
                  <div className={`h-12 w-12 rounded-full bg-gradient-to-br ${gradient} flex items-center justify-center flex-shrink-0 shadow-lg ${shadow} group-hover:scale-110 transition-all duration-300`}>
                    <span className="text-white font-extrabold font-mono text-[11px] tracking-tight leading-none">
                      {avatarShortcode}
                    </span>
                  </div>

                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 flex-wrap">
                      <p className="font-semibold text-slate-900 dark:text-white text-sm truncate group-hover:text-indigo-700 dark:group-hover:text-indigo-400 transition-colors">
                        {workspace.title}
                      </p>
                      {workspace.workspaceShortcode && (
                        <span className="text-xs text-slate-400 dark:text-slate-500 font-mono hidden sm:inline">
                          {workspace.workspaceShortcode}
                        </span>
                      )}
                      <ConnectivityBadge status={workspace.connectivityStatus} />
                      {workspace.status === 'DRAFT' && (
                        <Badge variant="warning">Draft</Badge>
                      )}
                      {workspace.myWorkspaceAdmin && (
                        <Badge variant="violet">Workspace Admin</Badge>
                      )}
                    </div>
                    <p className="text-xs text-slate-400 dark:text-slate-500 mt-0.5">
                      {viewMode === 'list' ? 'Open workspace' : 'Click to open'}
                    </p>
                  </div>

                  {viewMode === 'list' && (
                    <span className="hidden sm:inline-flex items-center px-3 py-1 rounded-full text-xs font-semibold bg-indigo-50 dark:bg-indigo-500/10 text-indigo-700 dark:text-indigo-300 border border-indigo-100 dark:border-indigo-500/20 flex-shrink-0">
                      Open
                    </span>
                  )}
                  <ChevronRight className="h-4 w-4 text-slate-300 dark:text-slate-600 group-hover:text-indigo-500 dark:group-hover:text-indigo-400 group-hover:translate-x-1 transition-all flex-shrink-0" />
                </button>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
};

export default LandingView;
