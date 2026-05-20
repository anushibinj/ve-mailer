import { useState, useEffect } from 'react';
import {
  adminGetNotificationPreferences,
  adminUpdateNotificationPreferences,
} from '../../services/apiService';
import type {
  NotificationPreferencesResponse,
  NotificationPreferencesUpdatePayload,
} from '../../services/apiService';
import toast from 'react-hot-toast';

const PASSWORD_PLACEHOLDER = '(unchanged)';

export default function NotificationPreferencesPage() {
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [configured, setConfigured] = useState(false);
  const [showPassword, setShowPassword] = useState(false);

  const [host, setHost] = useState('');
  const [port, setPort] = useState(25);
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [startTlsEnabled, setStartTlsEnabled] = useState(false);

  useEffect(() => {
    loadPreferences();
  }, []);

  const loadPreferences = async () => {
    try {
      setLoading(true);
      const data: NotificationPreferencesResponse = await adminGetNotificationPreferences();
      setConfigured(data.configured);
      if (data.configured) {
        setHost(data.host);
        setPort(data.port);
        setUsername(data.username);
        setPassword(PASSWORD_PLACEHOLDER);
        setStartTlsEnabled(data.startTlsEnabled);
      }
    } catch {
      toast.error('Failed to load notification preferences');
    } finally {
      setLoading(false);
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
    if (!username.trim()) {
      toast.error('Username is required');
      return;
    }
    if (!configured && (!password || password === PASSWORD_PLACEHOLDER)) {
      toast.error('Password is required for initial configuration');
      return;
    }

    const payload: NotificationPreferencesUpdatePayload = {
      host: host.trim(),
      port,
      username: username.trim(),
      password: password,
      startTlsEnabled,
    };

    try {
      setSaving(true);
      const data = await adminUpdateNotificationPreferences(payload);
      setConfigured(data.configured);
      setPassword(PASSWORD_PLACEHOLDER);
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
        <h2 className="text-xl font-semibold text-gray-900">Configure Notification Preferences</h2>
        <p className="mt-1 text-sm text-gray-500">
          Configure the SMTP settings used for sending notification emails and OTP codes.
        </p>
      </div>

      {!configured && (
        <div className="mb-6 p-4 bg-yellow-50 border border-yellow-200 rounded-lg">
          <p className="text-sm text-yellow-800">
            <strong>Not configured.</strong> Email notifications will not work until SMTP settings are saved.
          </p>
        </div>
      )}

      <form onSubmit={handleSubmit} className="space-y-5 max-w-lg">
        <div>
          <label htmlFor="smtp-host" className="block text-sm font-medium text-gray-700">
            SMTP Host
          </label>
          <input
            id="smtp-host"
            type="text"
            value={host}
            onChange={(e) => setHost(e.target.value)}
            placeholder="smtp.example.com"
            className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:ring-1 focus:ring-blue-500"
            required
          />
        </div>

        <div>
          <label htmlFor="smtp-port" className="block text-sm font-medium text-gray-700">
            SMTP Port
          </label>
          <input
            id="smtp-port"
            type="number"
            value={port}
            onChange={(e) => setPort(Number(e.target.value))}
            min={1}
            max={65535}
            className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:ring-1 focus:ring-blue-500"
            required
          />
        </div>

        <div>
          <label htmlFor="smtp-username" className="block text-sm font-medium text-gray-700">
            Username (also used as "From" address)
          </label>
          <input
            id="smtp-username"
            type="text"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            placeholder="noreply@example.com"
            className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:ring-1 focus:ring-blue-500"
            required
          />
        </div>

        <div>
          <label htmlFor="smtp-password" className="block text-sm font-medium text-gray-700">
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
              className="block w-full rounded-md border border-gray-300 px-3 py-2 pr-16 text-sm shadow-sm focus:border-blue-500 focus:ring-1 focus:ring-blue-500"
            />
            <button
              type="button"
              onClick={() => setShowPassword(!showPassword)}
              className="absolute inset-y-0 right-0 px-3 flex items-center text-xs text-gray-500 hover:text-gray-700"
            >
              {showPassword ? 'Hide' : 'Show'}
            </button>
          </div>
          {configured && (
            <p className="mt-1 text-xs text-gray-500">
              Leave as "(unchanged)" to keep the existing password.
            </p>
          )}
        </div>

        <div className="flex items-center gap-2">
          <input
            id="starttls"
            type="checkbox"
            checked={startTlsEnabled}
            onChange={(e) => setStartTlsEnabled(e.target.checked)}
            className="h-4 w-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500"
          />
          <label htmlFor="starttls" className="text-sm text-gray-700">
            Enable STARTTLS
          </label>
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
