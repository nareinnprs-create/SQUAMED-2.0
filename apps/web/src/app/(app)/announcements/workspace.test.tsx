import { beforeEach, describe, expect, it } from 'vitest';
import {
  AnnouncementsWorkspace,
  announcementLang,
  announcementVolume,
  matchesZone,
  parseAnnouncementEvent,
  type AnnouncementItem,
} from './workspace';
import { REALTIME_EVENT } from '@/providers/RealtimeProvider';
import { chooseOption, fixtureApi, flush, mount, textIncludes } from '@/test-support/react-dom-test';

const spoken: FakeUtterance[] = [];
let cancelled = 0;

class FakeUtterance {
  text: string;
  lang = '';
  volume = 1;
  listeners: Record<string, Array<() => void>> = {};

  constructor(text: string) {
    this.text = text;
  }

  addEventListener(type: string, callback: () => void) {
    (this.listeners[type] ??= []).push(callback);
  }
}

const zonesFixture = [
  { id: 'zone-1', name: 'Main Display', servicePointId: 'SP1', volume: 90, enabled: true },
  { id: 'zone-2', name: 'Pharmacy Window', servicePointId: 'SP2', volume: 75, enabled: true },
];

const announcementPayload = (overrides: Record<string, unknown> = {}): Record<string, unknown> => ({
  tokenNumber: 'C-01',
  counter: 'Counter 1',
  priority: 'routine',
  repeatCount: 3,
  languages: ['en', 'hi'],
  zoneId: null,
  zoneName: null,
  volume: 90,
  texts: { en: 'Token C-01, counter Counter 1', hi: 'टोकन C-01, काउंटर Counter 1' },
  ...overrides,
});

const dispatchAnnouncement = (payload: Record<string, unknown>) => {
  window.dispatchEvent(
    new CustomEvent(REALTIME_EVENT, {
      detail: { type: 'event.received', event: { eventType: 'announcement.enqueued', eventId: `ev-${Math.random()}`, payload } },
    }),
  );
};

const item = (overrides: Partial<AnnouncementItem> = {}): AnnouncementItem => ({
  id: 'a1',
  tokenNumber: 'C-01',
  counter: 'Counter 1',
  priority: 'routine',
  repeatCount: 2,
  languages: ['en', 'hi'],
  zoneId: null,
  zoneName: null,
  volume: 80,
  texts: { en: 'Token C-01' },
  ...overrides,
});

describe('announcement helpers', () => {
  it('maps languages to browser BCP tags and normalizes volume 0-100 → 0-1', () => {
    expect(announcementLang('en')).toBe('en-IN');
    expect(announcementLang('hi')).toBe('hi-IN');
    expect(announcementLang('ta')).toBe('ta');
    expect(announcementVolume(90)).toBe(0.9);
    expect(announcementVolume(150)).toBe(1);
    expect(announcementVolume(undefined)).toBe(0.8);
  });

  it('parses announcement.enqueued frames and rejects other events', () => {
    const parsed = parseAnnouncementEvent({
      type: 'event.received',
      event: { eventType: 'announcement.enqueued', eventId: 'ev-9', payload: announcementPayload() },
    });
    expect(parsed?.tokenNumber).toBe('C-01');
    expect(parsed?.languages).toEqual(['en', 'hi']);
    expect(parseAnnouncementEvent({ type: 'event.received', event: { eventType: 'queue.called', payload: {} } })).toBeNull();
    expect(parseAnnouncementEvent(null)).toBeNull();
  });

  it('passes all-zone events and filters foreign zones when one is selected', () => {
    const allZones = item();
    const otherZone = item({ id: 'a2', zoneId: 'zone-9' });
    expect(matchesZone(allZones, 'zone-1')).toBe(true);
    expect(matchesZone(otherZone, 'zone-1')).toBe(false);
    expect(matchesZone(otherZone, null)).toBe(true);
  });
});

describe('AnnouncementsWorkspace', () => {
  beforeEach(() => {
    spoken.length = 0;
    cancelled = 0;
    window.speechSynthesis = {
      cancel: () => {
        cancelled += 1;
      },
      speak: (utterance: SpeechSynthesisUtterance) => {
        spoken.push(utterance as unknown as FakeUtterance);
      },
    } as unknown as SpeechSynthesis;
    (globalThis as unknown as Record<string, unknown>).SpeechSynthesisUtterance = FakeUtterance;
  });

  it('loads zones and speaks queued announcements per language with zone volume', async () => {
    const fixture = fixtureApi((path) => {
      if (path === '/queue/zones') return zonesFixture;
      return [];
    });
    const mounted = mount(<AnnouncementsWorkspace apiFetch={fixture.apiFetch} />);
    await flush();

    expect(fixture.calls.map((call) => call.path)).toContain('/queue/zones');
    dispatchAnnouncement(announcementPayload());
    await flush();

    expect(textIncludes(mounted.container, 'Token C-01, counter Counter 1')).toBe(false); // queued, not yet in history
    expect(textIncludes(mounted.container, 'C-01 ×3')).toBe(true);
    // repeat 3 × languages [en, hi] = 6 utterances, zone volume 90 → 0.9.
    expect(spoken).toHaveLength(6);
    expect(spoken[0].text).toBe('Token C-01, counter Counter 1');
    expect(spoken[0].lang).toBe('en-IN');
    expect(spoken[0].volume).toBe(0.9);
    expect(spoken[1].lang).toBe('hi-IN');

    // Last utterance end → item moves to history.
    spoken[5].listeners.end?.forEach((callback) => callback());
    await flush();
    expect(textIncludes(mounted.container, 'Token C-01, counter Counter 1')).toBe(true);
    mounted.unmount();
  });

  it('zone select filters announcements from other zones', async () => {
    const fixture = fixtureApi((path) => {
      if (path === '/queue/zones') return zonesFixture;
      return [];
    });
    const mounted = mount(<AnnouncementsWorkspace apiFetch={fixture.apiFetch} />);
    await flush();

    await chooseOption(mounted.container, 'Announcement zone', 'zone-2');
    await flush();

    dispatchAnnouncement(announcementPayload({ tokenNumber: 'C-05', zoneId: 'zone-1', zoneName: 'Main Display' }));
    await flush();
    expect(textIncludes(mounted.container, 'C-05 ×3')).toBe(false);

    dispatchAnnouncement(announcementPayload({ tokenNumber: 'C-06' })); // all-zones event passes any filter
    await flush();
    expect(textIncludes(mounted.container, 'C-06 ×3')).toBe(true);
    mounted.unmount();
  });

  it('emergency announcements cancel current speech and jump the queue', async () => {
    const fixture = fixtureApi((path) => {
      if (path === '/queue/zones') return zonesFixture;
      return [];
    });
    const mounted = mount(<AnnouncementsWorkspace apiFetch={fixture.apiFetch} />);
    await flush();

    dispatchAnnouncement(announcementPayload({ tokenNumber: 'C-10' }));
    await flush();
    dispatchAnnouncement(announcementPayload({ tokenNumber: 'C-11', priority: 'emergency' }));
    await flush();

    expect(cancelled).toBe(1);
    expect(textIncludes(mounted.container, 'C-11 ×3')).toBe(true);
    expect(textIncludes(mounted.container, 'C-10 ×3')).toBe(false); // emergency jump drops routine items
    mounted.unmount();
  });
});
