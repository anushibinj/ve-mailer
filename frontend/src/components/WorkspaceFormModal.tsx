import React, { useEffect, useState } from 'react';
import { X, Eye, EyeOff, Loader2, Building2 } from 'lucide-react';
import type { WorkspaceAdmin, WorkspaceCreatePayload, WorkspaceUpdatePayload, WorkspaceStatus } from '../services/apiService';
import { adminCreateWorkspace, adminUpdateWorkspace } from '../services/apiService';
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
  sharedSpaceId: string;
  workspaceId: string;
  clientId: string;
  clientKey: string;
  rootUrl: string;
  status: WorkspaceStatus;
}

interface FormErrors {
  title?: string;
  sharedSpaceId?: string;
  workspaceId?: string;
  clientId?: string;
  clientKey?: string;
  rootUrl?: string;
  status?: string;
}

const inputClass =
  'w-full px-3.5 py-2.5 border rounded-xl text-sm transition-all ' +
  'text-slate-900 dark:text-slate-100 bg-white dark:bg-slate-800/60 ' +
  'placeholder:text-slate-400 dark:placeholder:text-slate-500 ' +
  'focus:outline-none focus:ring-2';

const fieldInputClass = (hasError: boolean) =>
  `${inputClass} ${hasError
    ? 'border-red-400 dark:border-red-500/50 bg-red-50 dark:bg-red-500/5 focus:border-red-500 focus:ring-red-500/20'
    : 'border-slate-200 dark:border-slate-600 focus:border-indigo-500 dark:focus:border-indigo-400 focus:ring-indigo-500/15 dark:focus:ring-indigo-400/15'
  }`;

const WorkspaceFormModal: React.FC<WorkspaceFormModalProps> = ({
  isOpen, workspace, onClose, onSuccess,
}) => {
  const isEditing = !!workspace;

  const [values, setValues] = useState<FormValues>({
    title: '', sharedSpaceId: '', workspaceId: '', clientId: '', clientKey: '', rootUrl: '', status: 'DRAFT',
  });
  const [errors, setErrors] = useState<FormErrors>({});
  const [showKey, setShowKey] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    if (isOpen) {
      if (workspace) {
        // eslint-disable-next-line react-hooks/set-state-in-effect
        setValues({
          title: workspace.title,
          sharedSpaceId: workspace.sharedSpaceId,
          workspaceId: workspace.workspaceId,
          clientId: workspace.clientId,
          clientKey: CLIENT_KEY_PLACEHOLDER,
          rootUrl: workspace.rootUrl,
          status: workspace.status,
        });
      } else {
        setValues({ title: '', sharedSpaceId: '', workspaceId: '', clientId: '', clientKey: '', rootUrl: '', status: 'DRAFT' });
      }
      setErrors({});
      setShowKey(false);
    }
  }, [isOpen, workspace]);

  const validate = (): boolean => {
    const errs: FormErrors = {};
    if (!values.title.trim()) errs.title = 'Title is required';
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
  };

  const handleKeyFocus = () => {
    if (isEditing && values.clientKey === CLIENT_KEY_PLACEHOLDER) {
      setValues(prev => ({ ...prev, clientKey: '' }));
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
          workspaceId: values.workspaceId.trim(), clientId: values.clientId.trim(),
          clientKey: values.clientKey.trim() || CLIENT_KEY_PLACEHOLDER, rootUrl: values.rootUrl.trim(),
          status: values.status,
        };
        saved = await adminUpdateWorkspace(workspace.id, payload);
        toast.success('Workspace updated successfully');
      } else {
        const payload: WorkspaceCreatePayload = {
          title: values.title.trim(), sharedSpaceId: values.sharedSpaceId.trim(),
          workspaceId: values.workspaceId.trim(), clientId: values.clientId.trim(),
          clientKey: values.clientKey.trim(), rootUrl: values.rootUrl.trim(),
          status: values.status,
        };
        saved = await adminCreateWorkspace(payload);
        toast.success('Workspace created successfully');
      }
      onSuccess(saved);
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string; errors?: { message?: string }[] } } };
      const msg =
        axiosErr.response?.data?.message ??
        axiosErr.response?.data?.errors?.[0]?.message ??
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
          {[
            { field: 'title' as const, label: 'Title', type: 'text', placeholder: 'e.g. ALM Octane — Team Alpha', required: true },
            { field: 'rootUrl' as const, label: 'Root URL', type: 'url', placeholder: 'https://octane.example.com', required: true, hint: 'Base URL of the ValueEdge / Octane server.' },
          ].map(({ field, label, type, placeholder, required, hint }) => (
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
              {hint && !errors[field] && <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">{hint}</p>}
            </div>
          ))}

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
              disabled={isSubmitting}
              className="px-4 py-2 text-sm font-medium text-slate-600 dark:text-slate-300 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl hover:bg-slate-50 dark:hover:bg-slate-700/60 disabled:opacity-50 transition-colors cursor-pointer"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isSubmitting}
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
