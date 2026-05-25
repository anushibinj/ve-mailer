import { useState, useEffect } from 'react';
import { useLocation, useNavigate, Link } from 'react-router-dom';
import { verifyResetOtp, resetPassword, forgotPassword } from '../services/authService';
import type { ApiErrorResponse } from '../types/auth';
import toast from 'react-hot-toast';
import { Loader2, AlertCircle, ArrowLeft, Eye, EyeOff } from 'lucide-react';
import AuthShell from '../components/AuthShell';

type Step = 'verify-otp' | 'new-password';

const inputClass =
  'w-full px-4 py-2.5 border border-slate-200 dark:border-slate-600 rounded-xl text-sm ' +
  'text-slate-900 dark:text-slate-100 placeholder:text-slate-400 dark:placeholder:text-slate-500 ' +
  'bg-white dark:bg-slate-800/60 focus:outline-none focus:border-indigo-500 dark:focus:border-indigo-400 ' +
  'focus:ring-2 focus:ring-indigo-500/15 dark:focus:ring-indigo-400/15 transition-all';

export default function ResetPasswordPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const [step, setStep] = useState<Step>('verify-otp');
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

  const email = (location.state as { email?: string })?.email;

  useEffect(() => {
    if (!email) navigate('/forgot-password', { replace: true });
  }, [email, navigate]);

  useEffect(() => {
    if (countdown > 0) {
      const timer = setTimeout(() => setCountdown(countdown - 1), 1000);
      return () => clearTimeout(timer);
    }
  }, [countdown]);

  const handleVerifyOtp = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email) return;
    setError('');
    setIsLoading(true);
    try {
      await verifyResetOtp({ email, otp });
      setStep('new-password');
      toast.success('OTP verified!');
    } catch (err: unknown) {
      const axiosError = err as { response?: { data?: ApiErrorResponse } };
      setError(axiosError.response?.data?.message || 'OTP verification failed.');
    } finally {
      setIsLoading(false);
    }
  };

  const handleResetPassword = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email) return;
    setError('');
    setFieldErrors({});
    if (newPassword.length < 8) { setFieldErrors({ newPassword: 'Password must be at least 8 characters.' }); return; }
    if (newPassword !== confirmPassword) { setFieldErrors({ confirmPassword: 'Passwords do not match.' }); return; }
    setIsLoading(true);
    try {
      await resetPassword({ email, otp, newPassword, confirmPassword });
      toast.success('Password reset successfully!');
      navigate('/login', { replace: true });
    } catch (err: unknown) {
      const axiosError = err as { response?: { data?: ApiErrorResponse } };
      const data = axiosError.response?.data;
      if (data?.fieldErrors) setFieldErrors(data.fieldErrors);
      setError(data?.message || 'Password reset failed.');
    } finally {
      setIsLoading(false);
    }
  };

  const handleResend = async () => {
    if (!email || countdown > 0) return;
    setIsResending(true);
    try {
      await forgotPassword({ email });
      toast.success('New OTP sent!');
      setCountdown(60);
    } catch {
      toast.error('Failed to resend OTP.');
    } finally {
      setIsResending(false);
    }
  };

  if (!email) return null;

  const errorBanner = error && (
    <div className="flex items-center gap-2.5 bg-red-50 dark:bg-red-500/10 border border-red-200 dark:border-red-500/20 text-red-600 dark:text-red-400 px-4 py-3 rounded-xl text-sm animate-slide-down">
      <AlertCircle className="h-4 w-4 flex-shrink-0" />
      <span>{error}</span>
    </div>
  );

  const btnClass =
    'w-full flex items-center justify-center gap-2 py-2.5 px-4 bg-indigo-600 hover:bg-indigo-500 dark:bg-indigo-500 dark:hover:bg-indigo-400 text-white text-sm font-semibold rounded-xl shadow-sm shadow-indigo-500/20 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 dark:focus:ring-offset-slate-900 disabled:opacity-50 disabled:cursor-not-allowed transition-all';

  return (
    <AuthShell>
      {step === 'verify-otp' ? (
        <>
          <h2 className="text-2xl font-bold text-slate-900 dark:text-white mb-1">Enter your OTP</h2>
          <p className="text-slate-500 dark:text-slate-400 text-sm mb-7">
            We sent a reset code to{' '}
            <span className="font-semibold text-slate-700 dark:text-slate-200">{email}</span>
          </p>

          <form onSubmit={handleVerifyOtp} className="space-y-5">
            {errorBanner}

            <div className="space-y-1.5">
              <label htmlFor="otp" className="block text-sm font-medium text-slate-700 dark:text-slate-300">OTP Code</label>
              <input
                id="otp" type="text" inputMode="numeric" maxLength={6} required
                value={otp}
                onChange={e => setOtp(e.target.value.replace(/\D/g, '').slice(0, 6))}
                className="w-full px-4 py-3 border border-slate-200 dark:border-slate-600 rounded-xl text-center text-2xl font-bold tracking-[0.5em] text-slate-900 dark:text-slate-100 placeholder:text-slate-300 dark:placeholder:text-slate-600 bg-white dark:bg-slate-800/60 focus:outline-none focus:border-indigo-500 dark:focus:border-indigo-400 focus:ring-2 focus:ring-indigo-500/15 transition-all"
                placeholder="000000"
              />
              <p className="text-xs text-slate-400 dark:text-slate-500">Code expires in 10 minutes.</p>
            </div>

            <button type="submit" disabled={isLoading || otp.length !== 6} className={btnClass}>
              {isLoading ? <><Loader2 className="h-4 w-4 animate-spin" />Verifying…</> : 'Verify OTP'}
            </button>

            <div className="text-center">
              <button type="button" onClick={handleResend} disabled={isResending || countdown > 0}
                className="text-sm text-indigo-600 dark:text-indigo-400 hover:text-indigo-700 dark:hover:text-indigo-300 font-medium disabled:text-slate-400 dark:disabled:text-slate-600 disabled:cursor-not-allowed transition-colors">
                {countdown > 0 ? `Resend in ${countdown}s` : isResending ? 'Sending…' : 'Resend OTP'}
              </button>
            </div>
          </form>
        </>
      ) : (
        <>
          <h2 className="text-2xl font-bold text-slate-900 dark:text-white mb-1">Set new password</h2>
          <p className="text-slate-500 dark:text-slate-400 text-sm mb-7">Choose a strong password for your account.</p>

          <form onSubmit={handleResetPassword} className="space-y-4">
            {errorBanner}

            <div className="space-y-1.5">
              <label htmlFor="newPassword" className="block text-sm font-medium text-slate-700 dark:text-slate-300">New Password</label>
              <div className="relative">
                <input id="newPassword" type={showNew ? 'text' : 'password'} required value={newPassword} onChange={e => setNewPassword(e.target.value)} className={`${inputClass} pr-10`} placeholder="Min 8 chars" />
                <button type="button" onClick={() => setShowNew(v => !v)} className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 dark:text-slate-500 hover:text-slate-600 dark:hover:text-slate-300 transition-colors">
                  {showNew ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
              {fieldErrors.newPassword && <p className="text-xs text-red-500 dark:text-red-400 mt-1">{fieldErrors.newPassword}</p>}
            </div>

            <div className="space-y-1.5">
              <label htmlFor="confirmNewPassword" className="block text-sm font-medium text-slate-700 dark:text-slate-300">Confirm Password</label>
              <div className="relative">
                <input id="confirmNewPassword" type={showConfirm ? 'text' : 'password'} required value={confirmPassword} onChange={e => setConfirmPassword(e.target.value)} className={`${inputClass} pr-10`} placeholder="Re-enter your password" />
                <button type="button" onClick={() => setShowConfirm(v => !v)} className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 dark:text-slate-500 hover:text-slate-600 dark:hover:text-slate-300 transition-colors">
                  {showConfirm ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
              {fieldErrors.confirmPassword && <p className="text-xs text-red-500 dark:text-red-400 mt-1">{fieldErrors.confirmPassword}</p>}
            </div>

            <button type="submit" disabled={isLoading} className={`${btnClass} mt-1`}>
              {isLoading ? <><Loader2 className="h-4 w-4 animate-spin" />Resetting…</> : 'Reset Password'}
            </button>
          </form>
        </>
      )}

      <div className="mt-6 text-center">
        <Link to="/login" className="inline-flex items-center gap-1.5 text-sm text-indigo-600 dark:text-indigo-400 hover:text-indigo-700 dark:hover:text-indigo-300 font-medium transition-colors">
          <ArrowLeft className="h-3.5 w-3.5" />
          Back to Sign In
        </Link>
      </div>
    </AuthShell>
  );
}
