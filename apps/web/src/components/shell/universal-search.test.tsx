import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { UniversalSearch } from './universal-search';

const mocks = vi.hoisted(() => ({
  apiFetch: vi.fn(),
  push: vi.fn(),
  permissions: ['patient:read:patient'] as string[],
}));

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: mocks.push, replace: vi.fn(), back: vi.fn() }),
  usePathname: () => '/dashboard',
}));

vi.mock('@squamed/auth', () => ({
  useAuth: () => ({ accessToken: 't', user: { displayName: 'Op' } }),
  usePermission: (required: string) => mocks.permissions.includes(required),
}));

vi.mock('@/lib/use-api', () => ({
  useApi: () => ({ apiFetch: mocks.apiFetch, apiFetchRaw: vi.fn(), isReady: true }),
}));

const HIT = {
  index: 'squamed-patients',
  id: 'p-1',
  type: 'patient',
  title: 'Sara Search',
  subtitle: 'MRN-1001',
  fields: {},
};

beforeEach(() => {
  mocks.permissions = ['patient:read:patient'];
  mocks.push.mockReset();
  mocks.apiFetch.mockReset().mockResolvedValue({ total: 1, hits: [HIT] });
});

afterEach(() => {
  cleanup();
});

// Real timers: the 250 ms debounce resolves inside findBy/waitFor polling.
async function typeQuery(text: string) {
  const user = userEvent.setup();
  await user.type(screen.getByRole('combobox'), text);
}

describe('UniversalSearch', () => {
  it('renders null without patient:read:patient', () => {
    mocks.permissions = [];
    const { container } = render(<UniversalSearch />);
    expect(container).toBeEmptyDOMElement();
  });

  it('debounces queries into one fetch with the typed query', async () => {
    render(<UniversalSearch />);
    await typeQuery('ab');
    await waitFor(() => expect(mocks.apiFetch).toHaveBeenCalledTimes(1));
    expect(mocks.apiFetch.mock.calls[0][0]).toContain('/search/search?q=ab');
  });

  it('shows patient hits and navigates to the patient record on click', async () => {
    render(<UniversalSearch />);
    await typeQuery('sara');
    const option = await screen.findByRole('option', { name: /Sara Search/ });
    option.click();
    expect(mocks.push).toHaveBeenCalledWith('/patients/p-1');
  });

  it('navigates encounter hits to the owning patient', async () => {
    mocks.apiFetch.mockResolvedValue({
      total: 1,
      hits: [
        { index: 'squamed-encounters', id: 'e-1', type: 'encounter', title: 'OPD visit', subtitle: null, fields: { patientId: 'p-9' } },
      ],
    });
    render(<UniversalSearch />);
    await typeQuery('opd');
    const option = await screen.findByRole('option', { name: /OPD visit/ });
    option.click();
    expect(mocks.push).toHaveBeenCalledWith('/patients/p-9');
  });

  it('degrades to the error state when the API fails (403/outage)', async () => {
    mocks.apiFetch.mockRejectedValue(new Error('403'));
    render(<UniversalSearch />);
    await typeQuery('sara');
    expect(await screen.findByText('Search unavailable')).toBeTruthy();
  });

  it('closes the result list on Escape', async () => {
    render(<UniversalSearch />);
    await typeQuery('sara');
    await screen.findByRole('listbox');
    screen
      .getByRole('combobox')
      .dispatchEvent(new window.KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    await waitFor(() => expect(screen.queryByRole('listbox')).toBeNull());
  });

  it('shows the empty state when the backend returns no hits', async () => {
    mocks.apiFetch.mockResolvedValue({ total: 0, hits: [] });
    render(<UniversalSearch />);
    await typeQuery('zzz');
    expect(await screen.findByText('No matches')).toBeTruthy();
  });
});
