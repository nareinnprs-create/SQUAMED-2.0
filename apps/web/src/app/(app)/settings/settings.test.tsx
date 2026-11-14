import { describe, expect, it } from 'vitest';
import {
  MfaSetupPanel,
  NotificationsPanel,
  notificationStatusLabel,
  notificationStatusVariant,
  type NotificationDto,
} from './workspace';
import { clickButton, fixtureApi, flush, mount, textIncludes, typeInto } from '@/test-support/react-dom-test';

const notification = (overrides: Partial<NotificationDto> = {}): NotificationDto => ({
  id: 'n1',
  channel: 'in_app',
  type: 'task.created',
  title: 'Verify merged record',
  body: 'Review the merged patient record.',
  readAt: null,
  deliveredAt: '2026-11-01T09:05:00Z',
  createdAt: '2026-11-01T09:00:00Z',
  ...overrides,
});

describe('notification helpers', () => {
  it('classifies read vs unread', () => {
    expect(notificationStatusVariant(notification())).toBe('warning');
    expect(notificationStatusLabel(notification())).toBe('Unread');
    expect(notificationStatusVariant(notification({ readAt: '2026-11-01T10:00:00Z' }))).toBe('success');
    expect(notificationStatusLabel(notification({ readAt: '2026-11-01T10:00:00Z' }))).toBe('Read');
  });
});

describe('MfaSetupPanel', () => {
  it('starts setup, shows the secret, then confirms with the code', async () => {
    const fixture = fixtureApi((path, options) => {
      if (path === '/auth/mfa/setup' && options?.method === 'POST') {
        return { mfaStatus: 'ENABLED' };
      }
      if (path === '/auth/mfa/setup') {
        return { otpAuthUrl: 'otpauth://totp/SQUAMED:admin@squamed.local?secret=KRSXG5A=', secret: 'KRSXG5A=' };
      }
      return {};
    });
    const mounted = mount(<MfaSetupPanel apiFetch={fixture.apiFetch} />);

    await clickButton(mounted.container, 'Start MFA setup');
    await flush();

    expect(fixture.calls).toHaveLength(1);
    expect(fixture.calls[0].path).toBe('/auth/mfa/setup');
    expect(textIncludes(mounted.container, 'KRSXG5A=')).toBe(true);

    await typeInto(mounted.container, '6-digit verification code', '123456');
    await clickButton(mounted.container, 'Confirm and enable');
    await flush();

    const confirm = fixture.calls[1];
    expect(confirm.path).toBe('/auth/mfa/setup');
    expect(confirm.options?.method).toBe('POST');
    expect(JSON.parse(String(confirm.options?.body))).toEqual({ verificationCode: '123456' });
    expect(textIncludes(mounted.container, 'Multi-factor authentication is enabled.')).toBe(true);
    mounted.unmount();
  });

  it('rejects codes that are not six digits without calling the API', async () => {
    const fixture = fixtureApi((_path, options) => {
      if (options?.method === 'POST') {
        throw new Error('SHOULD_NOT_BE_CALLED');
      }
      return { otpAuthUrl: 'otpauth://totp/x?secret=AAA=', secret: 'AAA=' };
    });
    const mounted = mount(<MfaSetupPanel apiFetch={fixture.apiFetch} />);

    await clickButton(mounted.container, 'Start MFA setup');
    await flush();
    await typeInto(mounted.container, '6-digit verification code', '12');
    await clickButton(mounted.container, 'Confirm and enable');
    await flush();

    expect(fixture.calls).toHaveLength(1);
    expect(textIncludes(mounted.container, 'Enter the 6-digit code')).toBe(true);
    mounted.unmount();
  });

  it('surfaces backend rejection of an invalid code', async () => {
    const fixture = fixtureApi((path, options) => {
      if (path === '/auth/mfa/setup' && options?.method === 'POST') {
        throw new Error('INVALID_MFA_CODE');
      }
      return { otpAuthUrl: 'otpauth://totp/x?secret=AAA=', secret: 'AAA=' };
    });
    const mounted = mount(<MfaSetupPanel apiFetch={fixture.apiFetch} />);

    await clickButton(mounted.container, 'Start MFA setup');
    await flush();
    await typeInto(mounted.container, '6-digit verification code', '999999');
    await clickButton(mounted.container, 'Confirm and enable');
    await flush();

    expect(textIncludes(mounted.container, 'INVALID_MFA_CODE')).toBe(true);
    mounted.unmount();
  });
});

describe('NotificationsPanel', () => {
  it('supports marking all notifications read', async () => {
    const notifications: NotificationDto[] = [
      notification(),
      notification({ id: 'n2', title: 'Lab result released', readAt: '2026-11-01T11:00:00Z' }),
    ];
    const fixture = fixtureApi((path) => {
      if (path === '/notifications?unreadOnly=false') {
        return notifications;
      }
      if (path === '/notifications/read-all') {
        for (const entry of notifications) entry.readAt = entry.readAt ?? '2026-11-01T12:00:00Z';
        return 2;
      }
      return {};
    });
    const mounted = mount(<NotificationsPanel apiFetch={fixture.apiFetch} />);
    await flush();

    expect(fixture.calls[0].path).toBe('/notifications?unreadOnly=false');
    expect(textIncludes(mounted.container, '1 unread.')).toBe(true);

    await clickButton(mounted.container, 'Mark all read');
    await flush();
    expect(fixture.calls.some((call) => call.path === '/notifications/read-all')).toBe(true);
    expect(textIncludes(mounted.container, 'All notifications read.')).toBe(true);
    mounted.unmount();
  });

  it('supports marking a single notification read', async () => {
    const notifications: NotificationDto[] = [
      notification(),
      notification({ id: 'n2', title: 'Lab result released', readAt: '2026-11-01T11:00:00Z' }),
    ];
    const fixture = fixtureApi((path) => {
      if (path === '/notifications?unreadOnly=false') {
        return notifications;
      }
      if (path === '/notifications/n1/read') {
        notifications[0] = { ...notifications[0], readAt: '2026-11-01T12:00:00Z' };
        return {};
      }
      return {};
    });
    const mounted = mount(<NotificationsPanel apiFetch={fixture.apiFetch} />);
    await flush();

    expect(textIncludes(mounted.container, 'Unread')).toBe(true);
    expect(textIncludes(mounted.container, 'Read')).toBe(true);

    await clickButton(mounted.container, 'Mark read');
    await flush();
    expect(fixture.calls.some((call) => call.path === '/notifications/n1/read')).toBe(true);
    expect(textIncludes(mounted.container, 'All notifications read.')).toBe(true);
    mounted.unmount();
  });

  it('shows an empty state when there are no notifications', async () => {
    const fixture = fixtureApi(() => []);
    const mounted = mount(<NotificationsPanel apiFetch={fixture.apiFetch} />);
    await flush();

    expect(textIncludes(mounted.container, 'No notifications yet.')).toBe(true);
    mounted.unmount();
  });

  it('surfaces load failures honestly', async () => {
    const fixture = fixtureApi(() => {
      throw new Error('NOTIFICATION_SERVICE_DOWN');
    });
    const mounted = mount(<NotificationsPanel apiFetch={fixture.apiFetch} />);
    await flush();

    expect(textIncludes(mounted.container, 'NOTIFICATION_SERVICE_DOWN')).toBe(true);
    mounted.unmount();
  });
});
