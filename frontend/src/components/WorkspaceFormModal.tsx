import React, { useEffect, useState } from 'react';
import { X, Eye, EyeOff, Loader2, Building2, Plug, CheckCircle, Pencil, PowerOff, RefreshCw } from 'lucide-react';
import type {
  WorkspaceAdmin,
  WorkspaceConflictErrorData,
  WorkspaceUpdatePayload,
  WorkspaceStatus,
} from '../services/apiService';
import {
  adminTestWorkspaceConnection,
  adminUpdateWorkspace,
  adminRefetchWorkspaceMetadata,
} from '../services/apiService';
import toast from 'react-hot-toast';
import { useAuth } from '../hooks/useAuth';
import {
  CLIENT_KEY_PLACEHOLDER,
  SHORTCODE_UNKNOWN_ENABLE_BLOCKED_MESSAGE,
  isShortcodeUnknown,
  fieldInputClass,
  DuplicateWorkspaceConflictBanner,
  type ExistingWorkspaceConflict,
} from './workspaceFormShared';

// Workspace creation now happens exclusively through the WorkspaceCreationWizard, which
// auto-discovers the Title and Shortcode from the ValueEdge REST API. This modal is
// edit-only — the Title and Shortcode are system-derived and shown as read-only here.
// Workspace Status options, styled as a color-coded segmented control (rather than a plain
// <select>) so each state is instantly recognizable — colors match the status badges used in
// the Workspace Management table (green = Enabled, amber = Draft, slate = Disabled).
const STATUS_OPTIONS: {
  value: WorkspaceStatus;
  label: string;
  icon: typeof CheckCircle;
  idleClass: string;
  selectedClass: string;
}[] = [
  {
    value: 'DISABLED',
    label: 'Disabled',
    icon: PowerOff,
    idleClass: 'text-slate-500 dark:text-slate-400 hover:bg-slate-50 dark:hover:bg-slate-700',
    selectedClass: 'bg-slate-600 text-white shadow-sm',
  },
  {
    value: 'DRAFT',
    label: 'Draft',
    icon: Pencil,
    idleClass: 'text-amber-700 dark:text-amber-400 hover:bg-amber-50 dark:hover:bg-amber-500/10',
    selectedClass: 'bg-amber-500 text-white shadow-sm',
  },
  {
    value: 'ENABLED',
    label: 'Enabled',
    icon: CheckCircle,
    idleClass: 'text-emerald-700 dark:text-emerald-400 hover:bg-emerald-50 dark:hover:bg-emerald-500/10',
    selectedClass: 'bg-emerald-600 text-white shadow-sm',
  },
];

interface WorkspaceFormModalProps {
  isOpen: boolean;
  workspace: WorkspaceAdmin;
  onClose: () => void;
  onSuccess: (saved: WorkspaceAdmin) => void;
  /**
   * Fired after a successful "Refetch workspace metadata" action (Super Admin only) — lets the
   * parent update its own copy of the workspace (e.g. the management table row) without closing
   * this modal, since the admin may want to keep reviewing the refreshed values.
   */
  onRefetched?: (saved: WorkspaceAdmin) => void;
}

interface FormValues {
  title: string;
  workspaceShortcode: string;
  sharedSpaceId: string;
  workspaceId: string;
  clientId: string;
  clientKey: string;
  rootUrl: string;
  status: WorkspaceStatus;
}

interface FormErrors {
  title?: string;
  workspaceShortcode?: string;
  sharedSpaceId?: string;
  workspaceId?: string;
  clientId?: string;
  clientKey?: string;
  rootUrl?: string;
  status?: string;
}

const WorkspaceFormModal: React.FC<WorkspaceFormModalProps> = ({
  isOpen, workspace, onClose, onSuccess, onRefetched,
}) => {
  const { isAdmin } = useAuth();
  // Title and Shortcode are always read-only in this modal — they are system-derived during
  // creation by the workspace creation wizard and can only be corrected by editing the
  // discovery result (a Super Admin operation handled outside this form).
  const shortcodeUnknown = isShortcodeUnknown(workspace.workspaceShortcode);

  const [values, setValues] = useState<FormValues>({
    title: '', workspaceShortcode: '', sharedSpaceId: '', workspaceId: '', clientId: '', clientKey: '', rootUrl: '', status: 'DRAFT',
  });
  const [errors, setErrors] = useState<FormErrors>({});
  const [showKey, setShowKey] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isTesting, setIsTesting] = useState(false);
  const [isRefetching, setIsRefetching] = useState(false);
  // Populated when the backend rejects the submission as a duplicate (409 Conflict) —
  // keeps the modal open, preserves entered values, and lets the user jump to the existing workspace.
  const [duplicateConflict, setDuplicateConflict] = useState<ExistingWorkspaceConflict | null>(null);

  useEffect(() => {
    if (isOpen) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setValues({
        title: workspace.title,
        workspaceShortcode: workspace.workspaceShortcode,
        sharedSpaceId: workspace.sharedSpaceId,
        workspaceId: workspace.workspaceId,
        clientId: workspace.clientId,
        clientKey: CLIENT_KEY_PLACEHOLDER,
        rootUrl: workspace.rootUrl,
        status: workspace.status,
      });
      setErrors({});
      setShowKey(false);
      setDuplicateConflict(null);
    }
  }, [isOpen, workspace]);

  const validate = (): boolean => {
    const errs: FormErrors = {};
    if (!values.sharedSpaceId.trim()) errs.sharedSpaceId = 'Shared Space ID is required';
    if (!values.workspaceId.trim()) errs.workspaceId = 'Workspace ID is required';
    if (!values.clientId.trim()) errs.clientId = 'Client ID is required';
    if (!values.rootUrl.trim()) errs.rootUrl = 'Root URL is required';
    setErrors(errs);
    return Object.keys(errs).length === 0;
  };

  const handleChange = (field: keyof FormValues) => (e: React.ChangeEvent<HTMLInputElement>) => {
    setValues(prev => ({ ...prev, [field]: e.target.value }));
    if (errors[field]) setErrors(prev => ({ ...prev, [field]: undefined }));
    if (duplicateConflict) setDuplicateConflict(null);
  };

  const handleKeyFocus = () => {
    if (values.clientKey === CLIENT_KEY_PLACEHOLDER) {
      setValues(prev => ({ ...prev, clientKey: '' }));
    }
  };

  const validateConnectionInputs = (): boolean => {
    const connectionErrors: FormErrors = {
      sharedSpaceId: values.sharedSpaceId.trim() ? undefined : 'Shared Space ID is required',
      workspaceId: values.workspaceId.trim() ? undefined : 'Workspace ID is required',
      clientId: values.clientId.trim() ? undefined : 'Client ID is required',
      rootUrl: values.rootUrl.trim() ? undefined : 'Root URL is required',
      clientKey: undefined,
    };

    setErrors(prev => ({ ...prev, ...connectionErrors }));
    return Object.values(connectionErrors).every(err => !err);
  };

  const handleTestConnection = async () => {
    if (!validateConnectionInputs()) return;

    setIsTesting(true);
    try {
      const response = await adminTestWorkspaceConnection({
        workspaceRecordId: workspace.id,
        sharedSpaceId: values.sharedSpaceId.trim(),
        workspaceId: values.workspaceId.trim(),
        clientId: values.clientId.trim(),
        clientKey: values.clientKey.trim() || CLIENT_KEY_PLACEHOLDER,
        rootUrl: values.rootUrl.trim(),
      });
      if (response.hasData) {
        toast.success(response.message || `Connection successful for workspace ${response.workspaceId}`);
      } else {
        toast(response.message || 'Connection successful, but no data was returned from the server.', { icon: '⚠️' });
      }
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string; errors?: { message?: string }[] } } };
      const msg =
        axiosErr.response?.data?.message ??
        axiosErr.response?.data?.errors?.[0]?.message ??
        'Connection test failed';
      toast.error(msg);
    } finally {
      setIsTesting(false);
    }
  };

  /**
   * Super Admin-only: re-runs ValueEdge metadata discovery using the workspace's already-stored
   * credentials and overwrites Title/Shortcode with the freshly discovered values, using the
   * same parsing logic as the workspace creation wizard. Updates the form in place (does not
   * close the modal) so the admin can review the refreshed values before saving/closing.
   */
  const handleRefetchMetadata = async () => {
    setIsRefetching(true);
    try {
      const response = await adminRefetchWorkspaceMetadata(workspace.id);
      setValues(prev => ({
        ...prev,
        title: response.workspace.title,
        workspaceShortcode: response.workspace.workspaceShortcode,
        status: response.workspace.status,
      }));
      onRefetched?.(response.workspace);

      if (response.shortcodeDetected) {
        toast.success(`Workspace metadata refreshed: "${response.workspace.title}" (${response.workspace.workspaceShortcode})`);
      } else {
        toast(
          response.warning ||
            'Workspace metadata refreshed, but the shortcode could not be identified automatically. The workspace has been set to Draft.',
          { icon: '⚠️' }
        );
      }
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string; errors?: { message?: string }[] } } };
      const msg =
        axiosErr.response?.data?.message ??
        axiosErr.response?.data?.errors?.[0]?.message ??
        'Failed to refetch workspace metadata';
      toast.error(msg);
    } finally {
      setIsRefetching(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!validate()) return;
    setIsSubmitting(true);
    try {
      const payload: WorkspaceUpdatePayload = {
        title: values.title.trim(), sharedSpaceId: values.sharedSpaceId.trim(),
        workspaceShortcode: values.workspaceShortcode.trim(),
        workspaceId: values.workspaceId.trim(), clientId: values.clientId.trim(),
        clientKey: values.clientKey.trim() || CLIENT_KEY_PLACEHOLDER, rootUrl: values.rootUrl.trim(),
        status: values.status,
      };
      const saved = await adminUpdateWorkspace(workspace.id, payload);
      toast.success('Workspace updated successfully');
      onSuccess(saved);
    } catch (err: unknown) {
      const axiosErr = err as {
        response?: { status?: number; data?: WorkspaceConflictErrorData | { message?: string; errors?: { message?: string }[] } };
      };
      if (axiosErr.response?.status === 409) {
        const conflictData = axiosErr.response.data as WorkspaceConflictErrorData;
        setDuplicateConflict(conflictData?.existingWorkspace ?? null);
        toast.error('A workspace with the same Root URL, Shared Space ID, and Workspace ID already exists.');
        return;
      }
      const dataErr = axiosErr.response?.data as { message?: string; errors?: { message?: string }[] } | undefined;
      const msg =
        dataErr?.message ??
        dataErr?.errors?.[0]?.message ??
        'Failed to update workspace';
      toast.error(msg);
    } finally {
      setIsSubmitting(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div
        className="absolute inset-0 bg-slate-900/60 dark:bg-slate-950/70 backdrop-blur-sm animate-fade-in"
        onClick={onClose}
        aria-hidden="true"
      />

      <div className="relative bg-white dark:bg-slate-900 rounded-2xl shadow-2xl dark:shadow-slate-950/60 border border-slate-100/80 dark:border-slate-700/50 w-full max-w-lg overflow-hidden animate-scale-in">
        <div className="h-1 bg-gradient-to-r from-indigo-500 via-violet-500 to-purple-500" />

        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100 dark:border-slate-800">
          <div className="flex items-center gap-2.5">
            <div className="h-8 w-8 rounded-lg bg-indigo-50 dark:bg-indigo-500/10 flex items-center justify-center">
              <Building2 className="h-4 w-4 text-indigo-600 dark:text-indigo-400" />
            </div>
            <h2 className="text-base font-semibold text-slate-900 dark:text-white">
              Edit Workspace
            </h2>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg text-slate-400 dark:text-slate-500 hover:text-slate-600 dark:hover:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors cursor-pointer"
            aria-label="Close"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        {/* Form */}
        <form onSubmit={handleSubmit} className="px-6 py-5 space-y-4 max-h-[70vh] overflow-y-auto">
          {/* Duplicate workspace conflict banner (409 from backend) */}
          {duplicateConflict && <DuplicateWorkspaceConflictBanner existingWorkspace={duplicateConflict} />}

          {/* Title field — system-derived during creation, always read-only */}
          <div>
            <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
              Title
              <span className="ml-1 text-xs text-slate-400 dark:text-slate-500 font-normal">(system-derived, read-only)</span>
            </label>
            <input
              type="text"
              value={values.title}
              readOnly
              disabled
              className={fieldInputClass(false, true)}
            />
          </div>

          {/* Workspace Shortcode — system-derived during creation, always read-only */}
          <div>
            <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
              Workspace Shortcode
              <span className="ml-1 text-xs text-slate-400 dark:text-slate-500 font-normal">(system-derived, read-only)</span>
            </label>
            <input
              type="text"
              value={values.workspaceShortcode}
              readOnly
              disabled
              className={fieldInputClass(false, true)}
            />
          </div>

          {/* Workspace Status — color-coded segmented control instead of a plain dropdown */}
          <div>
            <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
              Workspace Status <span className="text-red-500">*</span>
            </label>
            <div
              role="group"
              aria-label="Workspace Status"
              className="flex rounded-xl border border-slate-200 dark:border-slate-700 divide-x divide-slate-200 dark:divide-slate-700 overflow-hidden bg-white dark:bg-slate-800"
            >
              {STATUS_OPTIONS.map(option => {
                const isSelected = values.status === option.value;
                const isOptionDisabled = option.value === 'ENABLED' && shortcodeUnknown;
                const Icon = option.icon;
                return (
                  <button
                    key={option.value}
                    type="button"
                    disabled={isOptionDisabled}
                    aria-pressed={isSelected}
                    title={isOptionDisabled ? SHORTCODE_UNKNOWN_ENABLE_BLOCKED_MESSAGE : undefined}
                    onClick={() => {
                      setValues(prev => ({ ...prev, status: option.value }));
                      if (errors.status) setErrors(prev => ({ ...prev, status: undefined }));
                    }}
                    className={`flex-1 inline-flex items-center justify-center gap-1.5 px-3 py-2 text-sm font-semibold transition-colors cursor-pointer disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:bg-transparent ${
                      isSelected ? option.selectedClass : option.idleClass
                    }`}
                  >
                    <Icon className="h-3.5 w-3.5" />
                    {option.label}
                  </button>
                );
              })}
            </div>
            {errors.status && <p className="mt-1 text-xs text-red-600 dark:text-red-400">{errors.status}</p>}
            {shortcodeUnknown ? (
              <p className="mt-1.5 text-xs text-amber-700 dark:text-amber-400">
                {SHORTCODE_UNKNOWN_ENABLE_BLOCKED_MESSAGE}
              </p>
            ) : (
              <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
                Draft workspaces are only visible to admins. Disabled workspaces are hidden from all views.
              </p>
            )}
          </div>

          {/* Root URL field */}
          <div>
            <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
              Root URL <span className="text-red-500">*</span>
            </label>
            <input
              type="url"
              value={values.rootUrl}
              onChange={handleChange('rootUrl')}
              placeholder="https://octane.example.com"
              className={fieldInputClass(!!errors.rootUrl)}
            />
            {errors.rootUrl && <p className="mt-1 text-xs text-red-600 dark:text-red-400">{errors.rootUrl}</p>}
            {!errors.rootUrl && <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">Base URL of the ValueEdge / Octane server.</p>}
          </div>

          {[
            { field: 'sharedSpaceId' as const, label: 'Shared Space ID', type: 'text', placeholder: 'e.g. 4001', required: true },
            { field: 'workspaceId' as const, label: 'Workspace ID', type: 'text', placeholder: 'e.g. 5015', required: true },
            { field: 'clientId' as const, label: 'Client ID', type: 'text', placeholder: 'e.g. my-api-client-id', required: true },
          ].map(({ field, label, type, placeholder, required }) => (
            <div key={field}>
              <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
                {label} {required && <span className="text-red-500">*</span>}
              </label>
              <input
                type={type}
                value={values[field]}
                onChange={handleChange(field)}
                placeholder={placeholder}
                className={fieldInputClass(!!errors[field])}
              />
              {errors[field] && <p className="mt-1 text-xs text-red-600 dark:text-red-400">{errors[field]}</p>}
            </div>
          ))}

          {/* Client Key (special — has show/hide toggle) */}
          <div>
            <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
              Client Key{' '}
              <span className="ml-1 text-xs text-slate-400 dark:text-slate-500 font-normal">
                — leave unchanged to keep existing
              </span>
            </label>
            <div className="relative">
              <input
                type={showKey ? 'text' : 'password'}
                value={values.clientKey}
                onChange={handleChange('clientKey')}
                onFocus={handleKeyFocus}
                placeholder="(unchanged)"
                className={`${fieldInputClass(!!errors.clientKey)} pr-10`}
              />
              <button
                type="button"
                onClick={() => setShowKey(v => !v)}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 dark:text-slate-500 hover:text-slate-600 dark:hover:text-slate-300 transition-colors cursor-pointer"
                tabIndex={-1}
                aria-label={showKey ? 'Hide key' : 'Show key'}
              >
                {showKey ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
              </button>
            </div>
            {errors.clientKey && <p className="mt-1 text-xs text-red-600 dark:text-red-400">{errors.clientKey}</p>}
            {!errors.clientKey && (
              <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
                Enter a new value to replace the existing key, or leave as-is to keep it.
              </p>
            )}
          </div>

          {/* Actions */}
          <div className="flex justify-end gap-3 pt-2">
            <button
              type="button"
              onClick={onClose}
              disabled={isSubmitting || isTesting || isRefetching}
              className="px-4 py-2 text-sm font-medium text-slate-600 dark:text-slate-300 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl hover:bg-slate-50 dark:hover:bg-slate-700/60 disabled:opacity-50 transition-colors cursor-pointer"
            >
              Cancel
            </button>
            {isAdmin && (
              <button
                type="button"
                onClick={handleRefetchMetadata}
                disabled={isSubmitting || isTesting || isRefetching}
                title="Re-discover this workspace's Title and Shortcode from ValueEdge using its stored credentials"
                className="inline-flex items-center gap-2 px-4 py-2 text-sm font-semibold text-violet-700 dark:text-violet-300 bg-violet-50 dark:bg-violet-900/20 border border-violet-200 dark:border-violet-700 rounded-xl hover:bg-violet-100 dark:hover:bg-violet-900/40 disabled:opacity-50 transition-all cursor-pointer"
              >
                {isRefetching ? <Loader2 className="h-4 w-4 animate-spin" /> : <RefreshCw className="h-4 w-4" />}
                Refetch workspace metadata
              </button>
            )}
            <button
              type="button"
              onClick={handleTestConnection}
              disabled={isSubmitting || isTesting || isRefetching}
              className="inline-flex items-center gap-2 px-4 py-2 text-sm font-semibold text-indigo-700 dark:text-indigo-300 bg-indigo-50 dark:bg-indigo-900/20 border border-indigo-200 dark:border-indigo-700 rounded-xl hover:bg-indigo-100 dark:hover:bg-indigo-900/40 disabled:opacity-50 transition-all cursor-pointer"
            >
              {isTesting ? <Loader2 className="h-4 w-4 animate-spin" /> : <Plug className="h-4 w-4" />}
              Test Connection
            </button>
            <button
              type="submit"
              disabled={isSubmitting || isTesting || isRefetching}
              className="inline-flex items-center gap-2 px-4 py-2 text-sm font-semibold text-white bg-indigo-600 hover:bg-indigo-500 dark:bg-indigo-500 dark:hover:bg-indigo-400 rounded-xl disabled:opacity-50 transition-all cursor-pointer"
            >
              {isSubmitting && <Loader2 className="h-4 w-4 animate-spin" />}
              Save Changes
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default WorkspaceFormModal;
