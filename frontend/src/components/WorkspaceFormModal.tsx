import React, { useEffect, useState } from 'react';
import { X, Eye, EyeOff, Loader2 } from 'lucide-react';
import type { WorkspaceAdmin, WorkspaceCreatePayload, WorkspaceUpdatePayload } from '../services/apiService';
import { adminCreateWorkspace, adminUpdateWorkspace } from '../services/apiService';
import toast from 'react-hot-toast';

const CLIENT_KEY_PLACEHOLDER = '(unchanged)';

interface WorkspaceFormModalProps {
  isOpen: boolean;
  workspace?: WorkspaceAdmin | null; // null = create mode, set = edit mode
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
}

interface FormErrors {
  title?: string;
  sharedSpaceId?: string;
  workspaceId?: string;
  clientId?: string;
  clientKey?: string;
  rootUrl?: string;
}

const WorkspaceFormModal: React.FC<WorkspaceFormModalProps> = ({
  isOpen,
  workspace,
  onClose,
  onSuccess,
}) => {
  const isEditing = !!workspace;

  const [values, setValues] = useState<FormValues>({
    title: '',
    sharedSpaceId: '',
    workspaceId: '',
    clientId: '',
    clientKey: '',
    rootUrl: '',
  });
  const [errors, setErrors] = useState<FormErrors>({});
  const [showKey, setShowKey] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Populate form when opening in edit mode
  useEffect(() => {
    if (isOpen) {
      if (workspace) {
        // eslint-disable-next-line react-hooks/set-state-in-effect
        setValues({
          title: workspace.title,
          sharedSpaceId: workspace.sharedSpaceId,
          workspaceId: workspace.workspaceId,
          clientId: workspace.clientId,
          // Never pre-fill the key — keep placeholder so user knows to enter a new one
          clientKey: CLIENT_KEY_PLACEHOLDER,
          rootUrl: workspace.rootUrl,
        });
      } else {
        setValues({ title: '', sharedSpaceId: '', workspaceId: '', clientId: '', clientKey: '', rootUrl: '' });
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

  // When the user focuses the clientKey field in edit mode, clear the placeholder
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
          title: values.title.trim(),
          sharedSpaceId: values.sharedSpaceId.trim(),
          workspaceId: values.workspaceId.trim(),
          clientId: values.clientId.trim(),
          // Send placeholder when unchanged so backend knows to preserve existing key
          clientKey: values.clientKey.trim() || CLIENT_KEY_PLACEHOLDER,
          rootUrl: values.rootUrl.trim(),
        };
        saved = await adminUpdateWorkspace(workspace.id, payload);
        toast.success('Workspace updated successfully');
      } else {
        const payload: WorkspaceCreatePayload = {
          title: values.title.trim(),
          sharedSpaceId: values.sharedSpaceId.trim(),
          workspaceId: values.workspaceId.trim(),
          clientId: values.clientId.trim(),
          clientKey: values.clientKey.trim(),
          rootUrl: values.rootUrl.trim(),
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
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      {/* Backdrop */}
      <div className="absolute inset-0 bg-black/50" onClick={onClose} aria-hidden="true" />

      {/* Modal */}
      <div className="relative bg-white rounded-xl shadow-xl w-full max-w-lg mx-4 overflow-hidden">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-200 bg-gray-50">
          <h2 className="text-lg font-semibold text-gray-900">
            {isEditing ? 'Edit Workspace' : 'Create Workspace'}
          </h2>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600 transition-colors"
            aria-label="Close"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Form */}
        <form onSubmit={handleSubmit} className="px-6 py-5 space-y-4">
          {/* Title */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Title <span className="text-red-500">*</span>
            </label>
            <input
              type="text"
              value={values.title}
              onChange={handleChange('title')}
              placeholder="e.g. ALM Octane — Team Alpha"
              className={`w-full px-3 py-2 border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 ${
                errors.title ? 'border-red-400 bg-red-50' : 'border-gray-300'
              }`}
            />
            {errors.title && (
              <p className="mt-1 text-xs text-red-600">{errors.title}</p>
            )}
          </div>

          {/* Root URL */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Root URL <span className="text-red-500">*</span>
            </label>
            <input
              type="url"
              value={values.rootUrl}
              onChange={handleChange('rootUrl')}
              placeholder="e.g. https://octane.example.com"
              className={`w-full px-3 py-2 border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 ${
                errors.rootUrl ? 'border-red-400 bg-red-50' : 'border-gray-300'
              }`}
            />
            {errors.rootUrl && (
              <p className="mt-1 text-xs text-red-600">{errors.rootUrl}</p>
            )}
            <p className="mt-1 text-xs text-gray-500">
              Base URL of the ValueEdge / Octane server.
            </p>
          </div>

          {/* Shared Space ID */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Shared Space ID <span className="text-red-500">*</span>
            </label>
            <input
              type="text"
              value={values.sharedSpaceId}
              onChange={handleChange('sharedSpaceId')}
              placeholder="e.g. 4001"
              className={`w-full px-3 py-2 border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 ${
                errors.sharedSpaceId ? 'border-red-400 bg-red-50' : 'border-gray-300'
              }`}
            />
            {errors.sharedSpaceId && (
              <p className="mt-1 text-xs text-red-600">{errors.sharedSpaceId}</p>
            )}
          </div>

          {/* Workspace ID */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Workspace ID <span className="text-red-500">*</span>
            </label>
            <input
              type="text"
              value={values.workspaceId}
              onChange={handleChange('workspaceId')}
              placeholder="e.g. 5015"
              className={`w-full px-3 py-2 border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 ${
                errors.workspaceId ? 'border-red-400 bg-red-50' : 'border-gray-300'
              }`}
            />
            {errors.workspaceId && (
              <p className="mt-1 text-xs text-red-600">{errors.workspaceId}</p>
            )}
          </div>

          {/* Client ID */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Client ID <span className="text-red-500">*</span>
            </label>
            <input
              type="text"
              value={values.clientId}
              onChange={handleChange('clientId')}
              placeholder="e.g. my-api-client-id"
              className={`w-full px-3 py-2 border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 ${
                errors.clientId ? 'border-red-400 bg-red-50' : 'border-gray-300'
              }`}
            />
            {errors.clientId && (
              <p className="mt-1 text-xs text-red-600">{errors.clientId}</p>
            )}
          </div>

          {/* Client Key */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Client Key{' '}
              {!isEditing && <span className="text-red-500">*</span>}
              {isEditing && (
                <span className="ml-1 text-xs text-gray-400 font-normal">
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
                className={`w-full px-3 py-2 pr-10 border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 ${
                  errors.clientKey ? 'border-red-400 bg-red-50' : 'border-gray-300'
                }`}
              />
              <button
                type="button"
                onClick={() => setShowKey(v => !v)}
                className="absolute right-2.5 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
                tabIndex={-1}
                aria-label={showKey ? 'Hide key' : 'Show key'}
              >
                {showKey ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
              </button>
            </div>
            {errors.clientKey && (
              <p className="mt-1 text-xs text-red-600">{errors.clientKey}</p>
            )}
            {isEditing && (
              <p className="mt-1 text-xs text-gray-500">
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
              className="px-4 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-50 disabled:opacity-50 transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isSubmitting}
              className="inline-flex items-center gap-2 px-4 py-2 text-sm font-medium text-white bg-blue-600 rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors"
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
