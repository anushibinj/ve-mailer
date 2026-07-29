import React, { Suspense, lazy } from 'react';
import { BrowserRouter, Routes, Route, Navigate, Link, useNavigate, useLocation, useParams } from 'react-router-dom';
import ProtectedRoute from './components/ProtectedRoute';
import LoadingPlaceholder from './components/LoadingPlaceholder';
import { AuthProvider, useAuth } from './hooks/useAuth';
import { ThemeProvider, useTheme } from './contexts/ThemeContext';
import { Toaster } from 'react-hot-toast';
import { Mail, LayoutDashboard, LogOut, ShieldCheck, Sun, Moon, ChevronRight, Home } from 'lucide-react';

const LandingView = lazy(() => import('./components/LandingView'));
const WorkspaceDashboard = lazy(() => import('./components/WorkspaceDashboard'));
const FilterBuilderView = lazy(() => import('./components/FilterBuilderView'));
const RecipientGroupsView = lazy(() => import('./components/RecipientGroupsView'));
const LoginPage = lazy(() => import('./pages/LoginPage'));
const SignupPage = lazy(() => import('./pages/SignupPage'));
const VerifySignupPage = lazy(() => import('./pages/VerifySignupPage'));
const ForgotPasswordPage = lazy(() => import('./pages/ForgotPasswordPage'));
const ResetPasswordPage = lazy(() => import('./pages/ResetPasswordPage'));
const AcceptInvitePage = lazy(() => import('./pages/AcceptInvitePage'));
const AdminControlPanel = lazy(() => import('./pages/admin/AdminControlPanel'));
const AppFooter = lazy(() => import('./components/AppFooter'));

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

function AppBreadcrumbs() {
  const location = useLocation();
  const { workspaceId } = useParams<{ workspaceId?: string }>();
  const navigate = useNavigate();

  if (!workspaceId) return null;

  const isFilters = location.pathname.endsWith('/filters');
  const isGroups  = location.pathname.endsWith('/groups');

  return (
    <nav aria-label="breadcrumb" className="hidden sm:flex items-center gap-1 text-xs text-slate-400 dark:text-slate-600">
      <button
        type="button"
        onClick={() => navigate('/')}
        className="flex items-center gap-1 hover:text-indigo-600 dark:hover:text-indigo-400 transition-colors cursor-pointer"
      >
        <Home className="h-3 w-3" />
        Workspaces
      </button>
      <ChevronRight className="h-3 w-3" />
      {isFilters || isGroups ? (
        <>
          <button
            type="button"
            onClick={() => navigate(`/workspace/${workspaceId}`)}
            className="hover:text-indigo-600 dark:hover:text-indigo-400 transition-colors cursor-pointer text-slate-500 dark:text-slate-400"
          >
            Dashboard
          </button>
          <ChevronRight className="h-3 w-3" />
          <span className="text-slate-700 dark:text-slate-300 font-medium">
            {isFilters ? 'Filters' : 'Groups'}
          </span>
        </>
      ) : (
        <span className="text-slate-700 dark:text-slate-300 font-medium">Dashboard</span>
      )}
    </nav>
  );
}

function AppHeader() {
  const { logout, user, isAdmin, isWorkspaceAdmin } = useAuth();
  const navigate = useNavigate();
  const initial = user?.name?.charAt(0).toUpperCase() ?? '?';
  const showAdminNav = isAdmin || isWorkspaceAdmin;

  return (
    <header className="sticky top-0 z-30 bg-white/90 dark:bg-slate-900/90 backdrop-blur-xl border-b border-slate-200/60 dark:border-slate-700/40 shadow-sm dark:shadow-slate-900/20">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-14 flex justify-between items-center gap-4">
        <div className="flex items-center gap-4 min-w-0">
          <Link
            to="/"
            className="flex items-center gap-2.5 group focus-visible:outline-none flex-shrink-0"
          >
            <div className="h-7 w-7 rounded-lg bg-gradient-to-br from-indigo-600 to-violet-600 flex items-center justify-center shadow-md shadow-indigo-500/30 group-hover:shadow-indigo-500/60 group-hover:scale-110 transition-all duration-200 animate-pulse-glow">
              <Mail className="h-3.5 w-3.5 text-white" />
            </div>
            <span className="font-bold text-slate-900 dark:text-white text-sm tracking-tight">
              VE Mailer
            </span>
          </Link>
          <AppBreadcrumbs />
        </div>

        <div className="flex items-center gap-2 flex-shrink-0">
          {showAdminNav && (
            <button
              onClick={() => navigate('/admin')}
              className="hidden sm:flex items-center gap-1.5 text-slate-500 dark:text-slate-400 hover:text-indigo-600 dark:hover:text-indigo-400 text-sm font-medium transition-colors cursor-pointer"
            >
              <LayoutDashboard className="h-3.5 w-3.5" />
              Admin
            </button>
          )}

          <ThemeToggle />

          <div className="w-px h-4 bg-slate-200 dark:bg-slate-700" />

          <div className="flex items-center gap-2">
            <div className="h-7 w-7 rounded-full bg-gradient-to-br from-indigo-500/20 to-violet-500/20 dark:from-indigo-500/30 dark:to-violet-500/30 border border-indigo-500/30 dark:border-indigo-400/30 flex items-center justify-center flex-shrink-0">
              <span className="text-indigo-600 dark:text-indigo-300 text-xs font-bold">{initial}</span>
            </div>
            <span className="text-slate-700 dark:text-slate-300 text-sm hidden sm:block font-medium max-w-[120px] truncate" title={user?.name ?? ''}>
              {user?.name}
            </span>
          </div>

          {showAdminNav && (
            <span className="hidden md:flex items-center gap-1 text-xs bg-violet-50 dark:bg-violet-500/10 text-violet-600 dark:text-violet-300 border border-violet-200 dark:border-violet-500/25 px-2 py-0.5 rounded-full font-medium">
              <ShieldCheck className="h-3 w-3" />
              {isAdmin ? 'Admin' : 'WS Admin'}
            </span>
          )}

          <div className="w-px h-4 bg-slate-200 dark:bg-slate-700" />

          <button
            onClick={() => logout()}
            className="flex items-center gap-1.5 text-slate-500 dark:text-slate-400 hover:text-rose-500 dark:hover:text-rose-400 text-sm font-medium transition-colors cursor-pointer"
            aria-label="Sign out"
          >
            <LogOut className="h-3.5 w-3.5" />
            <span className="hidden sm:block">Sign Out</span>
          </button>
        </div>
      </div>
    </header>
  );
}

function AppShell({ children }: { children: React.ReactNode }) {
  return (
    <div className="min-h-full bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-slate-50">
      <AppHeader />
      <main className="min-h-[calc(100vh-3.5rem)]">
        {children}
      </main>
    </div>
  );
}

function WorkspaceShell() {
  const { workspaceId } = useParams<{ workspaceId: string }>();
  const navigate = useNavigate();

  if (!workspaceId) return <Navigate to="/" replace />;

  return (
    <Routes>
      <Route
        index
        element={
          <Suspense fallback={<LoadingPlaceholder message="Loading workspace..." />}>
            <WorkspaceDashboard
              workspaceId={workspaceId}
              onBack={() => navigate('/')}
              onOpenFilterBuilder={() => navigate(`/workspace/${workspaceId}/filters`)}
              onOpenGroupManager={() => navigate(`/workspace/${workspaceId}/groups`)}
            />
          </Suspense>
        }
      />
      <Route
        path="filters"
        element={
          <Suspense fallback={<LoadingPlaceholder message="Loading filters..." />}>
            <FilterBuilderView
              workspaceId={workspaceId}
              onBack={() => navigate(`/workspace/${workspaceId}`)}
            />
          </Suspense>
        }
      />
      <Route
        path="groups"
        element={
          <Suspense fallback={<LoadingPlaceholder message="Loading groups..." />}>
            <RecipientGroupsView
              workspaceId={workspaceId}
              onBack={() => navigate(`/workspace/${workspaceId}`)}
            />
          </Suspense>
        }
      />
      <Route path="*" element={<Navigate to={`/workspace/${workspaceId}`} replace />} />
    </Routes>
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
                fontFamily: 'Inter, system-ui, sans-serif',
              },
            }}
          />
          <div className="flex h-screen flex-col overflow-hidden">
            <div className="flex-1 overflow-y-auto">
              <Routes>
                <Route path="/login"           element={<Suspense fallback={<LoadingPlaceholder message="Loading..." />}><LoginPage /></Suspense>} />
                <Route path="/signup"          element={<Suspense fallback={<LoadingPlaceholder message="Loading..." />}><SignupPage /></Suspense>} />
                <Route path="/verify-signup"   element={<Suspense fallback={<LoadingPlaceholder message="Loading..." />}><VerifySignupPage /></Suspense>} />
                <Route path="/forgot-password" element={<Suspense fallback={<LoadingPlaceholder message="Loading..." />}><ForgotPasswordPage /></Suspense>} />
                <Route path="/reset-password"  element={<Suspense fallback={<LoadingPlaceholder message="Loading..." />}><ResetPasswordPage /></Suspense>} />
                <Route path="/accept-invite"   element={<Suspense fallback={<LoadingPlaceholder message="Loading..." />}><AcceptInvitePage /></Suspense>} />

                <Route
                  path="/"
                  element={
                    <ProtectedRoute>
                      <AppShell>
                        <Suspense fallback={<LoadingPlaceholder message="Loading workspaces..." />}>
                          <LandingView />
                        </Suspense>
                      </AppShell>
                    </ProtectedRoute>
                  }
                />

                <Route
                  path="/workspace/:workspaceId/*"
                  element={
                    <ProtectedRoute>
                      <AppShell>
                        <WorkspaceShell />
                      </AppShell>
                    </ProtectedRoute>
                  }
                />

                <Route
                  path="/admin"
                  element={
                    <ProtectedRoute requiredRoles={['ADMIN', 'WORKSPACE_ADMIN']}>
                      <AppShell>
                        <Suspense fallback={<LoadingPlaceholder message="Loading admin panel..." />}>
                          <AdminControlPanel />
                        </Suspense>
                      </AppShell>
                    </ProtectedRoute>
                  }
                />

                <Route path="*" element={<Navigate to="/" replace />} />
              </Routes>
            </div>
            <Suspense fallback={null}>
              <AppFooter />
            </Suspense>
          </div>
        </AuthProvider>
      </ThemeProvider>
    </BrowserRouter>
  );
}

export default App;
