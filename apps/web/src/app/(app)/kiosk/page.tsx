'use client';

import { ContentPanel, PageHeader, StatusIndicator } from '@squamed/ui';
import { useAuth } from '@squamed/auth';
import { useApi } from '../../../lib/use-api';
import { useShellStore } from '@/lib/shell-store';
import { hospitalIdFromToken } from '../../../components/shell/hospital-selector';
import { KioskWorkspace } from './workspace';

export default function KioskPage() {
  const { apiFetch, isReady } = useApi();
  const { accessToken } = useAuth();
  // The kiosk device is a pinned KIOSK-role user: its hospital comes from the JWT `hp`
  // claim; the shell store only matters for multi-hospital admins driving the page.
  const storeHospitalId = useShellStore((state) => state.hospitalId);
  const hospitalId = hospitalIdFromToken(accessToken) ?? storeHospitalId;

  if (!isReady) {
    return (
      <div style={{ padding: 24 }}>
        <PageHeader
          title="Self-service kiosk"
          description="Check in with ABHA, ABHA address or mobile number and print your queue token."
        />
        <ContentPanel>
          <StatusIndicator tone="information" label="Loading kiosk..." />
        </ContentPanel>
      </div>
    );
  }

  return <KioskWorkspace apiFetch={apiFetch} hospitalId={hospitalId} />;
}
