import { beforeEach, describe, expect, it, vi } from 'vitest';
import { DowntimeWorkspace, parseTokenLines, parseVisitIds } from './workspace';
import { clickButton, fixtureApi, flush, mount, textIncludes, typeInto } from '@/test-support/react-dom-test';
import { act } from 'react';

/** Textarea twin of typeInto (test-support covers HTMLInputElement only); prototype setter bypasses React's value tracker. */
async function typeIntoTextarea(container: HTMLElement, label: string, value: string): Promise<void> {
  const labelEl = Array.from(container.querySelectorAll('label')).find(
    (entry) => entry.textContent?.replace(/\s*\*\s*$/, '').trim() === label,
  );
  const textarea = labelEl?.htmlFor ? document.getElementById(labelEl.htmlFor) : null;
  if (!(textarea instanceof HTMLTextAreaElement)) throw new Error(`No textarea labelled "${label}"`);
  const setter = Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value')?.set;
  if (!setter) throw new Error('Native value setter unavailable');
  await act(async () => {
    setter.call(textarea, value);
    textarea.dispatchEvent(new Event('input', { bubbles: true }));
  });
}

const windows = [
  { id: 'w-open', reason: 'PG outage drill', startedAt: '2026-11-18T09:00:00Z', endedAt: null, open: true },
  {
    id: 'w-closed',
    reason: 'Network switch replacement',
    startedAt: '2026-11-17T14:00:00Z',
    endedAt: '2026-11-17T15:30:00Z',
    open: false,
  },
];

describe('downtime re-entry parsers', () => {
  it('parses paper token lines with optional name and priority', () => {
    expect(
      parseTokenLines('consult,DT-01,p-1,Devika Paper\n\nconsult,DT-02,p-2,,urgent'),
    ).toEqual([
      { queueType: 'consult', tokenNumber: 'DT-01', patientId: 'p-1', patientName: 'Devika Paper', priority: undefined },
      { queueType: 'consult', tokenNumber: 'DT-02', patientId: 'p-2', patientName: undefined, priority: 'urgent' },
    ]);
  });

  it('rejects token lines missing the required triple', () => {
    expect(() => parseTokenLines('consult,DT-01')).toThrow('queueType,tokenNumber,patientId');
    expect(() => parseTokenLines('')).toThrow('Add at least one paper token line');
  });

  it('parses comma-separated visit ids and drops blanks', () => {
    expect(parseVisitIds(' v1 , v2 ,, ')).toEqual(['v1', 'v2']);
  });
});

describe('DowntimeWorkspace log tab', () => {
  it('lists windows and closes the open one with a PATCH', async () => {
    const fixture = fixtureApi((path) => {
      if (path === '/downtime/windows/w-open/close') {
        return { id: 'w-open', reason: 'PG outage drill', startedAt: '2026-11-18T09:00:00Z', endedAt: '2026-11-18T10:00:00Z', open: false };
      }
      if (path === '/downtime/windows') return windows;
      return [];
    });
    const mounted = mount(<DowntimeWorkspace apiFetch={fixture.apiFetch} hospitalId="h1" />);
    await flush();

    expect(textIncludes(mounted.container, 'PG outage drill')).toBe(true);
    expect(textIncludes(mounted.container, 'Network switch replacement')).toBe(true);

    await clickButton(mounted.container, 'Close window');
    await flush();

    const patch = fixture.calls.find((call) => call.options?.method === 'PATCH');
    expect(patch?.path).toBe('/downtime/windows/w-open/close');
    expect(textIncludes(mounted.container, 'Downtime window closed')).toBe(true);
    mounted.unmount();
  });
});

describe('DowntimeWorkspace re-entry tool', () => {
  it('parses the paper lines and posts them with the visit ids', async () => {
    const fixture = fixtureApi((path) => {
      if (path === '/downtime/windows') return windows;
      if (path === '/downtime/reentries') {
        return {
          tokens: [
            { tokenNumber: 'DT-01', queueType: 'consult', priority: 'routine' },
            { tokenNumber: 'DT-02', queueType: 'consult', priority: 'urgent' },
          ],
          visits: [{ id: 'v-1', status: 'checked-in' }],
        };
      }
      return [];
    });
    const mounted = mount(<DowntimeWorkspace apiFetch={fixture.apiFetch} hospitalId="h1" />);
    await flush();

    await clickButton(mounted.container, 'Re-entry tool');
    await flush();
    await typeIntoTextarea(
      mounted.container,
      'Paper tokens (one per line: queueType,tokenNumber,patientId[,patientName][,priority])',
      'consult,DT-01,p-1,Devika Paper\nconsult,DT-02,p-2,,urgent',
    );
    await typeInto(mounted.container, 'Paper check-ins (visit ids, comma-separated)', 'v-1');
    await clickButton(mounted.container, 'Re-enter paper records');
    await flush();

    const post = fixture.calls.find((call) => call.path === '/downtime/reentries');
    expect(JSON.parse(String(post?.options?.body))).toEqual({
      tokens: [
        { queueType: 'consult', tokenNumber: 'DT-01', patientId: 'p-1', patientName: 'Devika Paper', priority: undefined },
        { queueType: 'consult', tokenNumber: 'DT-02', patientId: 'p-2', patientName: undefined, priority: 'urgent' },
      ],
      visitIds: ['v-1'],
      hospitalId: 'h1',
    });
    expect(textIncludes(mounted.container, '2 tokens (DT-01, DT-02), 1 check-ins')).toBe(true);
    mounted.unmount();
  });

  it('blocks the submit client-side when no paper token line is entered', async () => {
    const fixture = fixtureApi((path) => {
      if (path === '/downtime/windows') return windows;
      return [];
    });
    const mounted = mount(<DowntimeWorkspace apiFetch={fixture.apiFetch} hospitalId="h1" />);
    await flush();

    await clickButton(mounted.container, 'Re-entry tool');
    await flush();
    await clickButton(mounted.container, 'Re-enter paper records');
    await flush();

    expect(textIncludes(mounted.container, 'Add at least one paper token line')).toBe(true);
    expect(fixture.calls.some((call) => call.path === '/downtime/reentries')).toBe(false);
    mounted.unmount();
  });
});

describe('DowntimeWorkspace print pack', () => {
  let print: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    print = vi.fn();
    window.print = print as unknown as typeof window.print;
  });

  it('loads today sheet, board and patient names, then prints', async () => {
    const today = new Date().toISOString().slice(0, 10);
    const fixture = fixtureApi((path) => {
      if (path === '/appointments?limit=200') {
        return [{ id: 'appt-1', patientId: 'p-1', scheduledStart: `${today}T09:00:00Z`, status: 'scheduled', type: 'consult' }];
      }
      if (path === '/downtime/windows') return windows;
      if (path === '/queue/board?queueType=consult') {
        return [{ tokenNumber: 'C-01', patientName: 'Arjun', priority: 'emergency', status: 'waiting' }];
      }
      if (path === '/patients/p-1') return { firstName: 'Farah', lastName: 'Paper' };
      return [];
    });
    const mounted = mount(<DowntimeWorkspace apiFetch={fixture.apiFetch} hospitalId="h1-uuid" />);
    await flush();

    await clickButton(mounted.container, 'Print pack'); // activate the pack tab
    await flush();
    await clickButton(mounted.container, 'Load pack data');
    await flush();

    expect(fixture.calls.map((call) => call.path)).toEqual(
      expect.arrayContaining(['/appointments?limit=200', '/downtime/windows', '/queue/board?queueType=consult', '/patients/p-1']),
    );
    expect(textIncludes(mounted.container, 'Farah Paper')).toBe(true);
    expect(textIncludes(mounted.container, '09:00')).toBe(true);
    expect(textIncludes(mounted.container, 'C-01')).toBe(true);
    expect(textIncludes(mounted.container, 'Paper token #')).toBe(true);

    await clickButton(mounted.container, 'Print');
    expect(print).toHaveBeenCalledTimes(1);
    mounted.unmount();
  });
});
