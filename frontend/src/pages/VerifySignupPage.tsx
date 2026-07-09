import { useState, useEffect } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { verifySignupOtp, signup } from '../services/authService';
import type { ApiErrorResponse } from '../types/auth';
import toast from 'react-hot-toast';
import { Loader2, AlertCircle } from 'lucide-react';
import AuthShell from '../components/AuthShell';

export default function VerifySignupPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { login } = useAuth();
  const [otp, setOtp] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [isResending, setIsResending] = useState(false);
  const [error, setError] = useState('');
  const [countdown, setCountdown] = useState(30);
  const [resendCount, setResendCount] = useState(0);

  const email = (location.state as { email?: string })?.email;

  useEffect(() => {
    if (!email) navigate('/signup', { replace: true });
  }, [email, navigate]);

  useEffect(() => {
    if (countdown > 0) {
      const timer = setTimeout(() => setCountdown(countdown - 1), 1000);
      return () => clearTimeout(timer);
    }
  }, [countdown]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email) return;
    setError('');
    setIsLoading(true);
    try {
      const response = await verifySignupOtp({ email, otp });
      login(response);
      toast.success('Account created successfully!');
      navigate('/', { replace: true });
    } catch (err: unknown) {
      const axiosError = err as { response?: { data?: ApiErrorResponse } };
      setError(axiosError.response?.data?.message || 'OTP verification failed.');
    } finally {
      setIsLoading(false);
    }
  };

  const handleResend = async () => {
    if (!email || countdown > 0) return;
    setIsResending(true);
    setError('');
    try {
      await signup({ name: '', email, password: 'placeholder', confirmPassword: 'placeholder' });
      toast.success('New OTP sent!');
      const nextWait = (resendCount + 1) * 30;
      setCountdown(nextWait);
      setResendCount(prev => prev + 1);
    } catch {
      toast.success('If your email is registered, a new OTP was sent.');
      const nextWait = (resendCount + 1) * 30;
      setCountdown(nextWait);
      setResendCount(prev => prev + 1);
    } finally {
      setIsResending(false);
    }
  };

  if (!email) return null;

  return (
    <AuthShell>
      <h2 className="text-2xl font-bold text-slate-900 dark:text-white mb-1">Check your email</h2>
      <p className="text-slate-500 dark:text-slate-400 text-sm mb-7">
        We sent a 6-digit code to{' '}
        <span className="font-semibold text-slate-700 dark:text-slate-200">{email}</span>
      </p>

      <form onSubmit={handleSubmit} className="space-y-5">
        {error && (
          <div className="flex items-center gap-2.5 bg-red-50 dark:bg-red-500/10 border border-red-200 dark:border-red-500/20 text-red-600 dark:text-red-400 px-4 py-3 rounded-xl text-sm animate-slide-down">
            <AlertCircle className="h-4 w-4 flex-shrink-0" />
            <span>{error}</span>
          </div>
        )}

        <div className="space-y-1.5">
          <label htmlFor="otp" className="block text-sm font-medium text-slate-700 dark:text-slate-300">
            Enter OTP
          </label>
          <input
            id="otp"
            type="text"
            inputMode="numeric"
            maxLength={6}
            required
            value={otp}
            onChange={e => setOtp(e.target.value.replace(/\D/g, '').slice(0, 6))}
            className="w-full px-4 py-3 border border-slate-200 dark:border-slate-600 rounded-xl text-center text-2xl font-bold tracking-[0.5em] text-slate-900 dark:text-slate-100 placeholder:text-slate-300 dark:placeholder:text-slate-600 bg-white dark:bg-slate-800/60 focus:outline-none focus:border-indigo-500 dark:focus:border-indigo-400 focus:ring-2 focus:ring-indigo-500/15 dark:focus:ring-indigo-400/15 transition-all"
            placeholder="000000"
          />
          <p className="text-xs text-slate-400 dark:text-slate-500">Code expires in 10 minutes.</p>
        </div>

        <button
          type="submit"
          disabled={isLoading || otp.length !== 6}
          className="w-full flex items-center justify-center gap-2 py-2.5 px-4 bg-indigo-600 hover:bg-indigo-500 dark:bg-indigo-500 dark:hover:bg-indigo-400 text-white text-sm font-semibold rounded-xl shadow-sm shadow-indigo-500/20 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 dark:focus:ring-offset-slate-900 disabled:opacity-50 disabled:cursor-not-allowed transition-all"
        >
          {isLoading ? <><Loader2 className="h-4 w-4 animate-spin" />Verifying…</> : 'Verify & Create Account'}
        </button>

        <div className="text-center">
          <button
            type="button"
            onClick={handleResend}
            disabled={isResending || countdown > 0}
            className="text-sm text-indigo-600 dark:text-indigo-400 hover:text-indigo-700 dark:hover:text-indigo-300 font-medium disabled:text-slate-400 dark:disabled:text-slate-600 disabled:cursor-not-allowed transition-colors"
          >
            {countdown > 0
              ? `Resend code available in ${countdown}s`
              : isResending ? 'Sending…' : 'Resend code'}
          </button>
        </div>
      </form>
    </AuthShell>
  );
}
