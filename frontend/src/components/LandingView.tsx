import React, { useEffect, useState } from 'react';
import { fetchWorkspaces, type Workspace } from '../services/apiService';
import { Loader2, ChevronRight, LayoutGrid, AlertCircle } from 'lucide-react';

interface LandingViewProps {
  onSelectWorkspace: (workspaceId: string) => void;
}

const ICON_GRADIENTS = [
  'from-indigo-500 to-violet-600',
  'from-blue-500 to-indigo-600',
  'from-violet-500 to-purple-600',
  'from-sky-500 to-blue-600',
  'from-purple-500 to-pink-600',
  'from-cyan-500 to-sky-600',
];

const ICON_SHADOWS = [
  'shadow-indigo-500/40',
  'shadow-blue-500/40',
  'shadow-violet-500/40',
  'shadow-sky-500/40',
  'shadow-purple-500/40',
  'shadow-cyan-500/40',
];

const LandingView: React.FC<LandingViewProps> = ({ onSelectWorkspace }) => {
  const [workspaces, setWorkspaces] = useState<Workspace[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const loadWorkspaces = async () => {
      try {
        const data = await fetchWorkspaces();
        setWorkspaces(data);
      } catch (err: unknown) {
        const axiosErr = err as { response?: { data?: { message?: string } } };
        if (axiosErr.response?.data?.message) {
          setError(`Failed to load workspaces: ${axiosErr.response.data.message}`);
        } else {
          setError('Network error — please ensure the backend is running and try again.');
        }
      } finally {
        setIsLoading(false);
      }
    };
    loadWorkspaces();
  }, []);

  if (isLoading) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[60vh]">
        <div className="h-14 w-14 rounded-2xl bg-gradient-to-br from-indigo-500 to-violet-600 flex items-center justify-center shadow-xl shadow-indigo-500/30 mb-5 animate-pulse-glow">
          <Loader2 className="h-7 w-7 animate-spin text-white" />
        </div>
        <p className="text-slate-500 dark:text-slate-400 text-sm font-medium">Loading workspaces…</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[60vh] px-4">
        <div className="flex items-start gap-3 bg-red-50 dark:bg-red-500/10 border border-red-200 dark:border-red-500/20 text-red-600 dark:text-red-400 px-6 py-4 rounded-2xl max-w-md text-sm animate-slide-up">
          <AlertCircle className="h-5 w-5 flex-shrink-0 mt-0.5" />
          <span>{error}</span>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-50 dark:bg-slate-950">
      {/* Hero section */}
      <div className="relative bg-white dark:bg-slate-900 border-b border-slate-100 dark:border-slate-800 overflow-hidden">
        {/* Decorative background elements */}
        <div className="absolute inset-0 pointer-events-none">
          <div className="absolute top-0 left-1/3 w-64 h-64 bg-indigo-400/5 dark:bg-indigo-400/8 rounded-full blur-3xl" />
          <div className="absolute bottom-0 right-1/4 w-80 h-80 bg-violet-400/5 dark:bg-violet-400/8 rounded-full blur-3xl" />
        </div>

        <div className="relative max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-14 text-center">
          <div className="inline-flex items-center gap-2 bg-indigo-50 dark:bg-indigo-500/10 text-indigo-600 dark:text-indigo-400 text-xs font-semibold px-3.5 py-1.5 rounded-full mb-5 border border-indigo-100 dark:border-indigo-500/20 animate-fade-in">
            <LayoutGrid className="h-3.5 w-3.5" />
            Workspaces
          </div>
          <h1 className="text-3xl sm:text-4xl font-bold text-slate-900 dark:text-white mb-3 animate-slide-up delay-100">
            Select a Workspace
          </h1>
          <p className="text-slate-500 dark:text-slate-400 text-base max-w-sm mx-auto animate-slide-up delay-200">
            Choose a workspace to manage your email notification subscriptions.
          </p>
        </div>
      </div>

      {/* Workspace grid */}
      <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-10">
        {workspaces.length === 0 ? (
          <div className="text-center bg-white dark:bg-slate-900 rounded-2xl border border-slate-100 dark:border-slate-800 shadow-sm p-14 animate-scale-in">
            <div className="h-14 w-14 rounded-2xl bg-slate-100 dark:bg-slate-800 flex items-center justify-center mx-auto mb-4">
              <LayoutGrid className="h-7 w-7 text-slate-400 dark:text-slate-500" />
            </div>
            <p className="text-slate-500 dark:text-slate-400 text-sm font-medium">No workspaces available.</p>
            <p className="text-slate-400 dark:text-slate-500 text-xs mt-1">Contact your admin to get access.</p>
          </div>
        ) : (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {workspaces.map((workspace, idx) => {
              const gradient = ICON_GRADIENTS[idx % ICON_GRADIENTS.length];
              const shadow = ICON_SHADOWS[idx % ICON_SHADOWS.length];
              const initial = workspace.title ? workspace.title.charAt(0).toUpperCase() : '?';
              return (
                <button
                  key={workspace.id}
                  onClick={() => onSelectWorkspace(workspace.id)}
                  style={{ animationDelay: `${idx * 60}ms` }}
                  className="group animate-slide-up bg-white dark:bg-slate-900 rounded-2xl border border-slate-100 dark:border-slate-800 shadow-sm hover:shadow-lg dark:hover:shadow-slate-900/60 hover:border-indigo-200 dark:hover:border-indigo-700/50 hover:-translate-y-0.5 transition-all duration-200 text-left p-5 flex items-center gap-4 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2 dark:focus-visible:ring-offset-slate-950 cursor-pointer"
                >
                  <div className={`h-12 w-12 rounded-xl bg-gradient-to-br ${gradient} flex items-center justify-center flex-shrink-0 shadow-lg ${shadow} group-hover:scale-110 group-hover:shadow-xl transition-all duration-300`}>
                    <span className="text-white font-bold text-lg">{initial}</span>
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="font-semibold text-slate-900 dark:text-white text-sm truncate group-hover:text-indigo-700 dark:group-hover:text-indigo-400 transition-colors">
                      {workspace.title}
                    </p>
                    <p className="text-slate-400 dark:text-slate-500 text-xs mt-0.5 group-hover:text-indigo-400/70 dark:group-hover:text-indigo-500 transition-colors">
                      Open workspace
                    </p>
                  </div>
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
