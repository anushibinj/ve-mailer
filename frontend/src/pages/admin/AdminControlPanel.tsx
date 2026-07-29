import { Suspense, lazy } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { Settings, Layers, Bell, Sparkles, BarChart2, Users, UsersRound, TriangleAlert } from 'lucide-react';
import { useAuth } from '../../hooks/useAuth';
import LoadingPlaceholder from '../../components/LoadingPlaceholder';

const NotificationPreferencesPage = lazy(() => import('./NotificationPreferencesPage'));
const AiPreferencesPage = lazy(() => import('./AiPreferencesPage'));
const WorkspaceManagementPage = lazy(() => import('./WorkspaceManagementPage'));
const MailAnalyticsPage = lazy(() => import('./MailAnalyticsPage'));
const UsersPage = lazy(() => import('./UsersPage'));
const GeneralSettingsPage = lazy(() => import('./GeneralSettingsPage'));
const RecipientGroupsPage = lazy(() => import('./RecipientGroupsPage'));
const IssuesPage = lazy(() => import('./IssuesPage'));

type AdminTab = 'notification-preferences' | 'ai-preferences' | 'workspaces' | 'mail-analytics' | 'users' | 'general' | 'recipient-groups' | 'issues';

const allTabs: { key: AdminTab; label: string; icon: React.ReactNode; description: string; adminOnly?: boolean }[] = [
  { key: 'general',                   label: 'General',       icon: <Settings className="h-4 w-4" />,     description: 'App-wide settings',      adminOnly: true },
  { key: 'workspaces',                label: 'Workspaces',    icon: <Layers className="h-4 w-4" />,       description: 'Manage workspaces' },
  { key: 'recipient-groups',          label: 'Groups',        icon: <UsersRound className="h-4 w-4" />,   description: 'Recipient groups' },
  { key: 'notification-preferences',  label: 'Notifications', icon: <Bell className="h-4 w-4" />,         description: 'Delivery preferences',   adminOnly: true },
  { key: 'ai-preferences',            label: 'AI',            icon: <Sparkles className="h-4 w-4" />,     description: 'AI configuration',       adminOnly: true },
  { key: 'mail-analytics',            label: 'Analytics',     icon: <BarChart2 className="h-4 w-4" />,    description: 'Mail statistics',        adminOnly: true },
  { key: 'users',                     label: 'Users',         icon: <Users className="h-4 w-4" />,        description: 'User management',        adminOnly: true },
  { key: 'issues',                    label: 'Issues',        icon: <TriangleAlert className="h-4 w-4" />,description: 'Issue reports',          adminOnly: true },
];

export default function AdminControlPanel() {
  const { isAdmin } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const tabs = isAdmin ? allTabs : allTabs.filter(t => !t.adminOnly);

  // Derive active tab directly from URL — no state needed, avoids sync effect
  const getTabFromUrl = (): AdminTab => {
    const params = new URLSearchParams(location.search);
    const tab = params.get('tab') as AdminTab | null;
    if (tab && tabs.find(t => t.key === tab)) return tab;
    return tabs[0]?.key ?? 'workspaces';
  };

  const activeTab = getTabFromUrl();

  // Navigate to the selected tab
  const handleTabChange = (key: AdminTab) => {
    navigate(`/admin?tab=${key}`, { replace: true });
  };

  const active = tabs.find(t => t.key === activeTab);
  const tabLoadingMessage: Record<AdminTab, string> = {
    'notification-preferences': 'Loading settings...',
    'ai-preferences': 'Loading AI settings...',
    'workspaces': 'Loading dashboard...',
    'mail-analytics': 'Loading analytics...',
    'users': 'Loading users...',
    'general': 'Loading settings...',
    'recipient-groups': 'Loading groups...',
    'issues': 'Loading issues...',
  };

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      {/* Page title */}
      <div className="mb-8">
        <h1 className="text-xl font-bold text-slate-900 dark:text-white tracking-tight">Admin Panel</h1>
        <p className="text-sm text-slate-500 dark:text-slate-400 mt-0.5">Manage application settings, users, and workspaces</p>
      </div>

      <div className="flex flex-col sm:flex-row gap-6">
        {/* Sidebar nav — horizontal scrollable on mobile, vertical on sm+ */}
        <nav className="sm:w-52 flex-shrink-0" aria-label="Admin sections">
          {/* Mobile: horizontal pill tabs */}
          <ul className="flex sm:hidden gap-1 overflow-x-auto pb-1 -mx-4 px-4">
            {tabs.map((tab) => {
              const isActive = activeTab === tab.key;
              return (
                <li key={tab.key} className="flex-shrink-0">
                  <button
                    onClick={() => handleTabChange(tab.key)}
                    className={`flex items-center gap-1.5 px-3 py-2 rounded-xl text-xs font-semibold whitespace-nowrap transition-all cursor-pointer ${
                      isActive
                        ? 'bg-indigo-600 text-white shadow-sm shadow-indigo-500/20'
                        : 'bg-white dark:bg-slate-800 text-slate-600 dark:text-slate-300 border border-slate-200 dark:border-slate-700 hover:border-indigo-200 dark:hover:border-indigo-700'
                    }`}
                  >
                    {tab.icon}
                    {tab.label}
                  </button>
                </li>
              );
            })}
          </ul>
          {/* Desktop: vertical list */}
          <ul className="hidden sm:flex flex-col space-y-0.5">
            {tabs.map((tab) => {
              const isActive = activeTab === tab.key;
              return (
                <li key={tab.key}>
                  <button
                    onClick={() => handleTabChange(tab.key)}
                    className={`w-full text-left px-3 py-2.5 rounded-xl text-sm font-medium transition-all cursor-pointer flex items-center gap-3 group ${
                      isActive
                        ? 'bg-indigo-50 dark:bg-indigo-500/10 text-indigo-700 dark:text-indigo-300 border border-indigo-100 dark:border-indigo-500/20'
                        : 'text-slate-600 dark:text-slate-400 hover:bg-slate-50 dark:hover:bg-slate-800/60 hover:text-slate-900 dark:hover:text-slate-200 border border-transparent'
                    }`}
                  >
                    <span className={`flex-shrink-0 transition-colors ${isActive ? 'text-indigo-600 dark:text-indigo-400' : 'text-slate-400 dark:text-slate-500 group-hover:text-slate-600 dark:group-hover:text-slate-400'}`}>
                      {tab.icon}
                    </span>
                    <span className="flex-1">{tab.label}</span>
                  </button>
                </li>
              );
            })}
          </ul>
        </nav>

        {/* Content area */}
        <div className="flex-1 min-w-0">
          {/* Section header */}
          {active && (
            <div className="mb-6 pb-5 border-b border-slate-100 dark:border-slate-800 flex items-center gap-3">
              <div className="h-9 w-9 rounded-xl bg-indigo-50 dark:bg-indigo-500/10 flex items-center justify-center text-indigo-600 dark:text-indigo-400 flex-shrink-0">
                {active.icon}
              </div>
              <div>
                <h2 className="text-base font-semibold text-slate-900 dark:text-white">{active.label}</h2>
                <p className="text-xs text-slate-400 dark:text-slate-500">{active.description}</p>
              </div>
            </div>
          )}

          <Suspense fallback={<LoadingPlaceholder message={tabLoadingMessage[activeTab]} />}>
            {activeTab === 'notification-preferences' && <NotificationPreferencesPage />}
            {activeTab === 'ai-preferences' && <AiPreferencesPage />}
            {activeTab === 'workspaces' && <WorkspaceManagementPage />}
            {activeTab === 'mail-analytics' && <MailAnalyticsPage />}
            {activeTab === 'users' && <UsersPage />}
            {activeTab === 'general' && <GeneralSettingsPage />}
            {activeTab === 'recipient-groups' && <RecipientGroupsPage />}
            {activeTab === 'issues' && <IssuesPage />}
          </Suspense>
        </div>
      </div>
    </div>
  );
}
