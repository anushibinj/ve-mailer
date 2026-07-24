import { Loader2 } from 'lucide-react';

interface LoadingPlaceholderProps {
  message: string;
}

export default function LoadingPlaceholder({ message }: LoadingPlaceholderProps) {
  return (
    <div className="flex items-center justify-center py-8">
      <div className="inline-flex items-center gap-2.5 rounded-xl border border-slate-200 dark:border-slate-700 bg-white/90 dark:bg-slate-900/80 px-4 py-2.5 text-sm text-slate-600 dark:text-slate-300 shadow-sm">
        <Loader2 className="h-4 w-4 animate-spin text-indigo-500" />
        <span>{message}</span>
      </div>
    </div>
  );
}
