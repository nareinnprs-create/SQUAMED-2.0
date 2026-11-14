import { ContentPanel, PageHeader, Stack, StatusIndicator } from '@squamed/ui';

export default function OfflinePage() {
  return (
    <main style={{ padding: '24px' }}>
      <PageHeader
        title="You are offline"
        description="The connection to the hospital network was lost. Queued work replays automatically, exactly once, when the connection returns."
      />
      <ContentPanel>
        <Stack gap="12px">
          <StatusIndicator tone="warning" pulse label="Offline — showing the cached shell" />
          <p style={{ margin: 0, color: 'var(--sq-fg)' }}>
            Clinical data is not cached on this device. Actions that were queued before the
            connection dropped carry an idempotency key, so replaying them cannot execute a write
            twice.
          </p>
        </Stack>
      </ContentPanel>
    </main>
  );
}
