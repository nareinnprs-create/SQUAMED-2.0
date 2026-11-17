'use client';

import { useEffect, useRef, useState } from 'react';
import { Badge, ContentPanel, PageHeader, Select, Stack, StatusIndicator } from '@squamed/ui';
import { REALTIME_EVENT, type RealtimeFrame } from '@/providers/RealtimeProvider';

type ZoneDto = {
  id: string | null;
  name: string;
  servicePointId: string | null;
  volume: number;
  enabled: boolean;
};

/** One announcement.enqueued event, shaped for the display page and the speech runner. */
export type AnnouncementItem = {
  id: string;
  tokenNumber: string | null;
  counter: string | null;
  priority: string | null;
  repeatCount: number;
  languages: string[];
  zoneId: string | null;
  zoneName: string | null;
  volume: number;
  texts: Record<string, string>;
};

export function announcementLang(language: string): string {
  if (language === 'en') return 'en-IN';
  if (language === 'hi') return 'hi-IN';
  return language;
}

/** Zone volume is configured 0-100; SpeechSynthesis utterance volume is 0-1. */
export function announcementVolume(volume: number | undefined): number {
  return Math.max(0, Math.min(1, (volume ?? 80) / 100));
}

/** Parses the realtime frame; returns null for anything that is not an announcement.enqueued event. */
export function parseAnnouncementEvent(frame: RealtimeFrame | null | undefined): AnnouncementItem | null {
  const event = (frame as { event?: { eventType?: string; eventId?: string; payload?: Record<string, unknown> } } | null | undefined)?.event;
  if (!event || event.eventType !== 'announcement.enqueued') return null;
  const payload = event.payload ?? {};
  const texts = (payload.texts ?? {}) as Record<string, string>;
  if (!Object.keys(texts).length) return null;
  return {
    id: String(event.eventId ?? `announcement-${Date.now()}`),
    tokenNumber: (payload.tokenNumber as string | undefined) ?? null,
    counter: (payload.counter as string | undefined) ?? null,
    priority: (payload.priority as string | undefined) ?? null,
    repeatCount: typeof payload.repeatCount === 'number' ? payload.repeatCount : 2,
    languages: Array.isArray(payload.languages) ? (payload.languages as string[]) : Object.keys(texts),
    zoneId: (payload.zoneId as string | undefined) ?? null,
    zoneName: (payload.zoneName as string | undefined) ?? null,
    volume: typeof payload.volume === 'number' ? payload.volume : 80,
    texts,
  };
}

/** Zone filter: null zoneId = every zone; otherwise only the selected zone hears it. */
export function matchesZone(item: AnnouncementItem, selectedZoneId: string | null): boolean {
  if (!selectedZoneId) return true;
  if (!item.zoneId) return true;
  return item.zoneId === selectedZoneId;
}

export function announcementLabel(item: AnnouncementItem): string {
  const text = item.texts.en ?? Object.values(item.texts)[0] ?? '';
  return `${item.tokenNumber ?? 'Manual'} · ${text}`;
}

type SpeechSynthesisLike = {
  cancel: () => void;
  speak: (utterance: SpeechSynthesisUtterance) => void;
};

/**
 * 01.07 display page: listens for `announcement.enqueued` over the realtime bridge and speaks
 * the TTS texts via browser SpeechSynthesis (OS/browser free tier). Emergency announcements
 * cancel current speech and jump the queue; repeats and volume come from the rule/zone payload.
 */
export function AnnouncementsWorkspace({ apiFetch, connected = true }: { apiFetch: (path: string) => Promise<unknown>; connected?: boolean }) {
  const [zones, setZones] = useState<ZoneDto[]>([]);
  const [selectedZone, setSelectedZone] = useState<string>('');
  const [queue, setQueue] = useState<AnnouncementItem[]>([]);
  const [history, setHistory] = useState<AnnouncementItem[]>([]);
  const [speaking, setSpeaking] = useState<AnnouncementItem | null>(null);
  const runIdRef = useRef(0);

  useEffect(() => {
    apiFetch('/queue/zones')
      .then((data) => setZones(Array.isArray(data) ? (data as ZoneDto[]) : []))
      .catch(() => setZones([]));
  }, [apiFetch]);

  useEffect(() => {
    if (typeof window === 'undefined') return;
    const listener = (event: Event) => {
      const item = parseAnnouncementEvent((event as CustomEvent<RealtimeFrame>).detail);
      if (!item) return;
      if (!matchesZone(item, selectedZone || null)) return;
      if (item.priority === 'emergency') {
        // Emergency: stop current speech and jump the queue (contract priority rule).
        (window.speechSynthesis as SpeechSynthesisLike | null)?.cancel();
        runIdRef.current += 1; // stale utterance callbacks no-op
        setSpeaking(null);
        setQueue((current) => [item, ...current.filter((existing) => existing.priority === 'emergency')]);
      } else {
        setQueue((current) => [...current, item]);
      }
    };
    window.addEventListener(REALTIME_EVENT, listener);
    return () => window.removeEventListener(REALTIME_EVENT, listener);
  }, [selectedZone]);

  useEffect(() => {
    if (speaking || queue.length === 0) return;
    if (typeof window === 'undefined' || !window.speechSynthesis) return;
    const next = queue[0];
    setSpeaking(next);
    const runId = ++runIdRef.current;
    const finish = () => {
      if (runIdRef.current !== runId) return; // superseded by an emergency jump
      setSpeaking(null);
      setQueue((current) => current.slice(1));
      setHistory((current) => [next, ...current].slice(0, 15));
    };
    const langs = next.languages.length > 0 ? next.languages : ['en'];
    for (let repeat = 0; repeat < next.repeatCount; repeat += 1) {
      langs.forEach((lang) => {
        const text = next.texts[lang] ?? next.texts.en ?? '';
        const utterance = new SpeechSynthesisUtterance(text);
        utterance.lang = announcementLang(lang);
        utterance.volume = announcementVolume(next.volume);
        const isLast = repeat === next.repeatCount - 1 && lang === langs[langs.length - 1];
        if (isLast) {
          utterance.addEventListener('end', finish);
          utterance.addEventListener('error', finish);
        }
        window.speechSynthesis.speak(utterance);
      });
    }
  }, [queue, speaking]);

  const heading = `Announcement queue ${speaking ? '· speaking' : ''}`;

  return (
    <div style={{ padding: 24, minHeight: '100vh', background: 'var(--sq-bg, #0b1220)' }}>
      <PageHeader
        title={heading}
        description="Voice display for called tokens: TTS runs in this browser per the hospital's announcement rules."
        actions={<StatusIndicator tone={connected ? 'success' : 'critical'} label={connected ? 'Realtime connected' : 'Realtime offline'} />}
      />
      <Stack gap={16}>
        <ContentPanel style={{ display: 'flex', gap: 16, alignItems: 'center' }}>
          <div style={{ width: 280 }}>
            <Select
              label="Announcement zone"
              value={selectedZone}
              onChange={(e) => setSelectedZone(e.target.value)}
            >
              <option value="">All zones</option>
              {zones
                .filter((zone) => zone.enabled)
                .map((zone) => (
                  <option key={zone.id ?? zone.name} value={zone.id ?? ''}>
                    {zone.name}
                  </option>
                ))}
            </Select>
          </div>
          <Badge variant={speaking ? 'information' : 'neutral'}>{speaking ? 'Speaking' : 'Idle'}</Badge>
        </ContentPanel>

        <ContentPanel>
          <div style={{ fontWeight: 700, marginBottom: 8 }}>Now / next</div>
          {queue.length === 0 ? (
            <StatusIndicator tone="information" label="No announcements queued — waiting for calls." />
          ) : (
            <ul style={{ listStyle: 'none', margin: 0, padding: 0 }}>
              {queue.map((item) => (
                <li key={item.id} style={{ padding: '8px 0', borderBottom: '1px solid var(--sq-border, #1e293b)' }}>
                  <strong>{item.tokenNumber ?? 'Manual announcement'}</strong> ×{item.repeatCount} · {item.counter ?? 'all zones'}
                </li>
              ))}
            </ul>
          )}
        </ContentPanel>

        <ContentPanel>
          <div style={{ fontWeight: 700, marginBottom: 8 }}>Last announcements</div>
          {history.length === 0 ? (
            <StatusIndicator tone="information" label="Nothing announced yet." />
          ) : (
            <ul style={{ listStyle: 'none', margin: 0, padding: 0 }}>
              {history.map((item) => (
                <li key={item.id} style={{ padding: '8px 0', borderBottom: '1px solid var(--sq-border, #1e293b)' }}>
                  {announcementLabel(item)}
                </li>
              ))}
            </ul>
          )}
        </ContentPanel>
      </Stack>
    </div>
  );
}
