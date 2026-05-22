import { useState, useEffect, useCallback } from 'react';
import toast from 'react-hot-toast';
import {
  BarChart, Bar, LineChart, Line, PieChart, Pie, Cell,
  XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend,
} from 'recharts';
import type {
  MailAnalyticsSummary, DailyVolumeEntry, WorkspaceDistributionEntry,
  FilterUsageEntry, MailAuditLogEntry, PagedResponse, MailHistoryParams,
} from '../../services/apiService';
import {
  adminGetMailAnalyticsSummary, adminGetDailyVolume, adminGetDailyRecipients,
  adminGetWorkspaceDistribution, adminGetFilterUsage, adminGetMailHistory,
} from '../../services/apiService';

type DatePreset = 'today' | '7days' | '30days';

const COLORS = ['#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#ec4899', '#14b8a6', '#f97316'];

function StatCard({ label, value }: { label: string; value: string | number | null }) {
  return (
    <div className="bg-white rounded-lg border border-gray-200 p-4 shadow-sm">
      <p className="text-xs text-gray-500 uppercase tracking-wide font-medium">{label}</p>
      <p className="mt-1 text-2xl font-bold text-gray-900">{value ?? '—'}</p>
    </div>
  );
}

export default function MailAnalyticsPage() {
  const [datePreset, setDatePreset] = useState<DatePreset>('7days');
  const [summary, setSummary] = useState<MailAnalyticsSummary | null>(null);
  const [dailyVolume, setDailyVolume] = useState<DailyVolumeEntry[]>([]);
  const [dailyRecipients, setDailyRecipients] = useState<DailyVolumeEntry[]>([]);
  const [workspaceDist, setWorkspaceDist] = useState<WorkspaceDistributionEntry[]>([]);
  const [filterUsage, setFilterUsage] = useState<FilterUsageEntry[]>([]);
  const [history, setHistory] = useState<PagedResponse<MailAuditLogEntry> | null>(null);
  const [loading, setLoading] = useState(true);

  // History table filters
  const [historyPage, setHistoryPage] = useState(0);
  const [historyFilter, setHistoryFilter] = useState<MailHistoryParams>({ size: 10 });

  const days = datePreset === 'today' ? 1 : datePreset === '7days' ? 7 : 30;

  const loadChartData = useCallback(async () => {
    setLoading(true);
    try {
      const [s, dv, dr, wd, fu] = await Promise.all([
        adminGetMailAnalyticsSummary(days),
        adminGetDailyVolume(days),
        adminGetDailyRecipients(days),
        adminGetWorkspaceDistribution(days),
        adminGetFilterUsage(days),
      ]);
      setSummary(s);
      setDailyVolume(dv);
      setDailyRecipients(dr);
      setWorkspaceDist(wd);
      setFilterUsage(fu);
    } catch {
      toast.error('Failed to load analytics data');
    } finally {
      setLoading(false);
    }
  }, [days]);

  const loadHistory = useCallback(async () => {
    try {
      const result = await adminGetMailHistory({ ...historyFilter, page: historyPage });
      setHistory(result);
    } catch {
      toast.error('Failed to load mail history');
    }
  }, [historyFilter, historyPage]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    loadChartData();
  }, [loadChartData]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    loadHistory();
  }, [loadHistory]);

  const hasData = summary && summary.mailsSentPeriod > 0;

  return (
    <div className="space-y-6">
      {/* Date preset selector */}
      <div className="flex items-center gap-2">
        <span className="text-sm text-gray-600 font-medium">Period:</span>
        {([['today', 'Today'], ['7days', 'Last 7 Days'], ['30days', 'Last 30 Days']] as [DatePreset, string][]).map(
          ([key, label]) => (
            <button
              key={key}
              onClick={() => setDatePreset(key)}
              className={`px-3 py-1 text-sm rounded-md font-medium transition-colors ${
                datePreset === key
                  ? 'bg-blue-600 text-white'
                  : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
              }`}
            >
              {label}
            </button>
          ),
        )}
      </div>

      {loading && <p className="text-sm text-gray-500">Loading analytics…</p>}

      {!loading && !hasData && (
        <div className="text-center py-16 bg-white rounded-lg border border-gray-200">
          <p className="text-gray-500 text-lg">No mail activity yet.</p>
          <p className="text-gray-400 text-sm mt-1">Analytics will appear once notifications are sent.</p>
        </div>
      )}

      {!loading && hasData && (
        <>
          {/* Summary cards */}
          <div className="grid grid-cols-2 md:grid-cols-5 gap-4">
            <StatCard label="Mails Sent Today" value={summary!.mailsSentToday} />
            <StatCard label={`Mails (${days}d)`} value={summary!.mailsSentPeriod} />
            <StatCard label="Unique Recipients" value={summary!.uniqueRecipients} />
            <StatCard label="Active Workspaces" value={summary!.activeWorkspaces} />
            <StatCard label="Top Filter" value={summary!.topFilter} />
          </div>

          {/* Charts row 1 */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            {/* Daily Volume */}
            <div className="bg-white rounded-lg border border-gray-200 p-4">
              <h3 className="text-sm font-semibold text-gray-700 mb-3">Daily Mail Volume</h3>
              <ResponsiveContainer width="100%" height={220}>
                <BarChart data={dailyVolume}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="date" tick={{ fontSize: 11 }} />
                  <YAxis allowDecimals={false} />
                  <Tooltip />
                  <Bar dataKey="count" fill="#3b82f6" radius={[4, 4, 0, 0]} name="Mails Sent" />
                </BarChart>
              </ResponsiveContainer>
            </div>

            {/* Unique Recipients */}
            <div className="bg-white rounded-lg border border-gray-200 p-4">
              <h3 className="text-sm font-semibold text-gray-700 mb-3">Unique Recipients per Day</h3>
              <ResponsiveContainer width="100%" height={220}>
                <LineChart data={dailyRecipients}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="date" tick={{ fontSize: 11 }} />
                  <YAxis allowDecimals={false} />
                  <Tooltip />
                  <Line type="monotone" dataKey="count" stroke="#10b981" strokeWidth={2} name="Recipients" />
                </LineChart>
              </ResponsiveContainer>
            </div>
          </div>

          {/* Charts row 2 */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            {/* Workspace Distribution */}
            <div className="bg-white rounded-lg border border-gray-200 p-4">
              <h3 className="text-sm font-semibold text-gray-700 mb-3">Workspace Distribution</h3>
              {workspaceDist.length > 0 ? (
                <ResponsiveContainer width="100%" height={220}>
                  <PieChart>
                    <Pie data={workspaceDist} dataKey="count" nameKey="workspace"
                         cx="50%" cy="50%" outerRadius={80} label>
                      {workspaceDist.map((_, i) => (
                        <Cell key={i} fill={COLORS[i % COLORS.length]} />
                      ))}
                    </Pie>
                    <Tooltip />
                    <Legend />
                  </PieChart>
                </ResponsiveContainer>
              ) : (
                <p className="text-sm text-gray-400 text-center py-8">No data</p>
              )}
            </div>

            {/* Filter Usage */}
            <div className="bg-white rounded-lg border border-gray-200 p-4">
              <h3 className="text-sm font-semibold text-gray-700 mb-3">Most Used Filters</h3>
              {filterUsage.length > 0 ? (
                <ResponsiveContainer width="100%" height={220}>
                  <BarChart data={filterUsage} layout="vertical">
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis type="number" allowDecimals={false} />
                    <YAxis type="category" dataKey="filter" width={120} tick={{ fontSize: 11 }} />
                    <Tooltip />
                    <Bar dataKey="count" fill="#8b5cf6" radius={[0, 4, 4, 0]} name="Usage" />
                  </BarChart>
                </ResponsiveContainer>
              ) : (
                <p className="text-sm text-gray-400 text-center py-8">No data</p>
              )}
            </div>
          </div>
        </>
      )}

      {/* Mail History Table */}
      <div className="bg-white rounded-lg border border-gray-200 p-4">
        <h3 className="text-sm font-semibold text-gray-700 mb-3">Mail History</h3>

        {/* Filters */}
        <div className="flex flex-wrap gap-3 mb-4">
          <input
            type="text"
            placeholder="Recipient email"
            className="border border-gray-300 rounded-md px-3 py-1.5 text-sm w-48"
            value={historyFilter.recipientEmail ?? ''}
            onChange={(e) => {
              setHistoryFilter((f) => ({ ...f, recipientEmail: e.target.value || undefined }));
              setHistoryPage(0);
            }}
          />
          <input
            type="text"
            placeholder="Filter template"
            className="border border-gray-300 rounded-md px-3 py-1.5 text-sm w-48"
            value={historyFilter.filterTitle ?? ''}
            onChange={(e) => {
              setHistoryFilter((f) => ({ ...f, filterTitle: e.target.value || undefined }));
              setHistoryPage(0);
            }}
          />
          <select
            className="border border-gray-300 rounded-md px-3 py-1.5 text-sm"
            value={historyFilter.status ?? ''}
            onChange={(e) => {
              const val = e.target.value as 'SUCCESS' | 'FAILED' | '';
              setHistoryFilter((f) => ({ ...f, status: val || undefined }));
              setHistoryPage(0);
            }}
          >
            <option value="">All statuses</option>
            <option value="SUCCESS">Success</option>
            <option value="FAILED">Failed</option>
          </select>
        </div>

        {/* Table */}
        <div className="overflow-x-auto">
          <table className="min-w-full text-sm">
            <thead>
              <tr className="border-b border-gray-200 text-left text-xs text-gray-500 uppercase">
                <th className="py-2 px-3">Time Sent</th>
                <th className="py-2 px-3">Workspace</th>
                <th className="py-2 px-3">Recipient</th>
                <th className="py-2 px-3">Filter</th>
                <th className="py-2 px-3">Tickets</th>
                <th className="py-2 px-3">Status</th>
                <th className="py-2 px-3">Duration</th>
              </tr>
            </thead>
            <tbody>
              {history && history.content.length > 0 ? (
                history.content.map((entry) => (
                  <tr key={entry.id} className="border-b border-gray-100 hover:bg-gray-50">
                    <td className="py-2 px-3 whitespace-nowrap">
                      {new Date(entry.sentAt).toLocaleString()}
                    </td>
                    <td className="py-2 px-3">{entry.workspaceTitle ?? '—'}</td>
                    <td className="py-2 px-3">{entry.recipientEmail}</td>
                    <td className="py-2 px-3">{entry.filterTitle ?? '—'}</td>
                    <td className="py-2 px-3">{entry.ticketCount}</td>
                    <td className="py-2 px-3">
                      <span className={`inline-block px-2 py-0.5 rounded-full text-xs font-medium ${
                        entry.deliveryStatus === 'SUCCESS'
                          ? 'bg-green-100 text-green-700'
                          : 'bg-red-100 text-red-700'
                      }`}>
                        {entry.deliveryStatus}
                      </span>
                    </td>
                    <td className="py-2 px-3">
                      {entry.durationMs != null ? `${entry.durationMs}ms` : '—'}
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={7} className="py-8 text-center text-gray-400">
                    No mail history found.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        {/* Pagination */}
        {history && history.totalPages > 1 && (
          <div className="flex items-center justify-between mt-4">
            <p className="text-xs text-gray-500">
              Page {history.number + 1} of {history.totalPages} ({history.totalElements} total)
            </p>
            <div className="flex gap-2">
              <button
                disabled={history.number === 0}
                onClick={() => setHistoryPage((p) => Math.max(0, p - 1))}
                className="px-3 py-1 text-sm rounded-md border border-gray-300 disabled:opacity-40 hover:bg-gray-100"
              >
                Previous
              </button>
              <button
                disabled={history.number >= history.totalPages - 1}
                onClick={() => setHistoryPage((p) => p + 1)}
                className="px-3 py-1 text-sm rounded-md border border-gray-300 disabled:opacity-40 hover:bg-gray-100"
              >
                Next
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
