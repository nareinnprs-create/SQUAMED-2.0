import { act, cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { useShellStore } from '@/lib/shell-store';
import { PatientContextBar } from './patient-context-bar';

beforeEach(() => {
  window.localStorage.clear();
  useShellStore.setState({ locale: 'en', hospitalId: null, activePatient: null });
});

afterEach(() => {
  cleanup();
});

const PATIENT = {
  id: 'p-1',
  name: 'Sara Search',
  mrn: 'MRN-1001',
  dateOfBirth: '1988-03-20',
  gender: 'female',
  age: 36,
  allergySummary: 'Penicillin',
  isEmergency: false,
};

describe('PatientContextBar', () => {
  it('renders nothing without an active patient', () => {
    const { container } = render(<PatientContextBar />);
    expect(container).toBeEmptyDOMElement();
  });

  it('shows the active patient banner with the clear action', async () => {
    useShellStore.getState().setPatient(PATIENT);
    render(<PatientContextBar />);
    expect(screen.getByText(/Sara Search/)).toBeTruthy();
    expect(screen.getByText(/MRN-1001/)).toBeTruthy();
    expect(screen.getByText(/Allergies/)).toBeTruthy();
    await userEvent.click(screen.getByRole('button', { name: 'Clear patient context' }));
    expect(useShellStore.getState().activePatient).toBeNull();
  });

  it('reflects store changes live', () => {
    render(<PatientContextBar />);
    expect(screen.queryByText(/Sara Search/)).toBeNull();
    act(() => {
      useShellStore.getState().setPatient(PATIENT);
    });
    expect(screen.getByText(/Sara Search/)).toBeTruthy();
  });
});
