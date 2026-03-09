import api from '../api';

export interface Workspace {
  id: string;
  title: string;
  sharedSpaceId: string;
  workspaceId: string;
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

export interface Subscription {
  id: string;
  recipientEmail: string;
  filterTitle: string;
  frequency: string;
}

export interface SubscriptionRequestPayload {
  email: string;
  actionType: string;
  workspaceId: string;
  filterId: string;
  frequency: string;
}

// --- Workspaces ---

export const fetchWorkspaces = async (): Promise<Workspace[]> => {
  const response = await api.get('/api/v1/workspaces');
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
