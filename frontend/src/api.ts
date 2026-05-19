import axios from 'axios';

const api = axios.create({
  baseURL: import.meta.env.VITE_BACKEND_ROOT_URL,
});

// Request interceptor: attach access token to all requests
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('accessToken');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// ── Auth-failure handling ──────────────────────────────────────────────────
// 401 = authentication failure (expired/missing/invalid JWT) → logout
// 403 = authorization failure (authenticated but missing permission) → toast
// Auth endpoints (login, signup, etc.) return 4xx for form-level errors
// and must NOT trigger session-expiry or access-denied handling.
let isHandlingAuthFailure = false;

api.interceptors.response.use(
  (response) => {
    // Reset the flag after any successful call to an auth endpoint so that
    // a fresh login/signup after session expiry works without reloading.
    if (isHandlingAuthFailure && response.config.url?.includes('/api/auth/')) {
      isHandlingAuthFailure = false;
    }
    return response;
  },
  (error) => {
    const status = error.response?.status;

    // Auth endpoints (login, signup, etc.) return 4xx for form-level errors
    // (wrong password, etc.).  Those must NOT trigger session-expiry or
    // access-denied handling.
    const isAuthEndpoint = Boolean(error.config?.url?.includes('/api/auth/'));

    // 401 = JWT missing, expired, or invalid → clear session and redirect to login
    if (status === 401 && !isAuthEndpoint && !isHandlingAuthFailure) {
      isHandlingAuthFailure = true;

      // Clear all stored session data immediately
      localStorage.removeItem('accessToken');
      localStorage.removeItem('refreshToken');
      localStorage.removeItem('user');

      // Notify the React auth context via a custom DOM event.
      // AuthProvider listens for this, clears React state, shows a toast,
      // and navigates to /login — all inside the React tree.
      window.dispatchEvent(new CustomEvent('auth:session-expired'));

      // Return a promise that never resolves so this request does not
      // propagate any further error handling (no toast, no UI update).
      return new Promise(() => {});
    }

    // While session expiry is being handled, suppress all subsequent errors
    // to prevent cascading error popups.
    if (isHandlingAuthFailure && !isAuthEndpoint) {
      return new Promise(() => {});
    }

    // 403 = authenticated but insufficient permission → show access-denied
    // toast.  Do NOT clear session or redirect to login.
    if (status === 403 && !isAuthEndpoint) {
      window.dispatchEvent(new CustomEvent('auth:access-denied'));
    }

    return Promise.reject(error);
  }
);

export default api;

