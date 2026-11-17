'use client';

import { ContentPanel, PageHeader, StatusIndicator } from '@squamed/ui';
import { useApi } from '../../../lib/use-api';
import { AnnouncementsWorkspace } from './workspace';

export default function AnnouncementsPage() {
  const { apiFetch, isReady } = useApi();

  if (!isReady) {
    return (
      <div style={{ padding: 24 }}>
        <PageHeader title="Announcements" description="Voice display for called tokens (TTS via the browser)." />
        <ContentPanel>
          <StatusIndicator tone="information" label="Loading announcements..." />
        </ContentPanel>
      </div>
    );
  }

  return <AnnouncementsWorkspace apiFetch={apiFetch} />;
}
