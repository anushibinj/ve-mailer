import React, { useEffect, useState } from 'react';
import { X, Eye, EyeOff, Loader2, Building2, Plug, AlertTriangle, ExternalLink } from 'lucide-react';
import type {
  WorkspaceAdmin,
  WorkspaceConflictErrorData,
  WorkspaceCreatePayload,
  WorkspaceUpdatePayload,
  WorkspaceStatus,
} from '../services/apiService';
import {
  adminCreateWorkspace,
  adminTestWorkspaceConnection,
  adminUpdateWorkspace,
} from '../services/apiService';
import { useAuth } from '../hooks/useAuth';
import toast from 'react-hot-toast';

const CLIENT_KEY_PLACEHOLDER = '(unchanged)';

interface WorkspaceFormModalProps {
  isOpen: boolean;
  workspace?: WorkspaceAdmin | null;
  onClose: () => void;
  onSuccess: (saved: WorkspaceAdmin) => void;
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

const inputClass =
  'w-full px-3.5 py-2.5 border rounded-xl text-sm transition-all ' +
  'placeholder:text-slate-400 dark:placeholder:text-slate-500 ' +
  'focus:outline-none focus:ring-2';

const fieldInputClass = (hasError: boolean, readOnly = false) =>
  `${inputClass} ${readOnly
    ? 'bg-slate-100 dark:bg-slate-800 text-slate-500 dark:text-slate-400 cursor-not-allowed'
    : 'text-slate-900 dark:text-slate-100 bg-white dark:bg-slate-800/60'
  } ${hasError
    ? 'border-red-400 dark:border-red-500/50 bg-red-50 dark:bg-red-500/5 focus:border-red-500 focus:ring-red-500/20'
    : 'border-slate-200 dark:border-slate-600 focus:border-indigo-500 dark:focus:border-indigo-400 focus:ring-indigo-500/15 dark:focus:ring-indigo-400/15'
  }`;

const WorkspaceFormModal: React.FC<WorkspaceFormModalProps> = ({
  isOpen, workspace, onClose, onSuccess,
}) => {
  const isEditing = !!workspace;
  const { isAdmin } = useAuth();
  // WORKSPACE_ADMIN can edit connection fields and workspace status (visibility), but not the title —
  // renaming a workspace remains a super admin operation.
  const isTitleReadOnly = isEditing && !isAdmin;

  const [values, setValues] = useState<FormValues>({
    title: '', workspaceShortcode: '', sharedSpaceId: '', workspaceId: '', clientId: '', clientKey: '', rootUrl: '', status: 'DRAFT',
  });
  const [errors, setErrors] = useState<FormErrors>({});
  const [showKey, setShowKey] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isTesting, setIsTesting] = useState(false);
  // Populated when the backend rejects the submission as a duplicate (409 Conflict) —
  // keeps the modal open, preserves entered values, and lets the user jump to the existing workspace.
  const [duplicateConflict, setDuplicateConflict] = useState<WorkspaceConflictErrorData['existingWorkspace'] | null>(null);

  useEffect(() => {
    if (isOpen) {
      if (workspace) {
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
      } else {
        setValues({ title: '', workspaceShortcode: '', sharedSpaceId: '', workspaceId: '', clientId: '', clientKey: '', rootUrl: '', status: 'DRAFT' });
      }
      setErrors({});
      setShowKey(false);
      setDuplicateConflict(null);
    }
  }, [isOpen, workspace]);

  const validate = (): boolean => {
    const errs: FormErrors = {};
    if (!values.title.trim()) errs.title = 'Title is required';
    if (!values.workspaceShortcode.trim()) errs.workspaceShortcode = 'Workspace shortcode is required';
    if (!values.sharedSpaceId.trim()) errs.sharedSpaceId = 'Shared Space ID is required';
    if (!values.workspaceId.trim()) errs.workspaceId = 'Workspace ID is required';
    if (!values.clientId.trim()) errs.clientId = 'Client ID is required';
    if (!isEditing && !values.clientKey.trim()) errs.clientKey = 'Client Key is required';
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
    if (isEditing && values.clientKey === CLIENT_KEY_PLACEHOLDER) {
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

    if (!isEditing && !values.clientKey.trim()) {
      connectionErrors.clientKey = 'Client Key is required';
    }

    setErrors(prev => ({ ...prev, ...connectionErrors }));
    return Object.values(connectionErrors).every(err => !err);
  };

  const handleTestConnection = async () => {
    if (!validateConnectionInputs()) return;

    setIsTesting(true);
    try {
      const response = await adminTestWorkspaceConnection({
        workspaceRecordId: workspace?.id,
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

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!validate()) return;
    setIsSubmitting(true);
    try {
      let saved: WorkspaceAdmin;
      if (isEditing && workspace) {
        const payload: WorkspaceUpdatePayload = {
          title: values.title.trim(), sharedSpaceId: values.sharedSpaceId.trim(),
          workspaceShortcode: values.workspaceShortcode.trim(),
          workspaceId: values.workspaceId.trim(), clientId: values.clientId.trim(),
          clientKey: values.clientKey.trim() || CLIENT_KEY_PLACEHOLDER, rootUrl: values.rootUrl.trim(),
          status: values.status,
        };
        saved = await adminUpdateWorkspace(workspace.id, payload);
        toast.success('Workspace updated successfully');
      } else {
        const payload: WorkspaceCreatePayload = {
          title: values.title.trim(), sharedSpaceId: values.sharedSpaceId.trim(),
          workspaceShortcode: values.workspaceShortcode.trim(),
          workspaceId: values.workspaceId.trim(), clientId: values.clientId.trim(),
          clientKey: values.clientKey.trim(), rootUrl: values.rootUrl.trim(),
          status: values.status,
        };
        saved = await adminCreateWorkspace(payload);
        toast.success('Workspace created successfully');
      }
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
        (isEditing ? 'Failed to update workspace' : 'Failed to create workspace');
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
              {isEditing ? 'Edit Workspace' : 'Create Workspace'}
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
          {duplicateConflict && (
            <div className="flex gap-3 rounded-xl border border-amber-300 dark:border-amber-600/50 bg-amber-50 dark:bg-amber-900/20 px-4 py-3">
              <AlertTriangle className="h-5 w-5 flex-shrink-0 text-amber-500 dark:text-amber-400 mt-0.5" />
              <div className="text-sm text-amber-800 dark:text-amber-200">
                <p className="font-medium">
                  A workspace with the same Root URL, Shared Space ID, and Workspace ID already exists.
                </p>
                <p className="mt-1">
                  Existing workspace: <span className="font-semibold">{duplicateConflict.name}</span>
                </p>
                <a
                  href={`/workspace/${duplicateConflict.id}`}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="mt-1.5 inline-flex items-center gap-1 font-medium text-amber-900 dark:text-amber-100 underline hover:no-underline"
                >
                  Open existing workspace
                  <ExternalLink className="h-3.5 w-3.5" />
                </a>
              </div>
            </div>
          )}

          {/* Title field */}
          <div>
            <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
              Title <span className="text-red-500">*</span>
              {isTitleReadOnly && <span className="ml-1 text-xs text-slate-400 dark:text-slate-500 font-normal">(read-only)</span>}
            </label>
            <input
              type="text"
              value={values.title}
              onChange={handleChange('title')}
              placeholder="e.g. ALM Octane — Team Alpha"
              className={fieldInputClass(!!errors.title, isTitleReadOnly)}
              readOnly={isTitleReadOnly}
              disabled={isTitleReadOnly}
            />
            {errors.title && <p className="mt-1 text-xs text-red-600 dark:text-red-400">{errors.title}</p>}
          </div>

          {/* Workspace Status */}
          <div>
            <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
              Workspace Status <span className="text-red-500">*</span>
            </label>
            <select
              value={values.status}
              onChange={(e) => {
                setValues(prev => ({ ...prev, status: e.target.value as WorkspaceStatus }));
                if (errors.status) setErrors(prev => ({ ...prev, status: undefined }));
              }}
              className={fieldInputClass(!!errors.status)}
            >
              <option value="ENABLED">Enabled</option>
              <option value="DRAFT">Draft</option>
              <option value="DISABLED">Disabled</option>
            </select>
            {errors.status && <p className="mt-1 text-xs text-red-600 dark:text-red-400">{errors.status}</p>}
            <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
              Draft workspaces are only visible to admins. Disabled workspaces are hidden from all views.
            </p>
          </div>

          <div>
            <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
              Workspace Shortcode <span className="text-red-500">*</span>
            </label>
            <input
              type="text"
              value={values.workspaceShortcode}
              onChange={handleChange('workspaceShortcode')}
              placeholder="e.g. 77BD"
              className={fieldInputClass(!!errors.workspaceShortcode)}
            />
            {errors.workspaceShortcode && <p className="mt-1 text-xs text-red-600 dark:text-red-400">{errors.workspaceShortcode}</p>}
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
              {!isEditing && <span className="text-red-500">*</span>}
              {isEditing && (
                <span className="ml-1 text-xs text-slate-400 dark:text-slate-500 font-normal">
                  — leave unchanged to keep existing
                </span>
              )}
            </label>
            <div className="relative">
              <input
                type={showKey ? 'text' : 'password'}
                value={values.clientKey}
                onChange={handleChange('clientKey')}
                onFocus={handleKeyFocus}
                placeholder={isEditing ? '(unchanged)' : 'Enter client key'}
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
            {isEditing && !errors.clientKey && (
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
              disabled={isSubmitting || isTesting}
              className="px-4 py-2 text-sm font-medium text-slate-600 dark:text-slate-300 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl hover:bg-slate-50 dark:hover:bg-slate-700/60 disabled:opacity-50 transition-colors cursor-pointer"
            >
              Cancel
            </button>
            <button
              type="button"
              onClick={handleTestConnection}
              disabled={isSubmitting || isTesting}
              className="inline-flex items-center gap-2 px-4 py-2 text-sm font-semibold text-indigo-700 dark:text-indigo-300 bg-indigo-50 dark:bg-indigo-900/20 border border-indigo-200 dark:border-indigo-700 rounded-xl hover:bg-indigo-100 dark:hover:bg-indigo-900/40 disabled:opacity-50 transition-all cursor-pointer"
            >
              {isTesting ? <Loader2 className="h-4 w-4 animate-spin" /> : <Plug className="h-4 w-4" />}
              Test Connection
            </button>
            <button
              type="submit"
              disabled={isSubmitting || isTesting}
              className="inline-flex items-center gap-2 px-4 py-2 text-sm font-semibold text-white bg-indigo-600 hover:bg-indigo-500 dark:bg-indigo-500 dark:hover:bg-indigo-400 rounded-xl disabled:opacity-50 transition-all cursor-pointer"
            >
              {isSubmitting && <Loader2 className="h-4 w-4 animate-spin" />}
              {isEditing ? 'Save Changes' : 'Create Workspace'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default WorkspaceFormModal;
