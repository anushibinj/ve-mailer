import React, { useState } from 'react';
import { verifyOtp } from '../services/apiService';
import { Loader2, X } from 'lucide-react';
import toast from 'react-hot-toast';

interface OtpModalProps {
  email: string;
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

const OtpModal: React.FC<OtpModalProps> = ({ email, isOpen, onClose, onSuccess }) => {
  const [otp, setOtp] = useState('');
  const [isVerifying, setIsVerifying] = useState(false);

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (otp.length !== 6) {
      toast.error('OTP must be exactly 6 characters.');
      return;
    }

    setIsVerifying(true);
    try {
      await verifyOtp(email, otp);
      toast.success('Successfully verified!');
      setOtp('');
      onSuccess(); // Close modal and refresh data via parent
    } catch (err: any) {
      if (err.response?.data?.message) {
        toast.error(`Verification failed: ${err.response.data.message}`);
      } else {
        toast.error('Invalid OTP or network error. Please try again.');
      }
    } finally {
      setIsVerifying(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 overflow-y-auto">
      <div className="flex items-center justify-center min-h-screen pt-4 px-4 pb-20 text-center sm:block sm:p-0">
        <div className="fixed inset-0 transition-opacity" aria-hidden="true" onClick={onClose}>
          <div className="absolute inset-0 bg-gray-500 opacity-75"></div>
        </div>

        <span className="hidden sm:inline-block sm:align-middle sm:h-screen" aria-hidden="true">&#8203;</span>

        <div className="inline-block align-bottom bg-white rounded-lg text-left overflow-hidden shadow-xl transform transition-all sm:my-8 sm:align-middle sm:max-w-lg sm:w-full">
          <div className="bg-white px-4 pt-5 pb-4 sm:p-6 sm:pb-4 relative">
            <button
              onClick={onClose}
              className="absolute top-4 right-4 text-gray-400 hover:text-gray-500 focus:outline-none"
            >
              <X className="h-6 w-6" />
            </button>

            <div className="sm:flex sm:items-start">
              <div className="mt-3 text-center sm:mt-0 sm:ml-4 sm:text-left w-full">
                <h3 className="text-lg leading-6 font-medium text-gray-900 mb-2">
                  Verify OTP
                </h3>
                <div className="mt-2">
                  <p className="text-sm text-gray-500 mb-4">
                    Please enter the 6-digit one-time password sent to <span className="font-semibold text-gray-900">{email}</span>.
                  </p>

                  <form onSubmit={handleSubmit}>
                    <div className="mb-4">
                      <input
                        type="text"
                        maxLength={6}
                        required
                        value={otp}
                        onChange={(e) => setOtp(e.target.value)}
                        className="w-full text-center text-2xl tracking-widest px-3 py-3 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-blue-500 focus:border-blue-500 uppercase"
                        placeholder="••••••"
                        autoFocus
                      />
                    </div>

                    <button
                      type="submit"
                      disabled={otp.length !== 6 || isVerifying}
                      className="w-full flex justify-center py-2 px-4 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 disabled:bg-gray-400 disabled:cursor-not-allowed transition-colors"
                    >
                      {isVerifying ? (
                        <Loader2 className="h-5 w-5 animate-spin" />
                      ) : (
                        'Verify'
                      )}
                    </button>
                  </form>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default OtpModal;
