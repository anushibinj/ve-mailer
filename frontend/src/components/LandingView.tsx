import React, { useEffect, useState } from 'react';
import { fetchWorkspaces, type Workspace } from '../services/apiService';
import { Loader2 } from 'lucide-react';

interface LandingViewProps {
  onSelectWorkspace: (workspaceId: string) => void;
}

const LandingView: React.FC<LandingViewProps> = ({ onSelectWorkspace }) => {
  const [workspaces, setWorkspaces] = useState<Workspace[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const loadWorkspaces = async () => {
      try {
        const data = await fetchWorkspaces();
        setWorkspaces(data);
    } catch (err: any) {
      if (err.response?.data?.message) {
        setError(`Failed to load workspaces: ${err.response.data.message}`);
      } else {
        setError('Network error. Failed to load workspaces. Please ensure the backend server is running and try again.');
      }
      } finally {
        setIsLoading(false);
      }
    };

    loadWorkspaces();
  }, []);

  if (isLoading) {
    return (
      <div className="flex flex-col items-center justify-center min-h-screen bg-gray-50">
        <Loader2 className="h-8 w-8 animate-spin text-blue-500 mb-4" />
        <p className="text-gray-600">Loading workspaces...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex flex-col items-center justify-center min-h-screen bg-gray-50">
        <div className="bg-red-50 text-red-600 p-4 rounded-lg shadow-sm border border-red-200">
          {error}
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50 py-12 px-4 sm:px-6 lg:px-8">
      <div className="max-w-4xl mx-auto">
        <div className="text-center mb-12">
          <h1 className="text-4xl font-extrabold text-gray-900 mb-4">
            Select a Workspace
          </h1>
          <p className="text-xl text-gray-600">
            Choose a workspace to manage your email notification subscriptions.
          </p>
        </div>

        {workspaces.length === 0 ? (
          <div className="text-center text-gray-500 bg-white p-8 rounded-lg shadow-sm border border-gray-200">
            No workspaces available at the moment.
          </div>
        ) : (
          <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
            {workspaces.map((workspace) => (
              <div
                key={workspace.id}
                onClick={() => onSelectWorkspace(workspace.id)}
                className="bg-white overflow-hidden shadow-sm rounded-lg border border-gray-200 hover:shadow-md hover:border-blue-300 transition-all duration-200 cursor-pointer group"
              >
                <div className="px-6 py-8 flex flex-col items-center">
                  <div className="flex items-center justify-center h-12 w-12 rounded-md bg-blue-100 text-blue-600 mx-auto mb-4 group-hover:scale-110 transition-transform duration-200">
                    <span className="text-xl font-bold">{workspace.name ? workspace.name.charAt(0).toUpperCase() : '?'}</span>
                  </div>
                  <h3 className="text-lg font-medium text-gray-900 text-center">
                    {workspace.name}
                  </h3>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};

export default LandingView;
