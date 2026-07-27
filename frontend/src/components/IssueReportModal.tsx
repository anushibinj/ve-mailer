import { useEffect, useState } from 'react';
import { AlertCircle, Loader2, X } from 'lucide-react';
import toast from 'react-hot-toast';
import type { ApiErrorResponse } from '../types/auth';
import { fetchIssueUploadConfig, submitIssueReport } from '../services/apiService';

interface IssueReportModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export default function IssueReportModal({ isOpen, onClose }: IssueReportModalProps) {
  const [message, setMessage] = useState('');
  const [reporterEmail, setReporterEmail] = useState('');
  const [screenshot, setScreenshot] = useState<File | null>(null);
  const [error, setError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isConfigLoading, setIsConfigLoading] = useState(false);
  const [maxUploadBytes, setMaxUploadBytes] = useState<number | null>(null);
  const [maxStoredScreenshotBytes, setMaxStoredScreenshotBytes] = useState<number | null>(null);

  useEffect(() => {
    if (!isOpen) return;
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setIsConfigLoading(true);
    fetchIssueUploadConfig()
      .then(config => {
        setMaxUploadBytes(config.maxUploadBytes);
        setMaxStoredScreenshotBytes(config.maxStoredScreenshotBytes);
      })
      .catch(() => {
        setError('Could not load screenshot limits from the server. Please try again.');
      })
      .finally(() => setIsConfigLoading(false));
  }, [isOpen]);

  if (!isOpen) return null;

  const maxUploadLabel = maxUploadBytes
    ? `${(maxUploadBytes / (1024 * 1024)).toFixed(2)} MB`
    : '';

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (!message.trim() && !screenshot) {
      setError('Either message or screenshot is required.');
      return;
    }
    if (screenshot && maxUploadBytes && screenshot.size > maxUploadBytes) {
      const selectedMb = (screenshot.size / (1024 * 1024)).toFixed(2);
      setError(
        `Selected file is ${selectedMb} MB, which exceeds the upload limit of ${maxUploadLabel}.`
      );
      return;
    }

    setIsSubmitting(true);
    try {
      const response = await submitIssueReport({
        message: message.trim() || undefined,
        reporterEmail: reporterEmail.trim() || undefined,
        screenshot,
      });
      toast.success(response.message || 'Issue submitted successfully.');
      setMessage('');
      setReporterEmail('');
      setScreenshot(null);
      onClose();
    } catch (err: unknown) {
      const axiosError = err as { response?: { data?: ApiErrorResponse } };
      const backendError = axiosError.response?.data;
      if (backendError?.fieldErrors && Object.keys(backendError.fieldErrors).length > 0) {
        const details = Object.entries(backendError.fieldErrors)
          .map(([field, messageText]) => `${field}: ${messageText}`)
          .join(' | ');
        setError(`${backendError.message} (${details})`);
      } else {
        setError(backendError?.message || 'Failed to submit issue.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleScreenshotChange = (file: File | null) => {
    setError('');
    if (!file) {
      setScreenshot(null);
      return;
    }
    if (maxUploadBytes && file.size > maxUploadBytes) {
      const selectedMb = (file.size / (1024 * 1024)).toFixed(2);
      setError(
        `Selected file is ${selectedMb} MB, which exceeds the upload limit of ${maxUploadLabel}.`
      );
      setScreenshot(null);
      return;
    }
    setScreenshot(file);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm p-4">
      <div className="w-full max-w-lg rounded-2xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 shadow-xl">
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100 dark:border-slate-700">
          <div>
            <h3 className="text-lg font-semibold text-slate-900 dark:text-slate-100">Raise an issue</h3>
            <p className="text-xs text-slate-500 dark:text-slate-400">Report a bug or problem in the application.</p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 transition-colors"
            aria-label="Close issue modal"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          {error && (
            <div className="flex items-center gap-2.5 bg-red-50 dark:bg-red-500/10 border border-red-200 dark:border-red-500/20 text-red-600 dark:text-red-400 px-4 py-3 rounded-xl text-sm">
              <AlertCircle className="h-4 w-4 flex-shrink-0" />
              <span>{error}</span>
            </div>
          )}

          <div className="space-y-1.5">
            <label className="block text-sm font-medium text-slate-700 dark:text-slate-300">Message (optional)</label>
            <textarea
              value={message}
              onChange={e => setMessage(e.target.value)}
              placeholder="Describe the issue..."
              rows={4}
              className="w-full px-4 py-2.5 border border-slate-200 dark:border-slate-600 rounded-xl text-sm text-slate-900 dark:text-slate-100 placeholder:text-slate-400 bg-white dark:bg-slate-800/60 focus:outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/15 transition-all"
            />
          </div>

          <div className="space-y-1.5">
            <label className="block text-sm font-medium text-slate-700 dark:text-slate-300">Screenshot (optional)</label>
            <input
              type="file"
              accept="image/*"
              onChange={e => handleScreenshotChange(e.target.files?.[0] ?? null)}
              className="w-full px-3 py-2 border border-slate-200 dark:border-slate-600 rounded-xl text-sm text-slate-700 dark:text-slate-300 bg-white dark:bg-slate-800/60"
            />
            {maxUploadBytes && (
              <p className="text-xs text-slate-500 dark:text-slate-400">
                Max upload size: {maxUploadLabel}. Stored screenshot target: {((maxStoredScreenshotBytes ?? maxUploadBytes) / (1024 * 1024)).toFixed(2)} MB.
              </p>
            )}
          </div>

          <div className="space-y-1.5">
            <label className="block text-sm font-medium text-slate-700 dark:text-slate-300">Email (optional)</label>
            <input
              type="email"
              value={reporterEmail}
              onChange={e => setReporterEmail(e.target.value)}
              placeholder="you@example.com"
              className="w-full px-4 py-2.5 border border-slate-200 dark:border-slate-600 rounded-xl text-sm text-slate-900 dark:text-slate-100 placeholder:text-slate-400 bg-white dark:bg-slate-800/60 focus:outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/15 transition-all"
            />
          </div>

          <div className="flex gap-3 pt-1">
            <button
              type="button"
              onClick={onClose}
              className="flex-1 py-2.5 px-4 border border-slate-200 dark:border-slate-600 text-slate-600 dark:text-slate-300 text-sm font-semibold rounded-xl hover:bg-slate-50 dark:hover:bg-slate-700/50 transition-all"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isSubmitting || isConfigLoading}
              className="flex-1 flex items-center justify-center gap-2 py-2.5 px-4 bg-indigo-600 hover:bg-indigo-500 text-white text-sm font-semibold rounded-xl disabled:opacity-50 disabled:cursor-not-allowed transition-all"
            >
              {isSubmitting
                ? <><Loader2 className="h-4 w-4 animate-spin" />Submitting…</>
                : isConfigLoading
                  ? <><Loader2 className="h-4 w-4 animate-spin" />Loading limits…</>
                : 'Submit Issue'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
