'use client';

import type { AuthClient, LoginStep, AuthUser } from './types';

interface AuthResponse {
  status: string;
  accessToken: string | null;
  refreshToken: string | null;
  mfaToken: string | null;
  user: AuthUser | null;
}

interface HttpAuthClientOptions {
  baseURL: string;
  fetchFn?: typeof fetch;
}

export function createHttpAuthClient({ baseURL, fetchFn }: HttpAuthClientOptions): AuthClient {
  const fetcher = fetchFn ?? ((...args: Parameters<typeof fetch>) => fetch(...args));

  async function post(path: string, body: unknown): Promise<AuthResponse> {
    const response = await fetcher(`${baseURL}${path}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
    if (!response.ok) {
      throw new Error(`Auth request failed: ${response.status}`);
    }
    const json = (await response.json()) as { data: AuthResponse; success: boolean };
    return json.data;
  }

  function mapLogin(raw: AuthResponse): LoginStep {
    switch (raw.status) {
      case 'MFA_REQUIRED':
        return { step: 'mfa_required', mfaToken: raw.mfaToken ?? '' };
      case 'SUCCESS':
        if (!raw.user || !raw.accessToken || !raw.refreshToken) {
          return { step: 'invalid_credentials' };
        }
        return {
          step: 'success',
          user: raw.user,
          accessToken: raw.accessToken,
          refreshToken: raw.refreshToken,
        };
      default:
        return { step: 'invalid_credentials' };
    }
  }

  return {
    async login(username, password, hospitalId) {
      const data = await post('/login', { username, password, hospitalId });
      return mapLogin(data);
    },
    async verifyMfa(mfaToken, code) {
      const data = await post('/mfa/verify', { mfaToken, code });
      return mapLogin(data);
    },
    async refresh(refreshToken) {
      const data = await post('/refresh', { refreshToken });
      return mapLogin(data);
    },
    async logout(accessToken) {
      const response = await fetcher(`${baseURL}/logout`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
        },
        body: JSON.stringify({ accessToken }),
      });
      if (!response.ok && response.status !== 401) {
        throw new Error(`Logout failed: ${response.status}`);
      }
    },
  };
}