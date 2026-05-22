import api from '../api';

export interface Workspace {
  id: string;
  title: string;
  sharedSpaceId: string;
  workspaceId: string;
  rootUrl: string;
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
}

export interface WorkspaceCreatePayload {
  title: string;
  sharedSpaceId: string;
  workspaceId: string;
  clientId: string;
  clientKey: string;
  rootUrl: string;
}

export interface WorkspaceUpdatePayload {
  title: string;
  sharedSpaceId: string;
  workspaceId: string;
  clientId: string;
  // Leave as "(unchanged)" to preserve existing key; provide a new value to replace
  clientKey?: string;
  rootUrl: string;
}

export interface FilterCriteriaClause {
  field: string;
  operator: string;
  values: string[];
}

export interface Filter {
  id: string;
  title: string;
  description: string;
  entityType: string;
  fields: string;   // JSON string from backend
  criteria: string;  // JSON string from backend
}

export interface FilterCreatePayload {
  title: string;
  description: string;
  entityType: string;
  fields: string[];
  criteria: FilterCriteriaClause[];
}

export interface FilterUpdatePayload {
  title: string;
  description: string;
  entityType: string;
  fields: string[];
  criteria: FilterCriteriaClause[];
}

export interface Schedule {
  type: 'DAILY' | 'WEEKLY';
  hours: number[];
}

export interface Subscription {
  id: string;
  recipientEmail: string;
  filterId: string;
  filterTitle: string;
  schedule: Schedule;
}

export interface SubscriptionCreatePayload {
  filterId: string;
  schedule: Schedule;
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
  const response = await api.get('/api/v1/workspaces');
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
  username: string;
  password: string; // always "(unchanged)" from the API
  startTlsEnabled: boolean;
  configured: boolean;
}

export interface NotificationPreferencesUpdatePayload {
  host: string;
  port: number;
  username: string;
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
