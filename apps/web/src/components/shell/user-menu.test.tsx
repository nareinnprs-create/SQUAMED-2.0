import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setLocale, t } from '@squamed/ui';
import { useShellStore } from '@/lib/shell-store';
import { UserMenu } from './user-menu';

const TEST_USER = {
  userId: 'u1',
  email: 'admin@squamed.local',
  displayName: 'Scan Operator',
  roles: ['ROLE_ADMIN', 'DOCTOR'],
  permissions: ['patient:read:patient', 'task:read:task'],
  themePreference: 'system',
};

const fixture = vi.hoisted(() => ({
  logout: vi.fn<() => Promise<void>>(),
  user: null as unknown,
}));

vi.mock('@squamed/auth', () => ({ // test-fixture
  useAuth: () => ({ user: fixture.user, logout: fixture.logout }),
}));

beforeEach(() => {
  window.localStorage.clear();
  useShellStore.setState({ locale: 'en', hospitalId: null, activePatient: null });
  setLocale('en');
  fixture.logout = vi.fn<() => Promise<void>>();
  fixture.user = TEST_USER;
});

afterEach(() => {
  cleanup();
  setLocale('en');
  useShellStore.setState({ locale: 'en' });
});

describe('UserMenu', () => {
  it('renders nothing without a user', () => {
    fixture.user = null;
    const { container } = render(<UserMenu />);
    expect(container).toBeEmptyDOMElement();
  });

  it('shows the visible Sign out action and opens the account menu', async () => {
    const user = userEvent.setup();
    render(<UserMenu />);
    const signOut = screen.getByRole('button', { name: 'Sign out' });
    await user.click(signOut);
    expect(fixture.logout).toHaveBeenCalledTimes(1);
    await user.click(screen.getByRole('button', { name: 'Account menu' }));
    expect(await screen.findByText('admin@squamed.local')).toBeTruthy();
    expect(screen.getByText('ROLE_ADMIN')).toBeTruthy();
    expect(screen.getByText(/Permissions: 2/)).toBeTruthy();
  });

  it('switches language to hi through the menu (module + store)', async () => {
    const user = userEvent.setup();
    render(<UserMenu />);
    await user.click(screen.getByRole('button', { name: 'Account menu' }));
    await user.click(await screen.findByRole('button', { name: 'हिन्दी' }));
    expect(useShellStore.getState().locale).toBe('hi');
    expect(t('shell.user.signOut')).toBe('साइन आउट');
    // The menu itself re-renders in the new locale immediately.
    expect(screen.getByRole('button', { name: 'साइन आउट' })).toBeTruthy();
  });

  it('marks the active language as pressed', async () => {
    const user = userEvent.setup();
    render(<UserMenu />);
    await user.click(screen.getByRole('button', { name: 'Account menu' }));
    const english = await screen.findByRole('button', { name: 'English' });
    expect(english).toHaveAttribute('aria-pressed', 'true');
  });
});
