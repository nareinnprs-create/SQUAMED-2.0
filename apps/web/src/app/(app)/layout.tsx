'use client';

import { useRouter } from 'next/navigation';
import { useEffect } from 'react';
import { AppShell, Button } from '@squamed/ui';
import { useAuth } from '@squamed/auth';
import type { SidebarItem } from '@squamed/ui';
import { NotificationBell } from '@/components/notification-bell';
import { CommandBar } from '@/components/ai/command-bar';

const navItems: SidebarItem[] = [
  { id: 'dashboard', label: 'Dashboard' },
  { id: 'patients', label: 'Patients' },
  { id: 'appointments', label: 'Appointments' },
  { id: 'reception', label: 'Reception & Triage' },
  { id: 'queue', label: 'Queue' },
  { id: 'consult', label: 'Consultation' },
  { id: 'orders', label: 'Orders' },
  { id: 'laboratory', label: 'Laboratory' },
  { id: 'pharmacy', label: 'Pharmacy' },
  { id: 'billing', label: 'Billing' },
  { id: 'communication', label: 'Communication' },
  { id: 'workflow', label: 'Workflow' },
  { id: 'integrations', label: 'Integrations' },
  { id: 'ai', label: 'AI Assistant' },
  { id: 'scribe', label: 'AI Scribe' },
  { id: 'ai', label: 'AI Assistant' },
  { id: 'master-data', label: 'Master Data' },
  { id: 'hospital-config', label: 'Hospital Config' },
  { id: 'settings', label: 'Settings' },
];

export default function AppLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const { user, isAuthenticated, isLoading, logout, refreshSession } = useAuth();

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
        Loading...
      </div>
    );
  }

  return (
    <AppShell
      navItems={navItems}
      topbar={{
        title: 'Mirdif Hospital — Clinical Workspace',
        rightSlot: (
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <NotificationBell />
            <span style={{ fontSize: 13, color: 'var(--sq-fg-muted)' }}>
              {user?.displayName}
            </span>
            <Button variant="outline" size="sm" onClick={() => void logout()}>
              Sign out
            </Button>
          </div>
        ),
      }}
    >
      {children}
      <CommandBar />
    </AppShell>
  );
}