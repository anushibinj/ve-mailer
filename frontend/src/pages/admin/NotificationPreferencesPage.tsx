import { useState, useEffect, useRef } from 'react';
import {
  adminGetNotificationPreferences,
  adminUpdateNotificationPreferences,
  adminSendTestNotificationEmail,
} from '../../services/apiService';
import type {
  NotificationPreferencesResponse,
  NotificationPreferencesUpdatePayload,
} from '../../services/apiService';
import toast from 'react-hot-toast';
import { X } from 'lucide-react';

const PASSWORD_PLACEHOLDER = '(unchanged)';

/** Simple email validation regex */
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export default function NotificationPreferencesPage() {
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [testingConnection, setTestingConnection] = useState(false);
  const [configured, setConfigured] = useState(false);
  const [showPassword, setShowPassword] = useState(false);

  const [host, setHost] = useState('');
  const [port, setPort] = useState(25);
  const [fromAddress, setFromAddress] = useState('');
  const [requiresAuth, setRequiresAuth] = useState(true);
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [startTlsEnabled, setStartTlsEnabled] = useState(false);

  // Admin notification emails
  const [adminEmails, setAdminEmails] = useState<string[]>([]);
  const [adminEmailInput, setAdminEmailInput] = useState('');
  const [adminEmailError, setAdminEmailError] = useState('');
  const adminEmailInputRef = useRef<HTMLInputElement>(null);

  const loadPreferences = async () => {
    try {
      setLoading(true);
      const data: NotificationPreferencesResponse = await adminGetNotificationPreferences();
      setConfigured(data.configured);
      if (data.configured) {
        setHost(data.host);
        setPort(data.port);
        setFromAddress(data.fromAddress ?? '');
        setRequiresAuth(data.requiresAuth);
        setUsername(data.username ?? '');
        setPassword(data.requiresAuth ? PASSWORD_PLACEHOLDER : '');
        setStartTlsEnabled(data.startTlsEnabled);
        setAdminEmails(data.adminNotificationEmails ?? []);
      }
    } catch {
      toast.error('Failed to load notification preferences');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    loadPreferences();
  }, []);

  const handleRequiresAuthChange = (value: boolean) => {
    setRequiresAuth(value);
    if (!value) {
      setUsername('');
      setPassword('');
    }
  };

  /** Adds a trimmed, lowercase email to the admin list if valid and not already present. */
  const addAdminEmail = (raw: string) => {
    const email = raw.trim().toLowerCase();
    if (!EMAIL_RE.test(email)) {
      setAdminEmailError('Please enter a valid email address.');
      return;
    }
    if (adminEmails.includes(email)) {
      setAdminEmailError('This email is already in the list.');
      return;
    }
    setAdminEmails(prev => [...prev, email]);
    setAdminEmailInput('');
    setAdminEmailError('');
  };

  const removeAdminEmail = (email: string) => {
    setAdminEmails(prev => prev.filter(e => e !== email));
  };

  /** Sends a test email to the saved admin notification addresses using the saved SMTP config. */
  const handleTestConnection = async () => {
    try {
      setTestingConnection(true);
      await adminSendTestNotificationEmail();
      toast.success('Test email sent successfully. Check the admin notification inbox.');
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string } } };
      toast.error(axiosErr.response?.data?.message ?? 'Failed to send test email.');
    } finally {
      setTestingConnection(false);
    }
  };

  const handleAdminEmailKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter' || e.key === ',') {
      e.preventDefault();
      if (adminEmailInput.trim()) addAdminEmail(adminEmailInput);
    } else if (e.key === 'Backspace' && adminEmailInput === '' && adminEmails.length > 0) {
      setAdminEmails(prev => prev.slice(0, -1));
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!host.trim()) {
      toast.error('SMTP host is required');
      return;
    }
    if (port < 1 || port > 65535) {
      toast.error('Port must be between 1 and 65535');
      return;
    }
    if (!fromAddress.trim()) {
      toast.error('From address is required');
      return;
    }
    if (requiresAuth) {
      if (!username.trim()) {
        toast.error('Username is required when authentication is enabled');
        return;
      }
      if (!configured && (!password || password === PASSWORD_PLACEHOLDER)) {
        toast.error('Password is required for initial configuration');
        return;
      }
    }

    const payload: NotificationPreferencesUpdatePayload = {
      host: host.trim(),
      port,
      fromAddress: fromAddress.trim(),
      requiresAuth,
      username: requiresAuth ? username.trim() : undefined,
      password: requiresAuth ? password : undefined,
      startTlsEnabled,
      adminNotificationEmails: adminEmails,
    };

    try {
      setSaving(true);
      const data = await adminUpdateNotificationPreferences(payload);
      setConfigured(data.configured);
      if (data.requiresAuth) {
        setPassword(PASSWORD_PLACEHOLDER);
      }
      setShowPassword(false);
      toast.success('Notification preferences saved successfully');
    } catch {
      toast.error('Failed to save notification preferences');
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center py-12">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600"></div>
      </div>
    );
  }

  return (
    <div>
      <div className="mb-6">
        <h2 className="text-xl font-semibold text-gray-900 dark:text-white">Configure Notification Preferences</h2>
        <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
          Configure the SMTP settings used for sending notification emails and OTP codes.
        </p>
      </div>

      {!configured && (
        <div className="mb-6 p-4 bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-700/30 rounded-lg">
          <p className="text-sm text-yellow-800 dark:text-yellow-300">
            <strong>Not configured.</strong> Email notifications will not work until SMTP settings are saved.
          </p>
        </div>
      )}

      <form onSubmit={handleSubmit} className="space-y-5 max-w-lg">
        <div>
          <label htmlFor="smtp-host" className="block text-sm font-medium text-gray-700 dark:text-gray-200">
            SMTP Host
          </label>
          <input
            id="smtp-host"
            type="text"
            value={host}
            onChange={(e) => setHost(e.target.value)}
            placeholder="smtp.example.com"
            className="mt-1 block w-full rounded-md border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:ring-1 focus:ring-blue-500 dark:bg-gray-700 dark:text-gray-200 dark:placeholder-gray-400"
            required
          />
        </div>

        <div>
          <label htmlFor="smtp-port" className="block text-sm font-medium text-gray-700 dark:text-gray-200">
            SMTP Port
          </label>
          <input
            id="smtp-port"
            type="number"
            value={port}
            onChange={(e) => setPort(Number(e.target.value))}
            min={1}
            max={65535}
            className="mt-1 block w-full rounded-md border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:ring-1 focus:ring-blue-500 dark:bg-gray-700 dark:text-gray-200 dark:placeholder-gray-400"
            required
          />
        </div>

        <div>
          <label htmlFor="from-address" className="block text-sm font-medium text-gray-700 dark:text-gray-200">
            From Address
          </label>
          <input
            id="from-address"
            type="email"
            value={fromAddress}
            onChange={(e) => setFromAddress(e.target.value)}
            placeholder="noreply@example.com"
            className="mt-1 block w-full rounded-md border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:ring-1 focus:ring-blue-500 dark:bg-gray-700 dark:text-gray-200 dark:placeholder-gray-400"
            required
          />
          <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">
            This address appears in the From: header of all outgoing emails.
          </p>
        </div>

        <div>
          <span className="block text-sm font-medium text-gray-700 dark:text-gray-200 mb-2">
            Authentication Required
          </span>
          <div className="flex gap-6">
            <label className="flex items-center gap-2 cursor-pointer">
              <input
                type="radio"
                name="requiresAuth"
                checked={requiresAuth}
                onChange={() => handleRequiresAuthChange(true)}
                className="h-4 w-4 text-blue-600 border-gray-300 focus:ring-blue-500"
              />
              <span className="text-sm text-gray-700 dark:text-gray-200">Yes</span>
            </label>
            <label className="flex items-center gap-2 cursor-pointer">
              <input
                type="radio"
                name="requiresAuth"
                checked={!requiresAuth}
                onChange={() => handleRequiresAuthChange(false)}
                className="h-4 w-4 text-blue-600 border-gray-300 focus:ring-blue-500"
              />
              <span className="text-sm text-gray-700 dark:text-gray-200">No (unauthenticated relay)</span>
            </label>
          </div>
        </div>

        {requiresAuth && (
          <>
            <div>
              <label htmlFor="smtp-username" className="block text-sm font-medium text-gray-700 dark:text-gray-200">
                Username
              </label>
              <input
                id="smtp-username"
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="user@example.com"
                className="mt-1 block w-full rounded-md border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:ring-1 focus:ring-blue-500 dark:bg-gray-700 dark:text-gray-200 dark:placeholder-gray-400"
                required={requiresAuth}
              />
            </div>

            <div>
              <label htmlFor="smtp-password" className="block text-sm font-medium text-gray-700 dark:text-gray-200">
                Password
              </label>
              <div className="mt-1 relative">
                <input
                  id="smtp-password"
                  type={showPassword ? 'text' : 'password'}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  onFocus={() => {
                    if (password === PASSWORD_PLACEHOLDER) {
                      setPassword('');
                    }
                  }}
                  onBlur={() => {
                    if (configured && password === '') {
                      setPassword(PASSWORD_PLACEHOLDER);
                    }
                  }}
                  placeholder={configured ? '(unchanged)' : 'Enter SMTP password'}
                  className="block w-full rounded-md border border-gray-300 dark:border-gray-600 px-3 py-2 pr-16 text-sm shadow-sm focus:border-blue-500 focus:ring-1 focus:ring-blue-500 dark:bg-gray-700 dark:text-gray-200 dark:placeholder-gray-400"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute inset-y-0 right-0 px-3 flex items-center text-xs text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200"
                >
                  {showPassword ? 'Hide' : 'Show'}
                </button>
              </div>
              {configured && (
                <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">
                  Leave as "(unchanged)" to keep the existing password.
                </p>
              )}
            </div>
          </>
        )}

        <div className="flex items-center gap-2">
          <input
            id="starttls"
            type="checkbox"
            checked={startTlsEnabled}
            onChange={(e) => setStartTlsEnabled(e.target.checked)}
            className="h-4 w-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500"
          />
          <label htmlFor="starttls" className="text-sm text-gray-700 dark:text-gray-200">
            Enable STARTTLS
          </label>
        </div>

        {/* Admin notification emails */}
        <div className="border-t border-gray-200 dark:border-gray-700 pt-5 mt-1">
          <label className="block text-sm font-medium text-gray-700 dark:text-gray-200 mb-1">
            Admin notification email addresses
          </label>
          <p className="text-xs text-gray-500 dark:text-gray-400 mb-3">
            These addresses receive system-level alerts — such as when a new user completes
            onboarding. They do not need to be registered in the application and may be
            distribution lists or role-based inboxes (e.g. <span className="font-mono">admin@company.com</span>).
          </p>

          {/* Tag pills */}
          <div
            className="flex flex-wrap gap-1.5 min-h-[42px] p-2 rounded-md border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 cursor-text"
            onClick={() => adminEmailInputRef.current?.focus()}
          >
            {adminEmails.map(email => (
              <span
                key={email}
                className="inline-flex items-center gap-1 pl-2.5 pr-1.5 py-0.5 rounded-full text-xs font-medium bg-indigo-50 dark:bg-indigo-500/15 text-indigo-700 dark:text-indigo-300 border border-indigo-100 dark:border-indigo-500/30"
              >
                {email}
                <button
                  type="button"
                  onClick={(e) => { e.stopPropagation(); removeAdminEmail(email); }}
                  className="text-indigo-500 dark:text-indigo-400 hover:text-indigo-700 dark:hover:text-indigo-200 transition-colors cursor-pointer"
                  aria-label={`Remove ${email}`}
                >
                  <X className="h-3 w-3" />
                </button>
              </span>
            ))}
            <input
              ref={adminEmailInputRef}
              type="text"
              value={adminEmailInput}
              onChange={(e) => { setAdminEmailInput(e.target.value); setAdminEmailError(''); }}
              onKeyDown={handleAdminEmailKeyDown}
              onBlur={() => { if (adminEmailInput.trim()) addAdminEmail(adminEmailInput); }}
              placeholder={adminEmails.length === 0 ? 'Type an email and press Enter or comma…' : ''}
              className="flex-1 min-w-[220px] bg-transparent text-sm text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 outline-none border-none p-0"
            />
          </div>
          {adminEmailError && (
            <p className="mt-1 text-xs text-red-600 dark:text-red-400">{adminEmailError}</p>
          )}

          <div className="mt-3">
            <button
              type="button"
              onClick={handleTestConnection}
              disabled={testingConnection || !configured || adminEmails.length === 0}
              title={
                !configured
                  ? 'Save preferences before testing the connection'
                  : adminEmails.length === 0
                    ? 'Add at least one admin notification email address'
                    : undefined
              }
              className="inline-flex items-center px-3 py-1.5 border border-gray-300 dark:border-gray-600 rounded-md shadow-sm text-sm font-medium text-gray-700 dark:text-gray-200 bg-white dark:bg-gray-700 hover:bg-gray-50 dark:hover:bg-gray-600 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {testingConnection ? 'Sending test email...' : 'Test Connection'}
            </button>
            <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">
              Sends a test email using the saved SMTP settings to the admin notification
              addresses above.
            </p>
          </div>
        </div>

        <div className="pt-4">
          <button
            type="submit"
            disabled={saving}
            className="inline-flex items-center px-4 py-2 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {saving ? 'Saving...' : 'Save Preferences'}
          </button>
        </div>
      </form>
    </div>
  );
}
