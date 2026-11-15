import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useShellStore } from '@/lib/shell-store';
import { HospitalSelector } from './hospital-selector';

const mocks = vi.hoisted(() => ({
  apiFetch: vi.fn(),
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

vi.mock('@squamed/auth', () => ({
  useAuth: () => ({ accessToken: mocks.accessToken, user: mocks.user }),
  usePermission: (required: string) =>
    mocks.permissions.some((granted) => granted === required || granted === '*:*:*'),
}));

vi.mock('@/lib/use-api', () => ({
  useApi: () => ({ apiFetch: mocks.apiFetch, apiFetchRaw: vi.fn(), isReady: true }),
}));

const HOSPITALS = [
  { id: 'hosp-1', name: 'Mirdif Hospital', code: 'MIR', country: 'Qatar' },
  { id: 'hosp-2', name: 'Al Wakrah Clinic', code: 'WAK', country: 'Qatar' },
];

function tokenWithClaim(hp: string | null): string {
  const payload = btoa(JSON.stringify(hp === null ? {} : { hp })).replaceAll('+', '-').replaceAll('/', '_');
  return `header.${payload}.signature`;
}

beforeEach(() => {
  window.localStorage.clear();
  useShellStore.setState({ locale: 'en', hospitalId: null, activePatient: null });
  mocks.accessToken = tokenWithClaim(null);
  mocks.permissions = ['*:*:*'];
  mocks.apiFetch.mockReset().mockResolvedValue(HOSPITALS);
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
    mocks.accessToken = tokenWithClaim('hosp-1');
    render(<HospitalSelector />);
    await waitFor(() => expect(screen.getByText(/Mirdif Hospital/)).toBeTruthy());
    expect(screen.queryByRole('combobox')).toBeNull();
  });

  it('falls back to the unknown-hospital label when the list is unreachable', async () => {
    mocks.accessToken = tokenWithClaim('hosp-77');
    mocks.apiFetch.mockRejectedValue(new Error('403'));
    render(<HospitalSelector />);
    await waitFor(() => expect(screen.getByText(/Unknown hospital/i)).toBeTruthy());
  });

  it('treats a non-JWT token (scan fixture) as unbound', () => {
    mocks.accessToken = 'scan-access-token';
    render(<HospitalSelector />);
    expect(screen.getByRole('combobox')).toBeTruthy();
  });

  it('stays in unbound fallback when listing is not permitted', () => {
    mocks.permissions = ['patient:read:patient'];
    render(<HospitalSelector />);
    const select = screen.getByRole('combobox');
    expect(select).toBeDisabled();
    expect(mocks.apiFetch).not.toHaveBeenCalled();
  });

  it('renders only the all-hospitals option when the API returns none (scan fixtures)', async () => {
    mocks.apiFetch.mockResolvedValue([]);
    render(<HospitalSelector />);
    const select = await screen.findByRole('combobox');
    await waitFor(() => expect(select).toBeDisabled());
    expect(screen.queryByRole('option', { name: 'Mirdif Hospital' })).toBeNull();
  });
});
