import { createContext, useContext, useState, useEffect, useCallback, type ReactNode } from 'react';
import type { UserProfile } from '../types/auth';
import {
  getStoredUser,
  getAccessToken,
  storeAuthData,
  logout as logoutService,
  getCurrentUser,
} from '../services/authService';
import type { AuthResponse } from '../types/auth';

interface AuthContextType {
  user: UserProfile | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (authResponse: AuthResponse) => void;
  logout: () => Promise<void>;
  updateUser: (user: UserProfile) => void;
  hasRole: (role: string) => boolean;
  isAdmin: boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserProfile | null>(() =>
    getAccessToken() ? getStoredUser() : null
  );
  const [isLoading, setIsLoading] = useState<boolean>(() =>
    // Loading only when we have stored credentials to verify against the server
    !!(getAccessToken() && getStoredUser())
  );

  useEffect(() => {
    // Check if user is already authenticated from localStorage
    const token = getAccessToken();
    const storedUser = getStoredUser();

    if (token && storedUser) {
      // user is already initialised from localStorage; verify freshness
      getCurrentUser()
        .then((freshUser) => {
          setUser(freshUser);
          // Update stored user data
          const currentData = localStorage.getItem('refreshToken');
          if (currentData) {
            localStorage.setItem('user', JSON.stringify(freshUser));
          }
        })
        .catch(() => {
          // Token expired or invalid - will be handled by interceptor
        })
        .finally(() => setIsLoading(false));
    }
  }, []);

  const login = useCallback((authResponse: AuthResponse) => {
    storeAuthData(authResponse);
    setUser(authResponse.user);
  }, []);

  const logout = useCallback(async () => {
    await logoutService();
    setUser(null);
  }, []);

  const updateUser = useCallback((updatedUser: UserProfile) => {
    setUser(updatedUser);
    localStorage.setItem('user', JSON.stringify(updatedUser));
  }, []);

  const hasRole = useCallback(
    (role: string) => {
      return user?.roles?.includes(role) ?? false;
    },
    [user]
  );

  const isAdmin = user?.roles?.includes('ADMIN') ?? false;

  const value: AuthContextType = {
    user,
    isAuthenticated: !!user,
    isLoading,
    login,
    logout,
    updateUser,
    hasRole,
    isAdmin,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthContextType {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
