'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { Badge, Button, Divider, t } from '@squamed/ui';
import { useApi } from '@/lib/use-api';
import { useNotificationsRealtime, useRealtime } from '@/providers/RealtimeProvider';

interface NotificationItem {
  id: string | null;
  channel: string;
  type: string;
  title: string;
  body: string | null;
  payload: string | null;
  readAt: string | null;
  createdAt: string | null;
}

const MAX_VISIBLE = 12;

function formatRelativeTime(value: string | null): string {
  if (!value) return '—';
  const then = new Date(value).getTime();
  if (Number.isNaN(then)) return '—';
  const seconds = Math.max(0, Math.round((Date.now() - then) / 1000));
  if (seconds < 60) return 'just now';
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.round(hours / 24);
  if (days < 30) return `${days}d ago`;
  return new Date(value).toLocaleDateString();
}

export function NotificationBell() {
  const { apiFetch, isReady } = useApi();
  const { connected } = useRealtime();
  const [open, setOpen] = useState(false);
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);
  const [unread, setUnread] = useState(0);
  const [busy, setBusy] = useState<string | null>(null);
  const rootRef = useRef<HTMLDivElement>(null);

  const load = useCallback(async () => {
    if (!isReady) return;
    try {
      const [items, count] = await Promise.all([
        apiFetch<NotificationItem[]>('/notifications'),
        apiFetch<number>('/notifications/unread-count'),
      ]);
      setNotifications(Array.isArray(items) ? items : []);
      setUnread(typeof count === 'number' ? count : 0);
    } catch {
      setNotifications([]);
      setUnread(0);
    }
  }, [apiFetch, isReady]);

  useEffect(() => {
    void load();
  }, [load]);

  useNotificationsRealtime(() => {
    void load();
  });

  useEffect(() => {
    if (!open) return;
    function onPointerDown(event: PointerEvent) {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener('pointerdown', onPointerDown);
    return () => document.removeEventListener('pointerdown', onPointerDown);
  }, [open]);

  async function markAllRead() {
    if (!isReady || busy) return;
    setBusy('all');
    try {
      await apiFetch('/notifications/read-all', { method: 'POST', body: '{}' });
      await load();
    } catch {
      setNotifications([]);
      setUnread(0);
    } finally {
      setBusy(null);
    }
  }

  async function markRead(notification: NotificationItem) {
    if (!isReady || !notification.id || busy) return;
    setBusy(notification.id);
    try {
      await apiFetch(`/notifications/${notification.id}/read`, { method: 'POST', body: '{}' });
      await load();
    } catch {
      await load();
    } finally {
      setBusy(null);
    }
  }

  return (
    <div ref={rootRef} style={{ position: 'relative' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <span
          role="img"
          aria-label={t(connected ? 'shell.notifications.connected' : 'shell.notifications.disconnected')}
          title={t(connected ? 'shell.notifications.connected' : 'shell.notifications.disconnected')}
          style={{
            width: 8,
            height: 8,
            borderRadius: '50%',
            flexShrink: 0,
            backgroundColor: connected ? 'var(--sq-success)' : 'var(--sq-critical)',
          }}
        />
        <Button
          variant="ghost"
          size="sm"
          aria-expanded={open}
          aria-label={t('shell.notifications.count', { count: unread })}
          onClick={() => setOpen((value) => !value)}
          style={{ gap: 6 }}
        >
          <span style={{ position: 'relative', display: 'inline-flex', alignItems: 'center' }}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path
                d="M6 9a6 6 0 1 1 12 0c0 5 2 7 2 7H4s2-2 2-7"
                stroke="currentColor"
                strokeWidth="1.5"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
              <path d="M10 20a2 2 0 0 0 4 0" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
            </svg>
            <Badge
              variant="information"
              size="sm"
              style={{
                position: 'absolute',
                top: -8,
                right: -14,
                border: '2px solid var(--sq-surface)',
              }}
            >
              {unread}
            </Badge>
          </span>
        </Button>
      </div>

      {open ? (
        <div
          role="menu"
          aria-label={t('shell.notifications.title')}
          style={{
            position: 'absolute',
            top: 'calc(100% + 8px)',
            right: 0,
            width: 340,
            maxWidth: 'calc(100vw - 32px)',
            zIndex: 900,
            boxSizing: 'border-box',
            backgroundColor: 'var(--sq-surface)',
            border: '1px solid var(--sq-border)',
            borderRadius: 12,
            boxShadow: '0 16px 48px color-mix(in srgb, var(--sq-fg) 20%, transparent)',
            overflow: 'hidden',
          }}
        >
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              gap: 12,
              padding: '10px 16px',
            }}
          >
            <strong style={{ fontSize: 14 }}>{t('shell.notifications.title')}</strong>
            <Button
              variant="ghost"
              size="sm"
              loading={busy === 'all'}
              disabled={unread === 0}
              onClick={() => void markAllRead()}
            >
              {t('shell.notifications.markAll')}
            </Button>
          </div>
          <Divider />
          <div style={{ maxHeight: 360, overflowY: 'auto' }}>
            {notifications.length === 0 ? (
              <div
                style={{
                  padding: '28px 16px',
                  textAlign: 'center',
                  fontSize: 13,
                  color: 'var(--sq-fg-muted)',
                }}
              >
                {t('shell.notifications.empty')}
              </div>
            ) : (
              notifications
                .slice(0, MAX_VISIBLE)
                .map((notification) => (
                  <button
                    key={notification.id ?? notification.createdAt ?? `${notification.type}-${notification.title}`}
                    type="button"
                    onClick={() => {
                      if (!notification.readAt) void markRead(notification);
                    }}
                    style={{
                      display: 'flex',
                      alignItems: 'flex-start',
                      gap: 10,
                      width: '100%',
                      padding: '10px 16px',
                      border: 'none',
                      borderBottom: '1px solid var(--sq-border)',
                      boxSizing: 'border-box',
                      backgroundColor: notification.readAt
                        ? 'transparent'
                        : 'color-mix(in srgb, var(--sq-information) 8%, transparent)',
                      cursor: 'pointer',
                      textAlign: 'left',
                      fontFamily: 'inherit',
                      fontSize: 13,
                      color: 'var(--sq-fg)',
                    }}
                  >
                    <span style={{ flex: 1, minWidth: 0 }}>
                      <span style={{ display: 'block', fontWeight: 600, overflowWrap: 'break-word' }}>
                        {notification.title}
                      </span>
                      {notification.body ? (
                        <span
                          style={{
                            display: 'block',
                            marginTop: 2,
                            color: 'var(--sq-fg-muted)',
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                            whiteSpace: 'nowrap',
                          }}
                        >
                          {notification.body}
                        </span>
                      ) : null}
                      <span style={{ display: 'block', marginTop: 4, fontSize: 12, color: 'var(--sq-fg-muted)' }}>
                        {formatRelativeTime(notification.createdAt)}
                      </span>
                    </span>
                    <Badge variant="information" size="sm" appearance="soft">
                      {notification.type}
                    </Badge>
                  </button>
                ))
            )}
          </div>
        </div>
      ) : null}
    </div>
  );
}
