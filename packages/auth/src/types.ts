export interface AuthUser {
  userId: string;
  email: string;
  displayName: string;
  roles: string[];
  permissions: string[];
  themePreference: string;
}

export interface AuthContextValue {
  user: AuthUser | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  accessToken: string | null;
  login: (username: string, password: string, hospitalId?: string) => Promise<LoginStep>;
  verifyMfa: (mfaToken: string, code: string) => Promise<LoginStep>;
  logout: () => Promise<void>;
  refreshSession: () => Promise<boolean>;
}

export type LoginStep =
  | { step: 'success'; user: AuthUser; accessToken: string; refreshToken: string }
  | { step: 'mfa_required'; mfaToken: string }
  | { step: 'invalid_credentials' };

export interface AuthClient {
  login: (
    username: string,
    password: string,
    hospitalId?: string,
  ) => Promise<LoginStep>;
  verifyMfa: (mfaToken: string, code: string) => Promise<LoginStep>;
  refresh: (refreshToken: string) => Promise<LoginStep>;
  logout: (accessToken: string | null) => Promise<void>;
}