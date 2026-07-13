import React, { useEffect, useState } from 'react';
import {
  fetchSubscriptionsByWorkspace,
  fetchFilters,
  runSubscription,
  toggleSubscription,
  adminFetchWorkspace,
  type Subscription,
  type Filter,
  type Schedule,
  type WorkspaceAdmin,
} from '../services/apiService';
import { formatHourLabel } from '../services/scheduleUtils';
import { useAuth } from '../hooks/useAuth';
import { Loader2, ArrowLeft, SlidersHorizontal, Pencil, Play, Plus, Bell, Mail, Settings2, Users, PowerOff, Power } from 'lucide-react';
import toast from 'react-hot-toast';
import EditSubscriptionModal from './EditSubscriptionModal';
import SubscriptionFormModal from './SubscriptionFormModal';
import WorkspaceFormModal from './WorkspaceFormModal';

interface WorkspaceDashboardProps {
  workspaceId: string;
  onBack: () => void;
  onOpenFilterBuilder: () => void;
  /** Opens the recipient-groups management view for this workspace (admin/workspace-admin only). */
  onOpenGroupManager: () => void;
}

const WorkspaceDashboard: React.FC<WorkspaceDashboardProps> = ({ workspaceId, onBack, onOpenFilterBuilder, onOpenGroupManager }) => {
  const { isAdmin, isWorkspaceAdmin } = useAuth();
  const canManage = isAdmin || isWorkspaceAdmin;
  const [subscriptions, setSubscriptions] = useState<Subscription[]>([]);
  const [filters, setFilters] = useState<Filter[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [editingSubscription, setEditingSubscription] = useState<Subscription | null>(null);
  const [runningIds, setRunningIds] = useState<Set<string>>(new Set());
  const [togglingIds, setTogglingIds] = useState<Set<string>>(new Set());
  const [workspaceData, setWorkspaceData] = useState<WorkspaceAdmin | null>(null);
  const [isEditWorkspaceOpen, setIsEditWorkspaceOpen] = useState(false);

  const handleRunSubscription = async (sub: Subscription) => {
    setRunningIds(prev => new Set(prev).add(sub.id));
    try {
      await runSubscription(workspaceId, sub.id);
      if (sub.groupName) {
        toast.success(`Emails sent to group "${sub.groupName}"!`);
      } else {
        toast.success(`Email sent to ${sub.recipientEmail}!`);
      }
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string } } };
      toast.error(axiosErr.response?.data?.message ?? 'Failed to send email. Please try again.');
    } finally {
      setRunningIds(prev => {
        const next = new Set(prev);
        next.delete(sub.id);
        return next;
      });
    }
  };

  const handleToggleSubscription = async (sub: Subscription) => {
    setTogglingIds(prev => new Set(prev).add(sub.id));
    try {
      await toggleSubscription(workspaceId, sub.id);
      const isNowDisabled = sub.status === 'ACTIVE';
      toast.success(isNowDisabled ? 'Subscription disabled.' : 'Subscription enabled.');
      loadData();
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string } } };
      toast.error(axiosErr.response?.data?.message ?? 'Failed to toggle subscription.');
    } finally {
      setTogglingIds(prev => {
        const next = new Set(prev);
        next.delete(sub.id);
        return next;
      });
    }
  };

  const loadData = async () => {
    setIsLoading(true);
    try {
    const promises: [Promise<Subscription[]>, Promise<Filter[]>, Promise<WorkspaceAdmin>] = [
        fetchSubscriptionsByWorkspace(workspaceId),
        fetchFilters(workspaceId),
      adminFetchWorkspace(workspaceId),
      ];
      const [subsData, filtersData, wsData] = await Promise.all(promises);
      setSubscriptions(subsData);
      setFilters(filtersData);
    setWorkspaceData(wsData);
    } catch {
      toast.error('Failed to load dashboard data.');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    loadData();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [workspaceId]);

  const formatSchedule = (schedule: Schedule): string => {
    const typeLabel = schedule.type === 'DAILY' ? 'Daily' : 'Weekly (Mon)';
    const hoursLabel = schedule.hours.map(formatHourLabel).join(', ');
    return `${typeLabel} @ ${hoursLabel}`;
  };

  if (isLoading) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[60vh]">
        <div className="h-14 w-14 rounded-2xl bg-gradient-to-br from-indigo-500 to-violet-600 flex items-center justify-center shadow-xl shadow-indigo-500/30 mb-5 animate-pulse-glow">
          <Loader2 className="h-7 w-7 animate-spin text-white" />
        </div>
        <p className="text-slate-500 dark:text-slate-400 text-sm font-medium">Loading dashboard…</p>
      </div>
    );
  }

  return (
    <div className="max-w-7xl mx-auto py-8 px-4 sm:px-6 lg:px-8">

      {/* Header */}
      <div className="mb-8 flex items-center justify-between gap-4 flex-wrap animate-fade-in">
        <div className="flex items-center gap-3">
          <button
            onClick={onBack}
            className="p-2 rounded-xl hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors text-slate-500 dark:text-slate-400 hover:text-slate-700 dark:hover:text-slate-200 cursor-pointer"
            aria-label="Back to workspaces"
          >
            <ArrowLeft className="h-5 w-5" />
          </button>
          <div>
            <h1 className="text-2xl font-bold text-slate-900 dark:text-white">
              {workspaceData?.title ?? 'Dashboard'}
            </h1>
            <div className="flex items-center gap-2 mt-0.5">
              <p className="text-slate-400 dark:text-slate-500 text-sm">Manage your email subscriptions</p>
              {workspaceData?.connectivityStatus === 'ONLINE' && (
                <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold bg-green-50 dark:bg-green-500/10 text-green-700 dark:text-green-400 border border-green-200 dark:border-green-500/25">
                  <span className="h-1.5 w-1.5 rounded-full bg-green-500" />
                  Online
                </span>
              )}
              {workspaceData?.connectivityStatus === 'OFFLINE' && (
                <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold bg-red-50 dark:bg-red-500/10 text-red-700 dark:text-red-400 border border-red-200 dark:border-red-500/25">
                  <span className="h-1.5 w-1.5 rounded-full bg-red-500" />
                  Unreachable
                </span>
              )}
              {workspaceData?.connectivityStatus === 'UNKNOWN' && (
                <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold bg-slate-100 dark:bg-slate-700/60 text-slate-600 dark:text-slate-300 border border-slate-200 dark:border-slate-600">
                  <span className="h-1.5 w-1.5 rounded-full bg-slate-400" />
                  Unknown
                </span>
              )}
            </div>
          </div>
        </div>

        <div className="flex items-center gap-2.5 flex-shrink-0">
          {canManage && (
            <button
              onClick={() => setIsEditWorkspaceOpen(true)}
              className="inline-flex items-center gap-2 px-4 py-2 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-medium text-slate-600 dark:text-slate-300 bg-white dark:bg-slate-800 hover:bg-slate-50 dark:hover:bg-slate-700/60 hover:border-violet-300 dark:hover:border-violet-600 hover:text-violet-600 dark:hover:text-violet-400 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet-500 transition-all shadow-sm cursor-pointer"
            >
              <Settings2 className="h-4 w-4" />
              Edit Workspace
            </button>
          )}
          {canManage && (
            <button
              onClick={onOpenGroupManager}
              className="inline-flex items-center gap-2 px-4 py-2 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-medium text-slate-600 dark:text-slate-300 bg-white dark:bg-slate-800 hover:bg-slate-50 dark:hover:bg-slate-700/60 hover:border-teal-300 dark:hover:border-teal-600 hover:text-teal-600 dark:hover:text-teal-400 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-teal-500 transition-all shadow-sm cursor-pointer"
            >
              <Users className="h-4 w-4" />
              Groups
            </button>
          )}
          <button
            onClick={onOpenFilterBuilder}
            className="inline-flex items-center gap-2 px-4 py-2 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-medium text-slate-600 dark:text-slate-300 bg-white dark:bg-slate-800 hover:bg-slate-50 dark:hover:bg-slate-700/60 hover:border-indigo-300 dark:hover:border-indigo-600 hover:text-indigo-600 dark:hover:text-indigo-400 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 transition-all shadow-sm cursor-pointer"
          >
            <SlidersHorizontal className="h-4 w-4" />
            {canManage ? 'Manage Filters' : 'Browse Filters'}
          </button>
          <button
            onClick={() => setIsCreateModalOpen(true)}
            className="inline-flex items-center gap-2 px-4 py-2 border border-transparent rounded-xl text-sm font-semibold text-white bg-indigo-600 hover:bg-indigo-500 dark:bg-indigo-500 dark:hover:bg-indigo-400 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2 dark:focus-visible:ring-offset-slate-950 transition-all shadow-sm shadow-indigo-500/20 cursor-pointer"
          >
            <Plus className="h-4 w-4" />
            New Subscription
          </button>
        </div>
      </div>

      {canManage && (
        <WorkspaceFormModal
          isOpen={isEditWorkspaceOpen}
          workspace={workspaceData}
          onClose={() => setIsEditWorkspaceOpen(false)}
          onSuccess={(saved) => {
            setWorkspaceData(saved);
            setIsEditWorkspaceOpen(false);
          }}
        />
      )}

      <SubscriptionFormModal
        isOpen={isCreateModalOpen}
        workspaceId={workspaceId}
        filters={filters}
        canManage={canManage}
        onClose={() => setIsCreateModalOpen(false)}
        onSuccess={() => { setIsCreateModalOpen(false); loadData(); }}
      />

      {editingSubscription && (
        <EditSubscriptionModal
          isOpen={true}
          subscription={editingSubscription}
          workspaceId={workspaceId}
          onClose={() => setEditingSubscription(null)}
          onSuccess={() => { setEditingSubscription(null); loadData(); }}
        />
      )}

      {/* Subscriptions table card */}
      <div className="bg-white dark:bg-slate-900 rounded-2xl border border-slate-100 dark:border-slate-800 shadow-sm overflow-hidden animate-slide-up">
        <div className="px-6 py-4 border-b border-slate-100 dark:border-slate-800 flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <div className="h-8 w-8 rounded-lg bg-indigo-50 dark:bg-indigo-500/10 flex items-center justify-center">
              <Bell className="h-4 w-4 text-indigo-600 dark:text-indigo-400" />
            </div>
            <h2 className="text-sm font-semibold text-slate-900 dark:text-white">
              {canManage ? 'All Subscriptions' : 'My Subscriptions'}
            </h2>
          </div>
          {subscriptions.length > 0 && (
            <span className="text-xs text-slate-500 dark:text-slate-400 bg-slate-50 dark:bg-slate-800 border border-slate-100 dark:border-slate-700 px-2.5 py-1 rounded-full font-medium">
              {subscriptions.length} subscription{subscriptions.length !== 1 ? 's' : ''}
              {subscriptions.filter(s => s.status === 'DISABLED').length > 0 && (
                <span className="ml-1 text-amber-500 dark:text-amber-400">
                  · {subscriptions.filter(s => s.status === 'DISABLED').length} disabled
                </span>
              )}
            </span>
          )}
        </div>

        {subscriptions.length === 0 ? (
          <div className="p-14 text-center">
            <div className="h-14 w-14 rounded-2xl bg-slate-50 dark:bg-slate-800 flex items-center justify-center mx-auto mb-5">
              <Mail className="h-7 w-7 text-slate-300 dark:text-slate-600" />
            </div>
            <p className="text-slate-600 dark:text-slate-400 text-sm mb-1 font-medium">No subscriptions yet</p>
            <p className="text-slate-400 dark:text-slate-500 text-xs mb-6">Create your first subscription to start receiving email digests.</p>
            <button
              onClick={() => setIsCreateModalOpen(true)}
              className="inline-flex items-center gap-2 px-4 py-2 border border-transparent rounded-xl text-sm font-semibold text-white bg-indigo-600 hover:bg-indigo-500 dark:bg-indigo-500 dark:hover:bg-indigo-400 transition-all shadow-sm shadow-indigo-500/20 cursor-pointer"
            >
              <Plus className="h-4 w-4" />
              Create your first subscription
            </button>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-slate-100 dark:divide-slate-800">
              <thead>
                <tr className="bg-slate-50/80 dark:bg-slate-800/50">
                  {canManage && (
                    <th className="px-6 py-3 text-left text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider">
                      Recipient
                    </th>
                  )}
                  <th className="px-6 py-3 text-left text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider">
                    Filter
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider">
                    Schedule
                  </th>
                  <th className="px-6 py-3" />
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-50 dark:divide-slate-800/60">
                    {subscriptions.map((sub, idx) => (
                    <tr
                      key={sub.id}
                      style={{ animationDelay: `${idx * 40}ms` }}
                      className={`animate-fade-in transition-colors ${
                        sub.status === 'DISABLED'
                          ? 'opacity-50 bg-slate-50/80 dark:bg-slate-800/30 hover:opacity-70'
                          : 'hover:bg-slate-50/60 dark:hover:bg-slate-800/40'
                      }`}
                    >
                      {canManage && (
                        <td className="px-6 py-4 whitespace-nowrap">
                          {sub.groupId ? (
                            <div className="flex items-center gap-2">
                              <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-teal-50 dark:bg-teal-500/10 text-teal-700 dark:text-teal-300 text-xs font-semibold border border-teal-100 dark:border-teal-500/20">
                                <Users className="h-3 w-3" />
                                {sub.groupName}
                              </span>
                              {sub.groupMemberCount != null && (
                                <span className="text-xs text-slate-400 dark:text-slate-500">
                                  {sub.groupMemberCount} member{sub.groupMemberCount !== 1 ? 's' : ''}
                                </span>
                              )}
                            </div>
                          ) : (
                            <span className="text-sm font-medium text-slate-900 dark:text-slate-200">{sub.recipientEmail}</span>
                          )}
                        </td>
                      )}
                      <td className="px-6 py-4 whitespace-nowrap">
                        <div className="flex items-center gap-2">
                          <span className={`inline-flex items-center px-2.5 py-1 rounded-lg text-xs font-medium border ${
                            sub.status === 'DISABLED'
                              ? 'bg-slate-50 dark:bg-slate-700/40 text-slate-400 dark:text-slate-500 border-slate-200 dark:border-slate-600 line-through'
                              : 'bg-indigo-50 dark:bg-indigo-500/10 text-indigo-700 dark:text-indigo-300 border-indigo-100 dark:border-indigo-500/20'
                          }`}>
                            {sub.filterTitle}
                          </span>
                          {sub.status === 'DISABLED' && (
                            <span className="inline-flex items-center px-2 py-0.5 rounded-md bg-amber-50 dark:bg-amber-500/10 text-amber-600 dark:text-amber-400 text-xs font-semibold border border-amber-100 dark:border-amber-500/20">
                              Disabled
                            </span>
                          )}
                        </div>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <span className="text-sm text-slate-500 dark:text-slate-400">{formatSchedule(sub.schedule)}</span>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-right">
                        <div className="inline-flex items-center gap-2">
                          {canManage && sub.status !== 'DISABLED' && (
                            <button
                              onClick={() => handleRunSubscription(sub)}
                              disabled={runningIds.has(sub.id)}
                              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium text-emerald-700 dark:text-emerald-400 bg-emerald-50 dark:bg-emerald-500/10 border border-emerald-100 dark:border-emerald-500/20 hover:bg-emerald-100 dark:hover:bg-emerald-500/20 disabled:opacity-50 disabled:cursor-not-allowed transition-colors cursor-pointer"
                              title="Send email now"
                            >
                              {runningIds.has(sub.id)
                                ? <Loader2 className="h-3.5 w-3.5 animate-spin" />
                                : <Play className="h-3.5 w-3.5" />
                              }
                              Run
                            </button>
                          )}
                          <button
                            onClick={() => handleToggleSubscription(sub)}
                            disabled={togglingIds.has(sub.id)}
                            className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium border transition-colors cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed ${
                              sub.status === 'DISABLED'
                                ? 'text-emerald-700 dark:text-emerald-400 bg-emerald-50 dark:bg-emerald-500/10 border-emerald-100 dark:border-emerald-500/20 hover:bg-emerald-100 dark:hover:bg-emerald-500/20'
                                : 'text-amber-700 dark:text-amber-400 bg-amber-50 dark:bg-amber-500/10 border-amber-100 dark:border-amber-500/20 hover:bg-amber-100 dark:hover:bg-amber-500/20'
                            }`}
                            title={sub.status === 'DISABLED' ? 'Enable subscription' : 'Disable subscription'}
                          >
                            {togglingIds.has(sub.id)
                              ? <Loader2 className="h-3.5 w-3.5 animate-spin" />
                              : sub.status === 'DISABLED'
                                ? <Power className="h-3.5 w-3.5" />
                                : <PowerOff className="h-3.5 w-3.5" />
                            }
                            {sub.status === 'DISABLED' ? 'Enable' : 'Disable'}
                          </button>
                          <button
                            onClick={() => setEditingSubscription(sub)}
                            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium text-slate-600 dark:text-slate-300 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 hover:bg-slate-50 dark:hover:bg-slate-700/60 hover:border-indigo-200 dark:hover:border-indigo-700 hover:text-indigo-600 dark:hover:text-indigo-400 transition-colors cursor-pointer"
                          >
                            <Pencil className="h-3.5 w-3.5" />
                            Edit
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
};

export default WorkspaceDashboard;
