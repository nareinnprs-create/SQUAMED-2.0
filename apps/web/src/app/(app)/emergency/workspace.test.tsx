import { describe, expect, it } from 'vitest';
import { TRIAGE_TAG_PRIORITY, EmergencyWorkspace, tagMessage } from './workspace';
import { clickButton, fixtureApi, flush, mount, textIncludes } from '@/test-support/react-dom-test';

const board = [
  {
    queueType: 'consult',
    tokenNumber: 'C-01',
    patientName: 'Arjun Walkin',
    status: 'waiting',
    priority: 'routine',
    estimatedWaitMinutes: 7,
    calledAt: null,
  },
  {
    queueType: 'consult',
    tokenNumber: 'C-02',
    patientName: 'Beena Walkin',
    status: 'called',
    priority: 'urgent',
    estimatedWaitMinutes: null,
    calledAt: '2026-11-18T09:01:00Z',
  },
];

describe('emergency fast-path helpers', () => {
  it('maps tags to the queue priority they boost to', () => {
    expect(TRIAGE_TAG_PRIORITY.red).toBe('emergency');
    expect(TRIAGE_TAG_PRIORITY.yellow).toBe('urgent');
    expect(TRIAGE_TAG_PRIORITY.green).toBe('routine');
  });

  it('words the tag feedback per tag', () => {
    expect(tagMessage('C-01', 'red', 'emergency').label).toBe('C-01 boosted to emergency (red tag)');
    expect(tagMessage('C-01', 'green', 'routine').label).toBe('C-01 stays routine (green tag)');
  });
});

describe('EmergencyWorkspace', () => {
  it('renders the walk-in board with triage buttons only on waiting rows', async () => {
    const fixture = fixtureApi(() => board);
    const mounted = mount(<EmergencyWorkspace apiFetch={fixture.apiFetch} />);
    await flush();

    expect(fixture.calls[0]?.path).toBe('/queue/board?queueType=consult');
    expect(textIncludes(mounted.container, 'C-01')).toBe(true);
    expect(textIncludes(mounted.container, 'Arjun Walkin')).toBe(true);
    expect(textIncludes(mounted.container, 'Beena Walkin')).toBe(true);
    // Red/Yellow/Green exactly once (the waiting row); the called row shows a dash.
    const reds = Array.from(mounted.container.querySelectorAll('button')).filter((b) => b.textContent?.trim() === 'Red');
    expect(reds).toHaveLength(1);
    expect(textIncludes(mounted.container, 'No walk-ins')).toBe(false);
    mounted.unmount();
  });

  it('posts the triage-lite tag and reloads the board with the boosted priority', async () => {
    let tagged = false;
    const fixture = fixtureApi((path, options) => {
      if (path === '/queue/entries/consult/C-01/triage-lite' && options?.method === 'POST') {
        tagged = true;
        return { queueType: 'consult', tokenNumber: 'C-01', priority: 'emergency', status: 'waiting' };
      }
      if (path === '/queue/board?queueType=consult') {
        return tagged
          ? [{ ...board[0], priority: 'emergency' }, board[1]]
          : board;
      }
      return [];
    });
    const mounted = mount(<EmergencyWorkspace apiFetch={fixture.apiFetch} />);
    await flush();

    await clickButton(mounted.container, 'Red');
    await flush();

    const post = fixture.calls.find((call) => call.path === '/queue/entries/consult/C-01/triage-lite');
    expect(post?.options?.method).toBe('POST');
    expect(JSON.parse(String(post?.options?.body))).toEqual({ tag: 'red' });
    expect(textIncludes(mounted.container, 'C-01 boosted to emergency (red tag)')).toBe(true);
    // Board reloaded after the boost.
    expect(fixture.calls.filter((call) => call.path.startsWith('/queue/board')).length).toBeGreaterThanOrEqual(2);
    mounted.unmount();
  });

  it('shows a critical message and keeps the board when the tag is rejected', async () => {
    const fixture = fixtureApi((path, options) => {
      if (options?.method === 'POST') throw new Error('TRIAGE_TAG_INVALID');
      if (path === '/queue/board?queueType=consult') return board;
      return [];
    });
    const mounted = mount(<EmergencyWorkspace apiFetch={fixture.apiFetch} />);
    await flush();

    await clickButton(mounted.container, 'Yellow');
    await flush();

    expect(textIncludes(mounted.container, 'TRIAGE_TAG_INVALID')).toBe(true);
    expect(textIncludes(mounted.container, 'C-01 boosted')).toBe(false);
    mounted.unmount();
  });
});
