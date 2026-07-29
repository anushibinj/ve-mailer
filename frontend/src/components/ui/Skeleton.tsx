interface SkeletonProps {
  className?: string;
}

export function Skeleton({ className = '' }: SkeletonProps) {
  return (
    <div
      className={`skeleton h-4 ${className}`}
      aria-hidden="true"
    />
  );
}

/** A skeleton card that mimics a workspace card */
export function SkeletonCard() {
  return (
    <div className="bg-white dark:bg-slate-900 rounded-2xl border border-slate-100 dark:border-slate-800 p-5 flex items-center gap-4">
      <div className="skeleton h-12 w-12 rounded-xl flex-shrink-0" />
      <div className="flex-1 space-y-2.5">
        <Skeleton className="w-36 h-4" />
        <Skeleton className="w-20 h-3" />
      </div>
    </div>
  );
}

/** Skeleton row for tables */
export function SkeletonRow({ cols = 4 }: { cols?: number }) {
  return (
    <tr>
      {Array.from({ length: cols }).map((_, i) => (
        <td key={i} className="px-6 py-4">
          <Skeleton className={`h-4 ${i === 0 ? 'w-32' : i === 1 ? 'w-24' : 'w-16'}`} />
        </td>
      ))}
    </tr>
  );
}

/** Full-page loading placeholder with skeleton cards */
export function SkeletonCardGrid({ count = 6 }: { count?: number }) {
  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {Array.from({ length: count }).map((_, i) => (
        <SkeletonCard key={i} />
      ))}
    </div>
  );
}

/** Dashboard skeleton: header + table rows */
export function SkeletonDashboard() {
  return (
    <div className="animate-fade-in space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="space-y-2">
          <Skeleton className="h-7 w-48" />
          <Skeleton className="h-4 w-64" />
        </div>
        <div className="flex gap-2">
          <Skeleton className="h-9 w-32 rounded-xl" />
          <Skeleton className="h-9 w-36 rounded-xl" />
        </div>
      </div>
      {/* Table card */}
      <div className="bg-white dark:bg-slate-900 rounded-2xl border border-slate-100 dark:border-slate-800 overflow-hidden">
        <div className="px-5 py-4 border-b border-slate-100 dark:border-slate-800">
          <Skeleton className="h-5 w-40" />
        </div>
        <table className="min-w-full">
          <tbody className="divide-y divide-slate-50 dark:divide-slate-800/60">
            {Array.from({ length: 4 }).map((_, i) => (
              <SkeletonRow key={i} cols={4} />
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

/** Inline text skeleton */
export function SkeletonText({ lines = 3 }: { lines?: number }) {
  const widths = ['w-full', 'w-5/6', 'w-4/6', 'w-3/5', 'w-2/3'];
  return (
    <div className="space-y-2">
      {Array.from({ length: lines }).map((_, i) => (
        <Skeleton key={i} className={`h-4 ${widths[i % widths.length]}`} />
      ))}
    </div>
  );
}
