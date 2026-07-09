import { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { forgotPassword } from '../services/authService';
import type { ApiErrorResponse } from '../types/auth';
import toast from 'react-hot-toast';
import { Loader2, AlertCircle, Clock, Mail, ArrowLeft } from 'lucide-react';
import AuthShell from '../components/AuthShell';

/** Extracts the leading integer from strings like "Please wait 72 more seconds…" */
function parseWaitSeconds(message: string): number {
  const match = message.match(/\b(\d+)\b/);
  return match ? parseInt(match[1], 10) : 0;
}

export default function ForgotPasswordPage() {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');
  const [countdown, setCountdown] = useState(0);

  // Tick down the cooldown timer every second
  useEffect(() => {
    if (countdown <= 0) return;
    const timer = setTimeout(() => setCountdown(prev => prev - 1), 1000);
    return () => clearTimeout(timer);
  }, [countdown]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (countdown > 0) return;
    setError('');
    setIsLoading(true);
    try {
      await forgotPassword({ email });
      toast.success('If your account exists, an OTP has been sent.');
      navigate('/reset-password', { state: { email } });
    } catch (err: unknown) {
      const axiosError = err as { response?: { status?: number; data?: ApiErrorResponse } };
      const message = axiosError.response?.data?.message || 'Failed to send OTP. Please try again.';
      // If the backend tells us to wait, start a live countdown and navigate the user to the
      // OTP entry page — they already have a valid code from the previous send.
      const waitSecs = axiosError.response?.status === 429 ? parseWaitSeconds(message) : 0;
      if (waitSecs > 0) {
        setCountdown(waitSecs);
        setError('');
        toast('You already have an active code. Please check your email.', { icon: '📬' });
        navigate('/reset-password', { state: { email } });
      } else {
        setError(message);
      }
    } finally {
      setIsLoading(false);
    }
  };

  const isBlocked = countdown > 0;

  return (
    <AuthShell>
      <h2 className="text-2xl font-bold text-slate-900 dark:text-white mb-1">Reset your password</h2>
      <p className="text-slate-500 dark:text-slate-400 text-sm mb-7">
        Enter your office email and we'll send you a one-time passcode.
      </p>

      <form onSubmit={handleSubmit} className="space-y-5">
        {isBlocked && (
          <div className="flex items-center gap-2.5 bg-amber-50 dark:bg-amber-500/10 border border-amber-200 dark:border-amber-500/20 text-amber-700 dark:text-amber-400 px-4 py-3 rounded-xl text-sm animate-slide-down">
            <Clock className="h-4 w-4 flex-shrink-0 animate-pulse" />
            <span>
              Send OTP available in <strong>{countdown}s</strong>
              {email && (
                <> &mdash; <button
                  type="button"
                  onClick={() => navigate('/reset-password', { state: { email } })}
                  className="underline font-semibold hover:text-amber-800 dark:hover:text-amber-300 transition-colors"
                >
                  Enter your existing code
                </button></>
              )}
            </span>
          </div>
        )}

        {!isBlocked && error && (
          <div className="flex items-center gap-2.5 bg-red-50 dark:bg-red-500/10 border border-red-200 dark:border-red-500/20 text-red-600 dark:text-red-400 px-4 py-3 rounded-xl text-sm animate-slide-down">
            <AlertCircle className="h-4 w-4 flex-shrink-0" />
            <span>{error}</span>
          </div>
        )}

        <div className="space-y-1.5">
          <label htmlFor="email" className="block text-sm font-medium text-slate-700 dark:text-slate-300">
            Office Email
          </label>
          <div className="relative">
            <Mail className="absolute left-3.5 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400 dark:text-slate-500 pointer-events-none" />
            <input
              id="email"
              type="email"
              autoComplete="email"
              required
              value={email}
              onChange={e => setEmail(e.target.value)}
              className="w-full pl-10 pr-4 py-2.5 border border-slate-200 dark:border-slate-600 rounded-xl text-sm text-slate-900 dark:text-slate-100 placeholder:text-slate-400 dark:placeholder:text-slate-500 bg-white dark:bg-slate-800/60 focus:outline-none focus:border-indigo-500 dark:focus:border-indigo-400 focus:ring-2 focus:ring-indigo-500/15 dark:focus:ring-indigo-400/15 transition-all"
              placeholder="you@company.com"
            />
          </div>
        </div>

        <button
          type="submit"
          disabled={isLoading || isBlocked}
          className="w-full flex items-center justify-center gap-2 py-2.5 px-4 bg-indigo-600 hover:bg-indigo-500 dark:bg-indigo-500 dark:hover:bg-indigo-400 text-white text-sm font-semibold rounded-xl shadow-sm shadow-indigo-500/20 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 dark:focus:ring-offset-slate-900 disabled:opacity-50 disabled:cursor-not-allowed transition-all"
        >
          {isLoading ? <><Loader2 className="h-4 w-4 animate-spin" />Sending OTP…</> : 'Send Reset OTP'}
        </button>

        <div className="text-center">
          <Link
            to="/login"
            className="inline-flex items-center gap-1.5 text-sm text-indigo-600 dark:text-indigo-400 hover:text-indigo-700 dark:hover:text-indigo-300 font-medium transition-colors"
          >
            <ArrowLeft className="h-3.5 w-3.5" />
            Back to Sign In
          </Link>
        </div>
      </form>
    </AuthShell>
  );
}
