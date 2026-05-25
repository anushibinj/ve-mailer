import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { signup } from '../services/authService';
import type { ApiErrorResponse } from '../types/auth';
import toast from 'react-hot-toast';
import { Loader2, AlertCircle, Eye, EyeOff } from 'lucide-react';
import AuthShell from '../components/AuthShell';

export default function SignupPage() {
  const navigate = useNavigate();
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const inputClass =
    'w-full px-4 py-2.5 border border-slate-200 dark:border-slate-600 rounded-xl text-sm ' +
    'text-slate-900 dark:text-slate-100 placeholder:text-slate-400 dark:placeholder:text-slate-500 ' +
    'bg-white dark:bg-slate-800/60 focus:outline-none focus:border-indigo-500 dark:focus:border-indigo-400 ' +
    'focus:ring-2 focus:ring-indigo-500/15 dark:focus:ring-indigo-400/15 transition-all';

  const validateForm = (): boolean => {
    const errors: Record<string, string> = {};
    if (name.trim().length < 2) errors.name = 'Name must be at least 2 characters.';
    if (!email.includes('@')) errors.email = 'Please enter a valid email address.';
    if (password.length < 8) errors.password = 'Password must be at least 8 characters.';
    else if (!/[A-Z]/.test(password)) errors.password = 'Must contain at least one uppercase letter.';
    else if (!/[a-z]/.test(password)) errors.password = 'Must contain at least one lowercase letter.';
    else if (!/\d/.test(password)) errors.password = 'Must contain at least one digit.';
    else if (!/[!@#$%^&*()_+\-=[\]{};':"\\|,.<>/?]/.test(password)) errors.password = 'Must contain at least one special character.';
    if (password !== confirmPassword) errors.confirmPassword = 'Passwords do not match.';
    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setFieldErrors({});
    if (!validateForm()) return;
    setIsLoading(true);
    try {
      await signup({ name, email, password, confirmPassword });
      toast.success('OTP sent to your email!');
      navigate('/verify-signup', { state: { email } });
    } catch (err: unknown) {
      const axiosError = err as { response?: { data?: ApiErrorResponse } };
      const data = axiosError.response?.data;
      if (data?.fieldErrors) setFieldErrors(data.fieldErrors);
      setError(data?.message || 'Signup failed. Please try again.');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <AuthShell>
      <h2 className="text-2xl font-bold text-slate-900 dark:text-white mb-1">Create your account</h2>
      <p className="text-slate-500 dark:text-slate-400 text-sm mb-7">Sign up with your office email to get started</p>

      <form onSubmit={handleSubmit} className="space-y-4">
        {error && (
          <div className="flex items-center gap-2.5 bg-red-50 dark:bg-red-500/10 border border-red-200 dark:border-red-500/20 text-red-600 dark:text-red-400 px-4 py-3 rounded-xl text-sm animate-slide-down">
            <AlertCircle className="h-4 w-4 flex-shrink-0" />
            <span>{error}</span>
          </div>
        )}

        <div className="space-y-1.5">
          <label htmlFor="name" className="block text-sm font-medium text-slate-700 dark:text-slate-300">Full Name</label>
          <input id="name" type="text" required value={name} onChange={e => setName(e.target.value)} className={inputClass} placeholder="John Doe" />
          {fieldErrors.name && <p className="text-xs text-red-500 dark:text-red-400 mt-1">{fieldErrors.name}</p>}
        </div>

        <div className="space-y-1.5">
          <label htmlFor="email" className="block text-sm font-medium text-slate-700 dark:text-slate-300">Office Email</label>
          <input id="email" type="email" autoComplete="email" required value={email} onChange={e => setEmail(e.target.value)} className={inputClass} placeholder="you@company.com" />
          {fieldErrors.email && <p className="text-xs text-red-500 dark:text-red-400 mt-1">{fieldErrors.email}</p>}
        </div>

        <div className="space-y-1.5">
          <label htmlFor="password" className="block text-sm font-medium text-slate-700 dark:text-slate-300">Password</label>
          <div className="relative">
            <input id="password" type={showPassword ? 'text' : 'password'} required value={password} onChange={e => setPassword(e.target.value)} className={`${inputClass} pr-10`} placeholder="Min 8 chars, upper, lower, digit & special" />
            <button type="button" onClick={() => setShowPassword(v => !v)} className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 dark:text-slate-500 hover:text-slate-600 dark:hover:text-slate-300 transition-colors" aria-label={showPassword ? 'Hide' : 'Show'}>
              {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
            </button>
          </div>
          {fieldErrors.password && <p className="text-xs text-red-500 dark:text-red-400 mt-1">{fieldErrors.password}</p>}
        </div>

        <div className="space-y-1.5">
          <label htmlFor="confirmPassword" className="block text-sm font-medium text-slate-700 dark:text-slate-300">Confirm Password</label>
          <div className="relative">
            <input id="confirmPassword" type={showConfirm ? 'text' : 'password'} required value={confirmPassword} onChange={e => setConfirmPassword(e.target.value)} className={`${inputClass} pr-10`} placeholder="Re-enter your password" />
            <button type="button" onClick={() => setShowConfirm(v => !v)} className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 dark:text-slate-500 hover:text-slate-600 dark:hover:text-slate-300 transition-colors" aria-label={showConfirm ? 'Hide' : 'Show'}>
              {showConfirm ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
            </button>
          </div>
          {fieldErrors.confirmPassword && <p className="text-xs text-red-500 dark:text-red-400 mt-1">{fieldErrors.confirmPassword}</p>}
        </div>

        <button
          type="submit"
          disabled={isLoading}
          className="w-full flex items-center justify-center gap-2 py-2.5 px-4 bg-indigo-600 hover:bg-indigo-500 dark:bg-indigo-500 dark:hover:bg-indigo-400 text-white text-sm font-semibold rounded-xl shadow-sm shadow-indigo-500/20 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 dark:focus:ring-offset-slate-900 disabled:opacity-50 disabled:cursor-not-allowed transition-all mt-2"
        >
          {isLoading ? <><Loader2 className="h-4 w-4 animate-spin" />Creating account…</> : 'Create Account'}
        </button>

        <p className="text-center text-sm text-slate-500 dark:text-slate-400 pt-1">
          Already have an account?{' '}
          <Link to="/login" className="text-indigo-600 dark:text-indigo-400 hover:text-indigo-700 dark:hover:text-indigo-300 font-semibold transition-colors">Sign In</Link>
        </p>
      </form>
    </AuthShell>
  );
}
