import { useState } from 'react';
import NotificationPreferencesPage from './NotificationPreferencesPage';
import AiPreferencesPage from './AiPreferencesPage';
import WorkspaceManagementPage from './WorkspaceManagementPage';
import MailAnalyticsPage from './MailAnalyticsPage';

type AdminTab = 'notification-preferences' | 'ai-preferences' | 'workspaces' | 'mail-analytics';

const tabs: { key: AdminTab; label: string }[] = [
  { key: 'workspaces', label: 'Manage Workspaces' },
  { key: 'notification-preferences', label: 'Configure Notification Preferences' },
  { key: 'ai-preferences', label: 'Configure AI Preferences' },
  { key: 'mail-analytics', label: 'Mail Analytics' },
];

export default function AdminControlPanel() {
  const [activeTab, setActiveTab] = useState<AdminTab>('notification-preferences');

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <h1 className="text-2xl font-bold text-gray-900 mb-6">Admin Control Panel</h1>

      <div className="flex gap-8">
        {/* Left sidebar navigation */}
        <nav className="w-64 flex-shrink-0">
          <ul className="space-y-1">
            {tabs.map((tab) => (
              <li key={tab.key}>
                <button
                  onClick={() => setActiveTab(tab.key)}
                  className={`w-full text-left px-4 py-2 rounded-md text-sm font-medium transition-colors ${
                    activeTab === tab.key
                      ? 'bg-blue-50 text-blue-700 border-l-4 border-blue-700'
                      : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900'
                  }`}
                >
                  {tab.label}
                </button>
              </li>
            ))}
          </ul>
        </nav>

        {/* Content area */}
        <div className="flex-1 min-w-0">
          {activeTab === 'notification-preferences' && <NotificationPreferencesPage />}
          {activeTab === 'ai-preferences' && <AiPreferencesPage />}
          {activeTab === 'workspaces' && <WorkspaceManagementPage />}
          {activeTab === 'mail-analytics' && <MailAnalyticsPage />}
        </div>
      </div>
    </div>
  );
}
