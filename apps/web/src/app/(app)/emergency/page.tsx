'use client';

import { ContentPanel, PageHeader, StatusIndicator } from '@squamed/ui';
import { useApi } from '../../../lib/use-api';
import { EmergencyWorkspace } from './workspace';

export default function EmergencyFastPathPage() {
  const { apiFetch, isReady } = useApi();

  if (!isReady) {
    return (
      <div style={{ padding: 24 }}>
        <PageHeader
          title="Emergency fast-path"
          description="Tag walk-ins red, yellow or green at triage — red boosts the token to emergency priority on every display."
        />
        <ContentPanel>
          <StatusIndicator tone="information" label="Loading fast-path..." />
        </ContentPanel>
      </div>
    );
  }

  return <EmergencyWorkspace apiFetch={apiFetch} />;
}
