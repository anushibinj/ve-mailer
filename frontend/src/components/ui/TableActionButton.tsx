import React from 'react';
import { Loader2 } from 'lucide-react';
import { Tooltip, type TooltipPosition } from './Tooltip';

export type TableActionButtonVariant = 'primary' | 'secondary' | 'danger' | 'success' | 'warning';

export interface TableActionButtonProps extends Omit<React.ButtonHTMLAttributes<HTMLButtonElement>, 'title'> {
  /** Icon to render (e.g. from lucide-react). Sized/aligned consistently by this component. */
  icon: React.ReactNode;
  /**
   * Accessible label describing the action (e.g. "Edit Workspace", "Delete User").
   * Rendered inside the shared `Tooltip` popover on hover/focus, and mirrored as the
   * button's `aria-label` for assistive technology.
   */
  label: string;
  variant?: TableActionButtonVariant;
  loading?: boolean;
  /** Side of the button the popover should appear on. Defaults to 'top'. */
  tooltipPosition?: TooltipPosition;
}

const variantClasses: Record<TableActionButtonVariant, string> = {
  primary:
    'text-indigo-600 dark:text-indigo-400 border-indigo-200 dark:border-indigo-700 bg-white dark:bg-slate-800 hover:bg-indigo-50 dark:hover:bg-indigo-500/10 focus-visible:ring-indigo-500',
  secondary:
    'text-slate-600 dark:text-slate-300 border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 hover:bg-slate-50 dark:hover:bg-slate-700/60 hover:text-indigo-600 dark:hover:text-indigo-400 focus-visible:ring-indigo-500',
  danger:
    'text-red-600 dark:text-red-400 border-red-200 dark:border-red-700 bg-white dark:bg-slate-800 hover:bg-red-50 dark:hover:bg-red-500/10 focus-visible:ring-red-500',
  success:
    'text-emerald-600 dark:text-emerald-400 border-emerald-200 dark:border-emerald-700 bg-white dark:bg-slate-800 hover:bg-emerald-50 dark:hover:bg-emerald-500/10 focus-visible:ring-emerald-500',
  warning:
    'text-amber-600 dark:text-amber-400 border-amber-200 dark:border-amber-700 bg-white dark:bg-slate-800 hover:bg-amber-50 dark:hover:bg-amber-500/10 focus-visible:ring-amber-500',
};

/**
 * Compact, icon-only button for row-level actions in management tables (Edit, Delete,
 * Test Connection, Promote/Demote, etc.). Standardizes size/spacing/color-coding across
 * all tables and surfaces its action via the shared `Tooltip` popover (shown on hover
 * *and* keyboard focus) plus a matching `aria-label`, so the action is discoverable
 * without relying on the browser's native `title` attribute.
 */
export const TableActionButton: React.FC<TableActionButtonProps> = ({
  icon,
  label,
  variant = 'secondary',
  loading = false,
  disabled,
  className = '',
  tooltipPosition = 'top',
  ...rest
}) => {
  return (
    <Tooltip content={label} position={tooltipPosition} disabled={disabled || loading}>
      <button
        type="button"
        aria-label={label}
        disabled={disabled || loading}
        className={`
          inline-flex items-center justify-center h-8 w-8 rounded-lg border
          transition-colors cursor-pointer flex-shrink-0
          focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2
          dark:focus-visible:ring-offset-slate-900
          disabled:opacity-50 disabled:cursor-not-allowed
          ${variantClasses[variant]}
          ${className}
        `.replace(/\s+/g, ' ').trim()}
        {...rest}
      >
        {loading ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : icon}
      </button>
    </Tooltip>
  );
};

export default TableActionButton;
