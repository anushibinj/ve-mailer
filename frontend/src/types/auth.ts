export interface UserProfile {
  id: string;
  name: string;
  email: string;
  roles: string[];
  /** True when the account was admin-created and the user has not yet set their own password. */
  mustSetPassword?: boolean;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  user: UserProfile;
}

export interface ApiResponseWrapper {
  success: boolean;
  message: string;
}

export interface ApiErrorResponse {
  status: number;
  error: string;
  message: string;
  fieldErrors?: Record<string, string>;
  timestamp?: string;
}

export interface SignupRequest {
  name: string;
  email: string;
  password: string;
  confirmPassword: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface VerifySignupOtpRequest {
  email: string;
  otp: string;
}

export interface ForgotPasswordRequest {
  email: string;
}

export interface VerifyResetOtpRequest {
  email: string;
  otp: string;
}

export interface ResetPasswordRequest {
  email: string;
  otp: string;
  newPassword: string;
  confirmPassword: string;
}

export interface RefreshTokenRequest {
  refreshToken: string;
}

export interface AcceptInviteRequest {
  token: string;
  newPassword: string;
  confirmPassword: string;
}

export interface RequestInviteMagicLinkRequest {
  email: string;
}

export interface InviteMagicLinkVerificationResponse {
  status: 'VALID' | 'EXPIRED' | 'INVALID' | 'USED';
  message: string;
  email?: string;
}
