import { Suspense, lazy, useState } from 'react';
import { Settings, Layers, Bell, Sparkles, BarChart2, Users, UsersRound } from 'lucide-react';
import { useAuth } from '../../hooks/useAuth';
import LoadingPlaceholder from '../../components/LoadingPlaceholder';

const NotificationPreferencesPage = lazy(() => import('./NotificationPreferencesPage'));
const AiPreferencesPage = lazy(() => import('./AiPreferencesPage'));
const WorkspaceManagementPage = lazy(() => import('./WorkspaceManagementPage'));
const MailAnalyticsPage = lazy(() => import('./MailAnalyticsPage'));
const UsersPage = lazy(() => import('./UsersPage'));
const GeneralSettingsPage = lazy(() => import('./GeneralSettingsPage'));
const RecipientGroupsPage = lazy(() => import('./RecipientGroupsPage'));

type AdminTab = 'notification-preferences' | 'ai-preferences' | 'workspaces' | 'mail-analytics' | 'users' | 'general' | 'recipient-groups';

const allTabs: { key: AdminTab; label: string; icon: React.ReactNode; description: string; adminOnly?: boolean }[] = [
  { key: 'general', label: 'General', icon: <Settings className="h-4 w-4" />, description: 'App-wide settings', adminOnly: true },
  { key: 'workspaces', label: 'Workspaces', icon: <Layers className="h-4 w-4" />, description: 'Manage workspaces' },
  { key: 'recipient-groups', label: 'Groups', icon: <UsersRound className="h-4 w-4" />, description: 'Recipient groups' },
  { key: 'notification-preferences', label: 'Notifications', icon: <Bell className="h-4 w-4" />, description: 'Delivery preferences', adminOnly: true },
  { key: 'ai-preferences', label: 'AI', icon: <Sparkles className="h-4 w-4" />, description: 'AI configuration', adminOnly: true },
  { key: 'mail-analytics', label: 'Analytics', icon: <BarChart2 className="h-4 w-4" />, description: 'Mail statistics', adminOnly: true },
  { key: 'users', label: 'Users', icon: <Users className="h-4 w-4" />, description: 'User management', adminOnly: true },
];

export default function AdminControlPanel() {
  const { isAdmin } = useAuth();
  const tabs = isAdmin ? allTabs : allTabs.filter(t => !t.adminOnly);
  const [activeTab, setActiveTab] = useState<AdminTab>(tabs[0]?.key ?? 'workspaces');
  const active = tabs.find(t => t.key === activeTab);
  const tabLoadingMessage: Record<AdminTab, string> = {
    'notification-preferences': 'Loading settings...',
    'ai-preferences': 'Loading AI settings...',
    'workspaces': 'Loading dashboard...',
    'mail-analytics': 'Loading analytics...',
    'users': 'Loading users...',
    'general': 'Loading settings...',
    'recipient-groups': 'Loading groups...',
  };

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      {/* Page title */}
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-slate-900">Admin Control Panel</h1>
        <p className="text-slate-400 text-sm mt-1">Manage application settings and users</p>
      </div>

      <div className="flex gap-6">
        {/* Sidebar */}
        <nav className="w-56 flex-shrink-0">
          <ul className="space-y-1">
            {tabs.map((tab) => {
              const isActive = activeTab === tab.key;
              return (
                <li key={tab.key}>
                  <button
                    onClick={() => setActiveTab(tab.key)}
                    className={`w-full text-left px-3 py-2.5 rounded-xl text-sm font-medium transition-all cursor-pointer flex items-center gap-3 group ${
                      isActive
                        ? 'bg-indigo-50 text-indigo-700 border border-indigo-100'
                        : 'text-slate-600 hover:bg-slate-50 hover:text-slate-900 border border-transparent'
                    }`}
                  >
                    <span className={`flex-shrink-0 transition-colors ${isActive ? 'text-indigo-600' : 'text-slate-400 group-hover:text-slate-600'}`}>
                      {tab.icon}
                    </span>
                    <span>{tab.label}</span>
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
            <div className="mb-6 pb-4 border-b border-slate-100">
              <div className="flex items-center gap-2.5">
                <div className="h-8 w-8 rounded-lg bg-indigo-50 flex items-center justify-center text-indigo-600">
                  {active.icon}
                </div>
                <div>
                  <h2 className="text-lg font-semibold text-slate-900">{active.label}</h2>
                  <p className="text-xs text-slate-400">{active.description}</p>
                </div>
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
          </Suspense>
        </div>
      </div>
    </div>
  );
}
