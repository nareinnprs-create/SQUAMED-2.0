'use client';

import { ContentPanel, PageHeader, StatusIndicator } from '@squamed/ui';

export default function PlaceholderPage() {
  return (
    <div style={{ padding: 24 }}>
      <PageHeader title="Orders" description="This module lands in the current build phase." />
      <ContentPanel>
        <StatusIndicator tone="information" label="Module in progress" />
      </ContentPanel>
    </div>
  );
}
