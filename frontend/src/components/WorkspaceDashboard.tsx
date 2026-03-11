import React, { useEffect, useState } from 'react';
import {
  fetchSubscriptionsByWorkspace,
  fetchFilters,
  requestSubscription,
  runSubscription,
  type Subscription,
  type Filter,
  type SubscriptionRequestPayload
} from '../services/apiService';
import { Loader2, ArrowLeft, SlidersHorizontal, Pencil, Play } from 'lucide-react';
import toast from 'react-hot-toast';
import OtpModal from './OtpModal';
import EditSubscriptionModal from './EditSubscriptionModal';

interface WorkspaceDashboardProps {
  workspaceId: string;
  onBack: () => void;
  onOpenFilterBuilder: () => void;
}

const WorkspaceDashboard: React.FC<WorkspaceDashboardProps> = ({ workspaceId, onBack, onOpenFilterBuilder }) => {
  const [subscriptions, setSubscriptions] = useState<Subscription[]>([]);
  const [filters, setFilters] = useState<Filter[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);

  // Subscribe form state
  const [email, setEmail] = useState('');
  const [selectedFilter, setSelectedFilter] = useState('');
  const [frequency, setFrequency] = useState('HOURLY');
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Subscribe OTP modal state
  const [isOtpModalOpen, setIsOtpModalOpen] = useState(false);
  const [pendingEmail, setPendingEmail] = useState('');

  // Edit subscription modal state
  const [editingSubscription, setEditingSubscription] = useState<Subscription | null>(null);

  // Per-row run loading state (tracks subscription IDs currently being run)
  const [runningIds, setRunningIds] = useState<Set<string>>(new Set());

  const handleRunSubscription = async (sub: Subscription) => {
    setRunningIds(prev => new Set(prev).add(sub.id));
    try {
      await runSubscription(workspaceId, sub.id);
      toast.success(`Email sent to ${sub.recipientEmail}!`);
    } catch (err: any) {
      toast.error(err.response?.data?.message ?? 'Failed to send email. Please try again.');
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
        fetchFilters(workspaceId)
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

  const handleOtpSuccess = () => {
    setIsOtpModalOpen(false);
    setEmail('');
    setSelectedFilter('');
    setFrequency('HOURLY');
    loadData();
  };

  const isValidEmail = (e: string) => /\S+@\S+\.\S+/.test(e);
  const isFormValid = isValidEmail(email) && selectedFilter !== '';

  const handleSubscribeSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!isFormValid) return;

    setIsSubmitting(true);
    try {
      const payload: SubscriptionRequestPayload = {
        email,
        actionType: 'SUBSCRIBE',
        workspaceId,
        filterId: selectedFilter,
        frequency,
      };
      await requestSubscription(payload);
      setPendingEmail(email);
      setIsOtpModalOpen(true);
      toast.success('OTP sent to your email!');
    } catch (err: any) {
      toast.error(err.response?.data?.message ?? 'Failed to process request. Please check your connection and try again.');
    } finally {
      setIsSubmitting(false);
    }
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
      <div className="mb-8 flex items-center justify-between">
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
        <button
          onClick={onOpenFilterBuilder}
          className="inline-flex items-center gap-2 px-4 py-2 border border-gray-300 rounded-lg shadow-sm text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 hover:border-blue-300 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 transition-all"
        >
          <SlidersHorizontal className="h-4 w-4" />
          Manage Filter Templates
        </button>
      </div>

      {/* Subscribe OTP modal */}
      <OtpModal
        isOpen={isOtpModalOpen}
        email={pendingEmail}
        onClose={() => setIsOtpModalOpen(false)}
        onSuccess={handleOtpSuccess}
      />

      {/* Edit subscription modal */}
      {editingSubscription && (
        <EditSubscriptionModal
          isOpen={editingSubscription !== null}
          subscription={editingSubscription}
          workspaceId={workspaceId}
          onClose={() => setEditingSubscription(null)}
          onSuccess={() => {
            setEditingSubscription(null);
            loadData();
          }}
        />
      )}

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">

        {/* Left: Current Subscriptions */}
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden flex flex-col h-full">
          <div className="px-6 py-5 border-b border-gray-200 bg-gray-50">
            <h2 className="text-lg font-medium text-gray-900">Current Subscriptions</h2>
          </div>
          <div className="flex-1 overflow-x-auto">
            {subscriptions.length === 0 ? (
              <div className="p-8 text-center text-gray-500">
                No active subscriptions found for this workspace.
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
                      Frequency
                    </th>
                    <th className="px-6 py-3" />
                  </tr>
                </thead>
                <tbody className="bg-white divide-y divide-gray-200">
                  {subscriptions.map((sub) => (
                    <tr key={sub.id}>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                        {sub.recipientEmail}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                        {sub.filterTitle}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                        {sub.frequency.charAt(0) + sub.frequency.slice(1).toLowerCase()}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-right">
                        <div className="inline-flex items-center gap-2">
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

        {/* Right: Subscribe form */}
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden h-fit">
          <div className="px-6 py-5 border-b border-gray-200 bg-gray-50">
            <h2 className="text-lg font-medium text-gray-900">Subscribe</h2>
          </div>
          <div className="p-6">
            <form onSubmit={handleSubscribeSubmit} className="space-y-6">

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Email Address
                </label>
                <input
                  type="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-blue-500 focus:border-blue-500"
                  placeholder="you@example.com"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Filter
                </label>
                <select
                  required
                  value={selectedFilter}
                  onChange={(e) => setSelectedFilter(e.target.value)}
                  className="w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-blue-500 focus:border-blue-500 bg-white"
                >
                  <option value="" disabled>Select a filter...</option>
                  {filters.map((filter) => (
                    <option key={filter.id} value={filter.id}>
                      {filter.title}
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Frequency
                </label>
                <select
                  required
                  value={frequency}
                  onChange={(e) => setFrequency(e.target.value)}
                  className="w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-blue-500 focus:border-blue-500 bg-white"
                >
                  <option value="HOURLY">Hourly</option>
                  <option value="DAILY">Daily</option>
                  <option value="WEEKLY">Weekly</option>
                </select>
              </div>

              <div className="pt-4">
                <button
                  type="submit"
                  disabled={!isFormValid || isSubmitting}
                  className="w-full flex justify-center py-2 px-4 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 disabled:bg-gray-400 disabled:cursor-not-allowed transition-colors"
                >
                  {isSubmitting ? (
                    <Loader2 className="h-5 w-5 animate-spin" />
                  ) : (
                    'Subscribe'
                  )}
                </button>
              </div>

            </form>
          </div>
        </div>

      </div>
    </div>
  );
};

export default WorkspaceDashboard;
