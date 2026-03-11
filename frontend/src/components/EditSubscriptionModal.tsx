import React, { useState } from 'react';
import { requestSubscription, verifyOtp, type Subscription } from '../services/apiService';
import { Loader2, X } from 'lucide-react';
import toast from 'react-hot-toast';

interface EditSubscriptionModalProps {
  subscription: Subscription;
  workspaceId: string;
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

type Step = 'edit' | 'unsubscribe' | 'otp';

const FREQUENCIES = ['HOURLY', 'DAILY', 'WEEKLY'];

const EditSubscriptionModal: React.FC<EditSubscriptionModalProps> = ({
  subscription,
  workspaceId,
  isOpen,
  onClose,
  onSuccess,
}) => {
  const [step, setStep] = useState<Step>('edit');
  const [frequency, setFrequency] = useState(subscription.frequency);
  const [isRequesting, setIsRequesting] = useState(false);
  const [otp, setOtp] = useState('');
  const [isVerifying, setIsVerifying] = useState(false);

  if (!isOpen) return null;

  const action = step === 'unsubscribe' ? 'UNSUBSCRIBE' : 'UPDATE';

  const handleClose = () => {
    setStep('edit');
    setFrequency(subscription.frequency);
    setOtp('');
    onClose();
  };

  const handleRequestOtp = async () => {
    setIsRequesting(true);
    try {
      await requestSubscription({
        email: subscription.recipientEmail,
        actionType: action,
        workspaceId,
        filterId: subscription.filterId,
        frequency: action === 'UPDATE' ? frequency : subscription.frequency,
      });
      toast.success('OTP sent to your email!');
      setStep('otp');
    } catch (err: any) {
      toast.error(err.response?.data?.message ?? 'Failed to send OTP. Please try again.');
    } finally {
      setIsRequesting(false);
    }
  };

  const handleVerifyOtp = async (e: React.FormEvent) => {
    e.preventDefault();
    if (otp.length !== 6) {
      toast.error('OTP must be exactly 6 characters.');
      return;
    }
    setIsVerifying(true);
    try {
      await verifyOtp(subscription.recipientEmail, otp);
      toast.success(action === 'UNSUBSCRIBE' ? 'Unsubscribed successfully!' : 'Subscription updated!');
      handleClose();
      onSuccess();
    } catch (err: any) {
      toast.error(err.response?.data?.message ?? 'Invalid OTP. Please try again.');
    } finally {
      setIsVerifying(false);
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
              <span className="font-medium text-gray-700">{subscription.recipientEmail}</span>
              {' · '}
              {subscription.filterTitle}
            </p>

            {/* ── Step: edit frequency ── */}
            {step === 'edit' && (
              <div className="space-y-5">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Notification Frequency
                  </label>
                  <select
                    value={frequency}
                    onChange={e => setFrequency(e.target.value)}
                    className="w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-blue-500 focus:border-blue-500 bg-white text-sm"
                  >
                    {FREQUENCIES.map(f => (
                      <option key={f} value={f}>
                        {f.charAt(0) + f.slice(1).toLowerCase()}
                      </option>
                    ))}
                  </select>
                </div>

                {/* Footer */}
                <div className="flex items-center justify-between pt-1">
                  <button
                    type="button"
                    onClick={() => setStep('unsubscribe')}
                    className="text-sm text-red-600 hover:text-red-700 hover:underline focus:outline-none"
                  >
                    Unsubscribe
                  </button>
                  <button
                    type="button"
                    onClick={handleRequestOtp}
                    disabled={isRequesting}
                    className="flex items-center gap-2 py-2 px-4 rounded-md shadow-sm text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 disabled:bg-blue-300 disabled:cursor-not-allowed transition-colors"
                  >
                    {isRequesting ? <Loader2 className="h-4 w-4 animate-spin" /> : 'Send OTP to Update'}
                  </button>
                </div>
              </div>
            )}

            {/* ── Step: confirm unsubscribe ── */}
            {step === 'unsubscribe' && (
              <div className="space-y-5">
                <p className="text-sm text-red-600 bg-red-50 border border-red-200 rounded-md px-3 py-3">
                  This will permanently remove the subscription for{' '}
                  <span className="font-medium">{subscription.recipientEmail}</span>. You will receive
                  an OTP to confirm.
                </p>

                {/* Footer */}
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
                    onClick={handleRequestOtp}
                    disabled={isRequesting}
                    className="flex items-center gap-2 py-2 px-4 rounded-md shadow-sm text-sm font-medium text-white bg-red-600 hover:bg-red-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-red-500 disabled:bg-red-300 disabled:cursor-not-allowed transition-colors"
                  >
                    {isRequesting ? <Loader2 className="h-4 w-4 animate-spin" /> : 'Send OTP to Unsubscribe'}
                  </button>
                </div>
              </div>
            )}

            {/* ── Step: enter OTP ── */}
            {step === 'otp' && (
              <form onSubmit={handleVerifyOtp} className="space-y-5">
                <p className="text-sm text-gray-500">
                  Enter the 6-digit OTP sent to{' '}
                  <span className="font-semibold text-gray-900">{subscription.recipientEmail}</span>.
                </p>
                <input
                  type="text"
                  maxLength={6}
                  required
                  value={otp}
                  onChange={e => setOtp(e.target.value)}
                  className="w-full text-center text-2xl tracking-widest px-3 py-3 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-blue-500 focus:border-blue-500 uppercase"
                  placeholder="••••••"
                  autoFocus
                />
                <div className="flex gap-3">
                  <button
                    type="button"
                    onClick={() => setStep(action === 'UNSUBSCRIBE' ? 'unsubscribe' : 'edit')}
                    className="flex-1 py-2 px-4 border border-gray-300 rounded-md text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 transition-colors"
                  >
                    Back
                  </button>
                  <button
                    type="submit"
                    disabled={otp.length !== 6 || isVerifying}
                    className={`flex-1 flex justify-center py-2 px-4 border border-transparent rounded-md shadow-sm text-sm font-medium text-white focus:outline-none focus:ring-2 focus:ring-offset-2 disabled:cursor-not-allowed transition-colors ${
                      action === 'UNSUBSCRIBE'
                        ? 'bg-red-600 hover:bg-red-700 focus:ring-red-500 disabled:bg-red-300'
                        : 'bg-blue-600 hover:bg-blue-700 focus:ring-blue-500 disabled:bg-gray-400'
                    }`}
                  >
                    {isVerifying ? <Loader2 className="h-5 w-5 animate-spin" /> : 'Verify'}
                  </button>
                </div>
              </form>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default EditSubscriptionModal;
