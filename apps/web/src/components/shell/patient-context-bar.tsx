'use client';

import { Button, PatientBanner, t } from '@squamed/ui';
import { useShellStore } from '@/lib/shell-store';

/**
 * Session-scoped patient context above the shell content. Set by the patient
 * record and 360 pages; the layout clears it when the route leaves a patient
 * record, so no page can act on a stale patient.
 */
export function PatientContextBar() {
  const patient = useShellStore((state) => state.activePatient);
  const setPatient = useShellStore((state) => state.setPatient);

  if (!patient) return null;

  return (
    <div
      role="region"
      aria-label={t('shell.patient.context')}
      style={{
        display: 'flex',
        alignItems: 'flex-start',
        gap: 10,
        marginBottom: 14,
      }}
    >
      <div style={{ flex: 1, minWidth: 0 }}>
        <PatientBanner
          name={patient.name}
          mrn={patient.mrn}
          dateOfBirth={patient.dateOfBirth ?? ''}
          gender={patient.gender ?? '—'}
          age={patient.age ?? 0}
          bloodGroup={patient.bloodGroup}
          allergySummary={patient.allergySummary}
          isEmergency={patient.isEmergency ?? false}
        />
      </div>
      <Button
        variant="outline"
        size="sm"
        aria-label={t('shell.patient.clear')}
        title={t('shell.patient.clear')}
        onClick={() => setPatient(null)}
      >
        {'✕'}
      </Button>
    </div>
  );
}
