import { useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate, Link } from 'react-router-dom';
import { acceptInvite, requestInviteMagicLink, verifyInviteMagicLink } from '../services/authService';
import { useAuth } from '../hooks/useAuth';
import type { ApiErrorResponse } from '../types/auth';
import toast from 'react-hot-toast';
import { Loader2, AlertCircle, ArrowLeft, Eye, EyeOff, Mail } from 'lucide-react';
import AuthShell from '../components/AuthShell';

type Step = 'request-link' | 'set-password';

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

  const prefillEmail = (location.state as { email?: string } | null)?.email ?? '';
  const token = useMemo(() => new URLSearchParams(location.search).get('token')?.trim() ?? '', [location.search]);

  const [step, setStep] = useState<Step>(token ? 'set-password' : 'request-link');
  const [email, setEmail] = useState(prefillEmail);
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showNew, setShowNew] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [isRequestingLink, setIsRequestingLink] = useState(false);
  const [isVerifyingToken, setIsVerifyingToken] = useState(Boolean(token));
  const [error, setError] = useState('');
  const [infoMessage, setInfoMessage] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  useEffect(() => {
    if (!token) {
      return;
    }

    // eslint-disable-next-line react-hooks/set-state-in-effect -- verification starts when token query param changes
    setIsVerifyingToken(true);
    verifyInviteMagicLink(token)
      .then((result) => {
        if (result.status === 'VALID') {
          setStep('set-password');
          if (result.email) setEmail(result.email);
          setError('');
          return;
        }
        setStep('request-link');
        setError(result.message || 'This invite link is not valid.');
      })
      .catch((err: unknown) => {
        const axiosError = err as { response?: { data?: ApiErrorResponse } };
        setStep('request-link');
        setError(axiosError.response?.data?.message || 'Failed to validate invite link.');
      })
      .finally(() => setIsVerifyingToken(false));
  }, [token]);

  const handleRequestLink = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setInfoMessage('');
    setIsRequestingLink(true);
    try {
      const response = await requestInviteMagicLink({ email: email.trim() });
      setInfoMessage(response.message);
      toast.success('If your invite is pending, the link is on its way.');
    } catch (err: unknown) {
      const axiosError = err as { response?: { data?: ApiErrorResponse } };
      setError(axiosError.response?.data?.message || 'Failed to request invite link.');
    } finally {
      setIsRequestingLink(false);
    }
  };

  const handleAcceptInvite = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setFieldErrors({});

    if (!token) {
      setStep('request-link');
      setError('Invite link token is missing. Please request a new link.');
      return;
    }
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
      const response = await acceptInvite({ token, newPassword, confirmPassword });
      login(response);
      toast.success('Welcome to VE Mailer! Your account is ready.');
      navigate('/', { replace: true });
    } catch (err: unknown) {
      const axiosError = err as { response?: { data?: ApiErrorResponse } };
      const data = axiosError.response?.data;
      if (data?.fieldErrors) setFieldErrors(data.fieldErrors);
      const message = data?.message || 'Failed to activate account. Please request a new invite link.';
      setError(message);
      if (message.toLowerCase().includes('invite link')) {
        setStep('request-link');
        navigate('/accept-invite', { replace: true, state: { email } });
      }
    } finally {
      setIsLoading(false);
    }
  };

  const errorBanner = error && (
    <div className="flex items-center gap-2.5 bg-red-50 dark:bg-red-500/10 border border-red-200 dark:border-red-500/20 text-red-600 dark:text-red-400 px-4 py-3 rounded-xl text-sm animate-slide-down">
      <AlertCircle className="h-4 w-4 flex-shrink-0" />
      <span>{error}</span>
    </div>
  );

  const infoBanner = infoMessage && (
    <div className="bg-indigo-50 dark:bg-indigo-500/10 border border-indigo-200 dark:border-indigo-500/25 text-indigo-700 dark:text-indigo-300 px-4 py-3 rounded-xl text-sm">
      {infoMessage}
    </div>
  );

  if (isVerifyingToken) {
    return (
      <AuthShell>
        <div className="flex flex-col items-center justify-center py-10 gap-3">
          <Loader2 className="h-5 w-5 animate-spin text-indigo-500" />
          <p className="text-sm text-slate-500 dark:text-slate-400">Verifying your invite link…</p>
        </div>
      </AuthShell>
    );
  }

  return (
    <AuthShell>
      {step === 'request-link' ? (
        <>
          <h2 className="text-2xl font-bold text-slate-900 dark:text-white mb-1">Accept your invite</h2>
          <p className="text-slate-500 dark:text-slate-400 text-sm mb-7">
            Enter your invited email and we will send you a secure magic link.
          </p>

          <form onSubmit={handleRequestLink} className="space-y-5">
            {errorBanner}
            {infoBanner}

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

            <button type="submit" disabled={isRequestingLink || !email.trim()} className={btnClass}>
              {isRequestingLink ? <><Loader2 className="h-4 w-4 animate-spin" />Sending link…</> : 'Send Magic Link'}
            </button>
          </form>
        </>
      ) : (
        <>
          <h2 className="text-2xl font-bold text-slate-900 dark:text-white mb-1">Set your password</h2>
          <p className="text-slate-500 dark:text-slate-400 text-sm mb-7">
            Your invite is verified for{' '}
            <span className="font-semibold text-slate-700 dark:text-slate-200">{email || 'your account'}</span>.
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

            <button type="submit" disabled={isLoading} className={btnClass}>
              {isLoading ? <><Loader2 className="h-4 w-4 animate-spin" />Activating…</> : 'Activate Account'}
            </button>
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
