import { useState } from 'react';
import LandingView from './components/LandingView';
import WorkspaceDashboard from './components/WorkspaceDashboard';
import FilterBuilderView from './components/FilterBuilderView';
import { Toaster } from 'react-hot-toast';

type View = 'landing' | 'workspace' | 'filters';

function App() {
  const [currentView, setCurrentView] = useState<View>('landing');
  const [selectedWorkspaceId, setSelectedWorkspaceId] = useState<string | null>(null);

  const handleSelectWorkspace = (workspaceId: string) => {
    setSelectedWorkspaceId(workspaceId);
    setCurrentView('workspace');
  };

  const handleBackToLanding = () => {
    setSelectedWorkspaceId(null);
    setCurrentView('landing');
  };

  const handleOpenFilterBuilder = () => {
    setCurrentView('filters');
  };

  return (
    <div className="min-h-screen bg-gray-50 text-gray-900 font-sans">
      <Toaster position="top-right" />

      {currentView === 'landing' && (
        <LandingView
          onSelectWorkspace={handleSelectWorkspace}
          onOpenFilterBuilder={handleOpenFilterBuilder}
        />
      )}
      {currentView === 'workspace' && selectedWorkspaceId && (
        <WorkspaceDashboard workspaceId={selectedWorkspaceId} onBack={handleBackToLanding} />
      )}
      {currentView === 'filters' && (
        <FilterBuilderView onBack={handleBackToLanding} />
      )}
    </div>
  );
}

export default App;
