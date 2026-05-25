import type { ReactNode } from 'react';
import { Mail } from 'lucide-react';

interface AuthShellProps {
  children: ReactNode;
}

export default function AuthShell({ children }: AuthShellProps) {
  return (
    <div className="auth-bg min-h-screen relative flex items-center justify-center px-4 py-12 overflow-hidden">
      {/* Animated atmospheric orbs */}
      <div
        className="absolute top-1/4 left-[15%] w-80 h-80 rounded-full blur-3xl pointer-events-none
          bg-indigo-400/15 dark:bg-indigo-600/25 animate-float-slow"
      />
      <div
        className="absolute bottom-1/4 right-[10%] w-96 h-96 rounded-full blur-3xl pointer-events-none
          bg-violet-400/15 dark:bg-violet-600/20 animate-float-slow delay-2000"
      />
      <div
        className="absolute top-1/2 right-[30%] w-64 h-64 rounded-full blur-3xl pointer-events-none
          bg-blue-400/10 dark:bg-blue-600/15 animate-float-slow delay-1000"
      />

      {/* Card */}
      <div className="relative w-full max-w-md animate-slide-up">
        <div className="bg-white dark:bg-slate-900/90 rounded-3xl overflow-hidden shadow-2xl shadow-slate-900/10 dark:shadow-indigo-900/20 border border-slate-100/80 dark:border-slate-700/50 backdrop-blur-sm">
          {/* Top gradient bar */}
          <div className="h-1 bg-gradient-to-r from-indigo-500 via-violet-500 to-purple-500" />

          <div className="px-8 pt-8 pb-10">
            {/* Brand */}
            <div className="flex items-center gap-3 mb-8">
              <div className="h-10 w-10 rounded-xl bg-gradient-to-br from-indigo-500 to-violet-600 flex items-center justify-center flex-shrink-0 shadow-lg shadow-indigo-500/25 animate-pulse-glow">
                <Mail className="h-5 w-5 text-white" />
              </div>
              <div>
                <p className="text-slate-900 dark:text-white font-bold text-base leading-none">VE Mailer</p>
                <p className="text-slate-400 dark:text-slate-500 text-xs mt-0.5">Notification Manager</p>
              </div>
            </div>

            {children}
          </div>
        </div>
      </div>
    </div>
  );
}
