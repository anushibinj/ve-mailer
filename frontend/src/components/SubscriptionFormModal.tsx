import React, { useState } from 'react';
import {
  createSubscription,
  type Filter,
  type Schedule,
} from '../services/apiService';
import { Loader2, X, Plus } from 'lucide-react';
import toast from 'react-hot-toast';

interface SubscriptionFormModalProps {
  isOpen: boolean;
  workspaceId: string;
  filters: Filter[];
  onClose: () => void;
  onSuccess: () => void;
}

const SubscriptionFormModal: React.FC<SubscriptionFormModalProps> = ({
  isOpen,
  workspaceId,
  filters,
  onClose,
  onSuccess,
}) => {
  const [selectedFilter, setSelectedFilter] = useState('');
  const [scheduleType, setScheduleType] = useState<'DAILY' | 'WEEKLY'>('DAILY');
  const [scheduledHours, setScheduledHours] = useState<number[]>([]);
  const [hourToAdd, setHourToAdd] = useState<number>(9);
  const [isSubmitting, setIsSubmitting] = useState(false);

  if (!isOpen) return null;

  const formatHour = (h: number) => `${String(h).padStart(2, '0')}:00`;
  const isFormValid = selectedFilter !== '' && scheduledHours.length > 0;

  const handleAddHour = () => {
    if (!scheduledHours.includes(hourToAdd)) {
      setScheduledHours(prev => [...prev, hourToAdd].sort((a, b) => a - b));
    }
  };

  const handleRemoveHour = (h: number) => {
    setScheduledHours(prev => prev.filter(x => x !== h));
  };

  const resetForm = () => {
    setSelectedFilter('');
    setScheduleType('DAILY');
    setScheduledHours([]);
    setHourToAdd(9);
  };

  const handleClose = () => {
    resetForm();
    onClose();
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!isFormValid) return;
    setIsSubmitting(true);
    try {
      const schedule: Schedule = { type: scheduleType, hours: scheduledHours };
      await createSubscription(workspaceId, { filterId: selectedFilter, schedule });
      toast.success('Subscribed successfully!');
      resetForm();
      onSuccess();
    } catch (err: unknown) {
      const message =
        err instanceof Error
          ? err.message
          : (err as { response?: { data?: { message?: string } } })?.response?.data?.message
            ?? 'Failed to create subscription. Please try again.';
      toast.error(message);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 overflow-y-auto"
      role="dialog"
      aria-modal="true"
      aria-labelledby="sub-form-title"
    >
      <div className="flex items-center justify-center min-h-screen pt-4 px-4 pb-20 text-center sm:block sm:p-0">
        {/* Backdrop */}
        <div
          className="fixed inset-0 bg-gray-500 bg-opacity-75 transition-opacity"
          aria-hidden="true"
          onClick={handleClose}
        />
        <span className="hidden sm:inline-block sm:align-middle sm:h-screen" aria-hidden="true">&#8203;</span>

        <div className="inline-block align-bottom bg-white rounded-lg text-left overflow-hidden shadow-xl transform transition-all sm:my-8 sm:align-middle sm:max-w-md sm:w-full">
          <div className="bg-white px-6 pt-5 pb-6 relative">

            {/* Close button */}
            <button
              onClick={handleClose}
              className="absolute top-4 right-4 text-gray-400 hover:text-gray-500 focus:outline-none focus:ring-2 focus:ring-blue-500 rounded"
              aria-label="Close"
            >
              <X className="h-5 w-5" />
            </button>

            <h3 id="sub-form-title" className="text-lg font-medium text-gray-900 mb-5">
              Create New Subscription
            </h3>

            <form onSubmit={handleSubmit} className="space-y-5">
              <div>
                <label htmlFor="sub-filter" className="block text-sm font-medium text-gray-700 mb-1">
                  Filter
                </label>
                <select
                  id="sub-filter"
                  required
                  value={selectedFilter}
                  onChange={e => setSelectedFilter(e.target.value)}
                  className="w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-blue-500 focus:border-blue-500 bg-white"
                >
                  <option value="" disabled>Select a filter...</option>
                  {filters.map(f => (
                    <option key={f.id} value={f.id}>{f.title}</option>
                  ))}
                </select>
              </div>

              <div>
                <label htmlFor="sub-schedule-type" className="block text-sm font-medium text-gray-700 mb-1">
                  Schedule Type
                </label>
                <select
                  id="sub-schedule-type"
                  value={scheduleType}
                  onChange={e => setScheduleType(e.target.value as 'DAILY' | 'WEEKLY')}
                  className="w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-blue-500 focus:border-blue-500 bg-white"
                >
                  <option value="DAILY">Daily</option>
                  <option value="WEEKLY">Weekly (every Monday)</option>
                </select>
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Notification Hours
                </label>
                <div className="flex gap-2 mb-2">
                  <select
                    value={hourToAdd}
                    onChange={e => setHourToAdd(Number(e.target.value))}
                    aria-label="Hour to add"
                    className="flex-1 px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-blue-500 focus:border-blue-500 bg-white text-sm"
                  >
                    {Array.from({ length: 24 }, (_, i) => (
                      <option key={i} value={i}>{String(i).padStart(2, '0')}:00</option>
                    ))}
                  </select>
                  <button
                    type="button"
                    onClick={handleAddHour}
                    disabled={scheduledHours.includes(hourToAdd)}
                    className="inline-flex items-center gap-1 px-3 py-2 border border-blue-500 rounded-md text-sm font-medium text-blue-700 hover:bg-blue-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
                  >
                    <Plus className="h-4 w-4" />
                    Add
                  </button>
                </div>
                {scheduledHours.length === 0 ? (
                  <p className="text-xs text-gray-400">No hours added. Add at least one.</p>
                ) : (
                  <div className="flex flex-wrap gap-1.5">
                    {scheduledHours.map(h => (
                      <span
                        key={h}
                        className="inline-flex items-center gap-1 px-2 py-0.5 bg-blue-100 text-blue-800 rounded-full text-xs font-medium"
                      >
                        {formatHour(h)}
                        <button
                          type="button"
                          onClick={() => handleRemoveHour(h)}
                          className="hover:text-blue-600"
                          aria-label={`Remove ${formatHour(h)}`}
                        >
                          <X className="h-3 w-3" />
                        </button>
                      </span>
                    ))}
                  </div>
                )}
              </div>

              <div className="pt-2">
                <button
                  type="submit"
                  disabled={!isFormValid || isSubmitting}
                  className="w-full flex justify-center py-2 px-4 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 disabled:bg-gray-400 disabled:cursor-not-allowed transition-colors"
                >
                  {isSubmitting ? <Loader2 className="h-5 w-5 animate-spin" /> : 'Subscribe'}
                </button>
              </div>
            </form>

          </div>
        </div>
      </div>
    </div>
  );
};

export default SubscriptionFormModal;
