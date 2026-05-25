import { useEffect, useState, useCallback } from 'react';
import { Loader2, Info } from 'lucide-react';
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

  return (
    <div>
      {/* Page header */}
      <div className="mb-6">
        <h1 className="text-3xl font-bold text-gray-900">General</h1>
        <p className="mt-1 text-sm text-gray-500">
          Application-wide configuration settings.
        </p>
      </div>

      {isLoading ? (
        <div className="flex flex-col items-center justify-center py-20">
          <Loader2 className="h-8 w-8 animate-spin text-blue-500 mb-3" />
          <p className="text-sm text-gray-500">Loading settings…</p>
        </div>
      ) : (
        <div className="space-y-6 max-w-xl">
          {/* Query Result Limit section */}
          <div className="bg-white border border-gray-200 rounded-lg shadow-sm p-6">
            <h2 className="text-base font-semibold text-gray-900 mb-4">Query Result Limit</h2>

            <div className="space-y-3">
              <div>
                <label
                  htmlFor="query-limit"
                  className="block text-sm font-medium text-gray-700 mb-1"
                >
                  Maximum Tickets Per Mail
                </label>
                <input
                  id="query-limit"
                  type="number"
                  value={inputValue}
                  onChange={e => setInputValue(e.target.value)}
                  className={`w-40 px-3 py-2 border rounded-md shadow-sm text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 ${
                    !isValidLimit && inputValue !== ''
                      ? 'border-red-400 bg-red-50'
                      : 'border-gray-300'
                  }`}
                  placeholder="e.g. 25"
                />
                <p className="mt-1.5 text-xs text-gray-500">
                  Set to <code className="bg-gray-100 px-1 rounded">-1</code> for unlimited results.
                </p>
                {!isValidLimit && inputValue !== '' && (
                  <p className="mt-1 text-xs text-red-600">
                    Enter a positive integer or <code>-1</code> for unlimited. 0 is not allowed.
                  </p>
                )}
              </div>

              {parsedLimit === -1 && isValidLimit && (
                <div className="flex items-start gap-2 p-3 bg-amber-50 border border-amber-200 rounded-md text-xs text-amber-800">
                  <Info className="h-4 w-4 mt-0.5 flex-shrink-0" />
                  <span>
                    Unlimited results may increase email size and query execution time.
                  </span>
                </div>
              )}

              <div className="pt-2">
                <button
                  type="button"
                  onClick={handleSaveRequest}
                  disabled={!isValidLimit || !isDirty || isSaving}
                  className="inline-flex items-center gap-2 px-4 py-2 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 disabled:bg-gray-400 disabled:cursor-not-allowed transition-colors"
                >
                  {isSaving ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
                  {isSaving ? 'Saving…' : 'Save Settings'}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      <ConfirmDialog
        isOpen={confirmOpen}
        title="Enable Unlimited Query Results?"
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
