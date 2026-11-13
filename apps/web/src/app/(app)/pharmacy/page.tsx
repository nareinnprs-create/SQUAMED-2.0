'use client';

import { useCallback, useEffect, useState } from 'react';
import {
  Badge,
  Button,
  ContentPanel,
  Input,
  Modal,
  PageHeader,
  Stack,
  StatusIndicator,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@squamed/ui';
import type { BadgeVariant, StatusTone } from '@squamed/ui';
import { useApi } from '../../../lib/use-api';

const TABS = ['Stock', 'Dispensing'];

const dispenseStatusVariants: Record<string, BadgeVariant> = {
  dispensed: 'information',
  verified: 'success',
  returned: 'neutral',
  cancelled: 'critical',
};

interface PatientSummary {
  id: string;
  mrn: string | null;
  firstName: string | null;
  lastName: string | null;
}

interface PharmacyStockView {
  id: string;
  drugName: string;
  drugCode: string | null;
  batchNumber: string | null;
  quantityOnHand: number;
  reorderLevel: number | null;
  unitPrice: number | null;
  location: string | null;
  isActive: boolean;
}

interface Dispensation {
  id: string;
  drugName: string;
  quantity: number;
  unitPrice: number | null;
  totalPrice: number;
  status: string;
  dispensedAt: string | null;
  verifiedAt: string | null;
}

interface Notice {
  tone: StatusTone;
  label: string;
}

function fullName(patient: PatientSummary): string {
  return [patient.firstName, patient.lastName].filter(Boolean).join(' ') || 'Unnamed patient';
}

export default function PharmacyPage() {
  const { apiFetch, isReady } = useApi();
  const [tab, setTab] = useState('Stock');

  const [stock, setStock] = useState<PharmacyStockView[]>([]);
  const [search, setSearch] = useState('');
  const [addOpen, setAddOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const [dispensations, setDispensations] = useState<Dispensation[]>([]);
  const [patientQuery, setPatientQuery] = useState('');
  const [patientResults, setPatientResults] = useState<PatientSummary[]>([]);
  const [searchingPatient, setSearchingPatient] = useState(false);
  const [selectedPatient, setSelectedPatient] = useState<PatientSummary | null>(null);
  const [drugQuery, setDrugQuery] = useState('');
  const [drugResults, setDrugResults] = useState<PharmacyStockView[]>([]);
  const [searchingDrug, setSearchingDrug] = useState(false);
  const [selectedStock, setSelectedStock] = useState<PharmacyStockView | null>(null);
  const [dispenseQty, setDispenseQty] = useState('');
  const [dispenseUnitPrice, setDispenseUnitPrice] = useState('');
  const [notes, setNotes] = useState('');
  const [dispensingResult, setDispensingResult] = useState<{ total: number } | null>(null);
  const [dispensing, setDispensing] = useState(false);
  const [working, setWorking] = useState<string | null>(null);

  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<Notice | null>(null);

  const [newStock, setNewStock] = useState({
    drugName: '',
    drugCode: '',
    batchNumber: '',
    expiryDate: '',
    quantityOnHand: '',
    reorderLevel: '',
    unitPrice: '',
    location: '',
  });

  const loadStock = useCallback(async () => {
    if (!isReady) return;
    try {
      const query = search.trim() ? `?search=${encodeURIComponent(search)}` : '';
      const data = await apiFetch<PharmacyStockView[]>(`/pharmacy/stock${query}`);
      setStock(data ?? []);
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load stock');
    }
  }, [apiFetch, isReady, search]);

  const loadDispensations = useCallback(async () => {
    if (!isReady || !selectedPatient) return;
    try {
      const data = await apiFetch<Dispensation[]>(
        `/pharmacy/dispensations?patientId=${encodeURIComponent(selectedPatient.id)}`,
      );
      setDispensations(data ?? []);
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load dispensations');
    }
  }, [apiFetch, isReady, selectedPatient]);

  useEffect(() => {
    if (tab === 'Stock') void loadStock();
  }, [tab, loadStock]);

  useEffect(() => {
    if (tab === 'Dispensing') void loadDispensations();
  }, [tab, loadDispensations]);

  async function searchPatients() {
    const term = patientQuery.trim();
    if (!term || !isReady) return;
    setSearchingPatient(true);
    setError(null);
    try {
      const data = await apiFetch<PatientSummary[]>(`/patients?query=${encodeURIComponent(term)}`);
      setPatientResults(data ?? []);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to search patients');
      setPatientResults([]);
    } finally {
      setSearchingPatient(false);
    }
  }

  async function searchDrugs() {
    const term = drugQuery.trim();
    if (!term || !isReady) return;
    setSearchingDrug(true);
    setError(null);
    try {
      const data = await apiFetch<PharmacyStockView[]>(`/pharmacy/stock?search=${encodeURIComponent(term)}`);
      setDrugResults(data ?? []);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to search drugs');
      setDrugResults([]);
    } finally {
      setSearchingDrug(false);
    }
  }

  function pickDrug(drug: PharmacyStockView) {
    setSelectedStock(drug);
    setDrugResults([]);
    setDrugQuery('');
    setDispenseUnitPrice(drug.unitPrice != null ? String(drug.unitPrice) : '');
  }

  async function createStock() {
    setSubmitting(true);
    setError(null);
    setNotice(null);
    try {
      await apiFetch('/pharmacy/stock', {
        method: 'POST',
        body: JSON.stringify({
          drugName: newStock.drugName,
          drugCode: newStock.drugCode.trim() || undefined,
          batchNumber: newStock.batchNumber.trim() || undefined,
          expiryDate: newStock.expiryDate || undefined,
          quantityOnHand: Number(newStock.quantityOnHand),
          reorderLevel: newStock.reorderLevel.trim() !== '' ? Number(newStock.reorderLevel) : undefined,
          unitPrice: newStock.unitPrice.trim() !== '' ? Number(newStock.unitPrice) : undefined,
          location: newStock.location.trim() || undefined,
        }),
      });
      setNotice({ tone: 'success', label: 'Stock added' });
      setAddOpen(false);
      setNewStock({
        drugName: '',
        drugCode: '',
        batchNumber: '',
        expiryDate: '',
        quantityOnHand: '',
        reorderLevel: '',
        unitPrice: '',
        location: '',
      });
      await loadStock();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to add stock');
    } finally {
      setSubmitting(false);
    }
  }

  async function dispense() {
    if (!selectedPatient || !selectedStock) {
      setError('Select a patient and drug first.');
      return;
    }
    const qty = Number(dispenseQty);
    if (!qty || qty <= 0) {
      setError('Enter a valid quantity.');
      return;
    }
    setDispensing(true);
    setError(null);
    setNotice(null);
    setDispensingResult(null);
    try {
      const data = await apiFetch<{ id: string; drugName: string; quantity: number; totalPrice: number; status: string }>(
        '/pharmacy/dispensations',
        {
          method: 'POST',
          body: JSON.stringify({
            patientId: selectedPatient.id,
            stockId: selectedStock.id,
            drugName: selectedStock.drugName,
            quantity: qty,
            unitPrice: dispenseUnitPrice.trim() !== '' ? Number(dispenseUnitPrice) : undefined,
            notes: notes.trim() || undefined,
          }),
        },
      );
      setDispensingResult({ total: data.totalPrice });
      setDispenseQty('');
      setNotes('');
      setSelectedStock(null);
      setDrugQuery('');
      setDrugResults([]);
      await loadDispensations();
      await loadStock();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to dispense');
    } finally {
      setDispensing(false);
    }
  }

  async function runDispenseAction(id: string, action: 'verify' | 'return') {
    setWorking(`${action}:${id}`);
    setError(null);
    try {
      await apiFetch(`/pharmacy/dispensations/${id}/${action}`, {
        method: 'POST',
        body: '{}',
      });
      await loadDispensations();
    } catch (e) {
      setError(e instanceof Error ? e.message : `Failed to ${action} dispensation`);
    } finally {
      setWorking(null);
    }
  }

  return (
    <div style={{ padding: 24 }}>
      <PageHeader
        title="Pharmacy"
        description="Manage drug stock and outpatient dispensing."
        actions={<Button size="sm" onClick={() => setAddOpen(true)}>Add stock</Button>}
      />

      {notice ? <StatusIndicator tone={notice.tone} label={notice.label} /> : null}
      {error ? <StatusIndicator tone="critical" label={error} /> : null}

      <div style={{ height: 16 }} />

      <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
        {TABS.map((name) => (
          <button
            key={name}
            type="button"
            onClick={() => setTab(name)}
            style={{
              padding: '7px 14px',
              border: '1px solid var(--sq-border)',
              borderRadius: 8,
              background: tab === name ? 'var(--sq-primary-soft)' : 'var(--sq-surface)',
              color: tab === name ? 'var(--sq-primary)' : 'var(--sq-fg)',
              fontWeight: 600,
              fontSize: 13,
              fontFamily: 'inherit',
              cursor: 'pointer',
            }}
          >
            {name}
          </button>
        ))}
      </div>

      <div style={{ height: 16 }} />

      {tab === 'Stock' ? (
        <Stack gap={12}>
          <Stack direction="row" gap={8} align="flex-end" wrap>
            <div style={{ width: 320 }}>
              <Input
                label="Search stock"
                placeholder="Drug name, code or batch..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') void loadStock();
                }}
              />
            </div>
            <Button variant="outline" onClick={() => void loadStock()}>Search</Button>
          </Stack>

          <ContentPanel>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Drug</TableHead>
                  <TableHead>Code</TableHead>
                  <TableHead>Batch</TableHead>
                  <TableHead>Qty on hand</TableHead>
                  <TableHead>Reorder level</TableHead>
                  <TableHead>Unit price</TableHead>
                  <TableHead>Location</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {stock.length === 0 ? (
                  <TableRow>
                    <TableCell>No stock items.</TableCell>
                  </TableRow>
                ) : (
                  stock.map((item) => (
                    <TableRow key={item.id}>
                      <TableCell>
                        <strong>{item.drugName}</strong>
                      </TableCell>
                      <TableCell>{item.drugCode ?? '—'}</TableCell>
                      <TableCell>{item.batchNumber ?? '—'}</TableCell>
                      <TableCell>
                        <strong>
                          {item.quantityOnHand}
                        </strong>{' '}
                        {item.reorderLevel !== null && item.quantityOnHand <= item.reorderLevel ? (
                          <Badge variant="critical" size="sm">Low</Badge>
                        ) : null}
                      </TableCell>
                      <TableCell>{item.reorderLevel ?? '—'}</TableCell>
                      <TableCell>{item.unitPrice != null ? `QAR ${item.unitPrice}` : '—'}</TableCell>
                      <TableCell>{item.location ?? '—'}</TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </ContentPanel>
        </Stack>
      ) : null}

      {tab === 'Dispensing' ? (
        <Stack gap={12}>
          <ContentPanel>
            <Stack gap={12}>
              <Stack direction="row" gap={8} align="flex-end" wrap>
                <div style={{ flex: 1, minWidth: 220 }}>
                  <Input
                    label="Find patient"
                    placeholder="Name, MRN, phone..."
                    value={patientQuery}
                    onChange={(e) => setPatientQuery(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') void searchPatients();
                    }}
                  />
                </div>
                <Button variant="outline" loading={searchingPatient} onClick={() => void searchPatients()}>
                  Search
                </Button>
              </Stack>

              {patientResults.length > 0 ? (
                <Stack gap={4}>
                  {patientResults.map((patient) => (
                    <button
                      key={patient.id}
                      type="button"
                      onClick={() => {
                        setSelectedPatient(patient);
                        setPatientResults([]);
                        setPatientQuery('');
                      }}
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
                      <strong>{fullName(patient)}</strong>
                      <span style={{ fontSize: 12, color: 'var(--sq-fg-muted)' }}>MRN {patient.mrn ?? '—'}</span>
                    </button>
                  ))}
                </Stack>
              ) : null}

              {selectedPatient ? (
                <StatusIndicator tone="success" label={`Patient: ${fullName(selectedPatient)}`} />
              ) : null}

              <Stack direction="row" gap={8} align="flex-end" wrap>
                <div style={{ flex: 1, minWidth: 220 }}>
                  <Input
                    label="Drug lookup"
                    placeholder="Search stock by name or code..."
                    value={drugQuery}
                    onChange={(e) => setDrugQuery(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') void searchDrugs();
                    }}
                  />
                </div>
                <Button variant="outline" loading={searchingDrug} onClick={() => void searchDrugs()}>
                  Search
                </Button>
              </Stack>

              {drugResults.length > 0 ? (
                <Stack gap={4}>
                  {drugResults.map((drug) => (
                    <button
                      key={drug.id}
                      type="button"
                      onClick={() => pickDrug(drug)}
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
                      <span>
                        <strong>{drug.drugName}</strong>
                        <span style={{ marginLeft: 8, fontSize: 12, color: 'var(--sq-fg-muted)' }}>
                          Qty {drug.quantityOnHand}
                        </span>
                      </span>
                      <span style={{ fontSize: 12, color: 'var(--sq-fg-muted)' }}>
                        {drug.unitPrice != null ? `QAR ${drug.unitPrice}` : ''} · {drug.batchNumber ?? 'no batch'}
                      </span>
                    </button>
                  ))}
                </Stack>
              ) : null}

              {selectedStock ? (
                <StatusIndicator tone="success" label={`Drug: ${selectedStock.drugName} (on hand ${selectedStock.quantityOnHand})`} />
              ) : null}

              <Stack direction="row" gap={8} wrap>
                <div style={{ width: 140 }}>
                  <Input
                    label="Quantity"
                    type="number"
                    value={dispenseQty}
                    onChange={(e) => setDispenseQty(e.target.value)}
                  />
                </div>
                <div style={{ width: 150 }}>
                  <Input
                    label="Unit price (QAR)"
                    type="number"
                    value={dispenseUnitPrice}
                    onChange={(e) => setDispenseUnitPrice(e.target.value)}
                  />
                </div>
                <div style={{ flex: 1, minWidth: 200 }}>
                  <Input
                    label="Notes"
                    value={notes}
                    onChange={(e) => setNotes(e.target.value)}
                  />
                </div>
              </Stack>

              <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
                <Button loading={dispensing} onClick={() => void dispense()}>
                  Dispense
                </Button>
              </div>

              {dispensingResult ? (
                <StatusIndicator
                  tone="success"
                  label={`Dispensed — token total QAR ${dispensingResult.total}`}
                />
              ) : null}
            </Stack>
          </ContentPanel>

          <ContentPanel>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Drug</TableHead>
                  <TableHead>Qty</TableHead>
                  <TableHead>Unit price</TableHead>
                  <TableHead>Total</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Dispensed</TableHead>
                  <TableHead>Verified</TableHead>
                  <TableHead>Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {dispensations.length === 0 ? (
                  <TableRow>
                    <TableCell>No dispensations for this patient.</TableCell>
                  </TableRow>
                ) : (
                  dispensations.map((d) => (
                    <TableRow key={d.id}>
                      <TableCell>
                        <strong>{d.drugName}</strong>
                      </TableCell>
                      <TableCell>{d.quantity}</TableCell>
                      <TableCell>{d.unitPrice != null ? `QAR ${d.unitPrice}` : '—'}</TableCell>
                      <TableCell>QAR {d.totalPrice}</TableCell>
                      <TableCell>
                        <Badge variant={dispenseStatusVariants[d.status] ?? 'neutral'}>{d.status}</Badge>
                      </TableCell>
                      <TableCell>{d.dispensedAt ? new Date(d.dispensedAt).toLocaleString() : '—'}</TableCell>
                      <TableCell>{d.verifiedAt ? new Date(d.verifiedAt).toLocaleString() : '—'}</TableCell>
                      <TableCell>
                        <Stack direction="row" gap={6}>
                          {d.status === 'dispensed' ? (
                            <Button
                              size="sm"
                              loading={working === `verify:${d.id}`}
                              onClick={() => void runDispenseAction(d.id, 'verify')}
                            >
                              Verify
                            </Button>
                          ) : null}
                          {d.status === 'dispensed' || d.status === 'verified' ? (
                            <Button
                              size="sm"
                              variant="outline"
                              loading={working === `return:${d.id}`}
                              onClick={() => void runDispenseAction(d.id, 'return')}
                            >
                              Return
                            </Button>
                          ) : null}
                        </Stack>
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </ContentPanel>
        </Stack>
      ) : null}

      <Modal
        open={addOpen}
        onClose={() => setAddOpen(false)}
        title="Add stock"
        description="Register a new stock item."
        footer={
          <>
            <Button variant="outline" onClick={() => setAddOpen(false)}>Cancel</Button>
            <Button loading={submitting} onClick={() => void createStock()}>Add</Button>
          </>
        }
      >
        <Stack gap={12}>
          <Input
            label="Drug name"
            value={newStock.drugName}
            onChange={(e) => setNewStock({ ...newStock, drugName: e.target.value })}
          />
          <Stack direction="row" gap={8}>
            <div style={{ flex: 1 }}>
              <Input
                label="Drug code"
                value={newStock.drugCode}
                onChange={(e) => setNewStock({ ...newStock, drugCode: e.target.value })}
              />
            </div>
            <div style={{ flex: 1 }}>
              <Input
                label="Batch number"
                value={newStock.batchNumber}
                onChange={(e) => setNewStock({ ...newStock, batchNumber: e.target.value })}
              />
            </div>
          </Stack>
          <Stack direction="row" gap={8}>
            <div style={{ flex: 1 }}>
              <Input
                label="Expiry date"
                type="date"
                value={newStock.expiryDate}
                onChange={(e) => setNewStock({ ...newStock, expiryDate: e.target.value })}
              />
            </div>
            <div style={{ flex: 1 }}>
              <Input
                label="Quantity on hand"
                type="number"
                value={newStock.quantityOnHand}
                onChange={(e) => setNewStock({ ...newStock, quantityOnHand: e.target.value })}
              />
            </div>
          </Stack>
          <Stack direction="row" gap={8}>
            <div style={{ flex: 1 }}>
              <Input
                label="Reorder level"
                type="number"
                value={newStock.reorderLevel}
                onChange={(e) => setNewStock({ ...newStock, reorderLevel: e.target.value })}
              />
            </div>
            <div style={{ flex: 1 }}>
              <Input
                label="Unit price (QAR)"
                type="number"
                value={newStock.unitPrice}
                onChange={(e) => setNewStock({ ...newStock, unitPrice: e.target.value })}
              />
            </div>
          </Stack>
          <Input
            label="Location"
            value={newStock.location}
            onChange={(e) => setNewStock({ ...newStock, location: e.target.value })}
          />
        </Stack>
      </Modal>
    </div>
  );
}
