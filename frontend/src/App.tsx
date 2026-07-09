import React, { useState } from 'react';
import { BrowserRouter, Routes, Route, Navigate, Link, useNavigate } from 'react-router-dom';
import LandingView from './components/LandingView';
import WorkspaceDashboard from './components/WorkspaceDashboard';
import FilterBuilderView from './components/FilterBuilderView';
import RecipientGroupsView from './components/RecipientGroupsView';
import ProtectedRoute from './components/ProtectedRoute';
import LoginPage from './pages/LoginPage';
import SignupPage from './pages/SignupPage';
import VerifySignupPage from './pages/VerifySignupPage';
import ForgotPasswordPage from './pages/ForgotPasswordPage';
import ResetPasswordPage from './pages/ResetPasswordPage';
import AdminControlPanel from './pages/admin/AdminControlPanel';
import AcceptInvitePage from './pages/AcceptInvitePage';
import AppFooter from './components/AppFooter';
import { AuthProvider, useAuth } from './hooks/useAuth';
import { ThemeProvider, useTheme } from './contexts/ThemeContext';
import { Toaster } from 'react-hot-toast';
import { Mail, LayoutDashboard, LogOut, ShieldCheck, Sun, Moon } from 'lucide-react';

function ThemeToggle() {
  const { isDark, toggleTheme } = useTheme();
  return (
    <button
      onClick={toggleTheme}
      aria-label={isDark ? 'Switch to light mode' : 'Switch to dark mode'}
      className="relative p-2 rounded-xl text-slate-500 dark:text-slate-400 hover:text-slate-700 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-700/60 transition-colors cursor-pointer"
    >
      <span key={String(isDark)} className="block animate-theme-flip">
        {isDark
          ? <Sun className="h-4 w-4 text-amber-400" />
          : <Moon className="h-4 w-4" />}
      </span>
    </button>
  );
}

function AppHeader({ onLogoClick }: { onLogoClick?: () => void }) {
  const { logout, user, isAdmin, isWorkspaceAdmin } = useAuth();
  const navigate = useNavigate();
  const initial = user?.name?.charAt(0).toUpperCase() ?? '?';
  const showAdminNav = isAdmin || isWorkspaceAdmin;

  return (
    <header className="sticky top-0 z-30 bg-white/80 dark:bg-slate-900/80 backdrop-blur-xl border-b border-slate-200/60 dark:border-slate-700/40 shadow-sm dark:shadow-slate-900/20">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-14 flex justify-between items-center gap-4">
        {/* Left: logo + admin nav */}
        <div className="flex items-center gap-6">
          <Link
            to="/"
            onClick={onLogoClick}
            className="flex items-center gap-2.5 group focus-visible:outline-none"
          >
            <div className="h-7 w-7 rounded-lg bg-gradient-to-br from-indigo-500 to-violet-600 flex items-center justify-center shadow-md shadow-indigo-500/30 group-hover:shadow-indigo-500/60 group-hover:scale-110 transition-all duration-200 animate-pulse-glow">
              <Mail className="h-3.5 w-3.5 text-white" />
            </div>
            <span className="font-semibold text-slate-900 dark:text-white text-sm tracking-tight">
              VE Mailer
            </span>
          </Link>

          {showAdminNav && (
            <button
              onClick={() => navigate('/admin')}
              className="flex items-center gap-1.5 text-slate-500 dark:text-slate-400 hover:text-indigo-600 dark:hover:text-indigo-400 text-sm font-medium transition-colors cursor-pointer"
            >
              <LayoutDashboard className="h-3.5 w-3.5" />
              Admin Panel
            </button>
          )}
        </div>

        {/* Right: theme toggle + user + sign out */}
        <div className="flex items-center gap-2">
          <ThemeToggle />

          <div className="w-px h-4 bg-slate-200 dark:bg-slate-700" />

          <div className="flex items-center gap-2.5">
            <div className="h-7 w-7 rounded-full bg-gradient-to-br from-indigo-500/20 to-violet-500/20 dark:from-indigo-500/30 dark:to-violet-500/30 border border-indigo-500/30 dark:border-indigo-400/30 flex items-center justify-center">
              <span className="text-indigo-600 dark:text-indigo-300 text-xs font-bold">{initial}</span>
            </div>
            <span className="text-slate-700 dark:text-slate-300 text-sm hidden sm:block font-medium">
              {user?.name}
            </span>
          </div>

          {showAdminNav && (
            <span className="hidden sm:flex items-center gap-1 text-xs bg-violet-50 dark:bg-violet-500/10 text-violet-600 dark:text-violet-300 border border-violet-200 dark:border-violet-500/25 px-2 py-0.5 rounded-full font-medium">
              <ShieldCheck className="h-3 w-3" />
              {isAdmin ? 'Admin' : 'Workspace Admin'}
            </span>
          )}

          <div className="w-px h-4 bg-slate-200 dark:bg-slate-700" />

          <button
            onClick={() => logout()}
            className="flex items-center gap-1.5 text-slate-500 dark:text-slate-400 hover:text-rose-500 dark:hover:text-rose-400 text-sm font-medium transition-colors cursor-pointer"
          >
            <LogOut className="h-3.5 w-3.5" />
            <span className="hidden sm:block">Sign Out</span>
          </button>
        </div>
      </div>
    </header>
  );
}

export function AdminLayout({ children }: { children: React.ReactNode }) {
  return (
    <>
      <AppHeader />
      {children}
    </>
  );
}

export function AppContent() {
  const [currentView, setCurrentView] = useState<'landing' | 'workspace' | 'filters' | 'groups'>('landing');
  const [selectedWorkspaceId, setSelectedWorkspaceId] = useState<string | null>(null);

  const handleSelectWorkspace = (workspaceId: string) => {
    setSelectedWorkspaceId(workspaceId);
    setCurrentView('workspace');
  };

  const handleBackToLanding = () => {
    setSelectedWorkspaceId(null);
    setCurrentView('landing');
  };

  const handleBackToWorkspace = () => setCurrentView('workspace');
  const handleOpenFilterBuilder = () => setCurrentView('filters');
  const handleOpenGroupManager = () => setCurrentView('groups');

  return (
    <div className="min-h-full bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-slate-50 font-sans">
      <AppHeader onLogoClick={() => { setCurrentView('landing'); setSelectedWorkspaceId(null); }} />

      {currentView === 'landing' && (
        <LandingView onSelectWorkspace={handleSelectWorkspace} />
      )}
      {currentView === 'workspace' && selectedWorkspaceId && (
        <WorkspaceDashboard
          workspaceId={selectedWorkspaceId}
          onBack={handleBackToLanding}
          onOpenFilterBuilder={handleOpenFilterBuilder}
          onOpenGroupManager={handleOpenGroupManager}
        />
      )}
      {currentView === 'filters' && selectedWorkspaceId && (
        <FilterBuilderView
          workspaceId={selectedWorkspaceId}
          onBack={handleBackToWorkspace}
        />
      )}
      {currentView === 'groups' && selectedWorkspaceId && (
        <RecipientGroupsView
          workspaceId={selectedWorkspaceId}
          onBack={handleBackToWorkspace}
        />
      )}
    </div>
  );
}

function App() {
  return (
    <BrowserRouter basename={import.meta.env.VITE_BASE_PATH || '/'}>
      <ThemeProvider>
        <AuthProvider>
          <Toaster
            position="top-right"
            toastOptions={{
              style: {
                borderRadius: '12px',
                fontSize: '14px',
              },
            }}
          />
          <div className="flex h-screen flex-col overflow-hidden">
            <div className="flex-1 overflow-y-auto">
              <Routes>
                <Route path="/login"           element={<LoginPage />} />
                <Route path="/signup"          element={<SignupPage />} />
                <Route path="/verify-signup"   element={<VerifySignupPage />} />
                <Route path="/forgot-password" element={<ForgotPasswordPage />} />
                <Route path="/reset-password"  element={<ResetPasswordPage />} />
                <Route path="/accept-invite"   element={<AcceptInvitePage />} />

                <Route
                  path="/"
                  element={
                    <ProtectedRoute>
                      <AppContent />
                    </ProtectedRoute>
                  }
                />

                <Route
                  path="/admin"
                  element={
                    <ProtectedRoute requiredRoles={['ADMIN', 'WORKSPACE_ADMIN']}>
                      <div className="min-h-full bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-slate-50 font-sans">
                        <AdminLayout>
                          <AdminControlPanel />
                        </AdminLayout>
                      </div>
                    </ProtectedRoute>
                  }
                />

                <Route path="*" element={<Navigate to="/" replace />} />
              </Routes>
            </div>
            <AppFooter />
          </div>
        </AuthProvider>
      </ThemeProvider>
    </BrowserRouter>
  );
}

export default App;
