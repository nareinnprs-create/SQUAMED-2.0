'use client';

import { useRouter, usePathname } from 'next/navigation';
import { useEffect, useState } from 'react';
import { AppShell, t } from '@squamed/ui';
import { useAuth } from '@squamed/auth';
import type { SidebarItem } from '@squamed/ui';
import { HospitalSelector } from '@/components/shell/hospital-selector';
import { UniversalSearch } from '@/components/shell/universal-search';
import { UserMenu } from '@/components/shell/user-menu';
import { PatientContextBar } from '@/components/shell/patient-context-bar';
import { NotificationBell } from '@/components/shell/notification-bell';
import { CommandBar } from '@/components/ai/command-bar';
import { useApi } from '@/lib/use-api';
import { visibleNavItems } from '@/lib/nav-entitlements';

// Labels are data (nav model), not rendered literals; each id maps to its route.
const navItems: SidebarItem[] = [
  { id: 'dashboard', label: 'Dashboard', href: '/dashboard' },
  { id: 'patients', label: 'Patients', href: '/patients' },
  { id: 'empi', label: 'EMPI Review', href: '/empi' },
  { id: 'tasks', label: 'Task Inbox', href: '/tasks' },
  { id: 'audit', label: 'Audit Viewer', href: '/audit' },
  { id: 'appointments', label: 'Appointments', href: '/appointments' },
  { id: 'reception', label: 'Reception & Triage', href: '/reception' },
  { id: 'queue', label: 'Queue', href: '/queue' },
  { id: 'emergency', label: 'Emergency', href: '/emergency' },
  { id: 'downtime', label: 'Downtime Pack', href: '/downtime' },
  { id: 'consult', label: 'Consultation', href: '/consult' },
  { id: 'orders', label: 'Orders', href: '/orders' },
  { id: 'laboratory', label: 'Laboratory', href: '/laboratory' },
  { id: 'radiology', label: 'Radiology', href: '/radiology' },
  { id: 'pharmacy', label: 'Pharmacy', href: '/pharmacy' },
  { id: 'billing', label: 'Billing', href: '/billing' },
  { id: 'communication', label: 'Communication', href: '/communication' },
  { id: 'workflow', label: 'Workflow', href: '/workflow' },
  { id: 'integrations', label: 'Integrations', href: '/integrations' },
  { id: 'ai', label: 'AI Assistant', href: '/ai' },
  { id: 'scribe', label: 'AI Scribe', href: '/scribe' },
  { id: 'master-data', label: 'Master Data', href: '/master-data' },
  { id: 'hospital-config', label: 'Hospital Config', href: '/hospital-config' },
  { id: 'settings', label: 'Settings', href: '/settings' },
];

export default function AppLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname() ?? '';
  const { isAuthenticated, isLoading, refreshSession } = useAuth();
  const { apiFetch, isReady } = useApi();
  // 01.10: null = unmanaged or not yet known, which shows every module.
  const [features, setFeatures] = useState<string[] | null>(null);

  useEffect(() => {
    if (!isReady) return;
    let cancelled = false;
    void apiFetch<{ managed: boolean; features: string[] }>('/entitlements')
      .then((entitlement) => {
        if (!cancelled) setFeatures(entitlement.managed ? entitlement.features : null);
      })
      // Fail open: an entitlement outage must not empty the menu.
      .catch(() => {
        if (!cancelled) setFeatures(null);
      });
    return () => {
      cancelled = true;
    };
  }, [apiFetch, isReady]);

  useEffect(() => {
    if (!isLoading && !isAuthenticated) {
      router.replace('/login');
    }
  }, [isLoading, isAuthenticated, router]);

  useEffect(() => {
    const interval = setInterval(() => {
      void refreshSession();
    }, 5 * 60 * 1000);
    return () => clearInterval(interval);
  }, [refreshSession]);

  if (isLoading || !isAuthenticated) {
    return (
      <div style={{ display: 'flex', minHeight: '100vh', alignItems: 'center', justifyContent: 'center' }}>
        {t('shell.loading')}
      </div>
    );
  }

  const activeNavId = pathname.split('/')[1] || 'dashboard';
  const navWithSpaNav = visibleNavItems(navItems, features).map((item) => ({
    ...item,
    onClick: () => router.push(`/${item.id}`),
  }));

  return (
    <AppShell
      navItems={navWithSpaNav}
      activeNavId={activeNavId}
      topbar={{
        title: t('shell.workspace'),
        leftSlot: <HospitalSelector />,
        centerSlot: <UniversalSearch />,
        rightSlot: (
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <NotificationBell />
            <UserMenu />
          </div>
        ),
      }}
    >
      <PatientContextBar />
      {children}
      <CommandBar />
    </AppShell>
  );
}
