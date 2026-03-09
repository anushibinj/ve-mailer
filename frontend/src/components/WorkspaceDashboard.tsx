import React, { useEffect, useState } from 'react';
import {
  fetchSubscriptionsByWorkspace,
  fetchFilters,
  requestSubscription,
  executeFilter,
  type Subscription,
  type Filter,
  type SubscriptionRequestPayload
} from '../services/apiService';
import { Loader2, ArrowLeft, Play } from 'lucide-react';
import toast from 'react-hot-toast';
import OtpModal from './OtpModal';

interface WorkspaceDashboardProps {
  workspaceId: string;
  onBack: () => void;
}

const WorkspaceDashboard: React.FC<WorkspaceDashboardProps> = ({ workspaceId, onBack }) => {
  const [subscriptions, setSubscriptions] = useState<Subscription[]>([]);
  const [filters, setFilters] = useState<Filter[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);

  // Form State
  const [email, setEmail] = useState('');
  const [selectedFilter, setSelectedFilter] = useState('');
  const [frequency, setFrequency] = useState('HOURLY');
  const [actionType, setActionType] = useState('SUBSCRIBE');
  const [isSubmitting, setIsSubmitting] = useState(false);

  // OTP Modal State
  const [isOtpModalOpen, setIsOtpModalOpen] = useState(false);
  const [pendingEmail, setPendingEmail] = useState('');

  // Execute Filter State
  const [executeFilterId, setExecuteFilterId] = useState('');
  const [isExecuting, setIsExecuting] = useState(false);
  const [executeResults, setExecuteResults] = useState<any[] | null>(null);

  const loadData = async () => {
    setIsLoading(true);
    try {
      const [subsData, filtersData] = await Promise.all([
        fetchSubscriptionsByWorkspace(workspaceId),
        fetchFilters()
      ]);
      setSubscriptions(subsData);
      setFilters(filtersData);
    } catch (err) {
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
    // Reset form
    setEmail('');
    setSelectedFilter('');
    setFrequency('HOURLY');
    setActionType('SUBSCRIBE');

    // Refresh table data
    loadData();
  };

  const isValidEmail = (email: string) => {
    return /\S+@\S+\.\S+/.test(email);
  };

  const isFormValid = isValidEmail(email) && selectedFilter !== '';

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!isFormValid) return;

    setIsSubmitting(true);
    try {
      const payload: SubscriptionRequestPayload = {
        email,
        actionType,
        workspaceId,
        filterId: selectedFilter,
        frequency
      };

      await requestSubscription(payload);
      setPendingEmail(email);
      setIsOtpModalOpen(true);
      toast.success('OTP sent to your email!');
    } catch (err: any) {
      if (err.response?.data?.message) {
        toast.error(`Error: ${err.response.data.message}`);
      } else {
        toast.error('Failed to process request. Please check your connection and try again.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleExecuteFilter = async () => {
    if (!executeFilterId) return;

    setIsExecuting(true);
    setExecuteResults(null);
    try {
      const results = await executeFilter(executeFilterId, workspaceId);
      setExecuteResults(results);
      toast.success(`Query returned ${results.length} result(s).`);
    } catch (err: any) {
      if (err.response?.data?.message) {
        toast.error(`Execute failed: ${err.response.data.message}`);
      } else {
        toast.error('Failed to execute filter. Please check your connection and try again.');
      }
    } finally {
      setIsExecuting(false);
    }
  };

  // Extract column headers from the first result row
  const resultColumns = executeResults && executeResults.length > 0
    ? Object.keys(executeResults[0])
    : [];

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
      <div className="mb-8 flex items-center">
        <button
          onClick={onBack}
          className="mr-4 p-2 rounded-full hover:bg-gray-200 transition-colors"
          aria-label="Back to workspaces"
        >
          <ArrowLeft className="h-6 w-6 text-gray-600" />
        </button>
        <h1 className="text-3xl font-bold text-gray-900">Workspace Dashboard</h1>
      </div>

      <OtpModal
        isOpen={isOtpModalOpen}
        email={pendingEmail}
        onClose={() => setIsOtpModalOpen(false)}
        onSuccess={handleOtpSuccess}
      />

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">

        {/* Left Column: Current Subscriptions */}
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
                        {sub.frequency}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </div>

        {/* Right Column: Manage Subscription */}
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden h-fit">
          <div className="px-6 py-5 border-b border-gray-200 bg-gray-50">
            <h2 className="text-lg font-medium text-gray-900">Manage Your Subscription</h2>
          </div>
          <div className="p-6">
            <form onSubmit={handleSubmit} className="space-y-6">

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

              <div>
                 <label className="block text-sm font-medium text-gray-700 mb-1">
                  Action
                </label>
                <div className="flex space-x-4">
                  {['SUBSCRIBE', 'UPDATE', 'UNSUBSCRIBE'].map((action) => (
                    <label key={action} className="flex items-center">
                      <input
                        type="radio"
                        name="actionType"
                        value={action}
                        checked={actionType === action}
                        onChange={(e) => setActionType(e.target.value)}
                        className="h-4 w-4 text-blue-600 focus:ring-blue-500 border-gray-300"
                      />
                      <span className="ml-2 text-sm text-gray-700">{action.charAt(0) + action.slice(1).toLowerCase()}</span>
                    </label>
                  ))}
                </div>
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
                    'Request Action'
                  )}
                </button>
              </div>
            </form>
          </div>
        </div>

      </div>

      {/* Execute Filter Section */}
      <div className="mt-8 bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
        <div className="px-6 py-5 border-b border-gray-200 bg-gray-50">
          <h2 className="text-lg font-medium text-gray-900">Execute Filter</h2>
          <p className="text-sm text-gray-500 mt-1">
            Run a saved filter template against this workspace to preview matching results from ValueEdge.
          </p>
        </div>
        <div className="p-6">
          <div className="flex items-end gap-4">
            <div className="flex-1">
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Filter Template
              </label>
              <select
                value={executeFilterId}
                onChange={(e) => setExecuteFilterId(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-blue-500 focus:border-blue-500 bg-white"
              >
                <option value="" disabled>Select a filter to execute...</option>
                {filters.map((filter) => (
                  <option key={filter.id} value={filter.id}>
                    {filter.title}
                    {filter.entityType ? ` (${filter.entityType})` : ''}
                  </option>
                ))}
              </select>
            </div>
            <button
              type="button"
              onClick={handleExecuteFilter}
              disabled={!executeFilterId || isExecuting}
              className="flex items-center gap-2 px-5 py-2 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-green-600 hover:bg-green-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-green-500 disabled:bg-gray-400 disabled:cursor-not-allowed transition-colors"
            >
              {isExecuting ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                <Play className="h-4 w-4" />
              )}
              Execute
            </button>
          </div>

          {/* Results Panel */}
          {executeResults !== null && (
            <div className="mt-6">
              <h3 className="text-sm font-medium text-gray-700 mb-2">
                Results ({executeResults.length} items)
              </h3>
              {executeResults.length === 0 ? (
                <div className="p-6 text-center text-gray-500 bg-gray-50 rounded-md border border-gray-200">
                  No results matched the filter criteria.
                </div>
              ) : (
                <div className="overflow-x-auto border border-gray-200 rounded-md">
                  <table className="min-w-full divide-y divide-gray-200">
                    <thead className="bg-gray-50">
                      <tr>
                        {resultColumns.map((col) => (
                          <th
                            key={col}
                            className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider whitespace-nowrap"
                          >
                            {col}
                          </th>
                        ))}
                      </tr>
                    </thead>
                    <tbody className="bg-white divide-y divide-gray-200">
                      {executeResults.map((row, ri) => (
                        <tr key={ri} className={ri % 2 === 0 ? 'bg-white' : 'bg-gray-50'}>
                          {resultColumns.map((col) => {
                            const value = row[col];
                            const display = value === null || value === undefined
                              ? ''
                              : typeof value === 'object'
                                ? JSON.stringify(value)
                                : String(value);
                            return (
                              <td
                                key={col}
                                className="px-4 py-3 text-sm text-gray-700 whitespace-nowrap max-w-xs truncate"
                                title={display}
                              >
                                {display}
                              </td>
                            );
                          })}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default WorkspaceDashboard;
