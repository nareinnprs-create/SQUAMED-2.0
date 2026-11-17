'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { ContentPanel, PageHeader, Stack, StatusIndicator } from '@squamed/ui';
import { REALTIME_EVENT, type RealtimeFrame, useRealtime } from '@/providers/RealtimeProvider';

export type DisplayToken = {
  tokenNumber: string;
  queueType: string;
  priority: string;
  calledAt: string | null;
};

export type CounterBoard = {
  servicePointId: string;
  servicePointName: string;
  departmentId: string | null;
  departmentName: string | null;
  current: DisplayToken | null;
  next: DisplayToken[];
};

export type DisplayBoard = {
  generatedAt: string;
  counters: CounterBoard[];
};

const CACHE_KEY = 'sq.display-board.v1';

/** Last board saved for offline paint; malformed or foreign cache entries are ignored. */
export function readBoardCache(): { savedAt: string; board: DisplayBoard } | null {
  if (typeof window === 'undefined') return null;
  try {
    const raw = window.localStorage.getItem(CACHE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as { savedAt?: string; board?: DisplayBoard };
    if (!parsed.board || !Array.isArray(parsed.board.counters)) return null;
    return { savedAt: parsed.savedAt ?? '', board: parsed.board };
  } catch {
    return null;
  }
}

export function writeBoardCache(board: DisplayBoard): void {
  try {
    window.localStorage.setItem(CACHE_KEY, JSON.stringify({ savedAt: new Date().toISOString(), board }));
  } catch {
    // private-mode/full storage: the offline cache is best-effort
  }
}

/** Frames that change the board: any queue.* event (called/started/skipped/no-show/...). */
export function isQueueFrame(frame: RealtimeFrame | null | undefined): boolean {
  const eventType = (frame as { event?: { eventType?: string } } | null | undefined)?.event?.eventType ?? '';
  return eventType.startsWith('queue.');
}

/**
 * 01.06 TV display: waiting-room board grouped per counter (now serving + next tokens).
 * Cache-first paint from localStorage, then refetch on mount, on realtime reconnect,
 * on any queue.* realtime event, and on a 60s safety poll.
 */
export function DisplayWorkspace({ apiFetch, connected }: { apiFetch: (path: string) => Promise<unknown>; connected?: boolean }) {
  const realtime = useRealtime();
  const isLive = connected ?? realtime.connected;
  const [board, setBoard] = useState<DisplayBoard | null>(null);
  const [cachedAt, setCachedAt] = useState<string | null>(null);
  const [offline, setOffline] = useState(false);

  const load = useCallback(async () => {
    try {
      const data = (await apiFetch('/queue/display-board')) as DisplayBoard;
      if (data && Array.isArray(data.counters)) {
        setBoard(data);
        setOffline(false);
        setCachedAt(null);
        writeBoardCache(data);
      }
    } catch {
      // Network/HTTP failure: keep whatever board is on screen and flag the staleness.
      setOffline(true);
    }
  }, [apiFetch]);

  // Cache-first paint: the last saved board shows immediately, then the network refresh takes over.
  useEffect(() => {
    const cached = readBoardCache();
    if (cached) {
      setBoard(cached.board);
      setCachedAt(cached.savedAt);
    }
    void load();
  }, [load]);

  // Refetch as soon as the realtime bridge comes back after a drop.
  const wasConnected = useRef(isLive);
  useEffect(() => {
    if (!wasConnected.current && isLive) void load();
    wasConnected.current = isLive;
  }, [isLive, load]);

  // Board-changing realtime events (queue.called/started/skipped/...) trigger a refetch.
  useEffect(() => {
    if (typeof window === 'undefined') return;
    const listener = (event: Event) => {
      if (isQueueFrame((event as CustomEvent<RealtimeFrame>).detail)) void load();
    };
    window.addEventListener(REALTIME_EVENT, listener);
    return () => window.removeEventListener(REALTIME_EVENT, listener);
  }, [load]);

  // 60s safety poll in case realtime events are missed.
  useEffect(() => {
    const timer = window.setInterval(() => void load(), 60_000);
    return () => window.clearInterval(timer);
  }, [load]);

  const counters = board?.counters ?? [];
  const savedTime = cachedAt ? new Date(cachedAt).toLocaleTimeString() : null;

  return (
    <div style={{ padding: 24, minHeight: '100vh', background: 'var(--sq-bg, #0b1220)' }}>
      <PageHeader
        title="Counter display"
        description="Waiting-room TV board: now serving and next tokens per counter."
        actions={
          <StatusIndicator
            tone={offline ? 'critical' : isLive ? 'success' : 'warning'}
            label={offline ? 'Offline — showing last board' : isLive ? 'Live' : 'Reconnecting…'}
          />
        }
      />
      <Stack gap={16}>
        {offline && (
          <ContentPanel>
            <StatusIndicator
              tone="critical"
              label={savedTime ? `Offline — showing the board saved at ${savedTime}; reconnecting…` : 'Offline — the board will refresh when the display reconnects.'}
            />
          </ContentPanel>
        )}
        {counters.length === 0 ? (
          <ContentPanel>
            <StatusIndicator
              tone="information"
              label={offline ? 'No connection — the board will load when the display reconnects.' : 'No tokens queued yet.'}
            />
          </ContentPanel>
        ) : (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: 16 }}>
            {counters.map((counter) => (
              <ContentPanel key={counter.servicePointId}>
                <div style={{ marginBottom: 12 }}>
                  <div style={{ fontSize: 22, fontWeight: 700 }}>{counter.servicePointName}</div>
                  {counter.departmentName && <div style={{ opacity: 0.85 }}>{counter.departmentName}</div>}
                </div>
                <div style={{ marginBottom: 12 }}>
                  <div style={{ fontSize: 13, opacity: 0.85, textTransform: 'uppercase' }}>Now serving</div>
                  <div style={{ fontSize: 40, fontWeight: 700 }}>{counter.current?.tokenNumber ?? '—'}</div>
                  {counter.current?.priority === 'emergency' && <StatusIndicator tone="critical" label="Emergency" />}
                </div>
                <div>
                  <div style={{ fontSize: 13, opacity: 0.85, textTransform: 'uppercase' }}>Next</div>
                  {counter.next.length === 0 ? (
                    <div style={{ opacity: 0.85 }}>—</div>
                  ) : (
                    counter.next.map((token) => (
                      <div
                        key={`${token.queueType}-${token.tokenNumber}`}
                        style={{ display: 'flex', justifyContent: 'space-between', padding: '4px 0', borderBottom: '1px solid var(--sq-border, #1e293b)' }}
                      >
                        <span style={{ fontWeight: 600 }}>{token.tokenNumber}</span>
                        <span style={{ opacity: 0.85 }}>{token.priority}</span>
                      </div>
                    ))
                  )}
                </div>
              </ContentPanel>
            ))}
          </div>
        )}
      </Stack>
    </div>
  );
}
