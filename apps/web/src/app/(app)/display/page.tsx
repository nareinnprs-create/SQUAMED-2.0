'use client';

import { ContentPanel, PageHeader, StatusIndicator } from '@squamed/ui';
import { useApi } from '../../../lib/use-api';
import { DisplayWorkspace } from './workspace';

export default function DisplayPage() {
  const { apiFetch, isReady } = useApi();

  if (!isReady) {
    return (
      <div style={{ padding: 24 }}>
        <PageHeader title="Counter display" description="Waiting-room TV board: now serving and next tokens per counter." />
        <ContentPanel>
          <StatusIndicator tone="information" label="Loading display..." />
        </ContentPanel>
      </div>
    );
  }

  return <DisplayWorkspace apiFetch={apiFetch} />;
}
