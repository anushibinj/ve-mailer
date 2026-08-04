import React, { Suspense, lazy, useEffect, useMemo, useState } from 'react';
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
import {
  ArrowLeft, SlidersHorizontal, Pencil, Eye, Play, Plus, Bell, Mail,
  Settings2, Users, PowerOff, Power, ChevronUp, ChevronDown, ChevronsUpDown, Info
} from 'lucide-react';
import toast from 'react-hot-toast';
import LoadingPlaceholder from './LoadingPlaceholder';
import {
  Button, Badge, ConnectivityBadge, Card, CardHeader,
  SkeletonDashboard, EmptyState, SearchInput, PageHeader, SectionHeader, TableActionButton
} from './ui';

const EditSubscriptionModal = lazy(() => import('./EditSubscriptionModal'));
const SubscriptionFormModal = lazy(() => import('./SubscriptionFormModal'));
const WorkspaceFormModal = lazy(() => import('./WorkspaceFormModal'));

interface WorkspaceDashboardProps {
  workspaceId: string;
  onBack: () => void;
  onOpenFilterBuilder: () => void;
  onOpenGroupManager: () => void;
}

type SortKey = 'filter' | 'schedule' | 'recipient' | 'status';
type SortDir = 'asc' | 'desc';

function SortIcon({ active, dir }: { active: boolean; dir: SortDir }) {
  if (!active) return <ChevronsUpDown className="h-3 w-3 text-slate-400 inline ml-0.5" />;
  return dir === 'asc'
    ? <ChevronUp className="h-3 w-3 text-indigo-600 dark:text-indigo-400 inline ml-0.5" />
    : <ChevronDown className="h-3 w-3 text-indigo-600 dark:text-indigo-400 inline ml-0.5" />;
}

function SortableHeader({
  label, sortKey, currentKey, currentDir, onSort
}: {
  label: string;
  sortKey: SortKey;
  currentKey: SortKey;
  currentDir: SortDir;
  onSort: (k: SortKey) => void;
}) {
  return (
    <th className="px-4 py-3 text-left">
      <button
        type="button"
        onClick={() => onSort(sortKey)}
        className="inline-flex items-center text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider hover:text-slate-700 dark:hover:text-slate-200 transition-colors cursor-pointer"
      >
        {label}
        <SortIcon active={currentKey === sortKey} dir={currentDir} />
      </button>
    </th>
  );
}

const WorkspaceDashboard: React.FC<WorkspaceDashboardProps> = ({
  workspaceId, onBack, onOpenFilterBuilder, onOpenGroupManager
}) => {
  const { isAdmin, isWorkspaceAdmin, user } = useAuth();
  const canManage = isAdmin || isWorkspaceAdmin;

  const [subscriptions, setSubscriptions] = useState<Subscription[]>([]);
  const [filters, setFilters] = useState<Filter[]>([]);
  const [subscribableFilters, setSubscribableFilters] = useState<Filter[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [editingSubscription, setEditingSubscription] = useState<Subscription | null>(null);
  const [runningIds, setRunningIds] = useState<Set<string>>(new Set());
  const [togglingIds, setTogglingIds] = useState<Set<string>>(new Set());
  const [workspaceData, setWorkspaceData] = useState<WorkspaceAdmin | null>(null);
  const [isEditWorkspaceOpen, setIsEditWorkspaceOpen] = useState(false);
  // Only global ADMINs and the WORKSPACE_ADMIN(s) who administer this specific workspace may
  // edit it — distinct from `canManage`, which governs broader dashboard actions.
  const canEditThisWorkspace = isAdmin || !!workspaceData?.myWorkspaceAdmin;

  // Table controls
  const [search, setSearch] = useState('');
  const [sortKey, setSortKey] = useState<SortKey>('filter');
  const [sortDir, setSortDir] = useState<SortDir>('asc');
  const [statusFilter, setStatusFilter] = useState<'all' | 'active' | 'disabled' | 'mine'>('all');

  const handleRunSubscription = async (sub: Subscription) => {
    setRunningIds(prev => new Set(prev).add(sub.id));
    try {
      await runSubscription(workspaceId, sub.id);
      toast.success(sub.groupName ? `Emails sent to group "${sub.groupName}"!` : `Email sent to ${sub.recipientEmail}!`);
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string } } };
      toast.error(axiosErr.response?.data?.message ?? 'Failed to send email. Please try again.');
    } finally {
      setRunningIds(prev => { const n = new Set(prev); n.delete(sub.id); return n; });
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
      setTogglingIds(prev => { const n = new Set(prev); n.delete(sub.id); return n; });
    }
  };

  const loadData = async () => {
    setIsLoading(true);
    try {
      const [subsData, filtersData, subscribableFiltersData, wsData] = await Promise.all([
        fetchSubscriptionsByWorkspace(workspaceId),
        fetchFilters(workspaceId),
        fetchFilters(workspaceId, { subscribableOnly: true }),
        adminFetchWorkspace(workspaceId),
      ]);
      setSubscriptions(subsData);
      setFilters(filtersData);
      setSubscribableFilters(subscribableFiltersData);
      setWorkspaceData(wsData);
    } catch {
      toast.error('Failed to load dashboard data.');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadData();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [workspaceId]);

  const formatSchedule = (schedule: Schedule): string => {
    const typeLabel = schedule.type === 'DAILY' ? 'Daily' : 'Weekly (Mon)';
    const hoursLabel = schedule.hours.map(formatHourLabel).join(', ');
    return `${typeLabel} @ ${hoursLabel}`;
  };

  const handleSort = (key: SortKey) => {
    if (sortKey === key) {
      setSortDir(d => d === 'asc' ? 'desc' : 'asc');
    } else {
      setSortKey(key);
      setSortDir('asc');
    }
  };

  const processedSubs = useMemo(() => {
    let result = [...subscriptions];

    // Status filter
    if (statusFilter === 'active')   result = result.filter(s => s.status !== 'DISABLED');
    if (statusFilter === 'disabled') result = result.filter(s => s.status === 'DISABLED');
    if (statusFilter === 'mine')     result = result.filter(s => s.recipientEmail?.toLowerCase() === user?.email?.toLowerCase());

    // Search
    if (search.trim()) {
      const q = search.toLowerCase();
      result = result.filter(s =>
        s.filterTitle?.toLowerCase().includes(q) ||
        s.recipientEmail?.toLowerCase().includes(q) ||
        s.groupName?.toLowerCase().includes(q)
      );
    }

    // Sort
    result.sort((a, b) => {
      let cmp = 0;
      if (sortKey === 'filter')    cmp = (a.filterTitle ?? '').localeCompare(b.filterTitle ?? '');
      if (sortKey === 'schedule')  cmp = (a.schedule.type + a.schedule.hours.join('')).localeCompare(b.schedule.type + b.schedule.hours.join(''));
      if (sortKey === 'recipient') cmp = (a.recipientEmail ?? a.groupName ?? '').localeCompare(b.recipientEmail ?? b.groupName ?? '');
      if (sortKey === 'status')    cmp = (a.status ?? '').localeCompare(b.status ?? '');
      return sortDir === 'asc' ? cmp : -cmp;
    });

    return result;
  }, [subscriptions, search, sortKey, sortDir, statusFilter, user]);

  const activeCount   = subscriptions.filter(s => s.status !== 'DISABLED').length;
  const disabledCount = subscriptions.filter(s => s.status === 'DISABLED').length;

  if (isLoading) {
    return (
      <div className="max-w-7xl mx-auto py-8 px-4 sm:px-6 lg:px-8">
        <SkeletonDashboard />
      </div>
    );
  }

  return (
    <div className="max-w-7xl mx-auto py-8 px-4 sm:px-6 lg:px-8">
      {/* Modals */}
      {canEditThisWorkspace && workspaceData && (
        <Suspense fallback={null}>
          <WorkspaceFormModal
            isOpen={isEditWorkspaceOpen}
            workspace={workspaceData}
            onClose={() => setIsEditWorkspaceOpen(false)}
            onSuccess={(saved) => { setWorkspaceData(saved); setIsEditWorkspaceOpen(false); }}
            onRefetched={(saved) => setWorkspaceData(saved)}
          />
        </Suspense>
      )}
      <Suspense fallback={null}>
        <SubscriptionFormModal
          isOpen={isCreateModalOpen}
          workspaceId={workspaceId}
          filters={subscribableFilters}
          canManage={canManage}
          onClose={() => setIsCreateModalOpen(false)}
          onSuccess={() => { setIsCreateModalOpen(false); loadData(); }}
        />
      </Suspense>
      {editingSubscription && (
        <Suspense fallback={<LoadingPlaceholder message="Loading subscription details..." />}>
          <EditSubscriptionModal
            isOpen={true}
            subscription={editingSubscription}
            workspaceId={workspaceId}
            filters={filters}
            readOnly={!canManage && !!editingSubscription.groupId}
            onClose={() => setEditingSubscription(null)}
            onSuccess={() => { setEditingSubscription(null); loadData(); }}
          />
        </Suspense>
      )}

      {/* Page header */}
      <div className="mb-8 animate-fade-in">
        <div className="flex items-center gap-2 mb-4">
          <button
            onClick={onBack}
            className="p-1.5 rounded-lg hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors text-slate-500 dark:text-slate-400 cursor-pointer"
            aria-label="Back to workspaces"
          >
            <ArrowLeft className="h-4 w-4" />
          </button>
        </div>
        <PageHeader
          title={workspaceData?.title ?? 'Dashboard'}
          description="Manage your email notification subscriptions"
          badge={workspaceData && (
            <>
              <ConnectivityBadge status={workspaceData.connectivityStatus} />
              {workspaceData.myWorkspaceAdmin && <Badge variant="violet">Workspace Admin</Badge>}
            </>
          )}
          actions={
            <>
              {canEditThisWorkspace && (
                <Button variant="secondary" size="sm" icon={<Settings2 className="h-4 w-4" />} onClick={() => setIsEditWorkspaceOpen(true)}>
                  Edit workspace
                </Button>
              )}
              {canManage && (
                <Button variant="secondary" size="sm" icon={<Users className="h-4 w-4" />} onClick={onOpenGroupManager}>
                  Groups
                </Button>
              )}
              <Button variant="secondary" size="sm" icon={<SlidersHorizontal className="h-4 w-4" />} onClick={onOpenFilterBuilder}>
                {canManage ? 'Manage Filters' : 'Browse Filters'}
              </Button>
              <Button variant="primary" size="sm" icon={<Plus className="h-4 w-4" />} onClick={() => setIsCreateModalOpen(true)}>
                New Subscription
              </Button>
            </>
          }
        />
      </div>

      {/* Stats chips */}
      {subscriptions.length > 0 && (
        <div className="flex items-center gap-3 mb-6 animate-slide-up">
          <span className="text-xs text-slate-500 dark:text-slate-400 font-medium">
            {subscriptions.length} subscription{subscriptions.length !== 1 ? 's' : ''}
          </span>
          {activeCount > 0 && (
            <Badge variant="success" dot>{activeCount} active</Badge>
          )}
          {disabledCount > 0 && (
            <Badge variant="warning">{disabledCount} disabled</Badge>
          )}
        </div>
      )}

      {/* Subscriptions table card */}
      <Card noPadding className="animate-slide-up">
        <CardHeader>
          <SectionHeader
            icon={<Bell className="h-4 w-4" />}
            title={canManage ? 'All Subscriptions' : 'My Subscriptions'}
            count={processedSubs.length !== subscriptions.length ? processedSubs.length : subscriptions.length}
          />

          {subscriptions.length > 0 && (
            <div className="flex items-center gap-2 flex-shrink-0">
              {/* Status filter chips */}
              <div className="hidden sm:flex items-center gap-1 text-xs">
                {(['all', 'active', 'disabled'] as const).map(f => (
                  <button
                    key={f}
                    type="button"
                    onClick={() => setStatusFilter(f)}
                    className={`px-2.5 py-1 rounded-lg font-medium transition-colors cursor-pointer ${
                      statusFilter === f
                        ? 'bg-indigo-50 dark:bg-indigo-500/10 text-indigo-700 dark:text-indigo-300'
                        : 'text-slate-500 dark:text-slate-400 hover:bg-slate-50 dark:hover:bg-slate-800'
                    }`}
                  >
                    {f.charAt(0).toUpperCase() + f.slice(1)}
                  </button>
                ))}
                {canManage && (
                  <button
                    type="button"
                    onClick={() => setStatusFilter('mine')}
                    className={`px-2.5 py-1 rounded-lg font-medium transition-colors cursor-pointer ${
                      statusFilter === 'mine'
                        ? 'bg-violet-50 dark:bg-violet-500/10 text-violet-700 dark:text-violet-300'
                        : 'text-slate-500 dark:text-slate-400 hover:bg-slate-50 dark:hover:bg-slate-800'
                    }`}
                  >
                    Mine
                  </button>
                )}
              </div>
              <SearchInput
                value={search}
                onChange={e => setSearch(e.target.value)}
                onClear={() => setSearch('')}
                placeholder="Filter..."
                className="w-40"
              />
            </div>
          )}
        </CardHeader>

        {processedSubs.length === 0 && subscriptions.length === 0 ? (
          <EmptyState
            icon={<Mail className="h-7 w-7" />}
            title="No subscriptions yet"
            description="Create your first subscription to start receiving email digests."
            action={{ label: 'New Subscription', icon: <Plus className="h-3.5 w-3.5" />, onClick: () => setIsCreateModalOpen(true) }}
          />
        ) : processedSubs.length === 0 ? (
          <EmptyState
            icon={<Mail className="h-7 w-7" />}
            title="No subscriptions match your filter"
            description="Try a different search or status filter."
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-full">
              <thead>
                <tr className="bg-slate-50/80 dark:bg-slate-800/40 border-b border-slate-100 dark:border-slate-800">
                  <th className="px-4 py-3 text-left text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider">Actions</th>
                  <SortableHeader label="Recipient" sortKey="recipient" currentKey={sortKey} currentDir={sortDir} onSort={handleSort} />
                  <SortableHeader label="Filter" sortKey="filter" currentKey={sortKey} currentDir={sortDir} onSort={handleSort} />
                  <SortableHeader label="Schedule" sortKey="schedule" currentKey={sortKey} currentDir={sortDir} onSort={handleSort} />
                  <SortableHeader label="Status" sortKey="status" currentKey={sortKey} currentDir={sortDir} onSort={handleSort} />
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-50 dark:divide-slate-800/60">
                {processedSubs.map((sub, idx) => (
                  <tr
                    key={sub.id}
                    style={{ animationDelay: `${idx * 30}ms` }}
                    className={`animate-fade-in transition-colors ${
                      sub.status === 'DISABLED'
                        ? 'opacity-60 bg-slate-50/60 dark:bg-slate-800/20 hover:opacity-80'
                        : 'hover:bg-slate-50/60 dark:hover:bg-slate-800/30'
                    }`}
                  >
                    <td className="px-4 py-3.5 whitespace-nowrap">
                      <div className="inline-flex items-center gap-1.5">
                        {canManage && sub.status !== 'DISABLED' && (
                          <TableActionButton
                            variant="success"
                            loading={runningIds.has(sub.id)}
                            icon={<Play className="h-3.5 w-3.5" />}
                            onClick={() => handleRunSubscription(sub)}
                            label="Send email now"
                          />
                        )}
                        {!canManage && sub.groupId ? (
                          <TableActionButton
                            variant="secondary"
                            icon={<Info className="h-3.5 w-3.5" />}
                            label="Contact your Workspace Admin for changes to this Group Subscription"
                          />
                        ) : (
                          <TableActionButton
                            variant={sub.status === 'DISABLED' ? 'success' : 'secondary'}
                            loading={togglingIds.has(sub.id)}
                            icon={sub.status === 'DISABLED' ? <Power className="h-3.5 w-3.5" /> : <PowerOff className="h-3.5 w-3.5" />}
                            onClick={() => handleToggleSubscription(sub)}
                            label={sub.status === 'DISABLED' ? 'Enable subscription' : 'Disable subscription'}
                          />
                        )}
                        <TableActionButton
                          variant="secondary"
                          icon={!canManage && sub.groupId ? <Eye className="h-3.5 w-3.5" /> : <Pencil className="h-3.5 w-3.5" />}
                          onClick={() => setEditingSubscription(sub)}
                          label={!canManage && sub.groupId ? 'View Subscription' : 'Edit Subscription'}
                        />
                      </div>
                    </td>
                    <td className="px-4 py-3.5 whitespace-nowrap">
                      {sub.groupId ? (
                        <div className="flex items-center gap-2">
                          <Badge variant="teal" className="gap-1">
                            <Users className="h-3 w-3" />
                            {sub.groupName}
                          </Badge>
                          {sub.groupMemberCount != null && (
                            <span className="text-xs text-slate-400 dark:text-slate-500 tabular-nums">
                              {sub.groupMemberCount}m
                            </span>
                          )}
                        </div>
                      ) : (
                        <span className="text-sm font-medium text-slate-900 dark:text-slate-200 max-w-[180px] truncate block" title={sub.recipientEmail ?? ''}>
                          {sub.recipientEmail}
                        </span>
                      )}
                    </td>
                    <td className="px-4 py-3.5 whitespace-nowrap">
                      <Badge variant={sub.status === 'DISABLED' ? 'neutral' : 'brand'} className={sub.status === 'DISABLED' ? 'line-through' : ''}>
                        {sub.filterTitle}
                      </Badge>
                    </td>
                    <td className="px-4 py-3.5 whitespace-nowrap">
                      <span className="text-sm text-slate-500 dark:text-slate-400 tabular-nums">{formatSchedule(sub.schedule)}</span>
                    </td>
                    <td className="px-4 py-3.5 whitespace-nowrap">
                      {sub.status === 'DISABLED' ? (
                        <Badge variant="warning">Disabled</Badge>
                      ) : sub.status === 'ACTIVE' ? (
                        <Badge variant="success" dot>Active</Badge>
                      ) : (
                        <Badge variant="neutral">{sub.status ?? 'Unknown'}</Badge>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  );
};

export default WorkspaceDashboard;
