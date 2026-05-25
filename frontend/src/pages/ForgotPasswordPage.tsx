import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { forgotPassword } from '../services/authService';
import type { ApiErrorResponse } from '../types/auth';
import toast from 'react-hot-toast';
import { Loader2, AlertCircle, Mail, ArrowLeft } from 'lucide-react';
import AuthShell from '../components/AuthShell';

export default function ForgotPasswordPage() {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setIsLoading(true);
    try {
      await forgotPassword({ email });
      toast.success('If your account exists, an OTP has been sent.');
      navigate('/reset-password', { state: { email } });
    } catch (err: unknown) {
      const axiosError = err as { response?: { data?: ApiErrorResponse } };
      setError(axiosError.response?.data?.message || 'Failed to send OTP. Please try again.');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <AuthShell>
      <h2 className="text-2xl font-bold text-slate-900 dark:text-white mb-1">Reset your password</h2>
      <p className="text-slate-500 dark:text-slate-400 text-sm mb-7">
        Enter your office email and we'll send you a one-time passcode.
      </p>

      <form onSubmit={handleSubmit} className="space-y-5">
        {error && (
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
          disabled={isLoading}
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
