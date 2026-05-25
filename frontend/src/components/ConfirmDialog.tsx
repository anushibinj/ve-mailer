import React from 'react';
import { AlertTriangle, X, Loader2 } from 'lucide-react';

interface ConfirmDialogProps {
  isOpen: boolean;
  title: string;
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  onConfirm: () => void;
  onCancel: () => void;
  isLoading?: boolean;
  variant?: 'danger' | 'warning';
}

const ConfirmDialog: React.FC<ConfirmDialogProps> = ({
  isOpen, title, message, confirmLabel = 'Confirm', cancelLabel = 'Cancel',
  onConfirm, onCancel, isLoading = false, variant = 'danger',
}) => {
  if (!isOpen) return null;

  const isDanger = variant === 'danger';

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div
        className="absolute inset-0 bg-slate-900/60 dark:bg-slate-950/70 backdrop-blur-sm animate-fade-in"
        onClick={onCancel}
        aria-hidden="true"
      />

      <div className="relative bg-white dark:bg-slate-900 rounded-2xl shadow-2xl dark:shadow-slate-950/60 border border-slate-100/80 dark:border-slate-700/50 w-full max-w-md overflow-hidden animate-scale-in">
        <div className={`h-1 ${isDanger ? 'bg-gradient-to-r from-rose-500 to-red-500' : 'bg-gradient-to-r from-amber-400 to-orange-500'}`} />

        <div className="p-6">
          <button
            onClick={onCancel}
            className="absolute top-5 right-5 p-1.5 rounded-lg text-slate-400 dark:text-slate-500 hover:text-slate-600 dark:hover:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors cursor-pointer"
            aria-label="Close"
          >
            <X className="h-4 w-4" />
          </button>

          <div className="flex items-start gap-4 pr-6">
            <div className={`flex-shrink-0 h-10 w-10 rounded-xl flex items-center justify-center ${
              isDanger ? 'bg-rose-50 dark:bg-rose-500/10' : 'bg-amber-50 dark:bg-amber-500/10'
            }`}>
              <AlertTriangle className={`h-5 w-5 ${isDanger ? 'text-rose-500 dark:text-rose-400' : 'text-amber-500 dark:text-amber-400'}`} />
            </div>
            <div className="flex-1 min-w-0">
              <h3 className="text-base font-semibold text-slate-900 dark:text-white mb-1">{title}</h3>
              <p className="text-sm text-slate-500 dark:text-slate-400 leading-relaxed">{message}</p>
            </div>
          </div>

          <div className="mt-6 flex justify-end gap-2.5">
            <button
              type="button"
              onClick={onCancel}
              disabled={isLoading}
              className="px-4 py-2 text-sm font-medium text-slate-600 dark:text-slate-300 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl hover:bg-slate-50 dark:hover:bg-slate-700/60 focus:outline-none focus:ring-2 focus:ring-slate-400 focus:ring-offset-2 dark:focus:ring-offset-slate-900 disabled:opacity-50 transition-colors cursor-pointer"
            >
              {cancelLabel}
            </button>
            <button
              type="button"
              onClick={onConfirm}
              disabled={isLoading}
              className={`flex items-center gap-2 px-4 py-2 text-sm font-semibold text-white rounded-xl focus:outline-none focus:ring-2 focus:ring-offset-2 dark:focus:ring-offset-slate-900 disabled:opacity-50 transition-all cursor-pointer ${
                isDanger
                  ? 'bg-rose-600 hover:bg-rose-500 dark:bg-rose-500 dark:hover:bg-rose-400 focus:ring-rose-500'
                  : 'bg-amber-500 hover:bg-amber-400 dark:bg-amber-500 dark:hover:bg-amber-400 focus:ring-amber-400'
              }`}
            >
              {isLoading ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
              {isLoading ? 'Please wait…' : confirmLabel}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default ConfirmDialog;
