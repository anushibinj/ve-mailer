import React, { useState } from 'react';
import {
  createSubscription,
  type Filter,
  type Schedule,
} from '../services/apiService';
import { formatHourLabel } from '../services/scheduleUtils';
import { Loader2, X, Plus, Bell } from 'lucide-react';
import toast from 'react-hot-toast';

interface SubscriptionFormModalProps {
  isOpen: boolean;
  workspaceId: string;
  filters: Filter[];
  onClose: () => void;
  onSuccess: () => void;
}

const selectClass =
  'w-full px-3.5 py-2.5 border border-slate-200 dark:border-slate-600 rounded-xl text-sm ' +
  'text-slate-900 dark:text-slate-100 bg-white dark:bg-slate-800/60 ' +
  'focus:outline-none focus:border-indigo-500 dark:focus:border-indigo-400 ' +
  'focus:ring-2 focus:ring-indigo-500/15 dark:focus:ring-indigo-400/15 transition-all appearance-none';

const SubscriptionFormModal: React.FC<SubscriptionFormModalProps> = ({
  isOpen, workspaceId, filters, onClose, onSuccess,
}) => {
  const [selectedFilter, setSelectedFilter] = useState('');
  const [scheduleType, setScheduleType] = useState<'DAILY' | 'WEEKLY'>('DAILY');
  const [scheduledHours, setScheduledHours] = useState<number[]>([]);
  const [hourToAdd, setHourToAdd] = useState<number>(9);
  const [isSubmitting, setIsSubmitting] = useState(false);

  if (!isOpen) return null;

  const isFormValid = selectedFilter !== '' && scheduledHours.length > 0;

  const handleAddHour = () => {
    if (!scheduledHours.includes(hourToAdd)) {
      setScheduledHours(prev => [...prev, hourToAdd].sort((a, b) => a - b));
    }
  };

  const resetForm = () => {
    setSelectedFilter(''); setScheduleType('DAILY'); setScheduledHours([]); setHourToAdd(9);
  };

  const handleClose = () => { resetForm(); onClose(); };

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
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4" role="dialog" aria-modal="true" aria-labelledby="sub-form-title">
      {/* Backdrop */}
      <div
        className="absolute inset-0 bg-slate-900/60 dark:bg-slate-950/70 backdrop-blur-sm animate-fade-in"
        onClick={handleClose}
        aria-hidden="true"
      />

      <div className="relative bg-white dark:bg-slate-900 rounded-2xl shadow-2xl dark:shadow-slate-950/60 border border-slate-100/80 dark:border-slate-700/50 w-full max-w-md overflow-hidden animate-scale-in">
        <div className="h-1 bg-gradient-to-r from-indigo-500 via-violet-500 to-purple-500" />

        <div className="px-6 pt-5 pb-6">
          {/* Header */}
          <div className="flex items-center justify-between mb-5">
            <div className="flex items-center gap-2.5">
              <div className="h-8 w-8 rounded-lg bg-indigo-50 dark:bg-indigo-500/10 flex items-center justify-center">
                <Bell className="h-4 w-4 text-indigo-600 dark:text-indigo-400" />
              </div>
              <h3 id="sub-form-title" className="text-base font-semibold text-slate-900 dark:text-white">
                New Subscription
              </h3>
            </div>
            <button
              onClick={handleClose}
              className="p-1.5 rounded-lg text-slate-400 dark:text-slate-500 hover:text-slate-600 dark:hover:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors cursor-pointer"
              aria-label="Close"
            >
              <X className="h-4 w-4" />
            </button>
          </div>

          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="space-y-1.5">
              <label htmlFor="sub-filter" className="block text-sm font-medium text-slate-700 dark:text-slate-300">Filter Template</label>
              <select
                id="sub-filter" required value={selectedFilter}
                onChange={e => setSelectedFilter(e.target.value)}
                className={selectClass}
              >
                <option value="" disabled>Select a filter…</option>
                {filters.map(f => <option key={f.id} value={f.id}>{f.title}</option>)}
              </select>
            </div>

            <div className="space-y-1.5">
              <label htmlFor="sub-schedule-type" className="block text-sm font-medium text-slate-700 dark:text-slate-300">Schedule</label>
              <select
                id="sub-schedule-type" value={scheduleType}
                onChange={e => setScheduleType(e.target.value as 'DAILY' | 'WEEKLY')}
                className={selectClass}
              >
                <option value="DAILY">Daily</option>
                <option value="WEEKLY">Weekly (every Monday)</option>
              </select>
            </div>

            <div className="space-y-1.5">
              <label className="block text-sm font-medium text-slate-700 dark:text-slate-300">Notification Hours</label>
              <div className="flex gap-2">
                <select
                  value={hourToAdd}
                  onChange={e => setHourToAdd(Number(e.target.value))}
                  aria-label="Hour to add"
                  className={`flex-1 ${selectClass}`}
                >
                  {Array.from({ length: 24 }, (_, i) => (
                    <option key={i} value={i}>{formatHourLabel(i)}</option>
                  ))}
                </select>
                <button
                  type="button"
                  onClick={handleAddHour}
                  disabled={scheduledHours.includes(hourToAdd)}
                  className="inline-flex items-center gap-1.5 px-4 py-2.5 border border-indigo-200 dark:border-indigo-500/30 rounded-xl text-sm font-medium text-indigo-600 dark:text-indigo-400 bg-indigo-50 dark:bg-indigo-500/10 hover:bg-indigo-100 dark:hover:bg-indigo-500/20 disabled:opacity-40 disabled:cursor-not-allowed transition-colors cursor-pointer"
                >
                  <Plus className="h-4 w-4" />
                  Add
                </button>
              </div>
              {scheduledHours.length === 0 ? (
                <p className="text-xs text-slate-400 dark:text-slate-500">No hours added yet — add at least one.</p>
              ) : (
                <div className="flex flex-wrap gap-1.5 mt-1">
                  {scheduledHours.map(h => (
                    <span key={h} className="inline-flex items-center gap-1 px-2.5 py-1 bg-indigo-50 dark:bg-indigo-500/10 text-indigo-700 dark:text-indigo-300 border border-indigo-100 dark:border-indigo-500/20 rounded-full text-xs font-medium">
                      {formatHourLabel(h)}
                      <button
                        type="button"
                        onClick={() => setScheduledHours(prev => prev.filter(x => x !== h))}
                        className="hover:text-indigo-900 dark:hover:text-indigo-100 transition-colors ml-0.5 cursor-pointer"
                        aria-label={`Remove ${formatHourLabel(h)}`}
                      >
                        <X className="h-3 w-3" />
                      </button>
                    </span>
                  ))}
                </div>
              )}
            </div>

            <button
              type="submit"
              disabled={!isFormValid || isSubmitting}
              className="w-full flex items-center justify-center gap-2 py-2.5 px-4 bg-indigo-600 hover:bg-indigo-500 dark:bg-indigo-500 dark:hover:bg-indigo-400 text-white text-sm font-semibold rounded-xl shadow-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 dark:focus:ring-offset-slate-900 disabled:opacity-50 disabled:cursor-not-allowed transition-all mt-2 cursor-pointer"
            >
              {isSubmitting ? <Loader2 className="h-4 w-4 animate-spin" /> : 'Subscribe'}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
};

export default SubscriptionFormModal;
