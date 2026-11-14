'use client';

import { useCallback, useEffect, useState } from 'react';
import {
  Badge,
  Button,
  ContentPanel,
  Input,
  Modal,
  PageHeader,
  Select,
  Stack,
  StatusIndicator,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from '@squamed/ui';

export type CatalogKey =
  | 'drugs'
  | 'lab-tests'
  | 'imaging-procedures'
  | 'procedure-codes'
  | 'diagnosis-codes'
  | 'charge-items'
  | 'vendors'
  | 'equipment'
  | 'employees'
  | 'price-lists';

export type CatalogFieldKind = 'text' | 'number';

export interface CatalogFieldSpec {
  key: string;
  label: string;
  required?: boolean;
  kind?: CatalogFieldKind;
}

export interface CatalogDetailSpec {
  key: string;
  label: string;
}

export interface CatalogSpec {
  key: CatalogKey;
  label: string;
  details: CatalogDetailSpec[];
  fields: CatalogFieldSpec[];
}

const CODE_FIELD: CatalogFieldSpec = { key: 'code', label: 'Code', required: true };
const NAME_FIELD: CatalogFieldSpec = { key: 'name', label: 'Name', required: true };

export const CATALOGS: CatalogSpec[] = [
  {
    key: 'drugs',
    label: 'Drugs',
    details: [
      { key: 'form', label: 'Form' },
      { key: 'strength', label: 'Strength' },
    ],
    fields: [CODE_FIELD, NAME_FIELD, { key: 'genericName', label: 'Generic name' }, { key: 'form', label: 'Form' }, { key: 'strength', label: 'Strength' }],
  },
  {
    key: 'lab-tests',
    label: 'Lab tests',
    details: [
      { key: 'category', label: 'Category' },
      { key: 'specimenType', label: 'Specimen' },
    ],
    fields: [CODE_FIELD, NAME_FIELD],
  },
  {
    key: 'imaging-procedures',
    label: 'Imaging',
    details: [
      { key: 'modality', label: 'Modality' },
      { key: 'bodyPart', label: 'Body part' },
    ],
    fields: [CODE_FIELD, NAME_FIELD],
  },
  {
    key: 'procedure-codes',
    label: 'Procedure codes',
    details: [
      { key: 'system', label: 'System' },
      { key: 'category', label: 'Category' },
    ],
    fields: [CODE_FIELD, NAME_FIELD, { key: 'system', label: 'System', required: true }],
  },
  {
    key: 'diagnosis-codes',
    label: 'Diagnosis codes',
    details: [
      { key: 'system', label: 'System' },
      { key: 'chapter', label: 'Chapter' },
    ],
    fields: [CODE_FIELD, NAME_FIELD],
  },
  {
    key: 'charge-items',
    label: 'Charge items',
    details: [
      { key: 'category', label: 'Category' },
      { key: 'priceQar', label: 'Price (QAR)' },
    ],
    fields: [CODE_FIELD, NAME_FIELD, { key: 'priceQar', label: 'Price (QAR)', kind: 'number' }],
  },
  {
    key: 'vendors',
    label: 'Vendors',
    details: [
      { key: 'contactPerson', label: 'Contact' },
      { key: 'phone', label: 'Phone' },
    ],
    fields: [CODE_FIELD, NAME_FIELD, { key: 'contactPerson', label: 'Contact person' }, { key: 'phone', label: 'Phone' }, { key: 'taxId', label: 'Tax ID' }],
  },
  {
    key: 'equipment',
    label: 'Equipment',
    details: [
      { key: 'category', label: 'Category' },
      { key: 'serialNumber', label: 'Serial no.' },
    ],
    fields: [CODE_FIELD, NAME_FIELD, { key: 'category', label: 'Category' }, { key: 'serialNumber', label: 'Serial number' }, { key: 'location', label: 'Location' }],
  },
  {
    key: 'employees',
    label: 'Employees',
    details: [
      { key: 'designation', label: 'Designation' },
      { key: 'licenseNumber', label: 'License no.' },
    ],
    fields: [
      CODE_FIELD,
      { key: 'firstName', label: 'First name', required: true },
      { key: 'lastName', label: 'Last name', required: true },
      { key: 'designation', label: 'Designation' },
      { key: 'qualification', label: 'Qualification' },
      { key: 'licenseNumber', label: 'License number' },
    ],
  },
  {
    key: 'price-lists',
    label: 'Price lists',
    details: [
      { key: 'currency', label: 'Currency' },
      { key: 'validFrom', label: 'Valid from' },
    ],
    fields: [CODE_FIELD, NAME_FIELD, { key: 'currency', label: 'Currency' }],
  },
];

export const PAGE_SIZE = 20;

export type ActiveFilter = 'all' | 'active' | 'inactive';

/** One wide shape covering the master-data catalog and registry DTOs. */
export interface CatalogDto {
  id: string | null;
  code: string | null;
  name: string | null;
  genericName?: string | null;
  form?: string | null;
  strength?: string | null;
  category?: string | null;
  specimenType?: string | null;
  modality?: string | null;
  bodyPart?: string | null;
  system?: string | null;
  chapter?: string | null;
  priceQar?: number | null;
  contactPerson?: string | null;
  phone?: string | null;
  taxId?: string | null;
  serialNumber?: string | null;
  location?: string | null;
  firstName?: string | null;
  lastName?: string | null;
  designation?: string | null;
  qualification?: string | null;
  licenseNumber?: string | null;
  currency?: string | null;
  validFrom?: string | null;
  isActive: boolean | null;
}

export interface CatalogRow {
  id: string;
  code: string;
  name: string;
  detail: Record<string, string>;
  isActive: boolean;
}

export function buildCatalogQuery(search: string, active: ActiveFilter, page: number): string {
  const params = new URLSearchParams();
  const term = search.trim();
  if (term !== '') params.set('search', term);
  if (active !== 'all') params.set('active', active === 'active' ? 'true' : 'false');
  params.set('page', String(Math.max(0, page)));
  params.set('size', String(PAGE_SIZE));
  const query = params.toString();
  return query === '' ? '' : `?${query}`;
}

export function catalogRowFrom(catalog: CatalogKey, dto: CatalogDto): CatalogRow | null {
  if (!dto.id) return null;
  const spec = CATALOGS.find((entry) => entry.key === catalog);
  const detail: Record<string, string> = {};
  if (spec) {
    for (const entry of spec.details) {
      const value = dto[entry.key as keyof CatalogDto];
      detail[entry.key] = value === null || value === undefined || value === '' ? '' : String(value);
    }
  }
  return {
    id: dto.id,
    code: dto.code ?? '',
    name: dto.name ?? [dto.firstName, dto.lastName].filter((v): v is string => !!v).join(' '),
    detail,
    isActive: dto.isActive ?? true,
  };
}

export function buildCreatePayload(catalog: CatalogKey, values: Record<string, string>): Record<string, unknown> {
  const spec = CATALOGS.find((entry) => entry.key === catalog);
  const payload: Record<string, unknown> = {};
  if (!spec) return payload;
  for (const field of spec.fields) {
    const raw = values[field.key]?.trim() ?? '';
    if (raw === '') continue;
    payload[field.key] = field.kind === 'number' ? Number(raw) : raw;
  }
  return payload;
}

export function missingRequired(spec: CatalogSpec, values: Record<string, string>): string[] {
  return spec.fields.filter((field) => field.required && (values[field.key]?.trim() ?? '') === '').map((field) => field.label);
}

export function detailValue(row: CatalogRow, key: string): string {
  const value = row.detail[key] ?? '';
  return value === '' ? '—' : value;
}

export type ApiFetch = <T>(path: string, options?: RequestInit) => Promise<T>;

export interface CatalogCreateFormProps {
  catalog: CatalogKey;
  apiFetch: ApiFetch;
  onCreated: () => void;
  onError: (message: string) => void;
}

export function CatalogCreateForm({ catalog, apiFetch, onCreated, onError }: CatalogCreateFormProps) {
  const spec = CATALOGS.find((entry) => entry.key === catalog);
  if (!spec) {
    onError('Unknown catalog.');
    return null;
  }
  return <CatalogCreateFields spec={spec} apiFetch={apiFetch} onCreated={onCreated} onError={onError} />;
}

function CatalogCreateFields({
  spec,
  apiFetch,
  onCreated,
  onError,
}: {
  spec: CatalogSpec;
  apiFetch: ApiFetch;
  onCreated: () => void;
  onError: (message: string) => void;
}) {
  const [values, setValues] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);
  const [validationError, setValidationError] = useState<string | null>(null);

  async function submit() {
    const missing = missingRequired(spec, values);
    if (missing.length > 0) {
      setValidationError(`Required: ${missing.join(', ')}`);
      return;
    }
    setSubmitting(true);
    setValidationError(null);
    try {
      await apiFetch(`/master-data/${spec.key}`, {
        method: 'POST',
        body: JSON.stringify(buildCreatePayload(spec.key, values)),
      });
      onCreated();
    } catch (e) {
      onError(e instanceof Error ? e.message : 'Failed to create entry');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Stack gap={12}>
      {spec.fields.map((field) => (
        <Input
          key={field.key}
          label={field.label}
          required={field.required}
          value={values[field.key] ?? ''}
          onChange={(e) => setValues({ ...values, [field.key]: e.target.value })}
        />
      ))}
      {validationError ? <StatusIndicator tone="critical" label={validationError} /> : null}
      <Button loading={submitting} onClick={() => void submit()}>
        Create entry
      </Button>
    </Stack>
  );
}

export interface MasterDataWorkspaceProps {
  apiFetch: ApiFetch;
}

export function MasterDataWorkspace({ apiFetch }: MasterDataWorkspaceProps) {
  const [catalog, setCatalog] = useState<CatalogKey>('drugs');
  const [search, setSearch] = useState('');
  const [appliedSearch, setAppliedSearch] = useState('');
  const [active, setActive] = useState<ActiveFilter>('all');
  const [page, setPage] = useState(0);
  const [rows, setRows] = useState<CatalogRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const [createOpen, setCreateOpen] = useState(false);

  const spec = CATALOGS.find((entry) => entry.key === catalog) ?? CATALOGS[0];

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const query = buildCatalogQuery(appliedSearch, active, page);
      const data = await apiFetch<CatalogDto[]>(`/master-data/${catalog}${query}`);
      setRows((data ?? []).map((dto) => catalogRowFrom(catalog, dto)).filter((row): row is CatalogRow => row !== null));
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load catalog');
      setRows([]);
    } finally {
      setLoading(false);
    }
  }, [apiFetch, catalog, appliedSearch, active, page]);

  useEffect(() => {
    void load();
  }, [load]);

  async function toggleActive(row: CatalogRow) {
    setError(null);
    setNotice(null);
    try {
      await apiFetch(`/master-data/${catalog}/${row.id}/active`, {
        method: 'PATCH',
        body: JSON.stringify({ isActive: !row.isActive }),
      });
      setNotice(`${row.name} ${row.isActive ? 'deactivated' : 'activated'}.`);
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to update entry');
    }
  }

  function switchCatalog(key: string) {
    setCatalog(key as CatalogKey);
    setPage(0);
    setSearch('');
    setAppliedSearch('');
    setNotice(null);
  }

  const hasMore = rows.length === PAGE_SIZE;

  return (
    <div style={{ padding: 24 }}>
      <PageHeader
        title="Master data"
        description="Clinical catalogs used across orders, prescriptions and billing."
        actions={<Button size="sm" onClick={() => setCreateOpen(true)}>New entry</Button>}
      />

      {notice ? <StatusIndicator tone="success" label={notice} /> : null}
      {error ? <StatusIndicator tone="critical" label={error} /> : null}

      <div style={{ height: 16 }} />

      <Tabs value={catalog} onValueChange={switchCatalog}>
        <TabsList>
          {CATALOGS.map((entry) => (
            <TabsTrigger key={entry.key} value={entry.key}>
              {entry.label}
            </TabsTrigger>
          ))}
        </TabsList>
        <TabsContent value={catalog}>
          <Stack gap={16}>
            <Stack direction="row" gap={8} align="flex-end" wrap>
              <div style={{ width: 240 }}>
                <Input
                  label={`Search ${spec.label.toLowerCase()}`}
                  aria-label={`Search ${spec.label.toLowerCase()}`}
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') {
                      setPage(0);
                      setAppliedSearch(search);
                    }
                  }}
                />
              </div>
              <Button variant="outline" onClick={() => { setPage(0); setAppliedSearch(search); }}>
                Search
              </Button>
              <div style={{ width: 160 }}>
                <Select label="Status" value={active} onChange={(e) => { setPage(0); setActive(e.target.value as ActiveFilter); }}>
                  <option value="all">All</option>
                  <option value="active">Active</option>
                  <option value="inactive">Inactive</option>
                </Select>
              </div>
              <Button variant="outline" onClick={() => void load()}>
                Refresh
              </Button>
            </Stack>

            <ContentPanel>
              {loading && rows.length === 0 ? (
                <StatusIndicator tone="information" label={`Loading ${spec.label.toLowerCase()}...`} />
              ) : (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Code</TableHead>
                      <TableHead>Name</TableHead>
                      {spec.details.map((entry) => (
                        <TableHead key={entry.key}>{entry.label}</TableHead>
                      ))}
                      <TableHead>Status</TableHead>
                      <TableHead>Actions</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {rows.length === 0 ? (
                      <TableRow>
                        <TableCell>No {spec.label.toLowerCase()} found.</TableCell>
                      </TableRow>
                    ) : (
                      rows.map((row) => (
                        <TableRow key={row.id}>
                          <TableCell>
                            <strong>{row.code}</strong>
                          </TableCell>
                          <TableCell>{row.name}</TableCell>
                          {spec.details.map((entry) => (
                            <TableCell key={entry.key}>{detailValue(row, entry.key)}</TableCell>
                          ))}
                          <TableCell>
                            <Badge variant={row.isActive ? 'success' : 'neutral'}>{row.isActive ? 'Active' : 'Inactive'}</Badge>
                          </TableCell>
                          <TableCell>
                            <Button size="sm" variant="outline" onClick={() => void toggleActive(row)}>
                              {row.isActive ? 'Deactivate' : 'Activate'}
                            </Button>
                          </TableCell>
                        </TableRow>
                      ))
                    )}
                  </TableBody>
                </Table>
              )}
            </ContentPanel>

            <Stack direction="row" gap={8}>
              <Button size="sm" variant="outline" disabled={page === 0 || loading} onClick={() => setPage(page - 1)}>
                Previous page
              </Button>
              <Button size="sm" variant="outline" disabled={!hasMore || loading} onClick={() => setPage(page + 1)}>
                Next page
              </Button>
            </Stack>
          </Stack>
        </TabsContent>
      </Tabs>

      <Modal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        title={`New ${spec.label.toLowerCase().replace(/s$/, '')} entry`}
        description={`Add an entry to the ${spec.label.toLowerCase()} catalog.`}
        size="md"
        footer={null}
      >
        <CatalogCreateForm
          catalog={catalog}
          apiFetch={apiFetch}
          onCreated={() => {
            setCreateOpen(false);
            setNotice('Entry created.');
            void load();
          }}
          onError={setError}
        />
      </Modal>
    </div>
  );
}

