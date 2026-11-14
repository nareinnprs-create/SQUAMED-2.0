'use client';

import { useCallback } from 'react';
import { useAuth } from '@squamed/auth';

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080/api/v1';

export function useApi() {
  const { accessToken } = useAuth();

  const authHeaders = useCallback(
    (): Record<string, string> => (accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
    [accessToken],
  );

  const apiFetch = useCallback(
    async <T,>(path: string, options: RequestInit = {}): Promise<T> => {
      const response = await fetch(`${API_BASE}${path}`, {
        ...options,
        headers: {
          // FormData needs the browser-generated multipart boundary; don't force JSON.
          ...(options.body instanceof FormData ? {} : { 'Content-Type': 'application/json' }),
          ...authHeaders(),
          ...options.headers,
        },
      });
      if (!response.ok) {
        const body = await response.json().catch(() => null);
        throw new Error(body?.error?.message ?? `Request failed (${response.status})`);
      }
      const json = (await response.json()) as { data: T; success: boolean };
      return json.data;
    },
    [authHeaders],
  );

  /** Raw response for non-JSON payloads (e.g. patient photo bytes). */
  const apiFetchRaw = useCallback(
    async (path: string, options: RequestInit = {}): Promise<Response> => {
      const response = await fetch(`${API_BASE}${path}`, {
        ...options,
        headers: { ...authHeaders(), ...options.headers },
      });
      if (!response.ok) {
        const body = await response.json().catch(() => null);
        throw new Error(body?.error?.message ?? `Request failed (${response.status})`);
      }
      return response;
    },
    [authHeaders],
  );

  return { apiFetch, apiFetchRaw, isReady: accessToken !== null };
}