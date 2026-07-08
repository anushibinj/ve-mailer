import { useState, useEffect } from 'react';
import { useLocation, useNavigate, Link } from 'react-router-dom';
import { acceptInvite, resendInvite } from '../services/authService';
import { useAuth } from '../hooks/useAuth';
import type { ApiErrorResponse } from '../types/auth';
import toast from 'react-hot-toast';
import { Loader2, AlertCircle, ArrowLeft, Eye, EyeOff, Mail } from 'lucide-react';
import AuthShell from '../components/AuthShell';

type Step = 'enter-otp' | 'set-password';

const inputClass =
  'w-full px-4 py-2.5 border border-slate-200 dark:border-slate-600 rounded-xl text-sm ' +
  'text-slate-900 dark:text-slate-100 placeholder:text-slate-400 dark:placeholder:text-slate-500 ' +
  'bg-white dark:bg-slate-800/60 focus:outline-none focus:border-indigo-500 dark:focus:border-indigo-400 ' +
  'focus:ring-2 focus:ring-indigo-500/15 dark:focus:ring-indigo-400/15 transition-all';

const btnClass =
  'w-full flex items-center justify-center gap-2 py-2.5 px-4 bg-indigo-600 hover:bg-indigo-500 ' +
  'dark:bg-indigo-500 dark:hover:bg-indigo-400 text-white text-sm font-semibold rounded-xl shadow-sm ' +
  'shadow-indigo-500/20 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 ' +
  'dark:focus:ring-offset-slate-900 disabled:opacity-50 disabled:cursor-not-allowed transition-all';

export default function AcceptInvitePage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { login } = useAuth();

  const prefillEmail = (location.state as { email?: string })?.email ?? '';

  const [step, setStep] = useState<Step>('enter-otp');
  const [email, setEmail] = useState(prefillEmail);
  const [otp, setOtp] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showNew, setShowNew] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [isResending, setIsResending] = useState(false);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [countdown, setCountdown] = useState(0);

  useEffect(() => {
    if (countdown > 0) {
      const timer = setTimeout(() => setCountdown(countdown - 1), 1000);
      return () => clearTimeout(timer);
    }
  }, [countdown]);

  const handleAcceptInvite = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setFieldErrors({});
    if (newPassword.length < 8) {
      setFieldErrors({ newPassword: 'Password must be at least 8 characters.' });
      return;
    }
    if (newPassword !== confirmPassword) {
      setFieldErrors({ confirmPassword: 'Passwords do not match.' });
      return;
    }
    setIsLoading(true);
    try {
      const response = await acceptInvite({ email, otp, newPassword, confirmPassword });
      login(response);
      toast.success('Welcome to VE Mailer! Your account is ready.');
      navigate('/', { replace: true });
    } catch (err: unknown) {
      const axiosError = err as { response?: { data?: ApiErrorResponse } };
      const data = axiosError.response?.data;
      if (data?.fieldErrors) setFieldErrors(data.fieldErrors);
      setError(data?.message || 'Failed to accept invite. Please try again.');
    } finally {
      setIsLoading(false);
    }
  };

  const handleVerifyOtp = (e: React.FormEvent) => {
    e.preventDefault();
    // Move to password step (OTP is fully validated on the server during acceptInvite)
    setStep('set-password');
  };

  const handleResend = async () => {
    if (!email.trim() || countdown > 0) return;
    setIsResending(true);
    try {
      await resendInvite(email.trim());
      toast.success('New invite code sent!');
      setCountdown(60);
    } catch (err: unknown) {
      const axiosError = err as { response?: { data?: ApiErrorResponse } };
      toast.error(axiosError.response?.data?.message || 'Failed to resend invite code.');
    } finally {
      setIsResending(false);
    }
  };

  const errorBanner = error && (
    <div className="flex items-center gap-2.5 bg-red-50 dark:bg-red-500/10 border border-red-200 dark:border-red-500/20 text-red-600 dark:text-red-400 px-4 py-3 rounded-xl text-sm animate-slide-down">
      <AlertCircle className="h-4 w-4 flex-shrink-0" />
      <span>{error}</span>
    </div>
  );

  return (
    <AuthShell>
      {step === 'enter-otp' ? (
        <>
          <h2 className="text-2xl font-bold text-slate-900 dark:text-white mb-1">Accept your invite</h2>
          <p className="text-slate-500 dark:text-slate-400 text-sm mb-7">
            Enter your email and the invite code sent to your inbox.
          </p>

          <form onSubmit={handleVerifyOtp} className="space-y-5">
            {errorBanner}

            <div className="space-y-1.5">
              <label htmlFor="invite-email" className="block text-sm font-medium text-slate-700 dark:text-slate-300">
                Email
              </label>
              <div className="relative">
                <Mail className="absolute left-3.5 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400 dark:text-slate-500 pointer-events-none" />
                <input
                  id="invite-email"
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

            <div className="space-y-1.5">
              <label htmlFor="invite-otp" className="block text-sm font-medium text-slate-700 dark:text-slate-300">
                Invite Code
              </label>
              <input
                id="invite-otp"
                type="text"
                inputMode="numeric"
                maxLength={6}
                required
                value={otp}
                onChange={e => setOtp(e.target.value.replace(/\D/g, '').slice(0, 6))}
                className="w-full px-4 py-3 border border-slate-200 dark:border-slate-600 rounded-xl text-center text-2xl font-bold tracking-[0.5em] text-slate-900 dark:text-slate-100 placeholder:text-slate-300 dark:placeholder:text-slate-600 bg-white dark:bg-slate-800/60 focus:outline-none focus:border-indigo-500 dark:focus:border-indigo-400 focus:ring-2 focus:ring-indigo-500/15 transition-all"
                placeholder="000000"
              />
              <p className="text-xs text-slate-400 dark:text-slate-500">Code expires in 10 minutes.</p>
            </div>

            <button type="submit" disabled={otp.length !== 6 || !email.trim()} className={btnClass}>
              Next: Set Password
            </button>

            <div className="text-center">
              <button
                type="button"
                onClick={handleResend}
                disabled={isResending || countdown > 0 || !email.trim()}
                className="text-sm text-indigo-600 dark:text-indigo-400 hover:text-indigo-700 dark:hover:text-indigo-300 font-medium disabled:text-slate-400 dark:disabled:text-slate-600 disabled:cursor-not-allowed transition-colors"
              >
                {countdown > 0 ? `Resend in ${countdown}s` : isResending ? 'Sending…' : 'Resend invite code'}
              </button>
            </div>
          </form>
        </>
      ) : (
        <>
          <h2 className="text-2xl font-bold text-slate-900 dark:text-white mb-1">Set your password</h2>
          <p className="text-slate-500 dark:text-slate-400 text-sm mb-7">
            Choose a strong password for{' '}
            <span className="font-semibold text-slate-700 dark:text-slate-200">{email}</span>.
          </p>

          <form onSubmit={handleAcceptInvite} className="space-y-4">
            {errorBanner}

            <div className="space-y-1.5">
              <label htmlFor="newPassword" className="block text-sm font-medium text-slate-700 dark:text-slate-300">
                New Password
              </label>
              <div className="relative">
                <input
                  id="newPassword"
                  type={showNew ? 'text' : 'password'}
                  required
                  value={newPassword}
                  onChange={e => setNewPassword(e.target.value)}
                  className={`${inputClass} pr-10`}
                  placeholder="Min 8 chars, upper, lower, digit, special"
                />
                <button
                  type="button"
                  onClick={() => setShowNew(v => !v)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 dark:text-slate-500 hover:text-slate-600 dark:hover:text-slate-300 transition-colors"
                >
                  {showNew ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
              {fieldErrors.newPassword && (
                <p className="text-xs text-red-500 dark:text-red-400 mt-1">{fieldErrors.newPassword}</p>
              )}
            </div>

            <div className="space-y-1.5">
              <label htmlFor="confirmPassword" className="block text-sm font-medium text-slate-700 dark:text-slate-300">
                Confirm Password
              </label>
              <div className="relative">
                <input
                  id="confirmPassword"
                  type={showConfirm ? 'text' : 'password'}
                  required
                  value={confirmPassword}
                  onChange={e => setConfirmPassword(e.target.value)}
                  className={`${inputClass} pr-10`}
                  placeholder="Re-enter your password"
                />
                <button
                  type="button"
                  onClick={() => setShowConfirm(v => !v)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 dark:text-slate-500 hover:text-slate-600 dark:hover:text-slate-300 transition-colors"
                >
                  {showConfirm ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
              {fieldErrors.confirmPassword && (
                <p className="text-xs text-red-500 dark:text-red-400 mt-1">{fieldErrors.confirmPassword}</p>
              )}
            </div>

            <div className="flex gap-3 mt-1">
              <button
                type="button"
                onClick={() => { setStep('enter-otp'); setError(''); setFieldErrors({}); }}
                className="flex-1 flex items-center justify-center gap-2 py-2.5 px-4 border border-slate-200 dark:border-slate-600 text-slate-600 dark:text-slate-300 text-sm font-semibold rounded-xl hover:bg-slate-50 dark:hover:bg-slate-700/50 transition-all"
              >
                Back
              </button>
              <button type="submit" disabled={isLoading} className={`flex-1 ${btnClass}`}>
                {isLoading ? <><Loader2 className="h-4 w-4 animate-spin" />Activating…</> : 'Activate Account'}
              </button>
            </div>
          </form>
        </>
      )}

      <div className="mt-6 text-center">
        <Link
          to="/login"
          className="inline-flex items-center gap-1.5 text-sm text-indigo-600 dark:text-indigo-400 hover:text-indigo-700 dark:hover:text-indigo-300 font-medium transition-colors"
        >
          <ArrowLeft className="h-3.5 w-3.5" />
          Back to Sign In
        </Link>
      </div>
    </AuthShell>
  );
}
