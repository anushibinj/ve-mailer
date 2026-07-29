import React from 'react';

interface CardProps {
  children: React.ReactNode;
  className?: string;
  /** Removes default padding */
  noPadding?: boolean;
  /** Hover lift effect */
  hoverable?: boolean;
}

export function Card({ children, className = '', noPadding = false, hoverable = false }: CardProps) {
  return (
    <div
      className={`
        bg-white dark:bg-slate-900
        border border-slate-100 dark:border-slate-800
        rounded-2xl
        shadow-[var(--shadow-card)]
        ${hoverable ? 'hover:shadow-[var(--shadow-card-hover)] hover:-translate-y-0.5 transition-all duration-200' : ''}
        ${noPadding ? '' : 'p-5'}
        ${className}
      `.replace(/\s+/g, ' ').trim()}
    >
      {children}
    </div>
  );
}

interface CardHeaderProps {
  children: React.ReactNode;
  className?: string;
}

export function CardHeader({ children, className = '' }: CardHeaderProps) {
  return (
    <div
      className={`px-5 py-4 border-b border-slate-100 dark:border-slate-800 flex items-center justify-between ${className}`}
    >
      {children}
    </div>
  );
}

export function CardBody({ children, className = '' }: CardHeaderProps) {
  return (
    <div className={`p-5 ${className}`}>
      {children}
    </div>
  );
}
