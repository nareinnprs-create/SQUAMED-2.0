import { describe, expect, it } from 'vitest';
import {
  PlaceOrderForm,
  buildOrderPayload,
  flattenOrders,
  orderMatchesFilters,
  orderStatusVariant,
  orderTypeVariant,
  patientName,
  priorityVariant,
  statusOptions,
  type EncounterOrdersDto,
  type OrderViewDto,
  type PatientOrdersDto,
} from './workspace';
import type { OrderEntryPayload } from '@squamed/ui';
import { clickButton, chooseOption, fixtureApi, flush, mount, textIncludes, typeInto } from '@/test-support/react-dom-test';

const order = (overrides: Partial<OrderViewDto> = {}): OrderViewDto => ({
  id: 'o1',
  encounterId: 'e1',
  patientId: 'p1',
  orderType: 'lab',
  orderCode: 'CBC',
  orderName: 'Complete blood count',
  status: 'pending',
  priority: 'routine',
  instructions: null,
  orderedAt: '2026-11-01T09:00:00Z',
  ...overrides,
});

const encounter = (overrides: Partial<EncounterOrdersDto> = {}): EncounterOrdersDto => ({
  encounterId: 'e1',
  date: '2026-11-01T08:30:00Z',
  status: 'in-progress',
  type: 'opd',
  orders: [order()],
  ...overrides,
});

describe('flattenOrders', () => {
  it('flattens encounter orders with encounter context', () => {
    const view: PatientOrdersDto = {
      encounters: [
        encounter({ orders: [order({ id: 'o1' }), order({ id: 'o2', orderType: 'imaging', orderCode: null, orderName: 'Chest X-ray' })] }),
        encounter({ encounterId: 'e2', date: '2026-10-20T10:00:00Z', status: 'completed', type: 'opd', orders: [] }),
      ],
    };
    const items = flattenOrders(view);
    expect(items).toHaveLength(2);
    expect(items[0].order.id).toBe('o1');
    expect(items[0].encounterId).toBe('e1');
    expect(items[0].encounterStatus).toBe('in-progress');
    expect(items[1].order.orderName).toBe('Chest X-ray');
    expect(items[1].encounterDate).toBe('2026-11-01T08:30:00Z');
  });

  it('tolerates missing encounter collections', () => {
    expect(flattenOrders({ encounters: [] })).toEqual([]);
  });
});

describe('orderMatchesFilters', () => {
  const item = flattenOrders({ encounters: [encounter()] })[0];

  it('passes when filters are "all"', () => {
    expect(orderMatchesFilters(item, 'all', 'all')).toBe(true);
  });

  it('filters by type and status', () => {
    expect(orderMatchesFilters(item, 'lab', 'all')).toBe(true);
    expect(orderMatchesFilters(item, 'imaging', 'all')).toBe(false);
    expect(orderMatchesFilters(item, 'all', 'pending')).toBe(true);
    expect(orderMatchesFilters(item, 'all', 'cancelled')).toBe(false);
  });
});

describe('badge variants', () => {
  it('maps order types', () => {
    expect(orderTypeVariant('lab')).toBe('information');
    expect(orderTypeVariant('medication')).toBe('success');
    expect(orderTypeVariant('referral')).toBe('neutral');
    expect(orderTypeVariant('unknown-type')).toBe('neutral');
  });

  it('maps order statuses', () => {
    expect(orderStatusVariant('pending')).toBe('warning');
    expect(orderStatusVariant('completed')).toBe('success');
    expect(orderStatusVariant('cancelled')).toBe('critical');
    expect(orderStatusVariant('something-new')).toBe('neutral');
  });

  it('maps priorities', () => {
    expect(priorityVariant('stat')).toBe('critical');
    expect(priorityVariant('urgent')).toBe('warning');
    expect(priorityVariant('routine')).toBe('neutral');
    expect(priorityVariant(null)).toBe('neutral');
  });
});

describe('buildOrderPayload', () => {
  it('maps the entry form onto the encounter order contract', () => {
    const payload: OrderEntryPayload = {
      type: 'lab',
      catalogValue: 'CBC',
      name: 'Complete blood count',
      priority: 'urgent',
      notes: 'Fasting sample',
    };
    expect(buildOrderPayload(payload)).toEqual({
      orderType: 'lab',
      orderCode: 'CBC',
      orderName: 'Complete blood count',
      priority: 'urgent',
      instructions: 'Fasting sample',
    });
  });

  it('omits empty instructions and absent catalog values', () => {
    const payload: OrderEntryPayload = {
      type: 'referral',
      catalogValue: undefined,
      name: 'Cardiology referral',
      priority: 'routine',
      notes: '',
    };
    expect(buildOrderPayload(payload)).toEqual({
      orderType: 'referral',
      orderCode: undefined,
      orderName: 'Cardiology referral',
      priority: 'routine',
      instructions: undefined,
    });
  });
});

describe('statusOptions', () => {
  it('prefixes "all" and de-duplicates statuses in sorted order', () => {
    const items = flattenOrders({
      encounters: [
        encounter({ orders: [order({ status: 'cancelled' }), order({ id: 'o2', status: 'pending' }), order({ id: 'o3', status: 'pending' })] }),
      ],
    });
    expect(statusOptions(items)).toEqual(['all', 'cancelled', 'pending']);
  });
});

describe('patientName', () => {
  it('joins names and falls back to MRN', () => {
    expect(patientName({ id: 'p1', mrn: 'M-12', firstName: 'Sara', lastName: 'Ali' })).toBe('Sara Ali');
    expect(patientName({ id: 'p1', mrn: 'M-12', firstName: null, lastName: null })).toBe('M-12');
    expect(patientName({ id: 'p1', mrn: null, firstName: null, lastName: null })).toBe('Unnamed patient');
  });
});

describe('PlaceOrderForm', () => {
  it('loads the active catalog for the selected order type and submits to the encounter endpoint', async () => {
    const fixture = fixtureApi((path) => {
      if (path === '/master-data/lab-tests?active=true&size=200') {
        return [
          { id: 'lt1', code: 'CBC', name: 'Complete blood count', isActive: true },
          { id: null, code: 'X', name: 'No-id entry', isActive: true },
        ];
      }
      return { id: 'o9' };
    });
    let placed = '';
    const mounted = mount(
      <PlaceOrderForm
        apiFetch={fixture.apiFetch}
        encounters={[encounter({ encounterId: 'e1' })]}
        onPlaced={(message) => { placed = message; }}
        onCancel={() => {}}
        onError={() => {}}
      />,
    );
    await flush();

    expect(fixture.calls[0]?.path).toBe('/master-data/lab-tests?active=true&size=200');
    expect(textIncludes(mounted.container, 'Complete blood count (CBC)')).toBe(true);

    await chooseOption(mounted.container, 'Test', 'CBC');
    await typeInto(mounted.container, 'Name / Description', 'Complete blood count');
    await clickButton(mounted.container, 'Submit');
    await flush();

    const submit = fixture.calls.find((call) => call.path === '/encounters/e1/orders');
    expect(submit).toBeDefined();
    expect(submit?.options?.method).toBe('POST');
    expect(JSON.parse(String(submit?.options?.body))).toEqual({
      orderType: 'lab',
      orderCode: 'CBC',
      orderName: 'Complete blood count',
      priority: 'routine',
    });
    expect(placed).toBe('Order placed.');
    mounted.unmount();
  });

  it('shows an honest empty state when the patient has no encounters', async () => {
    const fixture = fixtureApi(() => []);
    const mounted = mount(
      <PlaceOrderForm apiFetch={fixture.apiFetch} encounters={[]} onPlaced={() => {}} onCancel={() => {}} onError={() => {}} />,
    );
    await flush();

    expect(textIncludes(mounted.container, 'no encounters that can take orders')).toBe(true);
    expect(fixture.calls).toHaveLength(0);
    mounted.unmount();
  });

  it('surfaces submit failures through onError', async () => {
    const errors: string[] = [];
    const fixture = fixtureApi((path) => {
      if (path === '/master-data/lab-tests?active=true&size=200') {
        return [];
      }
      throw new Error('ENCOUNTER_NOT_FOUND');
    });
    const mounted = mount(
      <PlaceOrderForm
        apiFetch={fixture.apiFetch}
        encounters={[encounter({ encounterId: 'e1' })]}
        onPlaced={() => {}}
        onCancel={() => {}}
        onError={(message) => errors.push(message)}
      />,
    );
    await flush();

    await typeInto(mounted.container, 'Name / Description', 'Chest X-ray');
    await clickButton(mounted.container, 'Submit');
    await flush();

    expect(errors).toEqual(['ENCOUNTER_NOT_FOUND']);
    mounted.unmount();
  });
});
