'use client';

import { useCallback, useEffect, useState } from 'react';
import {
  Badge,
  Button,
  ContentPanel,
  Input,
  PageHeader,
  Stack,
  StatusIndicator,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  type BadgeVariant,
} from '@squamed/ui';

export interface UserSummaryDto {
  userId: string;
  email: string;
  displayName: string;
  roles: string[];
  permissions: string[];
  themePreference: string;
}

export interface MfaSetupDto {
  otpAuthUrl: string;
  secret: string;
}

export interface NotificationDto {
  id: string | null;
  channel: string;
  type: string;
  title: string;
  body: string | null;
  readAt: string | null;
  deliveredAt: string | null;
  createdAt: string | null;
}

function formatDateTime(value: string | null | undefined): string {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

export function notificationStatusVariant(notification: NotificationDto): BadgeVariant {
  return notification.readAt === null ? 'warning' : 'success';
}

export function notificationStatusLabel(notification: NotificationDto): string {
  return notification.readAt === null ? 'Unread' : 'Read';
}

export type ApiFetch = <T>(path: string, options?: RequestInit) => Promise<T>;

export interface MfaSetupPanelProps {
  apiFetch: ApiFetch;
}

export function MfaSetupPanel({ apiFetch }: MfaSetupPanelProps) {
  const [setup, setSetup] = useState<MfaSetupDto | null>(null);
  const [code, setCode] = useState('');
  const [starting, setStarting] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [enabled, setEnabled] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function startSetup() {
    setStarting(true);
    setError(null);
    try {
      const data = await apiFetch<MfaSetupDto>('/auth/mfa/setup');
      setSetup(data);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to start MFA setup');
    } finally {
      setStarting(false);
    }
  }

  async function confirmSetup() {
    if (code.trim().length !== 6) {
      setError('Enter the 6-digit code from your authenticator app.');
      return;
    }
    setConfirming(true);
    setError(null);
    try {
      await apiFetch('/auth/mfa/setup', {
        method: 'POST',
        body: JSON.stringify({ verificationCode: code.trim() }),
      });
      setEnabled(true);
      setSetup(null);
      setCode('');
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to enable MFA');
    } finally {
      setConfirming(false);
    }
  }

  return (
    <Stack gap={12}>
      {enabled ? <StatusIndicator tone="success" label="Multi-factor authentication is enabled." /> : null}
      {error ? <StatusIndicator tone="critical" label={error} /> : null}

      {setup === null && !enabled ? (
        <Stack direction="row" gap={8} align="center">
          <Button loading={starting} onClick={() => void startSetup()}>
            Start MFA setup
          </Button>
          <span style={{ fontSize: 13, color: 'var(--sq-fg-muted)' }}>
            Adds a time-based one-time password as a second factor for sign-in.
          </span>
        </Stack>
      ) : null}

      {setup ? (
        <Stack gap={12}>
          <div style={{ fontSize: 13, color: 'var(--sq-fg-muted)' }}>
            Add this secret to your authenticator app, then enter the current 6-digit code to confirm.
          </div>
          <code
            style={{
              display: 'inline-block',
              padding: '8px 12px',
              borderRadius: 8,
              border: '1px solid var(--sq-border)',
              background: 'var(--sq-surface)',
              fontFamily: 'monospace',
              fontSize: 14,
              letterSpacing: 1,
            }}
          >
            {setup.secret}
          </code>
          <div style={{ fontSize: 12, color: 'var(--sq-fg-muted)', wordBreak: 'break-all' }}>{setup.otpAuthUrl}</div>
          <Stack direction="row" gap={8} align="flex-end" wrap>
            <div style={{ width: 200 }}>
              <Input
                label="6-digit verification code"
                value={code}
                onChange={(e) => setCode(e.target.value)}
                inputMode="numeric"
                autoFocus
              />
            </div>
            <Button loading={confirming} onClick={() => void confirmSetup()}>
              Confirm and enable
            </Button>
            <Button variant="ghost" onClick={() => { setSetup(null); setError(null); }}>
              Cancel
            </Button>
          </Stack>
        </Stack>
      ) : null}
    </Stack>
  );
}

export interface NotificationsPanelProps {
  apiFetch: ApiFetch;
}

export function NotificationsPanel({ apiFetch }: NotificationsPanelProps) {
  const [notifications, setNotifications] = useState<NotificationDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [working, setWorking] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await apiFetch<NotificationDto[]>('/notifications?unreadOnly=false');
      setNotifications(data ?? []);
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load notifications');
      setNotifications([]);
    } finally {
      setLoading(false);
    }
  }, [apiFetch]);

  useEffect(() => {
    void load();
  }, [load]);

  async function markRead(id: string) {
    setWorking(id);
    setError(null);
    try {
      await apiFetch(`/notifications/${id}/read`, { method: 'POST', body: '{}' });
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to mark notification read');
    } finally {
      setWorking(null);
    }
  }

  async function markAllRead() {
    setWorking('all');
    setError(null);
    try {
      await apiFetch('/notifications/read-all', { method: 'POST', body: '{}' });
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to mark notifications read');
    } finally {
      setWorking(null);
    }
  }

  const unreadCount = notifications.filter((notification) => notification.readAt === null).length;

  return (
    <Stack gap={12}>
      {error ? <StatusIndicator tone="critical" label={error} /> : null}
      <Stack direction="row" gap={8} align="center">
        <span style={{ fontSize: 13, color: 'var(--sq-fg-muted)' }}>
          {unreadCount === 0 ? 'All notifications read.' : `${unreadCount} unread.`}
        </span>
        <div style={{ flex: 1 }} />
        <Button size="sm" variant="outline" loading={working === 'all'} disabled={unreadCount === 0} onClick={() => void markAllRead()}>
          Mark all read
        </Button>
      </Stack>
      {loading && notifications.length === 0 ? (
        <StatusIndicator tone="information" label="Loading notifications..." />
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Notification</TableHead>
              <TableHead>Type</TableHead>
              <TableHead>Channel</TableHead>
              <TableHead>Delivered</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {notifications.length === 0 ? (
              <TableRow>
                <TableCell>No notifications yet.</TableCell>
              </TableRow>
            ) : (
              notifications.map((notification) => (
                <TableRow key={notification.id ?? `${notification.type}-${notification.createdAt}`}>
                  <TableCell>
                    <strong>{notification.title}</strong>
                    {notification.body ? (
                      <div style={{ fontSize: 12, color: 'var(--sq-fg-muted)' }}>{notification.body}</div>
                    ) : null}
                  </TableCell>
                  <TableCell>{notification.type}</TableCell>
                  <TableCell>
                    <Badge size="sm" variant="neutral">
                      {notification.channel}
                    </Badge>
                  </TableCell>
                  <TableCell>{formatDateTime(notification.deliveredAt)}</TableCell>
                  <TableCell>
                    <Badge variant={notificationStatusVariant(notification)}>{notificationStatusLabel(notification)}</Badge>
                  </TableCell>
                  <TableCell>
                    {notification.readAt === null && notification.id !== null ? (
                      <Button size="sm" variant="outline" loading={working === notification.id} onClick={() => void markRead(notification.id as string)}>
                        Mark read
                      </Button>
                    ) : null}
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      )}
    </Stack>
  );
}

function ProfilePanel({ apiFetch }: { apiFetch: ApiFetch }) {
  const [profile, setProfile] = useState<UserSummaryDto | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    void (async () => {
      try {
        const data = await apiFetch<UserSummaryDto>('/auth/me');
        setProfile(data);
      } catch (e) {
        setError(e instanceof Error ? e.message : 'Failed to load profile');
      }
    })();
  }, [apiFetch]);

  if (error) return <StatusIndicator tone="critical" label={error} />;
  if (!profile) return <StatusIndicator tone="information" label="Loading profile..." />;

  return (
    <Stack gap={10}>
      <Stack direction="row" gap={12} wrap>
        <div style={{ minWidth: 220 }}>
          <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--sq-fg-muted)' }}>DISPLAY NAME</div>
          <div style={{ fontSize: 15 }}>{profile.displayName}</div>
        </div>
        <div style={{ minWidth: 220 }}>
          <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--sq-fg-muted)' }}>EMAIL</div>
          <div style={{ fontSize: 15 }}>{profile.email}</div>
        </div>
        <div style={{ minWidth: 180 }}>
          <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--sq-fg-muted)' }}>THEME</div>
          <div style={{ fontSize: 15 }}>{profile.themePreference}</div>
        </div>
      </Stack>
      <Stack direction="row" gap={6} wrap>
        {profile.roles.map((role) => (
          <Badge key={role} variant="information">
            {role}
          </Badge>
        ))}
      </Stack>
    </Stack>
  );
}

export interface SettingsWorkspaceProps {
  apiFetch: ApiFetch;
}

export function SettingsWorkspace({ apiFetch }: SettingsWorkspaceProps) {
  return (
    <div style={{ padding: 24 }}>
      <PageHeader title="Settings" description="Your account, security and notification history." />

      <div style={{ height: 16 }} />

      <Stack gap={16}>
        <ContentPanel>
          <Stack gap={12}>
            <div style={{ fontSize: 15, fontWeight: 600 }}>Profile</div>
            <ProfilePanel apiFetch={apiFetch} />
          </Stack>
        </ContentPanel>

        <ContentPanel>
          <Stack gap={12}>
            <div style={{ fontSize: 15, fontWeight: 600 }}>Security — multi-factor authentication</div>
            <MfaSetupPanel apiFetch={apiFetch} />
          </Stack>
        </ContentPanel>

        <ContentPanel>
          <Stack gap={12}>
            <div style={{ fontSize: 15, fontWeight: 600 }}>Notification history</div>
            <NotificationsPanel apiFetch={apiFetch} />
          </Stack>
        </ContentPanel>
      </Stack>
    </div>
  );
}

