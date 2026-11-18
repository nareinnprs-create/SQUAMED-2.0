'use client';

import { ContentPanel, PageHeader, StatusIndicator } from '@squamed/ui';
import { useAuth } from '@squamed/auth';
import { useApi } from '../../../lib/use-api';
import { useShellStore } from '@/lib/shell-store';
import { hospitalIdFromToken } from '../../../components/shell/hospital-selector';
import { DowntimeWorkspace } from './workspace';

export default function DowntimePackPage() {
  const { apiFetch, isReady } = useApi();
  const { accessToken } = useAuth();
  const storeHospitalId = useShellStore((state) => state.hospitalId);
  const hospitalId = hospitalIdFromToken(accessToken) ?? storeHospitalId;

  if (!isReady) {
    return (
      <div style={{ padding: 24 }}>
        <PageHeader
          title="Downtime pack"
          description="Paper-mode fallback for reception and queue outages."
        />
        <ContentPanel>
          <StatusIndicator tone="information" label="Loading downtime pack..." />
        </ContentPanel>
      </div>
    );
  }

  return <DowntimeWorkspace apiFetch={apiFetch} hospitalId={hospitalId} />;
}
