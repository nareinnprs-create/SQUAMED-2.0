import { beforeEach, describe, expect, it } from 'vitest';
import {
  DisplayWorkspace,
  isQueueFrame,
  readBoardCache,
  writeBoardCache,
  type DisplayBoard,
} from './workspace';
import { REALTIME_EVENT, type RealtimeFrame } from '@/providers/RealtimeProvider';
import { fixtureApi, flush, mount, textIncludes } from '@/test-support/react-dom-test';

const boardFixture = (overrides: Partial<DisplayBoard> = {}): DisplayBoard => ({
  generatedAt: '2026-11-18T09:00:00Z',
  counters: [
    {
      servicePointId: 'SP1',
      servicePointName: 'Counter 1',
      departmentId: 'dept-1',
      departmentName: 'Cardiology',
      current: { tokenNumber: 'C-03', queueType: 'consult', priority: 'emergency', calledAt: '2026-11-18T08:59:00Z' },
      next: [
        { tokenNumber: 'C-02', queueType: 'consult', priority: 'urgent', calledAt: null },
        { tokenNumber: 'C-01', queueType: 'consult', priority: 'routine', calledAt: null },
      ],
    },
    {
      servicePointId: 'SP2',
      servicePointName: 'Counter 2',
      departmentId: 'dept-1',
      departmentName: 'Cardiology',
      current: null,
      next: [{ tokenNumber: 'C-04', queueType: 'consult', priority: 'routine', calledAt: null }],
    },
  ],
  ...overrides,
});

const dispatchFrame = (eventType: string) => {
  window.dispatchEvent(
    new CustomEvent(REALTIME_EVENT, {
      detail: { type: 'event.received', event: { eventType, eventId: `ev-${Math.random()}`, payload: {} } } as RealtimeFrame,
    }),
  );
};

beforeEach(() => {
  window.localStorage.clear();
});

describe('display helpers', () => {
  it('treats queue.* frames as board-changing and ignores other events', () => {
    expect(isQueueFrame({ type: 'event.received', event: { eventType: 'queue.called', payload: {} } })).toBe(true);
    expect(isQueueFrame({ type: 'event.received', event: { eventType: 'queue.entry.started', payload: {} } })).toBe(true);
    expect(isQueueFrame({ type: 'event.received', event: { eventType: 'announcement.enqueued', payload: {} } })).toBe(false);
    expect(isQueueFrame(null)).toBe(false);
  });

  it('round-trips the offline cache and ignores malformed entries', () => {
    expect(readBoardCache()).toBeNull();
    window.localStorage.setItem('sq.display-board.v1', 'not-json');
    expect(readBoardCache()).toBeNull();
    writeBoardCache(boardFixture());
    const cached = readBoardCache();
    expect(cached?.savedAt).toBeTruthy();
    expect(cached?.board.counters).toHaveLength(2);
  });
});

describe('DisplayWorkspace', () => {
  it('renders the board grouped per counter with department names and priority-ordered next', async () => {
    const fixture = fixtureApi((path) => (path === '/queue/display-board' ? boardFixture() : null));
    const mounted = mount(<DisplayWorkspace apiFetch={fixture.apiFetch} connected />);
    await flush();

    expect(fixture.calls.map((call) => call.path)).toEqual(['/queue/display-board']);
    expect(textIncludes(mounted.container, 'Counter 1')).toBe(true);
    expect(textIncludes(mounted.container, 'Counter 2')).toBe(true);
    expect(textIncludes(mounted.container, 'Cardiology')).toBe(true);
    expect(textIncludes(mounted.container, 'C-03')).toBe(true); // now serving
    expect(textIncludes(mounted.container, 'C-04')).toBe(true); // counter 2 next
    expect(textIncludes(mounted.container, 'Emergency')).toBe(true);
    const text = mounted.container.textContent ?? '';
    expect(text.indexOf('C-02')).toBeLessThan(text.indexOf('C-01')); // urgent before routine
    mounted.unmount();
  });

  it('shows an empty-board state and no banner while connected', async () => {
    const fixture = fixtureApi(() => ({ generatedAt: '2026-11-18T09:00:00Z', counters: [] }));
    const mounted = mount(<DisplayWorkspace apiFetch={fixture.apiFetch} connected />);
    await flush();

    expect(textIncludes(mounted.container, 'No tokens queued yet.')).toBe(true);
    expect(textIncludes(mounted.container, 'Offline')).toBe(false);
    mounted.unmount();
  });

  it('paints the cached board with an offline banner when the fetch fails', async () => {
    writeBoardCache(boardFixture());
    const fixture = fixtureApi(() => {
      throw new Error('network down');
    });
    const mounted = mount(<DisplayWorkspace apiFetch={fixture.apiFetch} connected />);
    await flush();

    expect(textIncludes(mounted.container, 'Offline')).toBe(true);
    expect(textIncludes(mounted.container, 'C-03')).toBe(true); // cache-first paint
    expect(textIncludes(mounted.container, 'Cardiology')).toBe(true);
    mounted.unmount();
  });

  it('shows the reconnect message when offline with no cache', async () => {
    const fixture = fixtureApi(() => {
      throw new Error('network down');
    });
    const mounted = mount(<DisplayWorkspace apiFetch={fixture.apiFetch} connected={false} />);
    await flush();

    expect(textIncludes(mounted.container, 'No connection')).toBe(true);
    mounted.unmount();
  });

  it('refetches when the realtime bridge reconnects', async () => {
    let down = true;
    const fixture = fixtureApi((path) => {
      if (path !== '/queue/display-board') return null;
      if (down) throw new Error('down');
      return boardFixture();
    });
    const mounted = mount(<DisplayWorkspace apiFetch={fixture.apiFetch} connected={false} />);
    await flush();
    expect(fixture.calls.filter((call) => call.path === '/queue/display-board')).toHaveLength(1);

    down = false;
    mounted.rerender(<DisplayWorkspace apiFetch={fixture.apiFetch} connected />);
    await flush();

    expect(fixture.calls.filter((call) => call.path === '/queue/display-board')).toHaveLength(2); // reconnect refetch
    expect(textIncludes(mounted.container, 'C-03')).toBe(true);
    expect(textIncludes(mounted.container, 'Offline')).toBe(false);
    mounted.unmount();
  });

  it('refetches on queue.* realtime events but not on other events', async () => {
    const fixture = fixtureApi((path) => (path === '/queue/display-board' ? boardFixture() : null));
    const mounted = mount(<DisplayWorkspace apiFetch={fixture.apiFetch} connected />);
    await flush();
    expect(fixture.calls).toHaveLength(1);

    dispatchFrame('announcement.enqueued');
    await flush();
    expect(fixture.calls).toHaveLength(1);

    dispatchFrame('queue.called');
    await flush();
    expect(fixture.calls).toHaveLength(2);
    mounted.unmount();
  });
});
