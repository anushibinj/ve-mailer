import React from 'react';

interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
  hint?: string;
  leftIcon?: React.ReactNode;
  rightElement?: React.ReactNode;
}

const baseInput =
  'w-full border rounded-xl text-sm text-slate-900 dark:text-slate-100 ' +
  'placeholder:text-slate-400 dark:placeholder:text-slate-500 ' +
  'bg-white dark:bg-slate-800/60 ' +
  'focus:outline-none focus:ring-2 transition-all ' +
  'border-slate-200 dark:border-slate-700 ' +
  'focus:border-indigo-500 dark:focus:border-indigo-400 ' +
  'focus:ring-indigo-500/15 dark:focus:ring-indigo-400/15';

export const inputClass = `${baseInput} px-3.5 py-2.5`;

export function Input({
  label,
  error,
  hint,
  leftIcon,
  rightElement,
  id,
  className = '',
  ...rest
}: InputProps) {
  const inputId = id ?? label?.toLowerCase().replace(/\s+/g, '-');

  return (
    <div className="space-y-1.5">
      {label && (
        <label
          htmlFor={inputId}
          className="block text-sm font-medium text-slate-700 dark:text-slate-300"
        >
          {label}
        </label>
      )}
      <div className="relative">
        {leftIcon && (
          <span className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400 dark:text-slate-500 pointer-events-none">
            {leftIcon}
          </span>
        )}
        <input
          id={inputId}
          className={`
            ${baseInput}
            ${leftIcon ? 'pl-10' : 'px-3.5'}
            ${rightElement ? 'pr-10' : 'px-3.5'}
            py-2.5
            ${error ? 'border-red-400 dark:border-red-500 focus:border-red-400 focus:ring-red-400/20' : ''}
            ${className}
          `.replace(/\s+/g, ' ').trim()}
          {...rest}
        />
        {rightElement && (
          <span className="absolute right-3 top-1/2 -translate-y-1/2">
            {rightElement}
          </span>
        )}
      </div>
      {error && (
        <p className="text-xs text-red-500 dark:text-red-400">{error}</p>
      )}
      {hint && !error && (
        <p className="text-xs text-slate-400 dark:text-slate-500">{hint}</p>
      )}
    </div>
  );
}

export function Select({
  label,
  error,
  hint,
  id,
  className = '',
  children,
  ...rest
}: React.SelectHTMLAttributes<HTMLSelectElement> & {
  label?: string;
  error?: string;
  hint?: string;
}) {
  const selectId = id ?? label?.toLowerCase().replace(/\s+/g, '-');
  return (
    <div className="space-y-1.5">
      {label && (
        <label htmlFor={selectId} className="block text-sm font-medium text-slate-700 dark:text-slate-300">
          {label}
        </label>
      )}
      <select
        id={selectId}
        className={`
          w-full px-3.5 py-2.5 border rounded-xl text-sm appearance-none
          text-slate-900 dark:text-slate-100
          bg-white dark:bg-slate-800/60
          border-slate-200 dark:border-slate-700
          focus:outline-none focus:border-indigo-500 dark:focus:border-indigo-400
          focus:ring-2 focus:ring-indigo-500/15 dark:focus:ring-indigo-400/15
          transition-all cursor-pointer
          ${error ? 'border-red-400' : ''}
          ${className}
        `.replace(/\s+/g, ' ').trim()}
        {...rest}
      >
        {children}
      </select>
      {error && <p className="text-xs text-red-500 dark:text-red-400">{error}</p>}
      {hint && !error && <p className="text-xs text-slate-400 dark:text-slate-500">{hint}</p>}
    </div>
  );
}

export function Textarea({
  label,
  error,
  hint,
  id,
  className = '',
  ...rest
}: React.TextareaHTMLAttributes<HTMLTextAreaElement> & {
  label?: string;
  error?: string;
  hint?: string;
}) {
  const textareaId = id ?? label?.toLowerCase().replace(/\s+/g, '-');
  return (
    <div className="space-y-1.5">
      {label && (
        <label htmlFor={textareaId} className="block text-sm font-medium text-slate-700 dark:text-slate-300">
          {label}
        </label>
      )}
      <textarea
        id={textareaId}
        className={`
          w-full px-3.5 py-2.5 border rounded-xl text-sm resize-none
          text-slate-900 dark:text-slate-100
          placeholder:text-slate-400 dark:placeholder:text-slate-500
          bg-white dark:bg-slate-800/60
          border-slate-200 dark:border-slate-700
          focus:outline-none focus:border-indigo-500 dark:focus:border-indigo-400
          focus:ring-2 focus:ring-indigo-500/15 dark:focus:ring-indigo-400/15
          transition-all
          ${error ? 'border-red-400' : ''}
          ${className}
        `.replace(/\s+/g, ' ').trim()}
        {...rest}
      />
      {error && <p className="text-xs text-red-500 dark:text-red-400">{error}</p>}
      {hint && !error && <p className="text-xs text-slate-400 dark:text-slate-500">{hint}</p>}
    </div>
  );
}
