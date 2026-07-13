import api from '../api';

export type WorkspaceStatus = 'ENABLED' | 'DRAFT' | 'DISABLED';
export type WorkspaceConnectivityStatus = 'UNKNOWN' | 'ONLINE' | 'OFFLINE';

export interface Workspace {
  id: string;
  title: string;
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
  sharedSpaceId: string;
  workspaceId: string;
  clientId: string;
  clientKey: string;
  rootUrl: string;
  status?: WorkspaceStatus;
}

export interface WorkspaceUpdatePayload {
  title: string;
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
  ownerEmail?: string | null;
  editable?: boolean;
  adminManaged?: boolean;
}

export interface FilterCreatePayload {
  title: string;
  description: string;
  entityType: string;
  fields: string[];
  criteria: FilterCriteriaClause[];
  filterQueryString?: string;
}

export interface FilterUpdatePayload {
  title: string;
  description: string;
  entityType: string;
  fields: string[];
  criteria: FilterCriteriaClause[];
  filterQueryString?: string;
}

export interface ParseFilterQueryStringPayload {
  filterQueryString: string;
}

export interface ParsedFilterQueryResponse {
  fields: string[];
  criteria: FilterCriteriaClause[];
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
  /** Non-null when this is a group subscription. */
  groupId?: string | null;
  groupName?: string | null;
  groupMemberCount?: number | null;
}

export interface SubscriptionCreatePayload {
  filterId: string;
  schedule: Schedule;
  /** Admins and workspace admins may pass this to subscribe another user. */
  recipientEmail?: string;
}

export interface SubscriptionUpdatePayload {
  schedule: Schedule;
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
  entityType: string
): Promise<OctaneFieldValueDto[]> => {
  const response = await api.get(`/api/v1/workspaces/${workspaceId}/octane/field-values`, {
    params: { fieldName, entityType },
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
  deliveryStatus: 'SUCCESS' | 'FAILED';
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
  status?: 'SUCCESS' | 'FAILED';
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
