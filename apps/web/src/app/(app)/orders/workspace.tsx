'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Badge,
  Button,
  ContentPanel,
  Input,
  Modal,
  OrderEntryForm,
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
  type BadgeVariant,
} from '@squamed/ui';
import type { OrderEntryPayload, OrderEntryType } from '@squamed/ui';

export const ORDER_TYPES: OrderEntryType[] = ['lab', 'imaging', 'procedure', 'medication', 'referral'];

const CATALOG_RESOURCE: Record<OrderEntryType, string | null> = {
  lab: 'lab-tests',
  imaging: 'imaging-procedures',
  procedure: 'procedure-codes',
  medication: 'drugs',
  referral: null,
};

export interface PatientSummary {
  id: string;
  mrn: string | null;
  firstName: string | null;
  lastName: string | null;
}

export interface OrderViewDto {
  id: string | null;
  encounterId: string | null;
  patientId: string | null;
  orderType: string;
  orderCode: string | null;
  orderName: string;
  status: string;
  priority: string | null;
  instructions: string | null;
  orderedAt: string | null;
}

export interface EncounterOrdersDto {
  encounterId: string | null;
  date: string | null;
  status: string;
  type: string;
  orders: OrderViewDto[];
}

export interface PatientOrdersDto {
  encounters: EncounterOrdersDto[];
}

export interface OrderWorklistItem {
  order: OrderViewDto;
  encounterId: string | null;
  encounterDate: string | null;
  encounterStatus: string;
}

export type OrderTypeFilter = 'all' | OrderEntryType;

export function flattenOrders(view: PatientOrdersDto): OrderWorklistItem[] {
  const items: OrderWorklistItem[] = [];
  for (const encounter of view.encounters ?? []) {
    for (const order of encounter.orders ?? []) {
      items.push({
        order,
        encounterId: encounter.encounterId ?? order.encounterId ?? null,
        encounterDate: encounter.date,
        encounterStatus: encounter.status,
      });
    }
  }
  return items;
}

export function orderTypeVariant(type: string): BadgeVariant {
  switch (type) {
    case 'lab':
      return 'information';
    case 'imaging':
      return 'warning';
    case 'procedure':
      return 'warning';
    case 'medication':
      return 'success';
    case 'referral':
      return 'neutral';
    default:
      return 'neutral';
  }
}

export function orderStatusVariant(status: string): BadgeVariant {
  switch (status) {
    case 'pending':
      return 'warning';
    case 'in-progress':
      return 'information';
    case 'completed':
      return 'success';
    case 'cancelled':
      return 'critical';
    case 'on-hold':
      return 'neutral';
    default:
      return 'neutral';
  }
}

export function priorityVariant(priority: string | null): BadgeVariant {
  switch (priority) {
    case 'emergency':
    case 'stat':
      return 'critical';
    case 'urgent':
      return 'warning';
    default:
      return 'neutral';
  }
}

export function orderMatchesFilters(item: OrderWorklistItem, type: OrderTypeFilter, status: string): boolean {
  if (type !== 'all' && item.order.orderType !== type) return false;
  if (status !== 'all' && item.order.status !== status) return false;
  return true;
}

export function patientName(patient: PatientSummary): string {
  const name = [patient.firstName, patient.lastName].filter(Boolean).join(' ');
  return name || patient.mrn || 'Unnamed patient';
}

export function statusOptions(items: OrderWorklistItem[]): string[] {
  return ['all', ...Array.from(new Set(items.map((item) => item.order.status))).sort()];
}

function formatDateTime(value: string | null | undefined): string {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

/** Maps a UI order-entry payload onto the encounter order API contract. */
export function buildOrderPayload(
  payload: OrderEntryPayload,
): { orderType: string; orderCode?: string; orderName: string; priority: string; instructions?: string } {
  return {
    orderType: payload.type,
    orderCode: payload.catalogValue,
    orderName: payload.name,
    priority: payload.priority,
    instructions: payload.notes === '' ? undefined : payload.notes,
  };
}

export type ApiFetch = <T>(path: string, options?: RequestInit) => Promise<T>;

export interface PlaceOrderFormProps {
  apiFetch: ApiFetch;
  encounters: EncounterOrdersDto[];
  onPlaced: (message: string) => void;
  onCancel: () => void;
  onError: (message: string) => void;
}

export function PlaceOrderForm({ apiFetch, encounters, onPlaced, onCancel, onError }: PlaceOrderFormProps) {
  const usable = encounters.filter((encounter) => encounter.encounterId !== null);
  const [encounterId, setEncounterId] = useState<string>(usable[0]?.encounterId ?? '');
  const [type, setType] = useState<OrderEntryType>('lab');
  const [submitting, setSubmitting] = useState(false);
  const [catalogOptions, setCatalogOptions] = useState<{ label: string; value: string }[]>([]);

  useEffect(() => {
    const resource = CATALOG_RESOURCE[type];
    if (!resource || !encounterId) {
      setCatalogOptions([]);
      return;
    }
    let cancelled = false;
    void (async () => {
      try {
        const data = await apiFetch<Array<{ id: string | null; code: string | null; name: string | null }>>(
          `/master-data/${resource}?active=true&size=200`,
        );
        if (!cancelled) {
          setCatalogOptions(
            (data ?? [])
              .filter((entry) => entry.id !== null && entry.name !== null)
              .map((entry) => ({ label: `${entry.name}${entry.code ? ` (${entry.code})` : ''}`, value: entry.code ?? '' })),
          );
        }
      } catch {
        if (!cancelled) setCatalogOptions([]);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [type, apiFetch]);

  async function submitOrder(payload: OrderEntryPayload) {
    if (submitting) return;
    if (!encounterId) {
      onError('Select an encounter first.');
      return;
    }
    setSubmitting(true);
    try {
      await apiFetch(`/encounters/${encounterId}/orders`, {
        method: 'POST',
        body: JSON.stringify(buildOrderPayload(payload)),
      });
      onPlaced('Order placed.');
    } catch (e) {
      onError(e instanceof Error ? e.message : 'Failed to place order');
    } finally {
      setSubmitting(false);
    }
  }

  if (usable.length === 0) {
    return <StatusIndicator tone="information" label="This patient has no encounters that can take orders." />;
  }

  return (
    <Stack gap={12}>
      <Select label="Encounter" value={encounterId} onChange={(e) => setEncounterId(e.target.value)}>
        {usable.map((encounter) => (
          <option key={encounter.encounterId} value={encounter.encounterId ?? ''}>
            {formatDateTime(encounter.date)} — {encounter.type} ({encounter.status})
          </option>
        ))}
      </Select>
      <Select label="Order type" value={type} onChange={(e) => setType(e.target.value as OrderEntryType)}>
        {ORDER_TYPES.map((entry) => (
          <option key={entry} value={entry}>
            {entry}
          </option>
        ))}
      </Select>
      <OrderEntryForm
        type={type}
        catalogs={catalogOptions}
        onSubmit={(payload) => void submitOrder(payload)}
        onCancel={onCancel}
      />
    </Stack>
  );
}

export interface OrdersWorkspaceProps {
  apiFetch: ApiFetch;
}

export function OrdersWorkspace({ apiFetch }: OrdersWorkspaceProps) {
  const [query, setQuery] = useState('');
  const [patientResults, setPatientResults] = useState<PatientSummary[]>([]);
  const [searching, setSearching] = useState(false);
  const [selectedPatient, setSelectedPatient] = useState<PatientSummary | null>(null);
  const [items, setItems] = useState<OrderWorklistItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [typeFilter, setTypeFilter] = useState<OrderTypeFilter>('all');
  const [statusFilter, setStatusFilter] = useState('all');
  const [placeOpen, setPlaceOpen] = useState(false);

  const [encounters, setEncounters] = useState<EncounterOrdersDto[]>([]);

  const loadPatientOrders = useCallback(
    async (patient: PatientSummary) => {
      setLoading(true);
      setError(null);
      try {
        const view = await apiFetch<PatientOrdersDto>(`/patient360/${patient.id}`);
        const flat = flattenOrders(view);
        setItems(flat);
        setEncounters(view.encounters ?? []);
        setStatusFilter('all');
      } catch (e) {
        setError(e instanceof Error ? e.message : 'Failed to load patient orders');
        setItems([]);
        setEncounters([]);
      } finally {
        setLoading(false);
      }
    },
    [apiFetch],
  );

  async function searchPatients() {
    const term = query.trim();
    if (!term) return;
    setSearching(true);
    setError(null);
    try {
      const data = await apiFetch<PatientSummary[]>(`/patients?query=${encodeURIComponent(term)}`);
      setPatientResults(data ?? []);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to search patients');
      setPatientResults([]);
    } finally {
      setSearching(false);
    }
  }

  function selectPatient(patient: PatientSummary) {
    setSelectedPatient(patient);
    setPatientResults([]);
    setQuery('');
    void loadPatientOrders(patient);
  }

  const filtered = useMemo(() => items.filter((item) => orderMatchesFilters(item, typeFilter, statusFilter)), [items, typeFilter, statusFilter]);
  const statusList = useMemo(() => statusOptions(items), [items]);

  return (
    <div style={{ padding: 24 }}>
      <PageHeader
        title="Orders"
        description="Clinical orders placed during encounters: lab, imaging, procedures, medication and referrals."
        actions={
          <Button size="sm" disabled={selectedPatient === null} onClick={() => setPlaceOpen(true)}>
            Place order
          </Button>
        }
      />

      {notice ? <StatusIndicator tone="success" label={notice} /> : null}
      {error ? <StatusIndicator tone="critical" label={error} /> : null}

      <div style={{ height: 16 }} />

      <ContentPanel>
        <Stack gap={12}>
          <Stack direction="row" gap={8} align="flex-end" wrap>
            <div style={{ flex: 1, minWidth: 220 }}>
              <Input
                label="Find patient"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') void searchPatients();
                }}
              />
            </div>
            <Button variant="outline" loading={searching} onClick={() => void searchPatients()}>
              Search
            </Button>
          </Stack>

          {patientResults.length > 0 ? (
            <Stack gap={4}>
              {patientResults.map((patient) => (
                <button
                  key={patient.id}
                  type="button"
                  aria-label={`Select patient ${patientName(patient)}`}
                  onClick={() => selectPatient(patient)}
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    gap: 12,
                    width: '100%',
                    padding: '8px 10px',
                    border: '1px solid var(--sq-border)',
                    borderRadius: 8,
                    background: 'var(--sq-surface)',
                    color: 'var(--sq-fg)',
                    textAlign: 'left',
                    fontFamily: 'inherit',
                    fontSize: 14,
                    cursor: 'pointer',
                  }}
                >
                  <strong>{patientName(patient)}</strong>
                  <span style={{ fontSize: 12, color: 'var(--sq-fg-muted)' }}>MRN {patient.mrn ?? '—'}</span>
                </button>
              ))}
            </Stack>
          ) : null}

          {selectedPatient ? (
            <StatusIndicator tone="success" label={`Patient: ${patientName(selectedPatient)} · MRN ${selectedPatient.mrn ?? '—'}`} />
          ) : (
            <StatusIndicator tone="information" label="Search for a patient to see their clinical orders." />
          )}
        </Stack>
      </ContentPanel>

      <div style={{ height: 16 }} />

      {selectedPatient ? (
        <>
          <Stack direction="row" gap={8} align="flex-end" wrap>
            <div style={{ width: 180 }}>
              <Select label="Type" value={typeFilter} onChange={(e) => setTypeFilter(e.target.value as OrderTypeFilter)}>
                <option value="all">All</option>
                {ORDER_TYPES.map((entry) => (
                  <option key={entry} value={entry}>
                    {entry}
                  </option>
                ))}
              </Select>
            </div>
            <div style={{ width: 180 }}>
              <Select label="Status" value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
                {statusList.map((entry) => (
                  <option key={entry} value={entry}>
                    {entry}
                  </option>
                ))}
              </Select>
            </div>
            <Button variant="outline" onClick={() => selectedPatient && void loadPatientOrders(selectedPatient)}>
              Refresh
            </Button>
          </Stack>

          <div style={{ height: 16 }} />

          <ContentPanel>
            {loading && items.length === 0 ? (
              <StatusIndicator tone="information" label="Loading orders..." />
            ) : (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Order</TableHead>
                    <TableHead>Type</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead>Priority</TableHead>
                    <TableHead>Encounter</TableHead>
                    <TableHead>Ordered</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {filtered.length === 0 ? (
                    <TableRow>
                      <TableCell>No orders match the current filters.</TableCell>
                    </TableRow>
                  ) : (
                    filtered.map((item, index) => (
                      <TableRow key={item.order.id ?? `${item.order.orderType}-${item.order.orderName}-${index}`}>
                        <TableCell>
                          <strong>{item.order.orderName}</strong>
                          {item.order.orderCode ? (
                            <span style={{ color: 'var(--sq-fg-muted)', fontSize: 12 }}> ({item.order.orderCode})</span>
                          ) : null}
                          {item.order.instructions ? (
                            <div style={{ fontSize: 12, color: 'var(--sq-fg-muted)' }}>{item.order.instructions}</div>
                          ) : null}
                        </TableCell>
                        <TableCell>
                          <Badge variant={orderTypeVariant(item.order.orderType)}>{item.order.orderType}</Badge>
                        </TableCell>
                        <TableCell>
                          <Badge variant={orderStatusVariant(item.order.status)}>{item.order.status}</Badge>
                        </TableCell>
                        <TableCell>
                          <Badge size="sm" variant={priorityVariant(item.order.priority)}>
                            {item.order.priority ?? 'routine'}
                          </Badge>
                        </TableCell>
                        <TableCell>
                          {formatDateTime(item.encounterDate)} · {item.encounterStatus}
                        </TableCell>
                        <TableCell>{formatDateTime(item.order.orderedAt)}</TableCell>
                      </TableRow>
                    ))
                  )}
                </TableBody>
              </Table>
            )}
          </ContentPanel>
        </>
      ) : null}

      <Modal
        open={placeOpen}
        onClose={() => setPlaceOpen(false)}
        title="Place order"
        description={selectedPatient ? `${patientName(selectedPatient)} · MRN ${selectedPatient.mrn ?? '—'}` : undefined}
        size="md"
        footer={null}
      >
        <PlaceOrderForm
          apiFetch={apiFetch}
          encounters={encounters}
          onPlaced={(message) => {
            if (message !== '') setNotice(message);
            setPlaceOpen(false);
            if (selectedPatient) void loadPatientOrders(selectedPatient);
          }}
          onCancel={() => setPlaceOpen(false)}
          onError={setError}
        />
      </Modal>
    </div>
  );
}

