import { describe, expect, it } from 'vitest';
import {
  CATALOGS,
  CatalogCreateForm,
  buildCatalogQuery,
  buildCreatePayload,
  catalogRowFrom,
  detailValue,
  missingRequired,
  type CatalogDto,
} from './workspace';
import { clickButton, fixtureApi, flush, mount, textIncludes, typeInto } from '@/test-support/react-dom-test';

describe('buildCatalogQuery', () => {
  it('always sends paging', () => {
    expect(buildCatalogQuery('', 'all', 0)).toBe('?page=0&size=20');
  });

  it('sends search and active filter when set', () => {
    expect(buildCatalogQuery('Para', 'active', 2)).toBe('?search=Para&active=true&page=2&size=20');
    expect(buildCatalogQuery('Para', 'inactive', 0)).toBe('?search=Para&active=false&page=0&size=20');
  });

  it('trims search and drops empty terms', () => {
    expect(buildCatalogQuery('  ', 'all', 0)).toBe('?page=0&size=20');
    expect(buildCatalogQuery(' CBC ', 'all', 0)).toBe('?search=CBC&page=0&size=20');
  });

  it('never sends a negative page', () => {
    expect(buildCatalogQuery('', 'all', -3)).toBe('?page=0&size=20');
  });
});

describe('buildCreatePayload', () => {
  it('keeps required and filled optional fields, drops empty ones', () => {
    expect(
      buildCreatePayload('drugs', { code: ' D001 ', name: ' Paracetamol ', genericName: '', form: 'Tablet' }),
    ).toEqual({ code: 'D001', name: 'Paracetamol', form: 'Tablet' });
  });

  it('casts number fields', () => {
    expect(buildCreatePayload('charge-items', { code: 'CONS', name: 'Consultation', priceQar: '12.5' })).toEqual({
      code: 'CONS',
      name: 'Consultation',
      priceQar: 12.5,
    });
  });

  it('returns an empty payload for unknown catalogs', () => {
    expect(buildCreatePayload('nope' as never, { code: 'X' })).toEqual({});
  });
});

describe('missingRequired', () => {
  it('lists missing required labels per catalog', () => {
    const procedure = CATALOGS.find((spec) => spec.key === 'procedure-codes');
    expect(procedure).toBeDefined();
    expect(missingRequired(procedure as NonNullable<typeof procedure>, {})).toEqual(['Code', 'Name', 'System']);
    expect(missingRequired(procedure as NonNullable<typeof procedure>, { code: 'P1', name: 'Suture', system: 'ICD-10' })).toEqual([]);
  });
});

describe('catalogRowFrom', () => {
  const drug: CatalogDto = {
    id: 'd1',
    code: 'D001',
    name: 'Paracetamol',
    form: 'Tablet',
    strength: '500 mg',
    isActive: true,
  };

  it('projects catalog detail columns', () => {
    const row = catalogRowFrom('drugs', drug);
    expect(row).toEqual({
      id: 'd1',
      code: 'D001',
      name: 'Paracetamol',
      detail: { form: 'Tablet', strength: '500 mg' },
      isActive: true,
    });
  });

  it('renders missing details as dashes and defaults inactive to true', () => {
    const row = catalogRowFrom('diagnosis-codes', { id: 'x1', code: 'A00', name: 'Cholera', isActive: null });
    expect(row?.detail).toEqual({ system: '', chapter: '' });
    expect(row?.isActive).toBe(true);
    expect(detailValue(row as NonNullable<typeof row>, 'system')).toBe('—');
    expect(detailValue(row as NonNullable<typeof row>, 'chapter')).toBe('—');
  });

  it('drops entries without an id', () => {
    expect(catalogRowFrom('drugs', { ...drug, id: null })).toBeNull();
  });

  it('derives employee display name from first and last name', () => {
    const row = catalogRowFrom('employees', { id: 'e1', code: 'EMP-1', name: null, firstName: 'Asha', lastName: 'Rao', isActive: true });
    expect(row?.name).toBe('Asha Rao');
  });
});

describe('registry specs', () => {
  it('includes the four hospital registries with required fields', () => {
    const keys = CATALOGS.map((spec) => spec.key);
    expect(keys).toEqual(expect.arrayContaining(['vendors', 'equipment', 'employees', 'price-lists']));
    const employee = CATALOGS.find((spec) => spec.key === 'employees');
    expect(missingRequired(employee as NonNullable<typeof employee>, {})).toEqual(['Code', 'First name', 'Last name']);
  });
});

describe('CatalogCreateForm', () => {
  it('submits a typed payload to the catalog endpoint', async () => {
    const fixture = fixtureApi(() => ({ id: 'n1' }));
    let created = 0;
    const mounted = mount(
      <CatalogCreateForm catalog="drugs" apiFetch={fixture.apiFetch} onCreated={() => { created += 1; }} onError={() => {}} />,
    );

    await typeInto(mounted.container, 'Code', 'D009');
    await typeInto(mounted.container, 'Name', 'Ibuprofen');
    await clickButton(mounted.container, 'Create entry');
    await flush();

    expect(fixture.calls).toHaveLength(1);
    expect(fixture.calls[0].path).toBe('/master-data/drugs');
    expect(fixture.calls[0].options?.method).toBe('POST');
    expect(JSON.parse(String(fixture.calls[0].options?.body))).toEqual({ code: 'D009', name: 'Ibuprofen' });
    expect(created).toBe(1);
    mounted.unmount();
  });

  it('blocks submit when required fields are missing', async () => {
    const fixture = fixtureApi(() => ({ id: 'n1' }));
    const mounted = mount(
      <CatalogCreateForm catalog="procedure-codes" apiFetch={fixture.apiFetch} onCreated={() => {}} onError={() => {}} />,
    );

    await clickButton(mounted.container, 'Create entry');

    expect(fixture.calls).toHaveLength(0);
    expect(textIncludes(mounted.container, 'Required: Code, Name, System')).toBe(true);
    mounted.unmount();
  });

  it('reports backend failures through onError', async () => {
    const errors: string[] = [];
    const fixture = fixtureApi(() => {
      throw new Error('DUPLICATE_CODE');
    });
    const mounted = mount(
      <CatalogCreateForm catalog="drugs" apiFetch={fixture.apiFetch} onCreated={() => {}} onError={(message) => errors.push(message)} />,
    );

    await typeInto(mounted.container, 'Code', 'D001');
    await typeInto(mounted.container, 'Name', 'Paracetamol');
    await clickButton(mounted.container, 'Create entry');
    await flush();

    expect(errors).toEqual(['DUPLICATE_CODE']);
    mounted.unmount();
  });
});
