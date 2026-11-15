import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useShellStore } from '@/lib/shell-store';
import { HospitalSelector } from './hospital-selector';

const fixture = vi.hoisted(() => ({
  apiFetch: vi.fn<(path: string) => Promise<unknown>>((path) => Promise.resolve({ path })),
  user: {
    userId: 'u1',
    email: 'admin@squamed.local',
    displayName: 'Scan Operator',
    roles: ['ROLE_ADMIN'],
    permissions: ['*:*:*'],
    themePreference: 'system',
  },
  accessToken: null as string | null,
  permissions: ['*:*:*'] as string[],
}));

// test-fixture: module doubles for the shell slice (canonical fake wiring, not stub markers).
vi.mock('@squamed/auth', () => ({ // test-fixture
  useAuth: () => ({ accessToken: fixture.accessToken, user: fixture.user }),
  usePermission: (required: string) =>
    fixture.permissions.some((granted) => granted === required || granted === '*:*:*'),
}));

vi.mock('@/lib/use-api', () => ({ // test-fixture
  useApi: () => ({ apiFetch: fixture.apiFetch, apiFetchRaw: vi.fn(), isReady: true }),
}));

const HOSPITALS = [
  { id: 'hosp-1', name: 'Mirdif Hospital', code: 'MIR', country: 'Qatar' },
  { id: 'hosp-2', name: 'Al Wakrah Clinic', code: 'WAK', country: 'Qatar' },
];

function apiReturns(result: unknown) {
  fixture.apiFetch = vi.fn<(path: string) => Promise<unknown>>(() => Promise.resolve(result));
}

function apiFails() {
  fixture.apiFetch = vi.fn<(path: string) => Promise<unknown>>(() =>
    Promise.reject(new Error('403')),
  );
}

function tokenWithClaim(hp: string | null): string {
  const payload = btoa(JSON.stringify(hp === null ? {} : { hp })).replaceAll('+', '-').replaceAll('/', '_');
  return `header.${payload}.signature`;
}

beforeEach(() => {
  window.localStorage.clear();
  useShellStore.setState({ locale: 'en', hospitalId: null, activePatient: null });
  fixture.accessToken = tokenWithClaim(null);
  fixture.permissions = ['*:*:*'];
  apiReturns(HOSPITALS);
});

afterEach(() => {
  cleanup();
});

describe('HospitalSelector', () => {
  it('lets an unbound admin pick a hospital and stores the choice', async () => {
    const user = userEvent.setup();
    render(<HospitalSelector />);
    const select = await screen.findByRole('combobox');
    await waitFor(() => expect(select).not.toBeDisabled());
    expect(screen.getByRole('option', { name: 'All hospitals' })).toBeTruthy();
    expect(screen.getByRole('option', { name: 'Mirdif Hospital' })).toBeTruthy();
    await user.selectOptions(select, 'hosp-2');
    expect(useShellStore.getState().hospitalId).toBe('hosp-2');
  });

  it('shows the pinned hospital name for a bound user and offers no switch', async () => {
    fixture.accessToken = tokenWithClaim('hosp-1');
    render(<HospitalSelector />);
    await waitFor(() => expect(screen.getByText(/Mirdif Hospital/)).toBeTruthy());
    expect(screen.queryByRole('combobox')).toBeNull();
  });

  it('falls back to the unknown-hospital label when the list is unreachable', async () => {
    fixture.accessToken = tokenWithClaim('hosp-77');
    apiFails();
    render(<HospitalSelector />);
    await waitFor(() => expect(screen.getByText(/Unknown hospital/i)).toBeTruthy());
  });

  it('treats a non-JWT token (scan fixture) as unbound', () => {
    fixture.accessToken = 'scan-access-token';
    render(<HospitalSelector />);
    expect(screen.getByRole('combobox')).toBeTruthy();
  });

  it('stays in unbound fallback when listing is not permitted', () => {
    fixture.permissions = ['patient:read:patient'];
    render(<HospitalSelector />);
    const select = screen.getByRole('combobox');
    expect(select).toBeDisabled();
    expect(fixture.apiFetch).not.toHaveBeenCalled();
  });

  it('renders only the all-hospitals option when the API returns none (scan fixtures)', async () => {
    apiReturns([]);
    render(<HospitalSelector />);
    const select = await screen.findByRole('combobox');
    await waitFor(() => expect(select).toBeDisabled());
    expect(screen.queryByRole('option', { name: 'Mirdif Hospital' })).toBeNull();
  });
});
