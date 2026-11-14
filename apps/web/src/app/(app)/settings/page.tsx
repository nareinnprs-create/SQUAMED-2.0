'use client';

import { ContentPanel, PageHeader, StatusIndicator } from '@squamed/ui';
import { useApi } from '@/lib/use-api';
import { SettingsWorkspace } from './workspace';

export default function SettingsPage() {
  const { apiFetch, isReady } = useApi();

  if (!isReady) {
    return (
      <div style={{ padding: 24 }}>
        <PageHeader title="Settings" description="Your account, security and notification history." />
        <ContentPanel>
          <StatusIndicator tone="information" label="Loading settings..." />
        </ContentPanel>
      </div>
    );
  }

  return <SettingsWorkspace apiFetch={apiFetch} />;
}
