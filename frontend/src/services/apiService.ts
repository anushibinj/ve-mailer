import api from '../api';

export type WorkspaceStatus = 'ENABLED' | 'DRAFT' | 'DISABLED';
export type WorkspaceConnectivityStatus = 'UNKNOWN' | 'ONLINE' | 'OFFLINE';

export interface Workspace {
  id: string;
  title: string;
  workspaceShortcode: string;
  sharedSpaceId: string;
  workspaceId: string;
  rootUrl: string;
  status: WorkspaceStatus;
  connectivityStatus: WorkspaceConnectivityStatus;
  connectivityCheckedAt?: string | null;
  connectivityMessage?: string | null;
}

// Admin workspace type — includes clientId, masked clientKey, and a config flag
export interface WorkspaceAdmin {
  id: string;
  title: string;
  workspaceShortcode: string;
  sharedSpaceId: string;
  workspaceId: string;
  clientId: string;
  clientKey: string; // always "(unchanged)" from the API
  clientKeyConfigured: boolean;
  rootUrl: string;
  status: WorkspaceStatus;
  connectivityStatus: WorkspaceConnectivityStatus;
  connectivityCheckedAt?: string | null;
  connectivityMessage?: string | null;
}

export interface WorkspaceCreatePayload {
  title: string;
  workspaceShortcode: string;
  sharedSpaceId: string;
  workspaceId: string;
  clientId: string;
  clientKey: string;
  rootUrl: string;
  status?: WorkspaceStatus;
}

export interface WorkspaceUpdatePayload {
  title: string;
  workspaceShortcode: string;
  sharedSpaceId: string;
  workspaceId: string;
  clientId: string;
  // Leave as "(unchanged)" to preserve existing key; provide a new value to replace
  clientKey?: string;
  rootUrl: string;
  status: WorkspaceStatus;
}

export interface WorkspaceConnectionTestPayload {
  workspaceRecordId?: string;
  sharedSpaceId: string;
  workspaceId: string;
  clientId: string;
  clientKey?: string;
  rootUrl: string;
}

export interface WorkspaceConnectionTestResponse {
  success: boolean;
  hasData: boolean;
  workspaceId: string;
  message: string;
}

// Shape of the 409 error body returned when creating/updating a workspace whose
// (Root URL, Shared Space ID, Workspace ID) combination already exists.
export interface WorkspaceConflictErrorData {
  status: number;
  error: string;
  message: string;
  existingWorkspace: {
    id: string;
    name: string;
    rootUrl: string;
    sharedSpaceId: string;
    workspaceId: string;
  };
}

// --- Workspace creation wizard: duplicate pre-check + ValueEdge metadata discovery ---

export interface WorkspaceDuplicateCheckResult {
  duplicate: boolean;
}

export interface WorkspaceDiscoveryPayload {
  rootUrl: string;
  sharedSpaceId: string;
  workspaceId: string;
  clientId: string;
  clientKey: string;
}

export interface WorkspaceDiscoveryResult {
  workspaceTitle: string;
  workspaceShortcode: string;
  shortcodeDetected: boolean;
  warning?: string;
}

// Shape of the 404 error body returned when the Workspace ID entered in Step 1 isn't
// present in the ValueEdge shared space's workspace list — includes the raw JSON response
// so the wizard can show it in a troubleshooting panel.
export interface WorkspaceDiscoveryErrorData {
  status: number;
  error: string;
  message: string;
  rawResponse: string;
  timestamp?: string;
}

/** Backend is the source of truth here — the same duplicate check used by workspace creation. */
export const adminCheckDuplicateWorkspace = async (
  rootUrl: string,
  sharedSpaceId: string,
  workspaceId: string
): Promise<WorkspaceDuplicateCheckResult> => {
  const response = await api.get('/api/v1/workspaces/check-duplicate', {
    params: { rootUrl, sharedSpaceId, workspaceId },
  });
  return response.data;
};

/** The frontend never calls the ValueEdge REST API directly — this endpoint does it server-side. */
export const adminDiscoverWorkspaceMetadata = async (
  payload: WorkspaceDiscoveryPayload
): Promise<WorkspaceDiscoveryResult> => {
  const response = await api.post('/api/v1/workspaces/discover-metadata', payload);
  return response.data;
};

export interface FilterCriteriaClause {
  field: string;
  operator: string;
  values: string[];
  /** How this clause is joined to the previous one: "AND" (default) or "OR". Ignored for the first clause. */
  logicalOperator?: string;
  /** True when values represent reference IDs and should be queried as field EQ {id IN ...}. */
  referenceValues?: boolean;
}

export interface Filter {
  id: string;
  title: string;
  description: string;
  entityType: string;
  fields: string;   // JSON string from backend
  criteria: string;  // JSON string from backend
  orderBy?: string | null;
  orderByDirection?: 'ASC' | 'DESC' | null;
  ownerEmail?: string | null;
  isPublic?: boolean;
  editable?: boolean;
  adminManaged?: boolean;
  publicTemplate?: boolean;
}

export interface FilterCreatePayload {
  title: string;
  description: string;
  entityType: string;
  fields: string[];
  criteria: FilterCriteriaClause[];
  isPublic: boolean;
  orderBy?: string;
  orderByDirection?: 'ASC' | 'DESC';
  filterQueryString?: string;
}

export interface FilterUpdatePayload {
  title: string;
  description: string;
  entityType: string;
  fields: string[];
  criteria: FilterCriteriaClause[];
  isPublic: boolean;
  orderBy?: string;
  orderByDirection?: 'ASC' | 'DESC';
  filterQueryString?: string;
}

export interface ParseFilterQueryStringPayload {
  filterQueryString: string;
}

export interface ParsedFilterQueryResponse {
  fields: string[];
  criteria: FilterCriteriaClause[];
  orderBy?: string | null;
  orderByDirection?: 'ASC' | 'DESC' | null;
  filterQueryString: string;
}

export interface Schedule {
  type: 'DAILY' | 'WEEKLY';
  hours: number[];
}

export interface Subscription {
  id: string;
  /** Null for group subscriptions. */
  recipientEmail: string | null;
  filterId: string;
  filterTitle: string;
  schedule: Schedule;
  triageSlaThreshold: 'GREEN' | 'YELLOW' | 'RED';
  /** Non-null when this is a group subscription. */
  groupId?: string | null;
  groupName?: string | null;
  groupMemberCount?: number | null;
  /** ACTIVE means the subscription is sending emails; DISABLED means it is paused. */
  status: 'ACTIVE' | 'DISABLED' | 'PENDING' | null;
}

export interface SubscriptionCreatePayload {
  filterId: string;
  schedule: Schedule;
  triageSlaThreshold?: 'GREEN' | 'YELLOW' | 'RED';
  /** Admins and workspace admins may pass this to subscribe another user. */
  recipientEmail?: string;
}

export interface SubscriptionUpdatePayload {
  schedule: Schedule;
  triageSlaThreshold?: 'GREEN' | 'YELLOW' | 'RED';
}

// --- Workspaces ---

export const fetchWorkspaces = async (): Promise<Workspace[]> => {
  const response = await api.get('/api/v1/workspaces');
  return response.data;
};

// --- Admin workspace CRUD ---

export const adminFetchWorkspaces = async (): Promise<WorkspaceAdmin[]> => {
  const response = await api.get('/api/v1/workspaces/all');
  return response.data;
};

export const adminFetchWorkspace = async (id: string): Promise<WorkspaceAdmin> => {
  const response = await api.get(`/api/v1/workspaces/${id}`);
  return response.data;
};

export const adminCreateWorkspace = async (
  payload: WorkspaceCreatePayload
): Promise<WorkspaceAdmin> => {
  const response = await api.post('/api/v1/workspaces', payload);
  return response.data;
};

export const adminUpdateWorkspace = async (
  id: string,
  payload: WorkspaceUpdatePayload
): Promise<WorkspaceAdmin> => {
  const response = await api.put(`/api/v1/workspaces/${id}`, payload);
  return response.data;
};

export const adminDeleteWorkspace = async (id: string): Promise<void> => {
  await api.delete(`/api/v1/workspaces/${id}`);
};

export const adminTestWorkspaceConnection = async (
  payload: WorkspaceConnectionTestPayload
): Promise<WorkspaceConnectionTestResponse> => {
  const response = await api.post('/api/v1/workspaces/test-connection', payload);
  return response.data;
};

// --- Filters (workspace-scoped) ---

export const fetchFilters = async (workspaceId: string): Promise<Filter[]> => {
  const response = await api.get(`/api/v1/workspaces/${workspaceId}/filters`);
  return response.data;
};

export const createFilter = async (workspaceId: string, payload: FilterCreatePayload): Promise<Filter> => {
  const response = await api.post(`/api/v1/workspaces/${workspaceId}/filters`, payload);
  return response.data;
};

export const updateFilter = async (workspaceId: string, filterId: string, payload: FilterUpdatePayload): Promise<Filter> => {
  const response = await api.put(`/api/v1/workspaces/${workspaceId}/filters/${filterId}`, payload);
  return response.data;
};

export const executeFilter = async (workspaceId: string, filterId: string): Promise<Record<string, unknown>[]> => {
  const response = await api.post(`/api/v1/workspaces/${workspaceId}/filters/${filterId}/execute`);
  return response.data;
};

export interface PreviewResponse {
  records: Record<string, string>[];
  aiSummaryGenerated: boolean;
}

export const previewFilter = async (workspaceId: string, filterId: string, limit = 10): Promise<PreviewResponse> => {
  const response = await api.get(`/api/v1/workspaces/${workspaceId}/filters/${filterId}/preview`, {
    params: { limit },
  });
  return response.data;
};

export const cloneFilter = async (workspaceId: string, filterId: string): Promise<FilterCreatePayload> => {
  const response = await api.get(`/api/v1/workspaces/${workspaceId}/filters/${filterId}/clone`);
  return response.data;
};

export const parseFilterQueryString = async (
  workspaceId: string,
  payload: ParseFilterQueryStringPayload
): Promise<ParsedFilterQueryResponse> => {
  const response = await api.post(`/api/v1/workspaces/${workspaceId}/filters/parse-query-string`, payload);
  return response.data;
};

export const getFilterQueryString = async (workspaceId: string, filterId: string): Promise<string> => {
  const response = await api.get(`/api/v1/workspaces/${workspaceId}/filters/${filterId}/query-string`);
  return response.data.filterQueryString;
};

export const deleteFilter = async (workspaceId: string, filterId: string): Promise<void> => {
  await api.delete(`/api/v1/workspaces/${workspaceId}/filters/${filterId}`);
};

// --- Octane Metadata (Easy Filter Builder) ---

/** Describes a filterable Octane field. */
export interface OctaneFieldDto {
  /** Octane API field name, e.g. "phase", "owner", "severity" */
  name: string;
  /** Human-readable label shown in the UI, e.g. "Phase", "Owner", "Severity" */
  label: string;
  /** Octane field type: "string" | "memo" | "integer" | "float" | "boolean" | "date_time" | "reference" */
  fieldType: string;
  /** True when this is a reference field pointing to another entity */
  reference: boolean;
  /** True when multiple values can be selected */
  multiReference: boolean;
  /** For reference fields: the Octane entity type of the target (e.g. "list_node", "workspace_user") */
  targetEntityType?: string;
  /** For list_node targets: the logical name used to scope the list (e.g. "list_node.severity") */
  targetLogicalName?: string;
}

/** A selectable value for a reference field (id = what is stored; name = what is shown). */
export interface OctaneFieldValueDto {
  id: string;
  name: string;
}

/**
 * Returns filterable fields for the given Octane entity type, with human-readable labels.
 * Used to populate the "Field" dropdown in the Easy Filter Builder.
 */
export const fetchFilterableFields = async (
  workspaceId: string,
  entityType: string
): Promise<OctaneFieldDto[]> => {
  const response = await api.get(`/api/v1/workspaces/${workspaceId}/octane/fields`, {
    params: { entityType },
  });
  return response.data;
};

/**
 * Returns the selectable values for a reference field so the UI can show a
 * searchable dropdown (e.g. phase names, user names, severity options).
 */
export const fetchFieldValues = async (
  workspaceId: string,
  fieldName: string,
  entityType: string,
  search?: string,
  ids?: string[]
): Promise<OctaneFieldValueDto[]> => {
  const normalizedIds = ids?.map(id => id.trim()).filter(Boolean);
  const response = await api.get(`/api/v1/workspaces/${workspaceId}/octane/field-values`, {
    params: {
      fieldName,
      entityType,
      ...(search ? { search } : {}),
      ...(normalizedIds && normalizedIds.length > 0 ? { ids: normalizedIds.join(',') } : {}),
    },
  });
  return response.data;
};

// --- Subscriptions ---

export const fetchSubscriptionsByWorkspace = async (workspaceId: string): Promise<Subscription[]> => {
  const response = await api.get(`/api/v1/workspaces/${workspaceId}/subscriptions`);
  return response.data;
};

export const createSubscription = async (
  workspaceId: string,
  payload: SubscriptionCreatePayload
): Promise<Subscription> => {
  const response = await api.post(`/api/v1/workspaces/${workspaceId}/subscriptions`, payload);
  return response.data;
};

export const updateSubscription = async (
  workspaceId: string,
  subscriptionId: string,
  payload: SubscriptionUpdatePayload
): Promise<Subscription> => {
  const response = await api.put(
    `/api/v1/workspaces/${workspaceId}/subscriptions/${subscriptionId}`,
    payload
  );
  return response.data;
};

export const deleteSubscription = async (
  workspaceId: string,
  subscriptionId: string
): Promise<void> => {
  await api.delete(`/api/v1/workspaces/${workspaceId}/subscriptions/${subscriptionId}`);
};

export const runSubscription = async (workspaceId: string, subscriptionId: string): Promise<void> => {
  await api.post(`/api/v1/workspaces/${workspaceId}/subscriptions/${subscriptionId}/run`);
};

export const toggleSubscription = async (workspaceId: string, subscriptionId: string): Promise<Subscription> => {
  const response = await api.patch(
    `/api/v1/workspaces/${workspaceId}/subscriptions/${subscriptionId}/toggle`
  );
  return response.data;
};

// --- Public Issue Reporting ---

export type IssueStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED';

export interface IssueReport {
  id: string;
  reporterEmail: string | null;
  message: string | null;
  status: IssueStatus;
  createdAt: string;
  updatedAt: string;
  hasScreenshot: boolean;
  screenshotContentType: string | null;
  screenshotBase64: string | null;
}

export interface SubmitIssuePayload {
  message?: string;
  reporterEmail?: string;
  screenshot?: File | null;
}

export interface IssueUploadConfig {
  maxUploadBytes: number;
  maxStoredScreenshotBytes: number;
}

export const fetchIssueUploadConfig = async (): Promise<IssueUploadConfig> => {
  const response = await api.get('/api/v1/issues/config');
  return response.data;
};

export const submitIssueReport = async (
  payload: SubmitIssuePayload
): Promise<{ success: boolean; message: string }> => {
  const formData = new FormData();
  if (payload.message?.trim()) {
    formData.append('message', payload.message.trim());
  }
  if (payload.reporterEmail?.trim()) {
    formData.append('reporterEmail', payload.reporterEmail.trim());
  }
  if (payload.screenshot) {
    formData.append('screenshot', payload.screenshot);
  }

  const response = await api.post('/api/v1/issues', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return response.data;
};

export const adminGetIssues = async (): Promise<IssueReport[]> => {
  const response = await api.get('/api/admin/issues');
  return response.data;
};

export const adminUpdateIssueStatus = async (
  issueId: string,
  status: IssueStatus
): Promise<IssueReport> => {
  const response = await api.patch(`/api/admin/issues/${issueId}/status`, { status });
  return response.data;
};

// --- Admin Notification Preferences ---

export interface NotificationPreferencesResponse {
  host: string;
  port: number;
  fromAddress: string;
  requiresAuth: boolean;
  username: string;
  password: string; // always "(unchanged)" from the API when requiresAuth=true
  startTlsEnabled: boolean;
  configured: boolean;
  /** Admin emails that receive system-level notifications (e.g. user onboarded). */
  adminNotificationEmails: string[];
}

export interface NotificationPreferencesUpdatePayload {
  host: string;
  port: number;
  fromAddress: string;
  requiresAuth: boolean;
  username?: string;
  // Leave as "(unchanged)" to preserve existing password; provide new value to replace
  password?: string;
  startTlsEnabled: boolean;
  /** Admin emails that receive system-level notifications. May contain addresses not in the system. */
  adminNotificationEmails?: string[];
}

export const adminGetNotificationPreferences = async (): Promise<NotificationPreferencesResponse> => {
  const response = await api.get('/api/admin/notification-preferences');
  return response.data;
};

export const adminUpdateNotificationPreferences = async (
  payload: NotificationPreferencesUpdatePayload
): Promise<NotificationPreferencesResponse> => {
  const response = await api.put('/api/admin/notification-preferences', payload);
  return response.data;
};

// --- Admin AI Preferences ---

export interface AiPreferencesResponse {
  apiKey: string; // always "(unchanged)" from the API
  baseUrl: string;
  chatCompletionsPath: string;
  model: string;
  configured: boolean;
}

export interface AiPreferencesUpdatePayload {
  // Leave as "(unchanged)" to preserve existing key; provide new value to replace
  apiKey?: string;
  baseUrl: string;
  chatCompletionsPath: string;
  model: string;
}

export const adminGetAiPreferences = async (): Promise<AiPreferencesResponse> => {
  const response = await api.get('/api/admin/ai-preferences');
  return response.data;
};

export const adminUpdateAiPreferences = async (
  payload: AiPreferencesUpdatePayload
): Promise<AiPreferencesResponse> => {
  const response = await api.put('/api/admin/ai-preferences', payload);
  return response.data;
};

// --- Mail Analytics ---

export interface MailAnalyticsSummary {
  mailsSentToday: number;
  mailsSentPeriod: number;
  uniqueRecipients: number;
  activeWorkspaces: number;
  topFilter: string | null;
  periodDays: number;
}

export interface DailyVolumeEntry {
  date: string;
  count: number;
}

export interface WorkspaceDistributionEntry {
  workspace: string;
  count: number;
}

export interface FilterUsageEntry {
  filter: string;
  count: number;
}

export interface MailAuditLogEntry {
  id: string;
  workspaceId: string | null;
  workspaceTitle: string | null;
  recipientEmail: string;
  filterTemplateId: string | null;
  filterTitle: string | null;
  subscriptionId: string | null;
  userId: string | null;
  mailSubject: string | null;
  ticketCount: number;
  deliveryStatus: 'SUCCESS' | 'FAILED' | 'SKIPPED';
  failureReason: string | null;
  sentAt: string;
  durationMs: number | null;
}

export interface PagedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export const adminGetMailAnalyticsSummary = async (days = 7): Promise<MailAnalyticsSummary> => {
  const response = await api.get('/api/admin/mail-analytics/summary', { params: { days } });
  return response.data;
};

export const adminGetDailyVolume = async (days = 7): Promise<DailyVolumeEntry[]> => {
  const response = await api.get('/api/admin/mail-analytics/daily-volume', { params: { days } });
  return response.data;
};

export const adminGetDailyRecipients = async (days = 7): Promise<DailyVolumeEntry[]> => {
  const response = await api.get('/api/admin/mail-analytics/daily-recipients', { params: { days } });
  return response.data;
};

export const adminGetWorkspaceDistribution = async (days = 30): Promise<WorkspaceDistributionEntry[]> => {
  const response = await api.get('/api/admin/mail-analytics/workspace-distribution', { params: { days } });
  return response.data;
};

export const adminGetFilterUsage = async (days = 30): Promise<FilterUsageEntry[]> => {
  const response = await api.get('/api/admin/mail-analytics/filter-usage', { params: { days } });
  return response.data;
};

export interface MailHistoryParams {
  workspaceId?: string;
  recipientEmail?: string;
  filterTitle?: string;
  status?: 'SUCCESS' | 'FAILED' | 'SKIPPED';
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

export const adminGetMailHistory = async (params: MailHistoryParams = {}): Promise<PagedResponse<MailAuditLogEntry>> => {
  const response = await api.get('/api/admin/mail-analytics/history', { params });
  return response.data;
};

// --- Admin Users ---

export interface UserSummary {
  id: string;
  name: string;
  email: string;
  roles: string[];
  subscribedFilterCount: number;
  /** True when the account was admin-created and the user has not yet set their own password. */
  mustSetPassword?: boolean;
}

export const adminGetUsers = async (): Promise<UserSummary[]> => {
  const response = await api.get('/api/admin/users');
  return response.data;
};

/** Fetch all users via the workspace-scoped endpoint, accessible to workspace admins. */
export const fetchWorkspaceUsers = async (workspaceId: string): Promise<UserSummary[]> => {
  const response = await api.get(`/api/v1/workspaces/${workspaceId}/users`);
  return response.data;
};

export const adminOnboardUser = async (name: string, email: string): Promise<{ message: string }> => {
  const response = await api.post('/api/admin/users', { name, email });
  return response.data;
};

export const adminDeleteUser = async (userId: string): Promise<{ success: boolean; message: string }> => {
  const response = await api.delete(`/api/admin/users/${userId}`);
  return response.data;
};

export const adminResendInvite = async (userId: string): Promise<{ success: boolean; message: string }> => {
  const response = await api.post(`/api/admin/users/${userId}/resend-invite`);
  return response.data;
};

export const adminGetNonAppUsers = async (): Promise<string[]> => {
  const response = await api.get('/api/admin/users/non-app-users');
  return response.data;
};

/**
 * Promotes or demotes a user's global role between MEMBER (plain user) and WORKSPACE_ADMIN.
 * Super-admin only. Demotion does not remove existing workspace admin assignments — it only
 * revokes the global WORKSPACE_ADMIN role required (alongside the workspace-level mapping) for
 * any workspace administration action.
 */
export const adminUpdateUserGlobalRole = async (
  userId: string,
  role: 'MEMBER' | 'WORKSPACE_ADMIN'
): Promise<UserSummary> => {
  const response = await api.patch(`/api/admin/users/${userId}/role`, { role });
  return response.data;
};

export const fetchAllowedDomains = async (): Promise<string[]> => {
  const response = await api.get('/api/auth/allowed-domains');
  return response.data;
};

// --- Admin General Settings ---

export interface GeneralSettings {
  queryLimit: number;
}

export const adminGetGeneralSettings = async (): Promise<GeneralSettings> => {
  const response = await api.get('/api/admin/general-settings');
  return response.data;
};

export const adminUpdateGeneralSettings = async (
  payload: GeneralSettings
): Promise<GeneralSettings> => {
  const response = await api.put('/api/admin/general-settings', payload);
  return response.data;
};

// --- Workspace Admin Management ---

export interface WorkspaceAdminEntry {
  id: string;
  workspaceId: string;
  workspaceTitle: string;
  userId: string;
  userName: string;
  userEmail: string;
  createdAt: string;
  createdBy: string;
}

export const fetchWorkspaceAdmins = async (workspaceId: string): Promise<WorkspaceAdminEntry[]> => {
  const response = await api.get(`/api/v1/workspaces/${workspaceId}/admins`);
  return response.data;
};

export const assignWorkspaceAdmin = async (workspaceId: string, userId: string): Promise<WorkspaceAdminEntry> => {
  const response = await api.post(`/api/v1/workspaces/${workspaceId}/admins`, { userId });
  return response.data;
};

export const removeWorkspaceAdmin = async (workspaceId: string, userId: string): Promise<void> => {
  await api.delete(`/api/v1/workspaces/${workspaceId}/admins/${userId}`);
};

// --- Recipient Groups ---

export interface RecipientGroup {
  id: string;
  workspaceId: string;
  name: string;
  description: string | null;
  memberEmails: string[];
  memberCount: number;
  createdAt: string;
  createdBy: string | null;
}

export interface RecipientGroupCreatePayload {
  name: string;
  description?: string;
  memberEmails?: string[];
}

export interface RecipientGroupUpdatePayload {
  name: string;
  description?: string;
  memberEmails: string[];
}

export const fetchRecipientGroups = async (workspaceId: string): Promise<RecipientGroup[]> => {
  const response = await api.get(`/api/v1/workspaces/${workspaceId}/recipient-groups`);
  return response.data;
};

export const createRecipientGroup = async (
  workspaceId: string,
  payload: RecipientGroupCreatePayload
): Promise<RecipientGroup> => {
  const response = await api.post(`/api/v1/workspaces/${workspaceId}/recipient-groups`, payload);
  return response.data;
};

export const updateRecipientGroup = async (
  workspaceId: string,
  groupId: string,
  payload: RecipientGroupUpdatePayload
): Promise<RecipientGroup> => {
  const response = await api.put(
    `/api/v1/workspaces/${workspaceId}/recipient-groups/${groupId}`,
    payload
  );
  return response.data;
};

export const deleteRecipientGroup = async (workspaceId: string, groupId: string): Promise<void> => {
  await api.delete(`/api/v1/workspaces/${workspaceId}/recipient-groups/${groupId}`);
};

export const addRecipientGroupMember = async (
  workspaceId: string,
  groupId: string,
  email: string
): Promise<RecipientGroup> => {
  const response = await api.post(
    `/api/v1/workspaces/${workspaceId}/recipient-groups/${groupId}/members`,
    { email }
  );
  return response.data;
};

export const removeRecipientGroupMember = async (
  workspaceId: string,
  groupId: string,
  email: string
): Promise<RecipientGroup> => {
  const response = await api.delete(
    `/api/v1/workspaces/${workspaceId}/recipient-groups/${groupId}/members/${encodeURIComponent(email)}`
  );
  return response.data;
};

export const createGroupSubscription = async (
  workspaceId: string,
  groupId: string,
  payload: { filterId: string; schedule: Schedule }
): Promise<Subscription> => {
  const response = await api.post(
    `/api/v1/workspaces/${workspaceId}/subscriptions/bulk-group`,
    { ...payload, groupId }
  );
  return response.data;
};
