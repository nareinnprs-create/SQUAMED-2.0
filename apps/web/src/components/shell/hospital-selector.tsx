'use client';

import { useEffect, useState } from 'react';
import { Select, t } from '@squamed/ui';
import { useAuth, usePermission } from '@squamed/auth';
import { useApi } from '@/lib/use-api';
import { useShellStore } from '@/lib/shell-store';

interface HospitalDto {
  id: string;
  name: string;
  code?: string | null;
  country?: string | null;
}

/**
 * The active hospital comes from the JWT `hp` claim (set at login from the
 * user's single hospital assignment, or the claimed id for multi-hospital
 * users). Bound users see their pinned hospital and cannot switch; platform
 * admins (hp null) may pick one, which the patients list/search endpoints
 * honor via TenantResolver.pin.
 */
export function hospitalIdFromToken(accessToken: string | null): string | null {
  if (!accessToken) return null;
  try {
    const payload = accessToken.split('.')[1];
    if (!payload) return null;
    const padded = payload + '='.repeat((4 - (payload.length % 4)) % 4);
    const json = JSON.parse(atob(padded.replaceAll('-', '+').replaceAll('_', '/'))) as {
      hp?: unknown;
    };
    return typeof json.hp === 'string' ? json.hp : null;
  } catch {
    return null;
  }
}

export function HospitalSelector() {
  const { accessToken } = useAuth();
  const { apiFetch, isReady } = useApi();
  const canList = usePermission('hospital-config:read:hospital');
  const hospitalId = useShellStore((state) => state.hospitalId);
  const setHospitalId = useShellStore((state) => state.setHospitalId);
  const [hospitals, setHospitals] = useState<HospitalDto[]>([]);

  const pinnedId = hospitalIdFromToken(accessToken);

  useEffect(() => {
    if (!isReady || !canList) return;
    apiFetch<HospitalDto[]>('/hospital-config/hospitals')
      .then((data) => setHospitals(Array.isArray(data) ? data : []))
      .catch(() => setHospitals([]));
  }, [apiFetch, isReady, canList]);

  if (pinnedId) {
    const pinned = hospitals.find((hospital) => hospital.id === pinnedId);
    return (
      <span style={{ fontSize: 13, color: 'var(--sq-fg-muted)', whiteSpace: 'nowrap' }}>
        {`${t('shell.hospital.label')}: ${pinned?.name ?? t('shell.hospital.unknown')}`}
      </span>
    );
  }

  return (
    <Select
      aria-label={t('shell.hospital.switchTitle')}
      title={t('shell.hospital.switchTitle')}
      value={hospitalId ?? ''}
      disabled={hospitals.length === 0}
      onChange={(event) => setHospitalId(event.target.value || null)}
      style={{ width: 200, fontSize: 13 }}
    >
      <option value="">{t('shell.hospital.all')}</option>
      {hospitals.map((hospital) => (
        <option key={hospital.id} value={hospital.id}>
          {hospital.name}
        </option>
      ))}
    </Select>
  );
}
