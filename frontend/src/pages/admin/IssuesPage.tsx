import { useCallback, useEffect, useState } from 'react';
import { AlertCircle, Eye, Loader2 } from 'lucide-react';
import toast from 'react-hot-toast';
import type { IssueReport, IssueStatus } from '../../services/apiService';
import { adminGetIssues, adminUpdateIssueStatus } from '../../services/apiService';
import { TableActionButton } from '../../components/ui';

const ISSUE_STATUSES: IssueStatus[] = ['OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'];

function formatStatusLabel(status: IssueStatus): string {
  return status.replace('_', ' ');
}

function screenshotDataUrl(issue: IssueReport): string | null {
  if (!issue.hasScreenshot || !issue.screenshotBase64) return null;
  const mimeType = issue.screenshotContentType || 'image/png';
  return `data:${mimeType};base64,${issue.screenshotBase64}`;
}

export default function IssuesPage() {
  const [issues, setIssues] = useState<IssueReport[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [updatingIssueId, setUpdatingIssueId] = useState<string | null>(null);
  const [previewIssue, setPreviewIssue] = useState<IssueReport | null>(null);

  const loadIssues = useCallback(async () => {
    setIsLoading(true);
    try {
      const data = await adminGetIssues();
      setIssues(data);
    } catch {
      toast.error('Failed to load issues.');
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    loadIssues();
  }, [loadIssues]);

  const handleStatusChange = async (issueId: string, status: IssueStatus) => {
    setUpdatingIssueId(issueId);
    try {
      const updated = await adminUpdateIssueStatus(issueId, status);
      setIssues(prev => prev.map(item => (item.id === issueId ? updated : item)));
      toast.success('Issue status updated.');
    } catch {
      toast.error('Failed to update issue status.');
    } finally {
      setUpdatingIssueId(null);
    }
  };

  if (isLoading) {
    return (
      <div className="flex flex-col items-center justify-center py-20">
        <Loader2 className="h-7 w-7 animate-spin text-indigo-500 mb-3" />
        <p className="text-sm text-slate-400 dark:text-gray-400">Loading issues…</p>
      </div>
    );
  }

  if (issues.length === 0) {
    return (
      <div className="bg-white dark:bg-gray-800 rounded-2xl border border-slate-100 dark:border-gray-700/50 shadow-sm p-12 text-center">
        <div className="h-12 w-12 rounded-2xl bg-slate-50 dark:bg-gray-700/40 flex items-center justify-center mx-auto mb-4">
          <AlertCircle className="h-6 w-6 text-slate-300" />
        </div>
        <p className="text-slate-500 dark:text-gray-400 text-sm">No issues have been raised yet.</p>
      </div>
    );
  }

  return (
    <>
      <div className="bg-white dark:bg-gray-800 rounded-2xl border border-slate-100 dark:border-gray-700/50 shadow-sm overflow-hidden">
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-100 text-sm">
            <thead className="bg-slate-50/70 dark:bg-gray-700/40">
              <tr>
                <th className="px-4 py-3 text-left font-semibold text-slate-600 dark:text-gray-300">Actions</th>
                <th className="px-4 py-3 text-left font-semibold text-slate-600 dark:text-gray-300">Raised At</th>
                <th className="px-4 py-3 text-left font-semibold text-slate-600 dark:text-gray-300">Email</th>
                <th className="px-4 py-3 text-left font-semibold text-slate-600 dark:text-gray-300">Message</th>
                <th className="px-4 py-3 text-left font-semibold text-slate-600 dark:text-gray-300">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 dark:divide-gray-700/50">
              {issues.map(issue => (
                <tr key={issue.id} className="hover:bg-slate-50/60 dark:hover:bg-gray-700/30">
                  <td className="px-4 py-3">
                    {issue.hasScreenshot ? (
                      <TableActionButton
                        icon={<Eye className="h-3.5 w-3.5" />}
                        label="View Screenshot"
                        variant="secondary"
                        onClick={() => setPreviewIssue(issue)}
                      />
                    ) : (
                      <span className="text-slate-300 dark:text-gray-600">—</span>
                    )}
                  </td>
                  <td className="px-4 py-3 text-slate-600 dark:text-gray-300 whitespace-nowrap">
                    {new Date(issue.createdAt).toLocaleString()}
                  </td>
                  <td className="px-4 py-3 text-slate-600 dark:text-gray-300">{issue.reporterEmail || '—'}</td>
                  <td className="px-4 py-3 text-slate-700 dark:text-gray-200 max-w-md">
                    <p className="whitespace-pre-wrap break-words">{issue.message || '—'}</p>
                  </td>
                  <td className="px-4 py-3">
                    <select
                      value={issue.status}
                      disabled={updatingIssueId === issue.id}
                      onChange={(e) => handleStatusChange(issue.id, e.target.value as IssueStatus)}
                      className="border border-slate-200 dark:border-gray-600 rounded-lg px-2.5 py-1.5 bg-white dark:bg-gray-700 text-slate-700 dark:text-gray-200"
                    >
                      {ISSUE_STATUSES.map(status => (
                        <option key={status} value={status}>
                          {formatStatusLabel(status)}
                        </option>
                      ))}
                    </select>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {previewIssue && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div className="max-w-4xl w-full bg-white dark:bg-gray-800 rounded-2xl border border-slate-200 dark:border-gray-700 p-4 space-y-3">
            <div className="flex items-center justify-between">
              <h3 className="font-semibold text-slate-900 dark:text-white">Issue screenshot</h3>
              <button
                type="button"
                onClick={() => setPreviewIssue(null)}
                className="text-slate-500 dark:text-gray-400 hover:text-slate-700 dark:hover:text-gray-200"
              >
                Close
              </button>
            </div>
            {screenshotDataUrl(previewIssue) ? (
              <img
                src={screenshotDataUrl(previewIssue) as string}
                alt="Issue screenshot"
                className="max-h-[70vh] w-full object-contain rounded-lg border border-slate-200 dark:border-gray-700"
              />
            ) : (
              <p className="text-sm text-slate-500 dark:text-gray-400">Screenshot is not available.</p>
            )}
          </div>
        </div>
      )}
    </>
  );
}
