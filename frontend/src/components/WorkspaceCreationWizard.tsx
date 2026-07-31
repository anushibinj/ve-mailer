import React, { useEffect, useState } from 'react';
import {
  X, Check, ChevronLeft, ChevronRight, Loader2, Eye, EyeOff,
  Building2, AlertTriangle, ChevronDown, Code2, CheckCircle2,
} from 'lucide-react';
import toast from 'react-hot-toast';
import type {
  WorkspaceAdmin,
  WorkspaceStatus,
  WorkspaceCreatePayload,
  WorkspaceConflictErrorData,
  WorkspaceDiscoveryResult,
  WorkspaceDiscoveryErrorData,
} from '../services/apiService';
import {
  adminCheckDuplicateWorkspace,
  adminDiscoverWorkspaceMetadata,
  adminCreateWorkspace,
} from '../services/apiService';
import {
  fieldInputClass,
  DuplicateWorkspaceConflictBanner,
  type ExistingWorkspaceConflict,
} from './workspaceFormShared';

interface WorkspaceCreationWizardProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: (saved: WorkspaceAdmin) => void;
}

type StepNumber = 1 | 2 | 3;

const STEPS: { number: StepNumber; title: string; description: string }[] = [
  {
    number: 1,
    title: 'Workspace Identification',
    description: 'Tell us which ValueEdge workspace you want to connect.',
  },
  {
    number: 2,
    title: 'Authentication',
    description: 'Provide credentials so we can discover workspace details.',
  },
  {
    number: 3,
    title: 'Review & Create',
    description: 'Confirm everything looks right, then create the workspace.',
  },
];

interface Step1Errors {
  rootUrl?: string;
  sharedSpaceId?: string;
  workspaceId?: string;
}

interface Step2Errors {
  clientId?: string;
  clientKey?: string;
}

const StepIndicator: React.FC<{ step: StepNumber; onStepClick: (n: StepNumber) => void }> = ({
  step, onStepClick,
}) => (
  <ol className="flex items-start">
    {STEPS.map((s, idx) => {
      const isCompleted = s.number < step;
      const isCurrent = s.number === step;
      const isClickable = isCompleted;
      return (
        <li key={s.number} className="flex items-start flex-1 last:flex-none">
          <div className="flex flex-col items-center text-center w-24 sm:w-32 shrink-0">
            <button
              type="button"
              onClick={() => isClickable && onStepClick(s.number)}
              disabled={!isClickable}
              aria-current={isCurrent ? 'step' : undefined}
              aria-label={`Step ${s.number}: ${s.title}`}
              className={`h-9 w-9 rounded-full flex items-center justify-center text-sm font-semibold transition-all shrink-0 ${
                isCompleted
                  ? 'bg-indigo-600 text-white hover:bg-indigo-500 cursor-pointer'
                  : isCurrent
                    ? 'bg-indigo-600 text-white ring-4 ring-indigo-100 dark:ring-indigo-500/20'
                    : 'bg-slate-100 dark:bg-slate-800 text-slate-400 dark:text-slate-500 cursor-not-allowed'
              }`}
            >
              {isCompleted ? <Check className="h-4 w-4" /> : s.number}
            </button>
            <p className={`mt-2 text-xs font-semibold leading-tight ${
              isCurrent
                ? 'text-indigo-700 dark:text-indigo-300'
                : isCompleted
                  ? 'text-slate-700 dark:text-slate-300'
                  : 'text-slate-400 dark:text-slate-500'
            }`}
            >
              {s.title}
            </p>
            <p className="mt-0.5 text-[11px] leading-snug text-slate-400 dark:text-slate-500 hidden sm:block">
              {s.description}
            </p>
          </div>
          {idx < STEPS.length - 1 && (
            <div
              className={`flex-1 h-0.5 mt-[18px] rounded-full mx-1 sm:mx-2 transition-colors ${
                s.number < step ? 'bg-indigo-500' : 'bg-slate-200 dark:bg-slate-700'
              }`}
            />
          )}
        </li>
      );
    })}
  </ol>
);

const SummarySection: React.FC<{ title: string; children: React.ReactNode }> = ({ title, children }) => (
  <div>
    <p className="text-[11px] font-semibold uppercase tracking-wide text-slate-400 dark:text-slate-500 mb-2">
      {title}
    </p>
    <div className="rounded-xl border border-slate-200 dark:border-slate-700 divide-y divide-slate-100 dark:divide-slate-800 overflow-hidden">
      {children}
    </div>
  </div>
);

const SummaryRow: React.FC<{ label: string; value: string }> = ({ label, value }) => (
  <div className="flex items-center justify-between gap-4 px-3.5 py-2.5 bg-white dark:bg-slate-800/40">
    <span className="text-xs text-slate-500 dark:text-slate-400">{label}</span>
    <span className="text-sm font-medium text-slate-900 dark:text-slate-100 truncate max-w-[60%] text-right">{value}</span>
  </div>
);

const WorkspaceCreationWizard: React.FC<WorkspaceCreationWizardProps> = ({ isOpen, onClose, onSuccess }) => {
  const [step, setStep] = useState<StepNumber>(1);

  // Step 1 — Workspace Identification
  const [rootUrl, setRootUrl] = useState('');
  const [sharedSpaceId, setSharedSpaceId] = useState('');
  const [workspaceId, setWorkspaceId] = useState('');
  const [step1Errors, setStep1Errors] = useState<Step1Errors>({});
  const [isCheckingDuplicate, setIsCheckingDuplicate] = useState(false);
  const [duplicateConflict, setDuplicateConflict] = useState<ExistingWorkspaceConflict | null>(null);

  // Step 2 — Authentication + metadata discovery
  const [clientId, setClientId] = useState('');
  const [clientKey, setClientKey] = useState('');
  const [showKey, setShowKey] = useState(false);
  const [step2Errors, setStep2Errors] = useState<Step2Errors>({});
  const [isDiscovering, setIsDiscovering] = useState(false);
  const [discoveryResult, setDiscoveryResult] = useState<WorkspaceDiscoveryResult | null>(null);
  const [discoveryError, setDiscoveryError] = useState<{ message: string; rawResponse?: string } | null>(null);
  const [showRawResponse, setShowRawResponse] = useState(false);

  // Step 3 — Review & Create
  const [isCreating, setIsCreating] = useState(false);

  useEffect(() => {
    if (isOpen) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setStep(1);
      setRootUrl(''); setSharedSpaceId(''); setWorkspaceId('');
      setStep1Errors({}); setIsCheckingDuplicate(false); setDuplicateConflict(null);
      setClientId(''); setClientKey(''); setShowKey(false);
      setStep2Errors({}); setIsDiscovering(false); setDiscoveryResult(null);
      setDiscoveryError(null); setShowRawResponse(false);
      setIsCreating(false);
    }
  }, [isOpen]);

  const isBusy = isCheckingDuplicate || isDiscovering || isCreating;

  const handleStepClick = (n: StepNumber) => {
    if (isBusy) return;
    setStep(n);
  };

  const handleBack = () => {
    if (isBusy || step === 1) return;
    setStep((s) => (s - 1) as StepNumber);
  };

  // Step 1 -> Step 2: re-runs the composite (Root URL, Shared Space ID, Workspace ID) duplicate
  // check every time — this is the same backend validation used at creation time, so the
  // wizard can never drift from what the final "Create" call will enforce.
  const handleStep1Next = async () => {
    const errs: Step1Errors = {};
    if (!rootUrl.trim()) errs.rootUrl = 'Root URL is required';
    if (!sharedSpaceId.trim()) errs.sharedSpaceId = 'Shared Space ID is required';
    if (!workspaceId.trim()) errs.workspaceId = 'Workspace ID is required';
    setStep1Errors(errs);
    if (Object.keys(errs).length > 0) return;

    setDuplicateConflict(null);
    setIsCheckingDuplicate(true);
    try {
      await adminCheckDuplicateWorkspace(rootUrl.trim(), sharedSpaceId.trim(), workspaceId.trim());
      setStep(2);
    } catch (err: unknown) {
      const axiosErr = err as { response?: { status?: number; data?: WorkspaceConflictErrorData } };
      if (axiosErr.response?.status === 409) {
        setDuplicateConflict(axiosErr.response.data?.existingWorkspace ?? null);
      } else {
        toast.error('Failed to validate the workspace identifiers. Please try again.');
      }
    } finally {
      setIsCheckingDuplicate(false);
    }
  };

  // Step 2 -> Step 3: calls the backend discovery endpoint (the frontend never talks to
  // ValueEdge directly). Re-run every time the user advances so edits to any field are reflected.
  const handleStep2Next = async () => {
    const errs: Step2Errors = {};
    if (!clientId.trim()) errs.clientId = 'Client ID is required';
    if (!clientKey.trim()) errs.clientKey = 'Client Key is required';
    setStep2Errors(errs);
    if (Object.keys(errs).length > 0) return;

    setDiscoveryError(null);
    setShowRawResponse(false);
    setIsDiscovering(true);
    try {
      const result = await adminDiscoverWorkspaceMetadata({
        rootUrl: rootUrl.trim(),
        sharedSpaceId: sharedSpaceId.trim(),
        workspaceId: workspaceId.trim(),
        clientId: clientId.trim(),
        clientKey: clientKey.trim(),
      });
      setDiscoveryResult(result);
      setStep(3);
    } catch (err: unknown) {
      const axiosErr = err as {
        response?: { status?: number; data?: WorkspaceDiscoveryErrorData | { message?: string } };
      };
      // Both the 404 (Workspace ID not in the returned list) and 400 (ValueEdge call itself
      // failed, e.g. invalid Shared Space ID or rejected credentials) error shapes may carry a
      // `rawResponse` — show it whenever present rather than gating on a specific status code.
      const data = axiosErr.response?.data as (WorkspaceDiscoveryErrorData & { message?: string }) | undefined;
      setDiscoveryError({
        message: data?.message ?? 'Failed to discover workspace metadata. Please check your credentials and try again.',
        rawResponse: data?.rawResponse,
      });
    } finally {
      setIsDiscovering(false);
    }
  };

  const handleCreate = async () => {
    if (!discoveryResult) return;
    setIsCreating(true);
    try {
      const status: WorkspaceStatus = discoveryResult.shortcodeDetected ? 'ENABLED' : 'DRAFT';
      const payload: WorkspaceCreatePayload = {
        title: discoveryResult.workspaceTitle,
        workspaceShortcode: discoveryResult.workspaceShortcode,
        sharedSpaceId: sharedSpaceId.trim(),
        workspaceId: workspaceId.trim(),
        clientId: clientId.trim(),
        clientKey: clientKey.trim(),
        rootUrl: rootUrl.trim(),
        status,
      };
      const saved = await adminCreateWorkspace(payload);
      toast.success('Workspace created successfully');
      onSuccess(saved);
    } catch (err: unknown) {
      const axiosErr = err as {
        response?: { status?: number; data?: WorkspaceConflictErrorData | { message?: string } };
      };
      if (axiosErr.response?.status === 409) {
        // Race condition: another request created the exact same combination between our
        // Step 1 pre-check and this final submit. Jump back to Step 1 without losing any data.
        const conflictData = axiosErr.response.data as WorkspaceConflictErrorData;
        setDuplicateConflict(conflictData?.existingWorkspace ?? null);
        setStep(1);
        toast.error('A workspace with the same Root URL, Shared Space ID, and Workspace ID already exists.');
        return;
      }
      const dataErr = axiosErr.response?.data as { message?: string } | undefined;
      toast.error(dataErr?.message ?? 'Failed to create workspace');
    } finally {
      setIsCreating(false);
    }
  };

  if (!isOpen) return null;

  const shortcodeDetected = discoveryResult?.shortcodeDetected ?? false;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div
        className="absolute inset-0 bg-slate-900/60 dark:bg-slate-950/70 backdrop-blur-sm animate-fade-in"
        onClick={() => !isBusy && onClose()}
        aria-hidden="true"
      />

      <div className="relative bg-white dark:bg-slate-900 rounded-2xl shadow-2xl dark:shadow-slate-950/60 border border-slate-100/80 dark:border-slate-700/50 w-full max-w-2xl overflow-hidden animate-scale-in">
        <div className="h-1 bg-gradient-to-r from-indigo-500 via-violet-500 to-purple-500" />

        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100 dark:border-slate-800">
          <div className="flex items-center gap-2.5">
            <div className="h-8 w-8 rounded-lg bg-indigo-50 dark:bg-indigo-500/10 flex items-center justify-center">
              <Building2 className="h-4 w-4 text-indigo-600 dark:text-indigo-400" />
            </div>
            <h2 className="text-base font-semibold text-slate-900 dark:text-white">Create Workspace</h2>
          </div>
          <button
            onClick={() => !isBusy && onClose()}
            disabled={isBusy}
            className="p-1.5 rounded-lg text-slate-400 dark:text-slate-500 hover:text-slate-600 dark:hover:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800 disabled:opacity-40 transition-colors cursor-pointer"
            aria-label="Close"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        {/* Step indicator */}
        <div className="px-6 pt-5 pb-4 border-b border-slate-100 dark:border-slate-800">
          <StepIndicator step={step} onStepClick={handleStepClick} />
        </div>

        {/* Step body */}
        <div key={step} className="px-6 py-5 space-y-4 max-h-[55vh] overflow-y-auto animate-fade-in">
          {step === 1 && (
            <>
              {duplicateConflict && <DuplicateWorkspaceConflictBanner existingWorkspace={duplicateConflict} />}

              <div>
                <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
                  Root URL <span className="text-red-500">*</span>
                </label>
                <input
                  type="url"
                  value={rootUrl}
                  onChange={(e) => { setRootUrl(e.target.value); setStep1Errors(p => ({ ...p, rootUrl: undefined })); setDuplicateConflict(null); }}
                  placeholder="https://octane.example.com"
                  className={fieldInputClass(!!step1Errors.rootUrl)}
                />
                {step1Errors.rootUrl && <p className="mt-1 text-xs text-red-600 dark:text-red-400">{step1Errors.rootUrl}</p>}
                {!step1Errors.rootUrl && <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">Base URL of the ValueEdge / Octane server.</p>}
              </div>

              <div>
                <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
                  Shared Space ID <span className="text-red-500">*</span>
                </label>
                <input
                  type="text"
                  value={sharedSpaceId}
                  onChange={(e) => { setSharedSpaceId(e.target.value); setStep1Errors(p => ({ ...p, sharedSpaceId: undefined })); setDuplicateConflict(null); }}
                  placeholder="e.g. 4001"
                  className={fieldInputClass(!!step1Errors.sharedSpaceId)}
                />
                {step1Errors.sharedSpaceId && <p className="mt-1 text-xs text-red-600 dark:text-red-400">{step1Errors.sharedSpaceId}</p>}
              </div>

              <div>
                <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
                  Workspace ID <span className="text-red-500">*</span>
                </label>
                <input
                  type="text"
                  value={workspaceId}
                  onChange={(e) => { setWorkspaceId(e.target.value); setStep1Errors(p => ({ ...p, workspaceId: undefined })); setDuplicateConflict(null); }}
                  placeholder="e.g. 5015"
                  className={fieldInputClass(!!step1Errors.workspaceId)}
                />
                {step1Errors.workspaceId && <p className="mt-1 text-xs text-red-600 dark:text-red-400">{step1Errors.workspaceId}</p>}
              </div>
            </>
          )}

          {step === 2 && (
            <>
              {discoveryError && (
                <div className="rounded-xl border border-red-300 dark:border-red-600/50 bg-red-50 dark:bg-red-900/20 px-4 py-3 animate-fade-in">
                  <div className="flex gap-3">
                    <AlertTriangle className="h-5 w-5 flex-shrink-0 text-red-500 dark:text-red-400 mt-0.5" />
                    <div className="min-w-0 flex-1 text-sm text-red-800 dark:text-red-200">
                      <p className="font-medium break-words">{discoveryError.message}</p>
                      <p className="mt-1 text-xs text-red-700/80 dark:text-red-300/80">
                        Double-check the credentials and Workspace ID, then try again.
                      </p>
                    </div>
                  </div>
                  {discoveryError.rawResponse && (
                    <div className="mt-3">
                      <button
                        type="button"
                        onClick={() => setShowRawResponse(v => !v)}
                        className="inline-flex items-center gap-1.5 text-xs font-medium text-red-700 dark:text-red-300 hover:underline cursor-pointer"
                      >
                        <Code2 className="h-3.5 w-3.5" />
                        {showRawResponse ? 'Hide raw response' : 'Show raw response'}
                        <ChevronDown className={`h-3.5 w-3.5 transition-transform ${showRawResponse ? 'rotate-180' : ''}`} />
                      </button>
                      {showRawResponse && (
                        <pre className="mt-2 max-h-48 overflow-auto rounded-lg bg-slate-900 dark:bg-black text-slate-100 text-[11px] leading-relaxed p-3 font-mono">
                          {(() => {
                            try {
                              return JSON.stringify(JSON.parse(discoveryError.rawResponse!), null, 2);
                            } catch {
                              return discoveryError.rawResponse;
                            }
                          })()}
                        </pre>
                      )}
                    </div>
                  )}
                </div>
              )}

              <div>
                <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
                  Client ID <span className="text-red-500">*</span>
                </label>
                <input
                  type="text"
                  value={clientId}
                  onChange={(e) => { setClientId(e.target.value); setStep2Errors(p => ({ ...p, clientId: undefined })); }}
                  placeholder="e.g. my-api-client-id"
                  className={fieldInputClass(!!step2Errors.clientId)}
                />
                {step2Errors.clientId && <p className="mt-1 text-xs text-red-600 dark:text-red-400">{step2Errors.clientId}</p>}
              </div>

              <div>
                <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
                  Client Key <span className="text-red-500">*</span>
                </label>
                <div className="relative">
                  <input
                    type={showKey ? 'text' : 'password'}
                    value={clientKey}
                    onChange={(e) => { setClientKey(e.target.value); setStep2Errors(p => ({ ...p, clientKey: undefined })); }}
                    placeholder="Enter client key"
                    className={`${fieldInputClass(!!step2Errors.clientKey)} pr-10`}
                  />
                  <button
                    type="button"
                    onClick={() => setShowKey(v => !v)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 dark:text-slate-500 hover:text-slate-600 dark:hover:text-slate-300 transition-colors cursor-pointer"
                    tabIndex={-1}
                    aria-label={showKey ? 'Hide key' : 'Show key'}
                  >
                    {showKey ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                  </button>
                </div>
                {step2Errors.clientKey && <p className="mt-1 text-xs text-red-600 dark:text-red-400">{step2Errors.clientKey}</p>}
                {!step2Errors.clientKey && (
                  <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
                    Used once to discover the workspace name from ValueEdge, then stored securely.
                  </p>
                )}
              </div>
            </>
          )}

          {step === 3 && (
            <div className="space-y-5">
              <SummarySection title="Connection">
                <SummaryRow label="Root URL" value={rootUrl} />
                <SummaryRow label="Shared Space ID" value={sharedSpaceId} />
                <SummaryRow label="Workspace ID" value={workspaceId} />
              </SummarySection>

              <SummarySection title="Authentication">
                <SummaryRow label="Client ID" value={clientId} />
                <SummaryRow label="Client Secret" value={clientKey ? '•'.repeat(Math.min(clientKey.length, 16)) : '—'} />
              </SummarySection>

              <SummarySection title="Discovered Workspace">
                <SummaryRow label="Workspace Title" value={discoveryResult?.workspaceTitle ?? '—'} />
                <SummaryRow label="Workspace Shortcode" value={discoveryResult?.workspaceShortcode ?? '—'} />
              </SummarySection>

              <div>
                <p className="text-[11px] font-semibold uppercase tracking-wide text-slate-400 dark:text-slate-500 mb-2">
                  Workspace Status
                </p>
                {shortcodeDetected ? (
                  <div className="flex items-center gap-2 rounded-xl border border-green-200 dark:border-green-700/50 bg-green-50 dark:bg-green-900/20 px-4 py-3">
                    <CheckCircle2 className="h-5 w-5 text-green-600 dark:text-green-400 flex-shrink-0" />
                    <div className="min-w-0 flex-1 text-sm text-green-800 dark:text-green-200">
                      <p className="font-medium">Enabled</p>
                      <p className="text-xs mt-0.5 text-green-700/80 dark:text-green-300/80">
                        The workspace shortcode was detected automatically, so this workspace will be created as Enabled.
                      </p>
                    </div>
                  </div>
                ) : (
                  <div className="flex items-start gap-3 rounded-xl border border-amber-300 dark:border-amber-600/50 bg-amber-50 dark:bg-amber-900/20 px-4 py-3">
                    <AlertTriangle className="h-5 w-5 flex-shrink-0 text-amber-500 dark:text-amber-400 mt-0.5" />
                    <div className="min-w-0 flex-1 text-sm text-amber-800 dark:text-amber-200">
                      <p className="font-medium">Draft</p>
                      <p className="mt-1 break-words">
                        This workspace has been created as a Draft because the workspace shortcode could not be
                        determined automatically.
                      </p>
                      {discoveryResult?.warning && (
                        <p className="mt-1 text-xs text-amber-700/80 dark:text-amber-300/80 break-words">{discoveryResult.warning}</p>
                      )}
                    </div>
                  </div>
                )}
              </div>
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="flex items-center justify-between gap-3 px-6 py-4 border-t border-slate-100 dark:border-slate-800">
          <button
            type="button"
            onClick={step === 1 ? onClose : handleBack}
            disabled={isBusy}
            className="inline-flex items-center gap-1.5 px-4 py-2 text-sm font-medium text-slate-600 dark:text-slate-300 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl hover:bg-slate-50 dark:hover:bg-slate-700/60 disabled:opacity-50 transition-colors cursor-pointer"
          >
            {step > 1 && <ChevronLeft className="h-4 w-4" />}
            {step === 1 ? 'Cancel' : 'Back'}
          </button>

          {step === 1 && (
            <button
              type="button"
              onClick={handleStep1Next}
              disabled={isBusy}
              className="inline-flex items-center gap-2 px-4 py-2 text-sm font-semibold text-white bg-indigo-600 hover:bg-indigo-500 dark:bg-indigo-500 dark:hover:bg-indigo-400 rounded-xl disabled:opacity-50 transition-all cursor-pointer"
            >
              {isCheckingDuplicate && <Loader2 className="h-4 w-4 animate-spin" />}
              Continue
              {!isCheckingDuplicate && <ChevronRight className="h-4 w-4" />}
            </button>
          )}
          {step === 2 && (
            <button
              type="button"
              onClick={handleStep2Next}
              disabled={isBusy}
              className="inline-flex items-center gap-2 px-4 py-2 text-sm font-semibold text-white bg-indigo-600 hover:bg-indigo-500 dark:bg-indigo-500 dark:hover:bg-indigo-400 rounded-xl disabled:opacity-50 transition-all cursor-pointer"
            >
              {isDiscovering && <Loader2 className="h-4 w-4 animate-spin" />}
              {isDiscovering ? 'Discovering…' : 'Discover & Continue'}
              {!isDiscovering && <ChevronRight className="h-4 w-4" />}
            </button>
          )}
          {step === 3 && (
            <button
              type="button"
              onClick={handleCreate}
              disabled={isBusy || !discoveryResult}
              className="inline-flex items-center gap-2 px-4 py-2 text-sm font-semibold text-white bg-indigo-600 hover:bg-indigo-500 dark:bg-indigo-500 dark:hover:bg-indigo-400 rounded-xl disabled:opacity-50 transition-all cursor-pointer"
            >
              {isCreating && <Loader2 className="h-4 w-4 animate-spin" />}
              Create Workspace
            </button>
          )}
        </div>
      </div>
    </div>
  );
};

export default WorkspaceCreationWizard;
