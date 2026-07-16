import React, { useState } from 'react';
import {
  updateSubscription,
  deleteSubscription,
  toggleSubscription,
  type Subscription,
  type Schedule,
  type Filter,
} from '../services/apiService';
import { formatHourLabel } from '../services/scheduleUtils';
import { useAuth } from '../hooks/useAuth';
import { Loader2, X, Plus, Bell, AlertTriangle, Users, Power, PowerOff } from 'lucide-react';
import toast from 'react-hot-toast';

interface EditSubscriptionModalProps {
  subscription: Subscription;
  workspaceId: string;
  filters: Filter[];
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

type Step = 'edit' | 'confirmDelete' | 'confirmToggle';

const selectClass =
  'w-full px-3.5 py-2.5 border border-slate-200 dark:border-slate-600 rounded-xl text-sm ' +
  'text-slate-900 dark:text-slate-100 bg-white dark:bg-slate-800/60 ' +
  'focus:outline-none focus:border-indigo-500 dark:focus:border-indigo-400 ' +
  'focus:ring-2 focus:ring-indigo-500/15 dark:focus:ring-indigo-400/15 transition-all appearance-none';

const TRIAGE_SLA_FIELD = 'Triage SLA';
const TRIAGE_SLA_OPTIONS = [
  { value: 'GREEN', label: '🟢 Green' },
  { value: 'YELLOW', label: '🟡 Yellow' },
  { value: 'RED', label: '🔴 Red' },
] as const;

function filterHasTriageSla(filter?: Filter): boolean {
  if (!filter?.fields) return false;
  try {
    const parsed = JSON.parse(filter.fields);
    return Array.isArray(parsed) && parsed.includes(TRIAGE_SLA_FIELD);
  } catch {
    return false;
  }
}

const EditSubscriptionModal: React.FC<EditSubscriptionModalProps> = ({
  subscription, workspaceId, filters, isOpen, onClose, onSuccess,
}) => {
  const { user } = useAuth();
  const isGroupSubscription = Boolean(subscription.groupId);
  // Group subscriptions have no recipientEmail — ownership check only applies to individual subs
  const isOwnSubscription = !isGroupSubscription &&
    subscription.recipientEmail?.toLowerCase() === (user?.email ?? '').toLowerCase();
  const [step, setStep] = useState<Step>('edit');
  const [scheduleType, setScheduleType] = useState<'DAILY' | 'WEEKLY'>(subscription.schedule.type);
  const [scheduledHours, setScheduledHours] = useState<number[]>([...subscription.schedule.hours]);
  const [hourToAdd, setHourToAdd] = useState<number>(subscription.schedule.hours[0] ?? 9);
  const [triageSlaThreshold, setTriageSlaThreshold] = useState<'GREEN' | 'YELLOW' | 'RED'>(
    subscription.triageSlaThreshold ?? 'GREEN'
  );
  const [isSaving, setIsSaving] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const [isToggling, setIsToggling] = useState(false);
  const isDisabled = subscription.status === 'DISABLED';
  const selectedFilterMeta = filters.find(filter => filter.id === subscription.filterId);
  const triageEnabled = filterHasTriageSla(selectedFilterMeta);

  if (!isOpen) return null;

  const handleClose = () => {
    setStep('edit');
    setScheduleType(subscription.schedule.type);
    setScheduledHours([...subscription.schedule.hours]);
    setHourToAdd(subscription.schedule.hours[0] ?? 9);
    setTriageSlaThreshold(subscription.triageSlaThreshold ?? 'GREEN');
    onClose();
  };

  const handleSave = async () => {
    if (scheduledHours.length === 0) { toast.error('Add at least one notification hour.'); return; }
    setIsSaving(true);
    try {
      const schedule: Schedule = { type: scheduleType, hours: scheduledHours };
      await updateSubscription(workspaceId, subscription.id, {
        schedule,
        ...(triageEnabled ? { triageSlaThreshold } : {}),
      });
      toast.success('Subscription updated!');
      handleClose(); onSuccess();
    } catch (err: unknown) {
      toast.error((err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? 'Failed to update subscription.');
    } finally { setIsSaving(false); }
  };

  const handleDelete = async () => {
    setIsDeleting(true);
    try {
      await deleteSubscription(workspaceId, subscription.id);
      toast.success('Unsubscribed successfully!');
      handleClose(); onSuccess();
    } catch (err: unknown) {
      toast.error((err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? 'Failed to unsubscribe.');
    } finally { setIsDeleting(false); }
  };

  const handleToggle = async () => {
    setIsToggling(true);
    try {
      await toggleSubscription(workspaceId, subscription.id);
      toast.success(isDisabled ? 'Subscription enabled!' : 'Subscription disabled!');
      handleClose(); onSuccess();
    } catch (err: unknown) {
      toast.error((err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? 'Failed to toggle subscription.');
    } finally { setIsToggling(false); }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div
        className="absolute inset-0 bg-slate-900/60 dark:bg-slate-950/70 backdrop-blur-sm animate-fade-in"
        onClick={handleClose}
        aria-hidden="true"
      />

      <div className="relative bg-white dark:bg-slate-900 rounded-2xl shadow-2xl dark:shadow-slate-950/60 border border-slate-100/80 dark:border-slate-700/50 w-full max-w-md overflow-hidden animate-scale-in">
        <div className="h-1 bg-gradient-to-r from-indigo-500 via-violet-500 to-purple-500" />

        <div className="px-6 pt-5 pb-6">
          {/* Header */}
          <div className="flex items-center justify-between mb-1">
            <div className="flex items-center gap-2.5">
              <div className={`h-8 w-8 rounded-lg flex items-center justify-center ${isDisabled ? 'bg-amber-50 dark:bg-amber-500/10' : 'bg-indigo-50 dark:bg-indigo-500/10'}`}>
                <Bell className={`h-4 w-4 ${isDisabled ? 'text-amber-600 dark:text-amber-400' : 'text-indigo-600 dark:text-indigo-400'}`} />
              </div>
              <div className="flex items-center gap-2">
                <h3 className="text-base font-semibold text-slate-900 dark:text-white">Edit Subscription</h3>
                {isDisabled && (
                  <span className="inline-flex items-center px-2 py-0.5 rounded-md bg-amber-50 dark:bg-amber-500/10 text-amber-600 dark:text-amber-400 text-xs font-semibold border border-amber-100 dark:border-amber-500/20">
                    Disabled
                  </span>
                )}
              </div>
            </div>
            <button
              onClick={handleClose}
              className="p-1.5 rounded-lg text-slate-400 dark:text-slate-500 hover:text-slate-600 dark:hover:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors cursor-pointer"
            >
              <X className="h-4 w-4" />
            </button>
          </div>
          <p className="text-xs text-slate-400 dark:text-slate-500 mb-5 ml-10">{subscription.filterTitle}</p>

          {/* Group subscription badge */}
          {isGroupSubscription && (
            <div className="mb-4 flex items-center gap-2 px-3 py-2 rounded-xl bg-teal-50 dark:bg-teal-500/10 border border-teal-100 dark:border-teal-500/20">
              <Users className="h-4 w-4 text-teal-600 dark:text-teal-400 flex-shrink-0" />
              <div>
                <span className="text-sm font-semibold text-teal-700 dark:text-teal-300">{subscription.groupName}</span>
                {subscription.groupMemberCount != null && (
                  <span className="text-xs text-teal-500 dark:text-teal-400 ml-1.5">
                    · {subscription.groupMemberCount} member{subscription.groupMemberCount !== 1 ? 's' : ''}
                  </span>
                )}
              </div>
            </div>
          )}

          {step === 'edit' && (
            <div className="space-y-4">
              <div className="space-y-1.5">
                <label className="block text-sm font-medium text-slate-700 dark:text-slate-300">Schedule</label>
                <select
                  value={scheduleType}
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
                    className={`flex-1 ${selectClass}`}
                  >
                    {Array.from({ length: 24 }, (_, i) => (
                      <option key={i} value={i}>{formatHourLabel(i)}</option>
                    ))}
                  </select>
                  <button
                    type="button"
                    onClick={() => { if (!scheduledHours.includes(hourToAdd)) setScheduledHours(prev => [...prev, hourToAdd].sort((a, b) => a - b)); }}
                    disabled={scheduledHours.includes(hourToAdd)}
                    className="inline-flex items-center gap-1.5 px-4 py-2.5 border border-indigo-200 dark:border-indigo-500/30 rounded-xl text-sm font-medium text-indigo-600 dark:text-indigo-400 bg-indigo-50 dark:bg-indigo-500/10 hover:bg-indigo-100 dark:hover:bg-indigo-500/20 disabled:opacity-40 disabled:cursor-not-allowed transition-colors cursor-pointer"
                  >
                    <Plus className="h-4 w-4" />Add
                  </button>
                </div>
                {scheduledHours.length === 0 ? (
                  <p className="text-xs text-slate-400 dark:text-slate-500">No hours added. Add at least one.</p>
                ) : (
                  <div className="flex flex-wrap gap-1.5 mt-1">
                    {scheduledHours.map(h => (
                      <span key={h} className="inline-flex items-center gap-1 px-2.5 py-1 bg-indigo-50 dark:bg-indigo-500/10 text-indigo-700 dark:text-indigo-300 border border-indigo-100 dark:border-indigo-500/20 rounded-full text-xs font-medium">
                        {formatHourLabel(h)}
                        <button
                          type="button"
                          onClick={() => setScheduledHours(prev => prev.filter(x => x !== h))}
                          className="hover:text-indigo-900 dark:hover:text-indigo-100 transition-colors ml-0.5 cursor-pointer"
                        >
                          <X className="h-3 w-3" />
                        </button>
                      </span>
                    ))}
                  </div>
                )}
              </div>

              {triageEnabled && (
                <div className="space-y-1.5">
                  <label className="block text-sm font-medium text-slate-700 dark:text-slate-300">
                    Triage SLA threshold
                  </label>
                  <div
                    role="group"
                    aria-label="Triage SLA threshold"
                    className="flex rounded-xl border border-slate-200 dark:border-slate-700 overflow-hidden"
                  >
                    {TRIAGE_SLA_OPTIONS.map(option => (
                      <button
                        key={option.value}
                        type="button"
                        onClick={() => setTriageSlaThreshold(option.value)}
                        className={`flex-1 px-3 py-2 text-sm font-semibold transition-colors cursor-pointer ${
                          triageSlaThreshold === option.value
                            ? 'bg-indigo-600 text-white'
                            : 'bg-white dark:bg-slate-800 text-slate-600 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-700'
                        }`}
                      >
                        {option.label}
                      </button>
                    ))}
                  </div>
                </div>
              )}

              <div className="flex items-center justify-between pt-1">
                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    onClick={() => setStep('confirmToggle')}
                    className={`flex items-center gap-1.5 text-sm font-medium transition-colors cursor-pointer ${
                      isDisabled
                        ? 'text-emerald-500 dark:text-emerald-400 hover:text-emerald-600 dark:hover:text-emerald-300'
                        : 'text-amber-500 dark:text-amber-400 hover:text-amber-600 dark:hover:text-amber-300'
                    }`}
                  >
                    {isDisabled ? <Power className="h-3.5 w-3.5" /> : <PowerOff className="h-3.5 w-3.5" />}
                    {isDisabled ? 'Enable' : 'Disable'}
                  </button>
                  <span className="text-slate-300 dark:text-slate-600">|</span>
                  <button
                    type="button"
                    onClick={() => setStep('confirmDelete')}
                    className="text-sm text-rose-500 dark:text-rose-400 hover:text-rose-600 dark:hover:text-rose-300 font-medium transition-colors cursor-pointer"
                  >
                    Unsubscribe
                  </button>
                </div>
                <button
                  type="button"
                  onClick={handleSave}
                  disabled={isSaving || scheduledHours.length === 0}
                  className="flex items-center gap-2 py-2 px-5 bg-indigo-600 hover:bg-indigo-500 dark:bg-indigo-500 dark:hover:bg-indigo-400 text-white text-sm font-semibold rounded-xl shadow-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 dark:focus:ring-offset-slate-900 disabled:opacity-50 disabled:cursor-not-allowed transition-all cursor-pointer"
                >
                  {isSaving ? <Loader2 className="h-4 w-4 animate-spin" /> : 'Save Changes'}
                </button>
              </div>
            </div>
          )}

          {step === 'confirmToggle' && (
            <div className="space-y-4">
              <div className={`flex items-start gap-3 p-4 rounded-xl border ${
                isDisabled
                  ? 'bg-emerald-50 dark:bg-emerald-500/10 border-emerald-200 dark:border-emerald-500/20'
                  : 'bg-amber-50 dark:bg-amber-500/10 border-amber-200 dark:border-amber-500/20'
              }`}>
                {isDisabled
                  ? <Power className="h-5 w-5 text-emerald-500 dark:text-emerald-400 flex-shrink-0 mt-0.5" />
                  : <PowerOff className="h-5 w-5 text-amber-500 dark:text-amber-400 flex-shrink-0 mt-0.5" />
                }
                <p className={`text-sm ${isDisabled ? 'text-emerald-700 dark:text-emerald-300' : 'text-amber-700 dark:text-amber-300'}`}>
                  {isDisabled ? (
                    <>This will <span className="font-semibold">re-enable</span> the subscription to{' '}
                    <span className="font-semibold">{subscription.filterTitle}</span>. Email digests will resume as scheduled.</>
                  ) : (
                    <>This will <span className="font-semibold">disable</span> the subscription to{' '}
                    <span className="font-semibold">{subscription.filterTitle}</span>. Email digests will be paused. You can re-enable it later.</>
                  )}
                </p>
              </div>
              <div className="flex items-center justify-end gap-2.5">
                <button
                  type="button"
                  onClick={() => setStep('edit')}
                  className="py-2 px-4 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-medium text-slate-600 dark:text-slate-300 bg-white dark:bg-slate-800 hover:bg-slate-50 dark:hover:bg-slate-700/60 transition-colors cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="button"
                  onClick={handleToggle}
                  disabled={isToggling}
                  className={`flex items-center gap-2 py-2 px-4 text-white text-sm font-semibold rounded-xl focus:outline-none focus:ring-2 focus:ring-offset-2 dark:focus:ring-offset-slate-900 disabled:opacity-50 disabled:cursor-not-allowed transition-all cursor-pointer ${
                    isDisabled
                      ? 'bg-emerald-600 hover:bg-emerald-500 dark:bg-emerald-500 dark:hover:bg-emerald-400 focus:ring-emerald-500'
                      : 'bg-amber-600 hover:bg-amber-500 dark:bg-amber-500 dark:hover:bg-amber-400 focus:ring-amber-500'
                  }`}
                >
                  {isToggling
                    ? <Loader2 className="h-4 w-4 animate-spin" />
                    : isDisabled ? 'Yes, Enable' : 'Yes, Disable'
                  }
                </button>
              </div>
            </div>
          )}

          {step === 'confirmDelete' && (
            <div className="space-y-4">
              <div className="flex items-start gap-3 p-4 bg-rose-50 dark:bg-rose-500/10 border border-rose-200 dark:border-rose-500/20 rounded-xl">
                <AlertTriangle className="h-5 w-5 text-rose-500 dark:text-rose-400 flex-shrink-0 mt-0.5" />
                <p className="text-sm text-rose-700 dark:text-rose-300">
                  {isGroupSubscription ? (
                    <>This will permanently remove the group subscription for{' '}
                    <span className="font-semibold">{subscription.groupName}</span> to{' '}
                    <span className="font-semibold">{subscription.filterTitle}</span>. Are you sure?</>
                  ) : isOwnSubscription ? (
                    <>This will permanently remove your subscription to{' '}
                    <span className="font-semibold">{subscription.filterTitle}</span>. Are you sure?</>
                  ) : (
                    <>This will permanently remove <span className="font-semibold">{subscription.recipientEmail}</span>'s subscription to{' '}
                    <span className="font-semibold">{subscription.filterTitle}</span>. Are you sure?</>
                  )}
                </p>
              </div>
              <div className="flex items-center justify-end gap-2.5">
                <button
                  type="button"
                  onClick={() => setStep('edit')}
                  className="py-2 px-4 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-medium text-slate-600 dark:text-slate-300 bg-white dark:bg-slate-800 hover:bg-slate-50 dark:hover:bg-slate-700/60 transition-colors cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="button"
                  onClick={handleDelete}
                  disabled={isDeleting}
                  className="flex items-center gap-2 py-2 px-4 bg-rose-600 hover:bg-rose-500 dark:bg-rose-500 dark:hover:bg-rose-400 text-white text-sm font-semibold rounded-xl focus:outline-none focus:ring-2 focus:ring-rose-500 focus:ring-offset-2 dark:focus:ring-offset-slate-900 disabled:opacity-50 disabled:cursor-not-allowed transition-all cursor-pointer"
                >
                  {isDeleting ? <Loader2 className="h-4 w-4 animate-spin" /> : 'Yes, Unsubscribe'}
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default EditSubscriptionModal;
