'use client';

import { ContentPanel, PageHeader, StatusIndicator } from '@squamed/ui';
import { useApi } from '@/lib/use-api';
import { OrdersWorkspace } from './workspace';

export default function OrdersPage() {
  const { apiFetch, isReady } = useApi();

  if (!isReady) {
    return (
      <div style={{ padding: 24 }}>
        <PageHeader title="Orders" description="Clinical orders placed during encounters." />
        <ContentPanel>
          <StatusIndicator tone="information" label="Loading orders..." />
        </ContentPanel>
      </div>
    );
  }

  return <OrdersWorkspace apiFetch={apiFetch} />;
}
