import { useEffect, useState, useCallback } from 'react';
import { Loader2, Info, AlertTriangle } from 'lucide-react';
import toast from 'react-hot-toast';
import type { GeneralSettings } from '../../services/apiService';
import { adminGetGeneralSettings, adminUpdateGeneralSettings } from '../../services/apiService';
import ConfirmDialog from '../../components/ConfirmDialog';

export default function GeneralSettingsPage() {
  const [settings, setSettings] = useState<GeneralSettings | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [inputValue, setInputValue] = useState('');
  const [confirmOpen, setConfirmOpen] = useState(false);

  const load = useCallback(async () => {
    setIsLoading(true);
    try {
      const data = await adminGetGeneralSettings();
      setSettings(data);
      setInputValue(String(data.queryLimit));
    } catch {
      toast.error('Failed to load general settings.');
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    load();
  }, [load]);

  const parsedLimit = parseInt(inputValue, 10);
  const isValidLimit = !isNaN(parsedLimit) && (parsedLimit === -1 || parsedLimit > 0);
  const isDirty = settings !== null && parsedLimit !== settings.queryLimit;

  const handleSaveRequest = () => {
    if (!isValidLimit || !isDirty) return;
    if (parsedLimit === -1) {
      setConfirmOpen(true);
    } else {
      doSave(parsedLimit);
    }
  };

  const doSave = async (limit: number) => {
    setIsSaving(true);
    setConfirmOpen(false);
    try {
      const updated = await adminUpdateGeneralSettings({ queryLimit: limit });
      setSettings(updated);
      setInputValue(String(updated.queryLimit));
      toast.success('General settings saved.');
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string } } };
      toast.error(axiosErr.response?.data?.message ?? 'Failed to save settings.');
    } finally {
      setIsSaving(false);
    }
  };

  if (isLoading) {
    return (
      <div className="flex flex-col items-center justify-center py-20">
        <Loader2 className="h-7 w-7 animate-spin text-indigo-500 mb-3" />
        <p className="text-sm text-slate-400">Loading settings…</p>
      </div>
    );
  }

  return (
    <div className="space-y-6 max-w-lg">
      <div className="bg-white rounded-2xl border border-slate-100 shadow-sm overflow-hidden">
        <div className="px-6 py-4 border-b border-slate-100">
          <h2 className="text-sm font-semibold text-slate-900">Query Result Limit</h2>
          <p className="text-xs text-slate-400 mt-0.5">Controls the maximum number of tickets included per email digest.</p>
        </div>
        <div className="p-6 space-y-4">
          <div className="space-y-1.5">
            <label htmlFor="query-limit" className="block text-sm font-medium text-slate-700">
              Maximum Tickets Per Mail
            </label>
            <input
              id="query-limit"
              type="number"
              value={inputValue}
              onChange={e => setInputValue(e.target.value)}
              className={`w-40 px-3.5 py-2.5 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/10 focus:border-indigo-500 transition-all ${
                !isValidLimit && inputValue !== ''
                  ? 'border-rose-300 bg-rose-50 text-rose-900'
                  : 'border-slate-200 text-slate-900'
              }`}
              placeholder="e.g. 25"
            />
            <p className="text-xs text-slate-400 mt-1">
              Set to <code className="bg-slate-100 text-slate-600 px-1.5 py-0.5 rounded-md font-mono text-xs">-1</code> for unlimited results.
            </p>
            {!isValidLimit && inputValue !== '' && (
              <div className="flex items-center gap-1.5 text-xs text-rose-600 mt-1">
                <AlertTriangle className="h-3.5 w-3.5 flex-shrink-0" />
                Enter a positive integer or <code className="font-mono">-1</code> for unlimited. 0 is not allowed.
              </div>
            )}
          </div>

          {parsedLimit === -1 && isValidLimit && (
            <div className="flex items-start gap-2.5 p-3.5 bg-amber-50 border border-amber-100 rounded-xl text-xs text-amber-700">
              <Info className="h-4 w-4 flex-shrink-0 mt-0.5 text-amber-500" />
              <span>Unlimited results may increase email size and query execution time significantly.</span>
            </div>
          )}

          <button
            type="button"
            onClick={handleSaveRequest}
            disabled={!isValidLimit || !isDirty || isSaving}
            className="inline-flex items-center gap-2 px-4 py-2.5 bg-indigo-600 hover:bg-indigo-700 text-white text-sm font-semibold rounded-xl shadow-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed transition-all"
          >
            {isSaving && <Loader2 className="h-4 w-4 animate-spin" />}
            {isSaving ? 'Saving…' : 'Save Settings'}
          </button>
        </div>
      </div>

      <ConfirmDialog
        isOpen={confirmOpen}
        title="Enable Unlimited Results?"
        message="Unlimited query results may impact performance and significantly increase email size. Continue?"
        confirmLabel="Yes, set unlimited"
        onConfirm={() => doSave(-1)}
        onCancel={() => setConfirmOpen(false)}
        isLoading={isSaving}
        variant="warning"
      />
    </div>
  );
}
