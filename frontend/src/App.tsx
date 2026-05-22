import React, { useState } from 'react';
import { BrowserRouter, Routes, Route, Navigate, Link, useNavigate } from 'react-router-dom';
import LandingView from './components/LandingView';
import WorkspaceDashboard from './components/WorkspaceDashboard';
import FilterBuilderView from './components/FilterBuilderView';
import ProtectedRoute from './components/ProtectedRoute';
import LoginPage from './pages/LoginPage';
import SignupPage from './pages/SignupPage';
import VerifySignupPage from './pages/VerifySignupPage';
import ForgotPasswordPage from './pages/ForgotPasswordPage';
import ResetPasswordPage from './pages/ResetPasswordPage';
import AdminControlPanel from './pages/admin/AdminControlPanel';
import AppFooter from './components/AppFooter';
import { AuthProvider, useAuth } from './hooks/useAuth';
import { Toaster } from 'react-hot-toast';

export function AdminLayout({ children }: { children: React.ReactNode }) {
  const { logout, user, isAdmin } = useAuth();
  const navigate = useNavigate();

  return (
    <>
      {/* Sticky app-shell header: stays visible while content scrolls below */}
      <header className="sticky top-0 z-30 bg-white shadow-sm border-b border-gray-200">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-3 flex justify-between items-center">
          <div className="flex items-center gap-4">
            <Link
              to="/"
              className="text-lg font-semibold text-gray-900 hover:text-blue-600 transition-colors"
            >
              VE Mailer
            </Link>
            {isAdmin && (
              <button
                onClick={() => navigate('/admin')}
                className="text-sm text-blue-600 font-medium"
              >
                Admin Control Panel
              </button>
            )}
          </div>
          <div className="flex items-center gap-4">
            <span className="text-sm text-gray-600">{user?.name}</span>
            <span className="text-xs bg-purple-100 text-purple-700 px-2 py-0.5 rounded-full font-medium">
              Admin
            </span>
            <button
              onClick={() => logout()}
              className="text-sm text-gray-500 hover:text-gray-700 font-medium"
            >
              Sign Out
            </button>
          </div>
        </div>
      </header>
      {children}
    </>
  );
}

export function AppContent() {
  const [currentView, setCurrentView] = useState<'landing' | 'workspace' | 'filters'>('landing');
  const [selectedWorkspaceId, setSelectedWorkspaceId] = useState<string | null>(null);
  const { logout, user, isAdmin } = useAuth();
  const navigate = useNavigate();

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
    <div className="min-h-full bg-gray-50 text-gray-900 font-sans">
      {/* Sticky app-shell header: stays visible while content scrolls below */}
      <header className="sticky top-0 z-30 bg-white shadow-sm border-b border-gray-200">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-3 flex justify-between items-center">
          <div className="flex items-center gap-4">
            <Link
              to="/"
              onClick={() => { setCurrentView('landing'); setSelectedWorkspaceId(null); }}
              className="text-lg font-semibold text-gray-900 hover:text-blue-600 transition-colors"
            >
              VE Mailer
            </Link>
            {isAdmin && (
              <button
                onClick={() => navigate('/admin')}
                className="text-sm text-gray-500 hover:text-blue-600 font-medium transition-colors"
              >
                Admin Control Panel
              </button>
            )}
          </div>
          <div className="flex items-center gap-4">
            <span className="text-sm text-gray-600">{user?.name}</span>
            {isAdmin && (
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
        {/* App-shell layout: outer container fixes the viewport height and prevents outer page
            scroll; the inner div is the only scrollable region; AppFooter pins to the bottom. */}
        <div className="flex h-screen flex-col overflow-hidden">
          <div className="flex-1 overflow-y-auto">
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

              {/* Admin-only routes */}
              <Route
                path="/admin"
                element={
                  <ProtectedRoute requiredRole="ADMIN">
                    <div className="min-h-full bg-gray-50 text-gray-900 font-sans">
                      <AdminLayout>
                        <AdminControlPanel />
                      </AdminLayout>
                    </div>
                  </ProtectedRoute>
                }
              />

              {/* Catch-all redirect */}
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </div>
          {/* Persistent app footer — rendered only when VITE_FOOTER_HTML is set */}
          <AppFooter />
        </div>
      </AuthProvider>
    </BrowserRouter>
  );
}

export default App;
