import api from '../api';

export interface Workspace {
  id: string;
  name: string;
}

export interface Filter {
  id: string;
  name: string;
}

export interface Subscription {
  id: string;
  recipientEmail: string;
  filterId: string;
  frequency: string;
}

export interface SubscriptionRequestPayload {
  email: string;
  action_type: string;
  workspace_id: string;
  filter_id: string;
  frequency: string;
}

export const fetchWorkspaces = async (): Promise<Workspace[]> => {
  const response = await api.get('/api/v1/workspaces');
  return response.data;
};

export const fetchFilters = async (): Promise<Filter[]> => {
  const response = await api.get('/api/v1/filters');
  return response.data;
};

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
