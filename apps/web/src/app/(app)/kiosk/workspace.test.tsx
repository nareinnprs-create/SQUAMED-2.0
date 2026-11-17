import { beforeEach, describe, expect, it, vi } from 'vitest';
import { checkInBody, errorText, KioskWorkspace } from './workspace';
import { clickButton, fixtureApi, flush, mount, textIncludes, typeInto } from '@/test-support/react-dom-test';

// qrcode's browser build needs canvas (absent in jsdom); the data URL itself is not under test.
vi.mock('qrcode', () => ({
  default: { toDataURL: vi.fn(async () => 'data:image/png;base64,QRTEST') },
}));

const slip = {
  patientId: 'p-1',
  patientMrn: 'MQ-2026-0001',
  patientName: 'Kiran Rao',
  appointmentId: 'appt-1',
  appointmentStart: '2026-11-18T09:00:00Z',
  departmentName: 'Kiosk Dept',
  token: {
    tokenNumber: 'C-01',
    queueType: 'consult',
    priority: 'routine',
    status: 'waiting',
    estimatedWaitMinutes: 10,
    qrPayload: 'SQM:h1:consult:C-01:2026-11-18',
  },
  printedAt: '2026-11-18T08:30:00Z',
};

const departments = [
  { id: 'dept-1', name: 'Cardiology', description: 'First floor, past the pharmacy' },
  { id: 'dept-2', name: 'Radiology', description: 'Ground floor, block B' },
];

describe('kiosk helpers', () => {
  it('builds the check-in POST body trimmed', () => {
    expect(checkInBody('abha', '  90123456789012  ')).toBe(
      JSON.stringify({ identifierType: 'abha', identifier: '90123456789012' }),
    );
  });

  it('maps kiosk error codes to patient-readable text and keeps unknown codes raw', () => {
    expect(errorText(new Error('KIOSK_NO_APPOINTMENT'))).toContain('reception desk');
    expect(errorText(new Error('SOMETHING_ELSE'))).toContain('SOMETHING_ELSE');
  });
});

describe('KioskWorkspace check-in', () => {
  let print: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    print = vi.fn();
    window.print = print as unknown as typeof window.print;
  });

  it('posts the check-in and shows the token slip with QR and print button', async () => {
    const fixture = fixtureApi(() => slip);
    const mounted = mount(<KioskWorkspace apiFetch={fixture.apiFetch} hospitalId="h1" />);
    await flush();

    await typeInto(mounted.container, 'ABHA number', '90123456789012');
    await clickButton(mounted.container, 'Get token');
    await flush();

    expect(fixture.calls).toEqual([
      { path: '/kiosk/check-in', options: { method: 'POST', body: checkInBody('abha', '90123456789012') } },
    ]);
    expect(textIncludes(mounted.container, 'C-01')).toBe(true);
    expect(textIncludes(mounted.container, 'Kiran Rao')).toBe(true);
    expect(textIncludes(mounted.container, 'Go to: Kiosk Dept')).toBe(true);
    const qrImage = mounted.container.querySelector<HTMLImageElement>('#kiosk-slip img');
    expect(qrImage?.src).toBe('data:image/png;base64,QRTEST');
    expect(qrImage?.alt).toBe('QR code for token C-01');

    await clickButton(mounted.container, 'Print slip');
    expect(print).toHaveBeenCalledTimes(1);

    await clickButton(mounted.container, 'Done');
    expect(textIncludes(mounted.container, 'Identification method')).toBe(true); // back to the form
    mounted.unmount();
  });

  it('shows a patient-readable error and stays on the form when check-in fails', async () => {
    const fixture = fixtureApi(() => {
      throw new Error('KIOSK_PATIENT_NOT_FOUND');
    });
    const mounted = mount(<KioskWorkspace apiFetch={fixture.apiFetch} hospitalId="h1" />);
    await flush();

    await typeInto(mounted.container, 'ABHA number', '90123456789012');
    await clickButton(mounted.container, 'Get token');
    await flush();

    expect(textIncludes(mounted.container, 'No patient found for that value')).toBe(true);
    expect(mounted.container.querySelector('#kiosk-slip')).toBeNull();
    mounted.unmount();
  });
});

describe('KioskWorkspace departments tab', () => {
  it('lists departments with wayfinding descriptions for the pinned hospital', async () => {
    const fixture = fixtureApi((path) => {
      if (path === '/hospital-config/hospitals/h1/departments') return departments;
      return [];
    });
    const mounted = mount(<KioskWorkspace apiFetch={fixture.apiFetch} hospitalId="h1" />);
    await flush();

    await clickButton(mounted.container, 'Departments');
    await flush();

    expect(fixture.calls.map((call) => call.path)).toContain('/hospital-config/hospitals/h1/departments');
    expect(textIncludes(mounted.container, 'Cardiology')).toBe(true);
    expect(textIncludes(mounted.container, 'First floor, past the pharmacy')).toBe(true);
    expect(textIncludes(mounted.container, 'Radiology')).toBe(true);
    mounted.unmount();
  });

  it('shows an empty state when the kiosk has no linked hospital (unparseable token)', async () => {
    const fixture = fixtureApi(() => []);
    const mounted = mount(<KioskWorkspace apiFetch={fixture.apiFetch} hospitalId={null} />);
    await flush();

    await clickButton(mounted.container, 'Departments');
    await flush();

    expect(fixture.calls).toEqual([]); // no hospital id → no fetch
    expect(textIncludes(mounted.container, 'Hospital not linked yet')).toBe(true);
    mounted.unmount();
  });
});
