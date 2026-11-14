'use client';

import { ContentPanel, PageHeader, StatusIndicator } from '@squamed/ui';
import { useApi } from '@/lib/use-api';
import { MasterDataWorkspace } from './workspace';

export default function MasterDataPage() {
  const { apiFetch, isReady } = useApi();

  if (!isReady) {
    return (
      <div style={{ padding: 24 }}>
        <PageHeader title="Master data" description="Clinical catalogs used across orders, prescriptions and billing." />
        <ContentPanel>
          <StatusIndicator tone="information" label="Loading master data..." />
        </ContentPanel>
      </div>
    );
  }

  return <MasterDataWorkspace apiFetch={apiFetch} />;
}
