import api from '../api';

export interface Workspace {
  id: string;
  title: string;
  sharedSpaceId: string;
  workspaceId: string;
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
}

export interface WorkspaceCreatePayload {
  title: string;
  sharedSpaceId: string;
  workspaceId: string;
  clientId: string;
  clientKey: string;
}

export interface WorkspaceUpdatePayload {
  title: string;
  sharedSpaceId: string;
  workspaceId: string;
  clientId: string;
  // Leave as "(unchanged)" to preserve existing key; provide a new value to replace
  clientKey?: string;
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

export interface SubscriptionRequestPayload {
  email: string;
  actionType: string;
  workspaceId: string;
  filterId: string;
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

export const executeFilter = async (workspaceId: string, filterId: string): Promise<any[]> => {
  const response = await api.post(`/api/v1/workspaces/${workspaceId}/filters/${filterId}/execute`);
  return response.data;
};

// --- Subscriptions ---

export const fetchSubscriptionsByWorkspace = async (workspaceId: string): Promise<Subscription[]> => {
  const response = await api.get(`/api/v1/workspaces/${workspaceId}/subscriptions`);
  return response.data;
};

export const requestSubscription = async (payload: SubscriptionRequestPayload): Promise<void> => {
  const response = await api.post('/api/v1/subscriptions/request', payload);
  return response.data;
};

export const verifyOtp = async (email: string, otp: string): Promise<void> => {
  const response = await api.post('/api/v1/subscriptions/verify', { email, otp });
  return response.data;
};

export const runSubscription = async (workspaceId: string, subscriptionId: string): Promise<void> => {
  await api.post(`/api/v1/workspaces/${workspaceId}/subscriptions/${subscriptionId}/run`);
};
