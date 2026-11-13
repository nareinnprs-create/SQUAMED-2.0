'use client';

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import type { AuthUser, AuthClient, AuthContextValue, LoginStep } from './types';

const ACCESS_KEY = 'squamed.auth.access-token';
const REFRESH_KEY = 'squamed.auth.refresh-token';
const USER_KEY = 'squamed.auth.user';

function readStoredUser(): AuthUser | null {
  if (typeof window === 'undefined') return null;
  try {
    const raw = window.localStorage.getItem(USER_KEY);
    return raw ? (JSON.parse(raw) as AuthUser) : null;
  } catch {
    return null;
  }
}

function readStoredToken(key: string): string | null {
  if (typeof window === 'undefined') return null;
  return window.localStorage.getItem(key);
}

export function AuthProvider({
  children,
  authClient,
}: {
  children: ReactNode;
  authClient: AuthClient;
}) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [accessToken, setAccessToken] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const refreshPromise = useRef<Promise<boolean> | null>(null);

  useEffect(() => {
    const storedUser = readStoredUser();
    const storedAccess = readStoredToken(ACCESS_KEY);
    const storedRefresh = readStoredToken(REFRESH_KEY);
    if (storedUser && storedAccess && storedRefresh) {
      setUser(storedUser);
      setAccessToken(storedAccess);
      void refreshSessionAsync(storedRefresh).then(() => {
        setIsLoading(false);
      });
    } else {
      setIsLoading(false);
    }
  }, []);

  const refreshSessionAsync = useCallback(
    async (refreshToken: string): Promise<boolean> => {
      const result = await authClient.refresh(refreshToken);
      if (result.step === 'success') {
        setUser(result.user);
        setAccessToken(result.accessToken);
        try {
          window.localStorage.setItem(ACCESS_KEY, result.accessToken);
          window.localStorage.setItem(REFRESH_KEY, result.refreshToken);
          window.localStorage.setItem(USER_KEY, JSON.stringify(result.user));
        } catch {
          /* storage unavailable */
        }
        return true;
      }
      clearSession();
      return false;
    },
    [],
  );

  const refreshSession = useCallback(
    async (): Promise<boolean> => {
      const refreshToken = readStoredToken(REFRESH_KEY);
      if (!refreshToken) return false;
      if (!refreshPromise.current) {
        refreshPromise.current = refreshSessionAsync(refreshToken).finally(() => {
          refreshPromise.current = null;
        });
      }
      return refreshPromise.current;
    },
    [refreshSessionAsync],
  );

  const clearSession = useCallback(() => {
    setUser(null);
    setAccessToken(null);
    try {
      window.localStorage.removeItem(ACCESS_KEY);
      window.localStorage.removeItem(REFRESH_KEY);
      window.localStorage.removeItem(USER_KEY);
    } catch {
      /* storage unavailable */
    }
  }, []);

  const applySuccessfulStep = useCallback((result: Extract<LoginStep, { step: 'success' }>) => {
    setUser(result.user);
    setAccessToken(result.accessToken);
    try {
      window.localStorage.setItem(ACCESS_KEY, result.accessToken);
      window.localStorage.setItem(REFRESH_KEY, result.refreshToken);
      window.localStorage.setItem(USER_KEY, JSON.stringify(result.user));
    } catch {
      /* storage unavailable */
    }
  }, []);

  const login = useCallback(
    async (username: string, password: string, hospitalId?: string): Promise<LoginStep> => {
      const result = await authClient.login(username, password, hospitalId);
      if (result.step === 'success') {
        applySuccessfulStep(result);
      }
      return result;
    },
    [authClient, applySuccessfulStep],
  );

  const verifyMfa = useCallback(
    async (mfaToken: string, code: string): Promise<LoginStep> => {
      const result = await authClient.verifyMfa(mfaToken, code);
      if (result.step === 'success') {
        applySuccessfulStep(result);
      }
      return result;
    },
    [authClient, applySuccessfulStep],
  );

  const logout = useCallback(async (): Promise<void> => {
    try {
      const token = readStoredToken(ACCESS_KEY);
      await authClient.logout(token);
    } catch {
      /* best-effort */
    }
    clearSession();
  }, [authClient, clearSession]);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      isAuthenticated: user !== null,
      isLoading,
      accessToken,
      login,
      verifyMfa,
      logout,
      refreshSession,
    }),
    [user, isLoading, accessToken, login, verifyMfa, logout, refreshSession],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return ctx;
}