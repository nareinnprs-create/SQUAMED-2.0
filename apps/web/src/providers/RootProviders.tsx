'use client';

import { useMemo, type ReactNode } from 'react';
import { AuthProvider, createHttpAuthClient } from '@squamed/auth';
import { I18nProvider } from '@squamed/ui';
import { RealtimeProvider } from './RealtimeProvider';
import { useShellStore } from '@/lib/shell-store';

const apiBaseUrl =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080/api/v1';

/** Syncs the i18n module locale + document lang/dir with the persisted shell store. */
function ShellI18nSync({ children }: { children: ReactNode }) {
  const locale = useShellStore((state) => state.locale);
  return <I18nProvider locale={locale}>{children}</I18nProvider>;
}

export function RootProviders({ children }: { children: ReactNode }) {
  const authClient = useMemo(
    () => createHttpAuthClient({ baseURL: `${apiBaseUrl}/auth` }),
    [],
  );
  return (
    <AuthProvider authClient={authClient}>
      <ShellI18nSync>
        <RealtimeProvider>{children}</RealtimeProvider>
      </ShellI18nSync>
    </AuthProvider>
  );
}