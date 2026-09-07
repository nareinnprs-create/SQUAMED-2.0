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
} from '@squamed/ui';
import type { BadgeVariant, StatusTone } from '@squamed/ui';
import { useApi } from '../../../lib/use-api';

const TABS = ['Charges', 'Invoices', 'Tariffs'];

const invoiceStatusVariants: Record<string, BadgeVariant> = {
  draft: 'neutral',
  issued: 'information',
  partially_paid: 'warning',
  paid: 'success',
  cancelled: 'critical',
};

interface PatientSummary {
  id: string;
  mrn: string | null;
  firstName: string | null;
  lastName: string | null;
}

interface Charge {
  id: string;
  chargeCode: string | null;
  chargeName: string;
  category: string | null;
  quantity: number;
  unitPriceQar: number;
  totalQar: number;
  status: string;
  postedAt: string | null;
}

interface Tariff {
  id: string;
  code: string;
  name: string;
  category: string | null;
  priceQar: number;
}

interface InvoiceView {
  id: string;
  invoiceNumber: string;
  subtotalQar: number;
  totalQar: number;
  amountPaidQar: number;
  amountDueQar: number;
  status: string;
  issuedAt: string | null;
}

interface InvoiceLine {
  id: string;
  chargeName: string;
  quantity: number;
  unitPriceQar: number;
  totalQar: number;
}

interface PaymentView {
  id: string;
  method: string;
  amountQar: number;
  reference: string | null;
}

interface InvoiceDetail {
  invoice: InvoiceView;
  lines: InvoiceLine[];
  payments: PaymentView[];
}

interface Notice {
  tone: StatusTone;
  label: string;
}

function fullName(patient: PatientSummary): string {
  return [patient.firstName, patient.lastName].filter(Boolean).join(' ') || 'Unnamed patient';
}

export default function BillingPage() {
  const { apiFetch, isReady } = useApi();
  const [tab, setTab] = useState('Charges');

  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<Notice | null>(null);

  const [chargePatient, setChargePatient] = useState<PatientSummary | null>(null);
  const [charges, setCharges] = useState<Charge[]>([]);
  const [chargeQuery, setChargeQuery] = useState('');
  const [chargePatientResults, setChargePatientResults] = useState<PatientSummary[]>([]);
  const [searchingChargePatient, setSearchingChargePatient] = useState(false);
  const [chargeOpen, setChargeOpen] = useState(false);
  const [submittingCharge, setSubmittingCharge] = useState(false);
  const [chargeForm, setChargeForm] = useState({ chargeCode: '', chargeName: '', quantity: '', unitPriceQar: '' });

  const [invoicePatient, setInvoicePatient] = useState<PatientSummary | null>(null);
  const [invoices, setInvoices] = useState<InvoiceView[]>([]);
  const [invoiceQuery, setInvoiceQuery] = useState('');
  const [invoicePatientResults, setInvoicePatientResults] = useState<PatientSummary[]>([]);
  const [searchingInvoicePatient, setSearchingInvoicePatient] = useState(false);
  const [invoiceOpen, setInvoiceOpen] = useState(false);
  const [submittingInvoice, setSubmittingInvoice] = useState(false);
  const [discountQar, setDiscountQar] = useState('');

  const [detailOpen, setDetailOpen] = useState(false);
  const [detail, setDetail] = useState<InvoiceDetail | null>(null);
  const [paymentMethod, setPaymentMethod] = useState('card');
  const [paymentAmount, setPaymentAmount] = useState('');
  const [recordingPayment, setRecordingPayment] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [cancelReason, setCancelReason] = useState('');

  const [tariffs, setTariffs] = useState<Tariff[]>([]);
  const [tariffSearch, setTariffSearch] = useState('');
  const [tariffOpen, setTariffOpen] = useState(false);
  const [submittingTariff, setSubmittingTariff] = useState(false);
  const [tariffForm, setTariffForm] = useState({ code: '', name: '', category: '', priceQar: '' });

  const loadCharges = useCallback(async () => {
    if (!isReady || !chargePatient) return;
    try {
      const data = await apiFetch<Charge[]>(`/billing/charges?patientId=${encodeURIComponent(chargePatient.id)}`);
      setCharges(data ?? []);
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load charges');
    }
  }, [apiFetch, isReady, chargePatient]);

  const loadInvoices = useCallback(async () => {
    if (!isReady || !invoicePatient) return;
    try {
      const data = await apiFetch<InvoiceView[]>(`/billing/invoices?patientId=${encodeURIComponent(invoicePatient.id)}`);
      setInvoices(data ?? []);
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load invoices');
    }
  }, [apiFetch, isReady, invoicePatient]);

  const loadTariffs = useCallback(async () => {
    if (!isReady) return;
    try {
      const query = tariffSearch.trim() ? `?search=${encodeURIComponent(tariffSearch)}` : '';
      const data = await apiFetch<Tariff[]>(`/billing/tariffs${query}`);
      setTariffs(data ?? []);
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load tariffs');
    }
  }, [apiFetch, isReady, tariffSearch]);

  useEffect(() => {
    if (tab === 'Charges') void loadCharges();
  }, [tab, loadCharges]);

  useEffect(() => {
    if (tab === 'Invoices') void loadInvoices();
  }, [tab, loadInvoices]);

  useEffect(() => {
    if (tab === 'Tariffs') void loadTariffs();
  }, [tab, loadTariffs]);

  async function searchChargePatients() {
    const term = chargeQuery.trim();
    if (!term || !isReady) return;
    setSearchingChargePatient(true);
    setError(null);
    try {
      const data = await apiFetch<PatientSummary[]>(`/patients?query=${encodeURIComponent(term)}`);
      setChargePatientResults(data ?? []);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to search patients');
      setChargePatientResults([]);
    } finally {
      setSearchingChargePatient(false);
    }
  }

  async function searchInvoicePatients() {
    const term = invoiceQuery.trim();
    if (!term || !isReady) return;
    setSearchingInvoicePatient(true);
    setError(null);
    try {
      const data = await apiFetch<PatientSummary[]>(`/patients?query=${encodeURIComponent(term)}`);
      setInvoicePatientResults(data ?? []);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to search patients');
      setInvoicePatientResults([]);
    } finally {
      setSearchingInvoicePatient(false);
    }
  }

  async function createCharge() {
    if (!chargePatient) {
      setError('Select a patient first.');
      return;
    }
    setSubmittingCharge(true);
    setError(null);
    setNotice(null);
    try {
      await apiFetch('/billing/charges', {
        method: 'POST',
        body: JSON.stringify({
          patientId: chargePatient.id,
          chargeCode: chargeForm.chargeCode.trim() || undefined,
          chargeName: chargeForm.chargeName,
          quantity: Number(chargeForm.quantity),
          unitPriceQar: Number(chargeForm.unitPriceQar),
        }),
      });
      setNotice({ tone: 'success', label: 'Charge posted' });
      setChargeOpen(false);
      setChargeForm({ chargeCode: '', chargeName: '', quantity: '', unitPriceQar: '' });
      await loadCharges();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to post charge');
    } finally {
      setSubmittingCharge(false);
    }
  }

  async function createInvoice() {
    if (!invoicePatient) {
      setError('Select a patient first.');
      return;
    }
    setSubmittingInvoice(true);
    setError(null);
    setNotice(null);
    try {
      const data = await apiFetch<InvoiceDetail>('/billing/invoices', {
        method: 'POST',
        body: JSON.stringify({
          patientId: invoicePatient.id,
          discountQar: discountQar.trim() !== '' ? Number(discountQar) : undefined,
        }),
      });
      setNotice({ tone: 'success', label: 'Invoice created' });
      setInvoiceOpen(false);
      setDiscountQar('');
      setDetail(data);
      setPaymentAmount(data.invoice.amountDueQar ? String(data.invoice.amountDueQar) : '0');
      setPaymentMethod('card');
      setCancelReason('');
      setDetailOpen(true);
      await loadInvoices();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to create invoice');
    } finally {
      setSubmittingInvoice(false);
    }
  }

  async function addTariff() {
    setSubmittingTariff(true);
    setError(null);
    setNotice(null);
    try {
      await apiFetch('/billing/tariffs', {
        method: 'POST',
        body: JSON.stringify({
          code: tariffForm.code,
          name: tariffForm.name,
          category: tariffForm.category.trim() || undefined,
          priceQar: Number(tariffForm.priceQar),
        }),
      });
      setNotice({ tone: 'success', label: 'Tariff added' });
      setTariffOpen(false);
      setTariffForm({ code: '', name: '', category: '', priceQar: '' });
      await loadTariffs();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to add tariff');
    } finally {
      setSubmittingTariff(false);
    }
  }

  async function openInvoice(invoice: InvoiceView) {
    setDetailOpen(true);
    setDetail(null);
    setCancelReason('');
    try {
      const data = await apiFetch<InvoiceDetail>(`/billing/invoices/${invoice.id}`);
      setDetail(data);
      setPaymentAmount(data.invoice.amountDueQar ? String(data.invoice.amountDueQar) : '0');
      setPaymentMethod('card');
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load invoice');
    }
  }

  async function recordPayment() {
    if (!detail) return;
    setRecordingPayment(true);
    setError(null);
    try {
      const data = await apiFetch<PaymentView>(`/billing/invoices/${detail.invoice.id}/payments`, {
        method: 'POST',
        body: JSON.stringify({
          method: paymentMethod,
          amountQar: Number(paymentAmount),
        }),
      });
      setNotice({ tone: 'success', label: `Payment recorded (QAR ${data.amountQar})` });
      const updated = await apiFetch<InvoiceDetail>(`/billing/invoices/${detail.invoice.id}`);
      setDetail(updated);
      setPaymentAmount(updated.invoice.amountDueQar ? String(updated.invoice.amountDueQar) : '0');
      await loadInvoices();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to record payment');
    } finally {
      setRecordingPayment(false);
    }
  }

  async function cancelInvoice() {
    if (!detail) return;
    setCancelling(true);
    setError(null);
    try {
      await apiFetch(`/billing/invoices/${detail.invoice.id}/cancel`, {
        method: 'POST',
        body: JSON.stringify({ reason: cancelReason }),
      });
      setNotice({ tone: 'success', label: 'Invoice cancelled' });
      const updated = await apiFetch<InvoiceDetail>(`/billing/invoices/${detail.invoice.id}`);
      setDetail(updated);
      await loadInvoices();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to cancel invoice');
    } finally {
      setCancelling(false);
    }
  }

  return (
    <div style={{ padding: 24 }}>
      <PageHeader
        title="Billing"
        description="Manage charges, invoices and tariff master data."
        actions={
          <>
            <Button size="sm" variant="outline" onClick={() => setChargeOpen(true)}>
              New charge
            </Button>
            <Button size="sm" onClick={() => setInvoiceOpen(true)}>
              New invoice
            </Button>
          </>
        }
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

      {tab === 'Charges' ? (
        <Stack gap={12}>
          <Stack direction="row" gap={8} align="flex-end" wrap>
            <div style={{ flex: 1, minWidth: 240 }}>
              <Input
                label="Find patient"
                placeholder="Name, MRN, phone..."
                value={chargeQuery}
                onChange={(e) => setChargeQuery(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') void searchChargePatients();
                }}
              />
            </div>
            <Button variant="outline" loading={searchingChargePatient} onClick={() => void searchChargePatients()}>
              Search
            </Button>
          </Stack>

          {chargePatientResults.length > 0 ? (
            <Stack gap={4}>
              {chargePatientResults.map((patient) => (
                <button
                  key={patient.id}
                  type="button"
                  onClick={() => {
                    setChargePatient(patient);
                    setChargePatientResults([]);
                    setChargeQuery('');
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

          {chargePatient ? (
            <StatusIndicator tone="success" label={`Patient: ${fullName(chargePatient)}`} />
          ) : null}

          <ContentPanel>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Code</TableHead>
                  <TableHead>Name</TableHead>
                  <TableHead>Category</TableHead>
                  <TableHead>Qty</TableHead>
                  <TableHead>Unit price</TableHead>
                  <TableHead>Total</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Posted</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {charges.length === 0 ? (
                  <TableRow>
                    <TableCell>No charges for this patient.</TableCell>
                  </TableRow>
                ) : (
                  charges.map((c) => (
                    <TableRow key={c.id}>
                      <TableCell>{c.chargeCode ?? '—'}</TableCell>
                      <TableCell>
                        <strong>{c.chargeName}</strong>
                      </TableCell>
                      <TableCell>{c.category ?? '—'}</TableCell>
                      <TableCell>{c.quantity}</TableCell>
                      <TableCell>QAR {c.unitPriceQar}</TableCell>
                      <TableCell>QAR {c.totalQar}</TableCell>
                      <TableCell>
                        <Badge variant="neutral">{c.status}</Badge>
                      </TableCell>
                      <TableCell>{c.postedAt ? new Date(c.postedAt).toLocaleString() : '—'}</TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </ContentPanel>
        </Stack>
      ) : null}

      {tab === 'Invoices' ? (
        <Stack gap={12}>
          <Stack direction="row" gap={8} align="flex-end" wrap>
            <div style={{ flex: 1, minWidth: 240 }}>
              <Input
                label="Find patient"
                placeholder="Name, MRN, phone..."
                value={invoiceQuery}
                onChange={(e) => setInvoiceQuery(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') void searchInvoicePatients();
                }}
              />
            </div>
            <Button variant="outline" loading={searchingInvoicePatient} onClick={() => void searchInvoicePatients()}>
              Search
            </Button>
          </Stack>

          {invoicePatientResults.length > 0 ? (
            <Stack gap={4}>
              {invoicePatientResults.map((patient) => (
                <button
                  key={patient.id}
                  type="button"
                  onClick={() => {
                    setInvoicePatient(patient);
                    setInvoicePatientResults([]);
                    setInvoiceQuery('');
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

          {invoicePatient ? (
            <StatusIndicator tone="success" label={`Patient: ${fullName(invoicePatient)}`} />
          ) : null}

          <ContentPanel>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Invoice #</TableHead>
                  <TableHead>Total</TableHead>
                  <TableHead>Paid</TableHead>
                  <TableHead>Due</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Issued</TableHead>
                  <TableHead>Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {invoices.length === 0 ? (
                  <TableRow>
                    <TableCell>No invoices for this patient.</TableCell>
                  </TableRow>
                ) : (
                  invoices.map((inv) => (
                    <TableRow key={inv.id}>
                      <TableCell>
                        <strong>{inv.invoiceNumber}</strong>
                      </TableCell>
                      <TableCell>QAR {inv.totalQar}</TableCell>
                      <TableCell>QAR {inv.amountPaidQar}</TableCell>
                      <TableCell>QAR {inv.amountDueQar}</TableCell>
                      <TableCell>
                        <Badge variant={invoiceStatusVariants[inv.status] ?? 'neutral'}>{inv.status}</Badge>
                      </TableCell>
                      <TableCell>{inv.issuedAt ? new Date(inv.issuedAt).toLocaleString() : '—'}</TableCell>
                      <TableCell>
                        <Button size="sm" variant="outline" onClick={() => void openInvoice(inv)}>
                          View
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </ContentPanel>
        </Stack>
      ) : null}

      {tab === 'Tariffs' ? (
        <Stack gap={12}>
          <Stack direction="row" gap={8} align="flex-end" wrap>
            <div style={{ width: 320 }}>
              <Input
                label="Search tariffs"
                placeholder="Code, name or category..."
                value={tariffSearch}
                onChange={(e) => setTariffSearch(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') void loadTariffs();
                }}
              />
            </div>
            <Button variant="outline" onClick={() => void loadTariffs()}>Search</Button>
            <div style={{ flex: 1 }} />
            <Button onClick={() => setTariffOpen(true)}>Add tariff</Button>
          </Stack>

          <ContentPanel>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Code</TableHead>
                  <TableHead>Name</TableHead>
                  <TableHead>Category</TableHead>
                  <TableHead>Price (QAR)</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {tariffs.length === 0 ? (
                  <TableRow>
                    <TableCell>No tariffs.</TableCell>
                  </TableRow>
                ) : (
                  tariffs.map((t) => (
                    <TableRow key={t.id}>
                      <TableCell>{t.code}</TableCell>
                      <TableCell>
                        <strong>{t.name}</strong>
                      </TableCell>
                      <TableCell>{t.category ?? '—'}</TableCell>
                      <TableCell>QAR {t.priceQar}</TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </ContentPanel>
        </Stack>
      ) : null}

      <Modal
        open={chargeOpen}
        onClose={() => setChargeOpen(false)}
        title="New charge"
        description={chargePatient ? `For ${fullName(chargePatient)}` : 'Select a patient first'}
        footer={
          <>
            <Button variant="outline" onClick={() => setChargeOpen(false)}>Cancel</Button>
            <Button loading={submittingCharge} onClick={() => void createCharge()} disabled={!chargePatient}>
              Post charge
            </Button>
          </>
        }
      >
        <Stack gap={12}>
          <Input
            label="Charge code"
            value={chargeForm.chargeCode}
            onChange={(e) => setChargeForm({ ...chargeForm, chargeCode: e.target.value })}
          />
          <Input
            label="Charge name"
            value={chargeForm.chargeName}
            onChange={(e) => setChargeForm({ ...chargeForm, chargeName: e.target.value })}
          />
          <Stack direction="row" gap={8}>
            <div style={{ flex: 1 }}>
              <Input
                label="Quantity"
                type="number"
                value={chargeForm.quantity}
                onChange={(e) => setChargeForm({ ...chargeForm, quantity: e.target.value })}
              />
            </div>
            <div style={{ flex: 1 }}>
              <Input
                label="Unit price (QAR)"
                type="number"
                value={chargeForm.unitPriceQar}
                onChange={(e) => setChargeForm({ ...chargeForm, unitPriceQar: e.target.value })}
              />
            </div>
          </Stack>
        </Stack>
      </Modal>

      <Modal
        open={invoiceOpen}
        onClose={() => setInvoiceOpen(false)}
        title="New invoice"
        description={invoicePatient ? `For ${fullName(invoicePatient)}` : 'Select a patient first'}
        footer={
          <>
            <Button variant="outline" onClick={() => setInvoiceOpen(false)}>Cancel</Button>
            <Button loading={submittingInvoice} onClick={() => void createInvoice()} disabled={!invoicePatient}>
              Create invoice
            </Button>
          </>
        }
      >
        <Stack gap={12}>
          <StatusIndicator tone="success" label={invoicePatient ? fullName(invoicePatient) : 'No patient selected'} />
          <Input
            label="Discount (QAR)"
            type="number"
            value={discountQar}
            onChange={(e) => setDiscountQar(e.target.value)}
          />
        </Stack>
      </Modal>

      <Modal
        open={detailOpen}
        onClose={() => setDetailOpen(false)}
        title={detail ? `Invoice ${detail.invoice.invoiceNumber}` : 'Invoice'}
        description={detail ? `${detail.invoice.status}` : undefined}
        size="lg"
        footer={null}
      >
        {detail ? (
          <Stack gap={14}>
            <Stack direction="row" gap={16} wrap>
              <span>Total <strong>QAR {detail.invoice.totalQar}</strong></span>
              <span>Paid <strong>QAR {detail.invoice.amountPaidQar}</strong></span>
              <span>Due <strong>QAR {detail.invoice.amountDueQar}</strong></span>
              <Badge variant={invoiceStatusVariants[detail.invoice.status] ?? 'neutral'}>{detail.invoice.status}</Badge>
            </Stack>

            <div>
              <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--sq-fg-muted)', marginBottom: 8 }}>LINES</div>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Item</TableHead>
                    <TableHead>Qty</TableHead>
                    <TableHead>Unit price</TableHead>
                    <TableHead>Total</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {detail.lines.length === 0 ? (
                    <TableRow>
                      <TableCell>No lines.</TableCell>
                    </TableRow>
                  ) : (
                    detail.lines.map((line) => (
                      <TableRow key={line.id}>
                        <TableCell>{line.chargeName}</TableCell>
                        <TableCell>{line.quantity}</TableCell>
                        <TableCell>QAR {line.unitPriceQar}</TableCell>
                        <TableCell>QAR {line.totalQar}</TableCell>
                      </TableRow>
                    ))
                  )}
                </TableBody>
              </Table>
            </div>

            <div>
              <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--sq-fg-muted)', marginBottom: 8 }}>PAYMENTS</div>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Method</TableHead>
                    <TableHead>Amount</TableHead>
                    <TableHead>Reference</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {detail.payments.length === 0 ? (
                    <TableRow>
                      <TableCell>No payments yet.</TableCell>
                    </TableRow>
                  ) : (
                    detail.payments.map((p) => (
                      <TableRow key={p.id}>
                        <TableCell>{p.method}</TableCell>
                        <TableCell>QAR {p.amountQar}</TableCell>
                        <TableCell>{p.reference ?? '—'}</TableCell>
                      </TableRow>
                    ))
                  )}
                </TableBody>
              </Table>
            </div>

            {detail.invoice.amountDueQar > 0 ? (
              <Stack gap={10}>
                <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--sq-fg-muted)' }}>RECORD PAYMENT</div>
                <Stack direction="row" gap={8} wrap>
                  <div style={{ width: 180 }}>
                    <Select label="Method" value={paymentMethod} onChange={(e) => setPaymentMethod(e.target.value)}>
                      {['card', 'cash', 'insurance', 'transfer'].map((m) => (
                        <option key={m} value={m}>
                          {m}
                        </option>
                      ))}
                    </Select>
                  </div>
                  <div style={{ width: 160 }}>
                    <Input
                      label="Amount (QAR)"
                      type="number"
                      value={paymentAmount}
                      onChange={(e) => setPaymentAmount(e.target.value)}
                    />
                  </div>
                  <div style={{ alignSelf: 'flex-end' }}>
                    <Button loading={recordingPayment} onClick={() => void recordPayment()}>
                      Record payment
                    </Button>
                  </div>
                  <div style={{ flex: 1 }} />
                  <div style={{ alignSelf: 'flex-end' }}>
                    <Button variant="danger" loading={cancelling} onClick={() => void cancelInvoice()}>
                      Cancel
                    </Button>
                  </div>
                </Stack>
              </Stack>
            ) : null}
          </Stack>
        ) : (
          <StatusIndicator tone="information" label="Loading invoice..." />
        )}
      </Modal>

      <Modal
        open={tariffOpen}
        onClose={() => setTariffOpen(false)}
        title="Add tariff"
        description="Register a tariff item."
        footer={
          <>
            <Button variant="outline" onClick={() => setTariffOpen(false)}>Cancel</Button>
            <Button loading={submittingTariff} onClick={() => void addTariff()}>Add</Button>
          </>
        }
      >
        <Stack gap={12}>
          <Input
            label="Code"
            value={tariffForm.code}
            onChange={(e) => setTariffForm({ ...tariffForm, code: e.target.value })}
          />
          <Input
            label="Name"
            value={tariffForm.name}
            onChange={(e) => setTariffForm({ ...tariffForm, name: e.target.value })}
          />
          <Input
            label="Category"
            value={tariffForm.category}
            onChange={(e) => setTariffForm({ ...tariffForm, category: e.target.value })}
          />
          <Input
            label="Price (QAR)"
            type="number"
            value={tariffForm.priceQar}
            onChange={(e) => setTariffForm({ ...tariffForm, priceQar: e.target.value })}
          />
        </Stack>
      </Modal>
    </div>
  );
}
