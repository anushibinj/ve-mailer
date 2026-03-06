import { useState } from 'react';
import LandingView from './components/LandingView';
import WorkspaceDashboard from './components/WorkspaceDashboard';
import { Toaster } from 'react-hot-toast';

function App() {
  const [selectedWorkspaceId, setSelectedWorkspaceId] = useState<string | null>(null);

  const handleSelectWorkspace = (workspaceId: string) => {
    setSelectedWorkspaceId(workspaceId);
  };

  const handleBackToLanding = () => {
    setSelectedWorkspaceId(null);
  };

  return (
    <div className="min-h-screen bg-gray-50 text-gray-900 font-sans">
      <Toaster position="top-right" />

      {!selectedWorkspaceId ? (
        <LandingView onSelectWorkspace={handleSelectWorkspace} />
      ) : (
        <WorkspaceDashboard workspaceId={selectedWorkspaceId} onBack={handleBackToLanding} />
      )}
    </div>
  );
}

export default App;
