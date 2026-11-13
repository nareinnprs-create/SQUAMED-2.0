'use client';

import { useMemo, type ReactNode } from 'react';
import { AuthProvider, createHttpAuthClient } from '@squamed/auth';
import { RealtimeProvider } from './RealtimeProvider';

const apiBaseUrl =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080/api/v1';

export function RootProviders({ children }: { children: ReactNode }) {
  const authClient = useMemo(
    () => createHttpAuthClient({ baseURL: `${apiBaseUrl}/auth` }),
    [],
  );
  return (
    <AuthProvider authClient={authClient}>
      <RealtimeProvider>{children}</RealtimeProvider>
    </AuthProvider>
  );
}