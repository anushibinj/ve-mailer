import React from 'react';

export type BadgeVariant =
  | 'brand'
  | 'success'
  | 'warning'
  | 'danger'
  | 'neutral'
  | 'info'
  | 'teal'
  | 'violet';

interface BadgeProps {
  variant?: BadgeVariant;
  dot?: boolean;
  children: React.ReactNode;
  className?: string;
}

const variantClasses: Record<BadgeVariant, string> = {
  brand:   'bg-indigo-50 dark:bg-indigo-500/12 text-indigo-700 dark:text-indigo-300 border-indigo-200 dark:border-indigo-500/25',
  success: 'bg-emerald-50 dark:bg-emerald-500/12 text-emerald-700 dark:text-emerald-400 border-emerald-200 dark:border-emerald-500/25',
  warning: 'bg-amber-50 dark:bg-amber-500/12 text-amber-700 dark:text-amber-400 border-amber-200 dark:border-amber-500/25',
  danger:  'bg-red-50 dark:bg-red-500/12 text-red-700 dark:text-red-400 border-red-200 dark:border-red-500/25',
  neutral: 'bg-slate-100 dark:bg-slate-700/60 text-slate-600 dark:text-slate-300 border-slate-200 dark:border-slate-600',
  info:    'bg-sky-50 dark:bg-sky-500/12 text-sky-700 dark:text-sky-400 border-sky-200 dark:border-sky-500/25',
  teal:    'bg-teal-50 dark:bg-teal-500/12 text-teal-700 dark:text-teal-300 border-teal-200 dark:border-teal-500/25',
  violet:  'bg-violet-50 dark:bg-violet-500/12 text-violet-700 dark:text-violet-300 border-violet-200 dark:border-violet-500/25',
};

const dotClasses: Record<BadgeVariant, string> = {
  brand:   'bg-indigo-500',
  success: 'bg-emerald-500',
  warning: 'bg-amber-500',
  danger:  'bg-red-500',
  neutral: 'bg-slate-400',
  info:    'bg-sky-500',
  teal:    'bg-teal-500',
  violet:  'bg-violet-500',
};

export function Badge({ variant = 'neutral', dot = false, children, className = '' }: BadgeProps) {
  return (
    <span
      className={`
        inline-flex items-center gap-1.5 px-2 py-0.5
        rounded-full text-[11px] font-semibold border leading-none
        ${variantClasses[variant]}
        ${className}
      `.replace(/\s+/g, ' ').trim()}
    >
      {dot && <span className={`h-1.5 w-1.5 rounded-full flex-shrink-0 ${dotClasses[variant]}`} />}
      {children}
    </span>
  );
}

/** Convenience connectivity badge */
export function ConnectivityBadge({ status }: { status: 'ONLINE' | 'OFFLINE' | 'UNKNOWN' }) {
  if (status === 'ONLINE')
    return <Badge variant="success" dot>Online</Badge>;
  if (status === 'OFFLINE')
    return <Badge variant="danger" dot>Unreachable</Badge>;
  return <Badge variant="neutral" dot>Unknown</Badge>;
}
