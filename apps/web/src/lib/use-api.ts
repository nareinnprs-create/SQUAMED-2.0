'use client';

import { useCallback } from 'react';
import { useAuth } from '@squamed/auth';

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080/api/v1';

export function useApi() {
  const { accessToken } = useAuth();

  const apiFetch = useCallback(
    async <T,>(path: string, options: RequestInit = {}): Promise<T> => {
      const response = await fetch(`${API_BASE}${path}`, {
        ...options,
        headers: {
          'Content-Type': 'application/json',
          ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
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
    [accessToken],
  );

  return { apiFetch, isReady: accessToken !== null };
}