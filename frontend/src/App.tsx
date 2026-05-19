import { useState } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import LandingView from './components/LandingView';
import WorkspaceDashboard from './components/WorkspaceDashboard';
import FilterBuilderView from './components/FilterBuilderView';
import ProtectedRoute from './components/ProtectedRoute';
import LoginPage from './pages/LoginPage';
import SignupPage from './pages/SignupPage';
import VerifySignupPage from './pages/VerifySignupPage';
import ForgotPasswordPage from './pages/ForgotPasswordPage';
import ResetPasswordPage from './pages/ResetPasswordPage';
import { AuthProvider, useAuth } from './hooks/useAuth';
import { Toaster } from 'react-hot-toast';

function AppContent() {
  const [currentView, setCurrentView] = useState<'landing' | 'workspace' | 'filters'>('landing');
  const [selectedWorkspaceId, setSelectedWorkspaceId] = useState<string | null>(null);
  const { logout, user } = useAuth();

  const handleSelectWorkspace = (workspaceId: string) => {
    setSelectedWorkspaceId(workspaceId);
    setCurrentView('workspace');
  };

  const handleBackToLanding = () => {
    setSelectedWorkspaceId(null);
    setCurrentView('landing');
  };

  const handleBackToWorkspace = () => {
    setCurrentView('workspace');
  };

  const handleOpenFilterBuilder = () => {
    setCurrentView('filters');
  };

  const handleLogout = async () => {
    await logout();
  };

  return (
    <div className="min-h-screen bg-gray-50 text-gray-900 font-sans">
      {/* Navigation bar */}
      <header className="bg-white shadow-sm border-b border-gray-200">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-3 flex justify-between items-center">
          <h1 className="text-lg font-semibold text-gray-900">VE Mailer</h1>
          <div className="flex items-center gap-4">
            <span className="text-sm text-gray-600">{user?.name}</span>
            {user?.roles?.includes('ADMIN') && (
              <span className="text-xs bg-purple-100 text-purple-700 px-2 py-0.5 rounded-full font-medium">
                Admin
              </span>
            )}
            <button
              onClick={handleLogout}
              className="text-sm text-gray-500 hover:text-gray-700 font-medium"
            >
              Sign Out
            </button>
          </div>
        </div>
      </header>

      {currentView === 'landing' && (
        <LandingView onSelectWorkspace={handleSelectWorkspace} />
      )}
      {currentView === 'workspace' && selectedWorkspaceId && (
        <WorkspaceDashboard
          workspaceId={selectedWorkspaceId}
          onBack={handleBackToLanding}
          onOpenFilterBuilder={handleOpenFilterBuilder}
        />
      )}
      {currentView === 'filters' && selectedWorkspaceId && (
        <FilterBuilderView
          workspaceId={selectedWorkspaceId}
          onBack={handleBackToWorkspace}
        />
      )}
    </div>
  );
}

function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Toaster position="top-right" />
        <Routes>
          {/* Public routes */}
          <Route path="/login" element={<LoginPage />} />
          <Route path="/signup" element={<SignupPage />} />
          <Route path="/verify-signup" element={<VerifySignupPage />} />
          <Route path="/forgot-password" element={<ForgotPasswordPage />} />
          <Route path="/reset-password" element={<ResetPasswordPage />} />

          {/* Protected routes */}
          <Route
            path="/"
            element={
              <ProtectedRoute>
                <AppContent />
              </ProtectedRoute>
            }
          />

          {/* Catch-all redirect */}
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}

export default App;
