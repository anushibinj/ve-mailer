import React, { useState } from 'react';
import {
  updateSubscription,
  deleteSubscription,
  type Subscription,
  type Schedule,
} from '../services/apiService';
import { Loader2, X, Plus } from 'lucide-react';
import toast from 'react-hot-toast';

interface EditSubscriptionModalProps {
  subscription: Subscription;
  workspaceId: string;
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

type Step = 'edit' | 'confirmDelete';

const EditSubscriptionModal: React.FC<EditSubscriptionModalProps> = ({
  subscription,
  workspaceId,
  isOpen,
  onClose,
  onSuccess,
}) => {
  const [step, setStep] = useState<Step>('edit');
  const [scheduleType, setScheduleType] = useState<'DAILY' | 'WEEKLY'>(subscription.schedule.type);
  const [scheduledHours, setScheduledHours] = useState<number[]>([...subscription.schedule.hours]);
  const [hourToAdd, setHourToAdd] = useState<number>(subscription.schedule.hours[0] ?? 9);
  const [isSaving, setIsSaving] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);

  if (!isOpen) return null;

  const formatHour = (h: number) => `${String(h).padStart(2, '0')}:00`;

  const handleAddHour = () => {
    if (!scheduledHours.includes(hourToAdd)) {
      setScheduledHours(prev => [...prev, hourToAdd].sort((a, b) => a - b));
    }
  };

  const handleRemoveHour = (h: number) => {
    setScheduledHours(prev => prev.filter(x => x !== h));
  };

  const handleClose = () => {
    setStep('edit');
    setScheduleType(subscription.schedule.type);
    setScheduledHours([...subscription.schedule.hours]);
    setHourToAdd(subscription.schedule.hours[0] ?? 9);
    onClose();
  };

  const handleSave = async () => {
    if (scheduledHours.length === 0) {
      toast.error('Add at least one notification hour.');
      return;
    }
    setIsSaving(true);
    try {
      const schedule: Schedule = { type: scheduleType, hours: scheduledHours };
      await updateSubscription(workspaceId, subscription.id, { schedule });
      toast.success('Subscription updated!');
      handleClose();
      onSuccess();
    } catch (err: unknown) {
      const message =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message
        ?? 'Failed to update subscription. Please try again.';
      toast.error(message);
    } finally {
      setIsSaving(false);
    }
  };

  const handleDelete = async () => {
    setIsDeleting(true);
    try {
      await deleteSubscription(workspaceId, subscription.id);
      toast.success('Unsubscribed successfully!');
      handleClose();
      onSuccess();
    } catch (err: unknown) {
      const message =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message
        ?? 'Failed to unsubscribe. Please try again.';
      toast.error(message);
    } finally {
      setIsDeleting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 overflow-y-auto">
      <div className="flex items-center justify-center min-h-screen pt-4 px-4 pb-20 text-center sm:block sm:p-0">
        {/* Backdrop */}
        <div className="fixed inset-0 transition-opacity" aria-hidden="true" onClick={handleClose}>
          <div className="absolute inset-0 bg-gray-500 opacity-75" />
        </div>
        <span className="hidden sm:inline-block sm:align-middle sm:h-screen" aria-hidden="true">&#8203;</span>

        <div className="inline-block align-bottom bg-white rounded-lg text-left overflow-hidden shadow-xl transform transition-all sm:my-8 sm:align-middle sm:max-w-md sm:w-full">
          <div className="bg-white px-6 pt-5 pb-6 relative">

            {/* Close button */}
            <button
              onClick={handleClose}
              className="absolute top-4 right-4 text-gray-400 hover:text-gray-500 focus:outline-none"
              aria-label="Close"
            >
              <X className="h-5 w-5" />
            </button>

            <h3 className="text-lg font-medium text-gray-900 mb-1">Edit Subscription</h3>
            <p className="text-sm text-gray-500 mb-5">
              {subscription.filterTitle}
            </p>

            {/* ── Step: edit schedule ── */}
            {step === 'edit' && (
              <div className="space-y-5">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Schedule Type
                  </label>
                  <select
                    value={scheduleType}
                    onChange={e => setScheduleType(e.target.value as 'DAILY' | 'WEEKLY')}
                    className="w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-blue-500 focus:border-blue-500 bg-white text-sm"
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
                        <span key={h} className="inline-flex items-center gap-1 px-2 py-0.5 bg-blue-100 text-blue-800 rounded-full text-xs font-medium">
                          {formatHour(h)}
                          <button type="button" onClick={() => handleRemoveHour(h)} className="hover:text-blue-600">
                            <X className="h-3 w-3" />
                          </button>
                        </span>
                      ))}
                    </div>
                  )}
                </div>

                {/* Footer */}
                <div className="flex items-center justify-between pt-1">
                  <button
                    type="button"
                    onClick={() => setStep('confirmDelete')}
                    className="text-sm text-red-600 hover:text-red-700 hover:underline focus:outline-none"
                  >
                    Unsubscribe
                  </button>
                  <button
                    type="button"
                    onClick={handleSave}
                    disabled={isSaving || scheduledHours.length === 0}
                    className="flex items-center gap-2 py-2 px-4 rounded-md shadow-sm text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 disabled:bg-blue-300 disabled:cursor-not-allowed transition-colors"
                  >
                    {isSaving ? <Loader2 className="h-4 w-4 animate-spin" /> : 'Save Changes'}
                  </button>
                </div>
              </div>
            )}

            {/* ── Step: confirm delete ── */}
            {step === 'confirmDelete' && (
              <div className="space-y-5">
                <p className="text-sm text-red-600 bg-red-50 border border-red-200 rounded-md px-3 py-3">
                  This will permanently remove your subscription to{' '}
                  <span className="font-medium">{subscription.filterTitle}</span>. Are you sure?
                </p>
                <div className="flex items-center justify-end gap-3 pt-1">
                  <button
                    type="button"
                    onClick={() => setStep('edit')}
                    className="py-2 px-4 border border-gray-300 rounded-md text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 transition-colors"
                  >
                    Cancel
                  </button>
                  <button
                    type="button"
                    onClick={handleDelete}
                    disabled={isDeleting}
                    className="flex items-center gap-2 py-2 px-4 rounded-md shadow-sm text-sm font-medium text-white bg-red-600 hover:bg-red-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-red-500 disabled:bg-red-300 disabled:cursor-not-allowed transition-colors"
                  >
                    {isDeleting ? <Loader2 className="h-4 w-4 animate-spin" /> : 'Yes, Unsubscribe'}
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default EditSubscriptionModal;
