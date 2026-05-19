import React, { useEffect, useState } from 'react';
import {
  fetchSubscriptionsByWorkspace,
  fetchFilters,
  runSubscription,
  type Subscription,
  type Filter,
  type Schedule,
} from '../services/apiService';
import { useAuth } from '../hooks/useAuth';
import { Loader2, ArrowLeft, SlidersHorizontal, Pencil, Play, Plus } from 'lucide-react';
import toast from 'react-hot-toast';
import EditSubscriptionModal from './EditSubscriptionModal';
import SubscriptionFormModal from './SubscriptionFormModal';

interface WorkspaceDashboardProps {
  workspaceId: string;
  onBack: () => void;
  onOpenFilterBuilder: () => void;
}

const WorkspaceDashboard: React.FC<WorkspaceDashboardProps> = ({ workspaceId, onBack, onOpenFilterBuilder }) => {
  const { isAdmin } = useAuth();
  const [subscriptions, setSubscriptions] = useState<Subscription[]>([]);
  const [filters, setFilters] = useState<Filter[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);

  // Create subscription modal
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);

  // Edit subscription modal
  const [editingSubscription, setEditingSubscription] = useState<Subscription | null>(null);

  // Per-row run loading state (tracks subscription IDs currently being run)
  const [runningIds, setRunningIds] = useState<Set<string>>(new Set());

  const handleRunSubscription = async (sub: Subscription) => {
    setRunningIds(prev => new Set(prev).add(sub.id));
    try {
      await runSubscription(workspaceId, sub.id);
      toast.success(`Email sent to ${sub.recipientEmail}!`);
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

  const loadData = async () => {
    setIsLoading(true);
    try {
      const [subsData, filtersData] = await Promise.all([
        fetchSubscriptionsByWorkspace(workspaceId),
        fetchFilters(workspaceId),
      ]);
      setSubscriptions(subsData);
      setFilters(filtersData);
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

  const formatHour = (h: number) => `${String(h).padStart(2, '0')}:00`;

  const formatSchedule = (schedule: Schedule): string => {
    const typeLabel = schedule.type === 'DAILY' ? 'Daily' : 'Weekly (Mon)';
    const hoursLabel = schedule.hours.map(formatHour).join(', ');
    return `${typeLabel} @ ${hoursLabel}`;
  };

  if (isLoading) {
    return (
      <div className="flex flex-col items-center justify-center min-h-screen">
        <Loader2 className="h-8 w-8 animate-spin text-blue-500 mb-4" />
        <p className="text-gray-600">Loading dashboard...</p>
      </div>
    );
  }

  return (
    <div className="max-w-7xl mx-auto py-8 px-4 sm:px-6 lg:px-8">

      {/* Header */}
      <div className="mb-8 flex items-center justify-between gap-4 flex-wrap">
        <div className="flex items-center">
          <button
            onClick={onBack}
            className="mr-4 p-2 rounded-full hover:bg-gray-200 transition-colors"
            aria-label="Back to workspaces"
          >
            <ArrowLeft className="h-6 w-6 text-gray-600" />
          </button>
          <h1 className="text-3xl font-bold text-gray-900">Workspace Dashboard</h1>
        </div>

        {/* Action toolbar */}
        <div className="flex items-center gap-3 flex-shrink-0">
          <button
            onClick={onOpenFilterBuilder}
            className="inline-flex items-center gap-2 px-4 py-2 border border-gray-300 rounded-lg shadow-sm text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 hover:border-blue-300 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 transition-all"
          >
            <SlidersHorizontal className="h-4 w-4" />
            {isAdmin ? 'Manage Filter Templates' : 'Browse Filter Templates'}
          </button>
          <button
            onClick={() => setIsCreateModalOpen(true)}
            className="inline-flex items-center gap-2 px-4 py-2 border border-transparent rounded-lg shadow-sm text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 transition-all"
          >
            <Plus className="h-4 w-4" />
            Create New Subscription
          </button>
        </div>
      </div>

      {/* Create subscription modal */}
      <SubscriptionFormModal
        isOpen={isCreateModalOpen}
        workspaceId={workspaceId}
        filters={filters}
        onClose={() => setIsCreateModalOpen(false)}
        onSuccess={() => {
          setIsCreateModalOpen(false);
          loadData();
        }}
      />

      {/* Edit subscription modal */}
      {editingSubscription && (
        <EditSubscriptionModal
          isOpen={true}
          subscription={editingSubscription}
          workspaceId={workspaceId}
          onClose={() => setEditingSubscription(null)}
          onSuccess={() => {
            setEditingSubscription(null);
            loadData();
          }}
        />
      )}

      {/* Current Subscriptions */}
      <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
        <div className="px-6 py-5 border-b border-gray-200 bg-gray-50">
          <h2 className="text-lg font-medium text-gray-900">Current Subscriptions</h2>
        </div>
        <div className="overflow-x-auto">
          {subscriptions.length === 0 ? (
            <div className="p-12 text-center">
              <p className="text-gray-500 mb-4">No active subscriptions found for this workspace.</p>
              <button
                onClick={() => setIsCreateModalOpen(true)}
                className="inline-flex items-center gap-2 px-4 py-2 border border-transparent rounded-lg text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 transition-colors"
              >
                <Plus className="h-4 w-4" />
                Create your first subscription
              </button>
            </div>
          ) : (
            <table className="min-w-full divide-y divide-gray-200">
              <thead className="bg-gray-50">
                <tr>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Recipient Email
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Filter
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Schedule
                  </th>
                  <th className="px-6 py-3" />
                </tr>
              </thead>
              <tbody className="bg-white divide-y divide-gray-200">
                {subscriptions.map(sub => (
                  <tr key={sub.id} className="hover:bg-gray-50 transition-colors">
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                      {sub.recipientEmail}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                      {sub.filterTitle}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                      {formatSchedule(sub.schedule)}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-right">
                      <div className="inline-flex items-center gap-2">
                        {isAdmin && (
                          <button
                            onClick={() => handleRunSubscription(sub)}
                            disabled={runningIds.has(sub.id)}
                            className="inline-flex items-center gap-1.5 px-3 py-1.5 border border-gray-300 rounded-md text-xs font-medium text-gray-700 bg-white hover:bg-green-50 hover:border-green-400 hover:text-green-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
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
                          onClick={() => setEditingSubscription(sub)}
                          className="inline-flex items-center gap-1.5 px-3 py-1.5 border border-gray-300 rounded-md text-xs font-medium text-gray-700 bg-white hover:bg-gray-50 hover:border-blue-300 transition-colors"
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
          )}
        </div>
      </div>

    </div>
  );
};

export default WorkspaceDashboard;
