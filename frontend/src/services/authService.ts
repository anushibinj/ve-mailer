import api from '../api';
import type {
  AuthResponse,
  ApiResponseWrapper,
  SignupRequest,
  LoginRequest,
  VerifySignupOtpRequest,
  ForgotPasswordRequest,
  VerifyResetOtpRequest,
  ResetPasswordRequest,
  RefreshTokenRequest,
  UserProfile,
} from '../types/auth';

const TOKEN_KEY = 'accessToken';
const REFRESH_TOKEN_KEY = 'refreshToken';
const USER_KEY = 'user';

// --- Token Management ---

export const getAccessToken = (): string | null => {
  return localStorage.getItem(TOKEN_KEY);
};

export const getRefreshToken = (): string | null => {
  return localStorage.getItem(REFRESH_TOKEN_KEY);
};

export const getStoredUser = (): UserProfile | null => {
  const user = localStorage.getItem(USER_KEY);
  return user ? JSON.parse(user) : null;
};

export const storeAuthData = (data: AuthResponse): void => {
  localStorage.setItem(TOKEN_KEY, data.accessToken);
  localStorage.setItem(REFRESH_TOKEN_KEY, data.refreshToken);
  localStorage.setItem(USER_KEY, JSON.stringify(data.user));
};

export const clearAuthData = (): void => {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
};

// --- Auth API Calls ---

export const signup = async (data: SignupRequest): Promise<ApiResponseWrapper> => {
  const response = await api.post<ApiResponseWrapper>('/api/auth/signup', data);
  return response.data;
};

export const verifySignupOtp = async (data: VerifySignupOtpRequest): Promise<AuthResponse> => {
  const response = await api.post<AuthResponse>('/api/auth/verify-signup', data);
  storeAuthData(response.data);
  return response.data;
};

export const login = async (data: LoginRequest): Promise<AuthResponse> => {
  const response = await api.post<AuthResponse>('/api/auth/login', data);
  storeAuthData(response.data);
  return response.data;
};

export const refreshAccessToken = async (): Promise<AuthResponse> => {
  const refreshToken = getRefreshToken();
  if (!refreshToken) {
    throw new Error('No refresh token available');
  }
  const data: RefreshTokenRequest = { refreshToken };
  const response = await api.post<AuthResponse>('/api/auth/refresh', data);
  storeAuthData(response.data);
  return response.data;
};

export const logout = async (): Promise<void> => {
  const refreshToken = getRefreshToken();
  if (refreshToken) {
    try {
      await api.post('/api/auth/logout', { refreshToken });
    } catch {
      // Logout even if server call fails
    }
  }
  clearAuthData();
};

export const forgotPassword = async (data: ForgotPasswordRequest): Promise<ApiResponseWrapper> => {
  const response = await api.post<ApiResponseWrapper>('/api/auth/forgot-password', data);
  return response.data;
};

export const verifyResetOtp = async (data: VerifyResetOtpRequest): Promise<ApiResponseWrapper> => {
  const response = await api.post<ApiResponseWrapper>('/api/auth/verify-reset-otp', data);
  return response.data;
};

export const resetPassword = async (data: ResetPasswordRequest): Promise<ApiResponseWrapper> => {
  const response = await api.post<ApiResponseWrapper>('/api/auth/reset-password', data);
  return response.data;
};

export const getCurrentUser = async (): Promise<UserProfile> => {
  const response = await api.get<UserProfile>('/api/auth/me');
  return response.data;
};

// --- Role Helpers ---

export const hasRole = (user: UserProfile | null, role: string): boolean => {
  return user?.roles?.includes(role) ?? false;
};

export const isAdmin = (user: UserProfile | null): boolean => {
  return hasRole(user, 'ADMIN');
};
