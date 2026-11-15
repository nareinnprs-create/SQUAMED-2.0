import { beforeEach, describe, expect, it } from 'vitest';
import { useShellStore } from './shell-store';

const STORAGE_KEY = 'squamed.shell';

function persisted(): Record<string, unknown> {
  return JSON.parse(window.localStorage.getItem(STORAGE_KEY) ?? '{}') as Record<string, unknown>;
}

describe('shell store', () => {
  beforeEach(() => {
    window.localStorage.clear();
    useShellStore.setState({ locale: 'en', hospitalId: null, activePatient: null });
  });

  it('starts with defaults: en locale, no hospital, no patient context', () => {
    expect(useShellStore.getState().locale).toBe('en');
    expect(useShellStore.getState().hospitalId).toBeNull();
    expect(useShellStore.getState().activePatient).toBeNull();
  });

  it('setPatient stores the context and setPatient(null) clears it', () => {
    const patient = { id: 'p1', name: 'Sara Search', mrn: 'MRN-1', age: 36 };
    useShellStore.getState().setPatient(patient);
    expect(useShellStore.getState().activePatient).toEqual(patient);
    useShellStore.getState().setPatient(null);
    expect(useShellStore.getState().activePatient).toBeNull();
  });

  it('persists locale and hospitalId, never the session-scoped patient', () => {
    useShellStore.getState().setPatient({ id: 'p1', name: 'A B', mrn: 'MRN-2' });
    useShellStore.getState().setLocale('hi');
    useShellStore.getState().setHospitalId('hosp-9');
    const stored = persisted();
    expect(stored.state).toMatchObject({ locale: 'hi', hospitalId: 'hosp-9' });
    expect(JSON.stringify(stored)).not.toContain('activePatient');
    expect(JSON.stringify(stored)).not.toContain('MRN-2');
  });
});
