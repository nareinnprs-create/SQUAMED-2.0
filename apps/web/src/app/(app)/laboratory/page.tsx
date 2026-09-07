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

const STATUS_OPTIONS = ['all', 'ordered', 'collected', 'received', 'processing', 'resulted', 'rejected'];
const PRIORITY_OPTIONS = ['routine', 'urgent', 'stat'];

const statusVariants: Record<string, BadgeVariant> = {
  ordered: 'information',
  collected: 'information',
  received: 'warning',
  processing: 'warning',
  resulted: 'success',
  rejected: 'critical',
};

const resultStatusVariants: Record<string, BadgeVariant> = {
  pending: 'neutral',
  released: 'success',
  verified: 'information',
};

interface PatientSummary {
  id: string;
  mrn: string | null;
  firstName: string | null;
  lastName: string | null;
}

interface LabOrderView {
  id: string;
  orderNumber: string;
  patientId: string;
  priority: string;
  status: string;
  resultStatus: string | null;
  orderedAt: string;
}

interface LabTestView {
  id: string;
  testCode: string;
  testName: string;
  status: string;
  resultValue: string | null;
  unit: string | null;
  referenceRange: string | null;
  isAbnormal: boolean;
}

interface LabOrderDetail {
  order: LabOrderView;
  tests: LabTestView[];
}

interface TestRow {
  testCode: string;
  testName: string;
}

interface Notice {
  tone: StatusTone;
  label: string;
}

function fullName(patient: PatientSummary): string {
  return [patient.firstName, patient.lastName].filter(Boolean).join(' ') || 'Unnamed patient';
}

function formatDateTime(value: string | null | undefined): string {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

export default function LaboratoryPage() {
  const { apiFetch, isReady } = useApi();
  const [status, setStatus] = useState('all');
  const [orders, setOrders] = useState<LabOrderView[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<Notice | null>(null);

  const [newOpen, setNewOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [patientQuery, setPatientQuery] = useState('');
  const [patientResults, setPatientResults] = useState<PatientSummary[]>([]);
  const [searching, setSearching] = useState(false);
  const [selectedPatient, setSelectedPatient] = useState<PatientSummary | null>(null);
  const [tests, setTests] = useState<TestRow[]>([{ testCode: '', testName: '' }]);
  const [priority, setPriority] = useState('routine');

  const [detailOpen, setDetailOpen] = useState(false);
  const [detail, setDetail] = useState<LabOrderDetail | null>(null);
  const [working, setWorking] = useState<string | null>(null);
  const [rejectReason, setRejectReason] = useState('');
  const [resultsDrafts, setResultsDrafts] = useState<Record<string, { resultValue: string; unit: string; referenceRange: string }>>({});

  const loadOrders = useCallback(async () => {
    if (!isReady) return;
    try {
      const query = status === 'all' ? '' : `?status=${encodeURIComponent(status)}`;
      const data = await apiFetch<LabOrderView[]>(`/lab/orders${query}`);
      setOrders(data ?? []);
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load lab orders');
    }
  }, [apiFetch, isReady, status]);

  useEffect(() => {
    void loadOrders();
  }, [loadOrders]);

  async function searchPatients() {
    const term = patientQuery.trim();
    if (!term || !isReady) return;
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

  function addTestRow() {
    setTests((prev) => [...prev, { testCode: '', testName: '' }]);
  }

  async function submitOrder() {
    if (!selectedPatient) {
      setError('Select a patient first.');
      return;
    }
    const validTests = tests.filter((t) => t.testName.trim() !== '');
    if (validTests.length === 0) {
      setError('Add at least one test.');
      return;
    }
    setSubmitting(true);
    setError(null);
    setNotice(null);
    try {
      await apiFetch(`/lab/orders`, {
        method: 'POST',
        body: JSON.stringify({
          patientId: selectedPatient.id,
          priority,
          tests: validTests.map((t) => ({ testCode: t.testCode.trim() || undefined, testName: t.testName.trim() })),
        }),
      });
      setNotice({ tone: 'success', label: 'Lab order created' });
      setNewOpen(false);
      setSelectedPatient(null);
      setPatientQuery('');
      setPatientResults([]);
      setTests([{ testCode: '', testName: '' }]);
      setPriority('routine');
      await loadOrders();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to create lab order');
    } finally {
      setSubmitting(false);
    }
  }

  async function openDetail(order: LabOrderView) {
    setDetailOpen(true);
    setDetail(null);
    setRejectReason('');
    setResultsDrafts({});
    try {
      const data = await apiFetch<LabOrderDetail>(`/lab/orders/${order.id}`);
      setDetail(data);
      const drafts: Record<string, { resultValue: string; unit: string; referenceRange: string }> = {};
      for (const test of data.tests) {
        drafts[test.id] = {
          resultValue: test.resultValue ?? '',
          unit: test.unit ?? '',
          referenceRange: test.referenceRange ?? '',
        };
      }
      setResultsDrafts(drafts);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load order detail');
    }
  }

  async function runWorkflow(action: 'collect' | 'receive' | 'start' | 'verify' | 'release') {
    if (!detail) return;
    setWorking(action);
    setError(null);
    try {
      await apiFetch(`/lab/orders/${detail.order.id}/${action}`, {
        method: 'POST',
        body: action === 'collect' ? '{}' : '{}',
      });
      const data = await apiFetch<LabOrderDetail>(`/lab/orders/${detail.order.id}`);
      setDetail(data);
    } catch (e) {
      setError(e instanceof Error ? e.message : `Failed to ${action} order`);
    } finally {
      setWorking(null);
    }
  }

  async function rejectOrder() {
    if (!detail) return;
    setWorking('reject');
    setError(null);
    try {
      await apiFetch(`/lab/orders/${detail.order.id}/reject`, {
        method: 'POST',
        body: JSON.stringify({ reason: rejectReason }),
      });
      const data = await apiFetch<LabOrderDetail>(`/lab/orders/${detail.order.id}`);
      setDetail(data);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to reject order');
    } finally {
      setWorking(null);
    }
  }

  async function saveResults() {
    if (!detail) return;
    setWorking('results');
    setError(null);
    try {
      const results = detail.tests.map((test) => {
        const d = resultsDrafts[test.id];
        return {
          testRequestId: test.id,
          resultValue: d?.resultValue ?? '',
          unit: d?.unit ? d.unit : undefined,
          referenceRange: d?.referenceRange ? d.referenceRange : undefined,
        };
      });
      await apiFetch(`/lab/orders/${detail.order.id}/results`, {
        method: 'POST',
        body: JSON.stringify({ results }),
      });
      const data = await apiFetch<LabOrderDetail>(`/lab/orders/${detail.order.id}`);
      setDetail(data);
      setNotice({ tone: 'success', label: 'Results saved' });
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to save results');
    } finally {
      setWorking(null);
    }
  }

  const order = detail?.order;
  const orderStatus = order?.status;

  return (
    <div style={{ padding: 24 }}>
      <PageHeader
        title="Laboratory"
        description="Manage laboratory orders, sample workflows and results."
        actions={<Button size="sm" onClick={() => setNewOpen(true)}>New lab order</Button>}
      />

      {notice ? <StatusIndicator tone={notice.tone} label={notice.label} /> : null}
      {error ? <StatusIndicator tone="critical" label={error} /> : null}

      <div style={{ height: 16 }} />

      <Stack direction="row" gap={8} align="flex-end" wrap>
        <div style={{ width: 220 }}>
          <Select label="Status" value={status} onChange={(e) => setStatus(e.target.value)}>
            {STATUS_OPTIONS.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </Select>
        </div>
        <Button variant="outline" onClick={() => void loadOrders()}>
          Refresh
        </Button>
      </Stack>

      <div style={{ height: 16 }} />

      <ContentPanel>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Order #</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Result status</TableHead>
              <TableHead>Priority</TableHead>
              <TableHead>Ordered</TableHead>
              <TableHead>Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {orders.length === 0 ? (
              <TableRow>
                <TableCell>No lab orders yet.</TableCell>
              </TableRow>
            ) : (
              orders.map((o) => (
                <TableRow key={o.id}>
                  <TableCell>
                    <strong>{o.orderNumber}</strong>
                  </TableCell>
                  <TableCell>
                    <Badge variant={statusVariants[o.status] ?? 'neutral'}>{o.status}</Badge>
                  </TableCell>
                  <TableCell>
                    <Badge variant={resultStatusVariants[o.resultStatus ?? 'pending'] ?? 'neutral'}>
                      {o.resultStatus ?? 'pending'}
                    </Badge>
                  </TableCell>
                  <TableCell>
                    <Badge variant="information" size="sm">
                      {o.priority}
                    </Badge>
                  </TableCell>
                  <TableCell>{formatDateTime(o.orderedAt)}</TableCell>
                  <TableCell>
                    <Button size="sm" variant="outline" onClick={() => void openDetail(o)}>
                      Manage
                    </Button>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </ContentPanel>

      <Modal
        open={newOpen}
        onClose={() => setNewOpen(false)}
        title="New lab order"
        description="Select a patient and add the tests to request."
        size="lg"
        footer={
          <>
            <Button variant="outline" onClick={() => setNewOpen(false)}>Cancel</Button>
            <Button loading={submitting} onClick={() => void submitOrder()}>Create order</Button>
          </>
        }
      >
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

          <Stack direction="row" gap={8} align="flex-end">
            <div style={{ width: 160 }}>
              <Select label="Priority" value={priority} onChange={(e) => setPriority(e.target.value)}>
                {PRIORITY_OPTIONS.map((p) => (
                  <option key={p} value={p}>
                    {p}
                  </option>
                ))}
              </Select>
            </div>
            <Button variant="outline" size="sm" onClick={addTestRow}>
              Add test
            </Button>
          </Stack>

          {tests.map((test, index) => (
            <Stack key={index} direction="row" gap={8} wrap>
              <div style={{ flex: 1, minWidth: 140 }}>
                <Input
                  label="Test code"
                  placeholder="e.g. CBC"
                  value={test.testCode}
                  onChange={(e) => {
                    const next = [...tests];
                    next[index] = { ...next[index], testCode: e.target.value };
                    setTests(next);
                  }}
                />
              </div>
              <div style={{ flex: 2, minWidth: 200 }}>
                <Input
                  label="Test name"
                  placeholder="e.g. Complete blood count"
                  value={test.testName}
                  onChange={(e) => {
                    const next = [...tests];
                    next[index] = { ...next[index], testName: e.target.value };
                    setTests(next);
                  }}
                />
              </div>
              {tests.length > 1 ? (
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => setTests(tests.filter((_, i) => i !== index))}
                >
                  Remove
                </Button>
              ) : null}
            </Stack>
          ))}
        </Stack>
      </Modal>

      <Modal
        open={detailOpen}
        onClose={() => setDetailOpen(false)}
        title={order ? `Order ${order.orderNumber}` : 'Lab order'}
        description={order ? `${order.status} · ${order.priority}` : undefined}
        size="lg"
        footer={null}
      >
        <Stack gap={14}>
          {detail ? (
            <>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Test</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead>Result</TableHead>
                    <TableHead>Unit</TableHead>
                    <TableHead>Reference</TableHead>
                    <TableHead>Flag</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {detail.tests.map((test) => (
                    <TableRow key={test.id}>
                      <TableCell>
                        <strong>{test.testName}</strong>
                        <span style={{ color: 'var(--sq-fg-muted)', fontSize: 12 }}> ({test.testCode})</span>
                      </TableCell>
                      <TableCell>
                        <Badge size="sm" variant={statusVariants[test.status] ?? 'neutral'}>
                          {test.status}
                        </Badge>
                      </TableCell>
                      <TableCell>{test.resultValue ?? '—'}</TableCell>
                      <TableCell>{test.unit ?? '—'}</TableCell>
                      <TableCell>{test.referenceRange ?? '—'}</TableCell>
                      <TableCell>{test.isAbnormal ? <Badge variant="critical">Abnormal</Badge> : <Badge variant="success">Normal</Badge>}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>

              <Stack direction="row" gap={8} wrap>
                {orderStatus === 'ordered' ? (
                  <Button size="sm" loading={working === 'collect'} onClick={() => void runWorkflow('collect')}>
                    Collect
                  </Button>
                ) : null}
                {orderStatus === 'ordered' || orderStatus === 'collected' ? (
                  <Button size="sm" loading={working === 'receive'} onClick={() => void runWorkflow('receive')}>
                    Receive
                  </Button>
                ) : null}
                {orderStatus === 'received' ? (
                  <Button size="sm" loading={working === 'start'} onClick={() => void runWorkflow('start')}>
                    Start
                  </Button>
                ) : null}
                {orderStatus === 'processing' ? (
                  <Button
                    size="sm"
                    loading={working === 'results'}
                    onClick={() => void saveResults()}
                  >
                    Save results
                  </Button>
                ) : null}
                {orderStatus === 'resulted' ? (
                  <Button size="sm" loading={working === 'verify'} onClick={() => void runWorkflow('verify')}>
                    Verify
                  </Button>
                ) : null}
                {orderStatus === 'verified' ? (
                  <Button size="sm" loading={working === 'release'} onClick={() => void runWorkflow('release')}>
                    Release
                  </Button>
                ) : null}
                <div style={{ flex: 1 }} />
                {orderStatus === 'ordered' || orderStatus === 'collected' ? (
                  <Stack direction="row" gap={8} align="flex-end" wrap>
                    <div style={{ width: 180 }}>
                      <Input
                        label="Reject reason"
                        value={rejectReason}
                        onChange={(e) => setRejectReason(e.target.value)}
                      />
                    </div>
                    <Button
                      size="sm"
                      variant="danger"
                      loading={working === 'reject'}
                      onClick={() => void rejectOrder()}
                    >
                      Reject
                    </Button>
                  </Stack>
                ) : null}
              </Stack>

              {orderStatus === 'processing' ? (
                <Stack gap={10}>
                  <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--sq-fg-muted)' }}>ENTRY RESULTS</div>
                  {detail.tests.map((test) => {
                    const d = resultsDrafts[test.id] ?? { resultValue: '', unit: '', referenceRange: '' };
                    return (
                      <Stack key={test.id} direction="row" gap={8} wrap>
                        <div style={{ flex: 2, minWidth: 180 }}>
                          <Input
                            label={`Result — ${test.testName}`}
                            value={d.resultValue}
                            onChange={(e) =>
                              setResultsDrafts({ ...resultsDrafts, [test.id]: { ...d, resultValue: e.target.value } })
                            }
                          />
                        </div>
                        <div style={{ flex: 1, minWidth: 120 }}>
                          <Input
                            label="Unit"
                            value={d.unit}
                            onChange={(e) =>
                              setResultsDrafts({ ...resultsDrafts, [test.id]: { ...d, unit: e.target.value } })
                            }
                          />
                        </div>
                        <div style={{ flex: 1, minWidth: 140 }}>
                          <Input
                            label="Reference range"
                            value={d.referenceRange}
                            onChange={(e) =>
                              setResultsDrafts({ ...resultsDrafts, [test.id]: { ...d, referenceRange: e.target.value } })
                            }
                          />
                        </div>
                      </Stack>
                    );
                  })}
                </Stack>
              ) : null}
            </>
          ) : (
            <StatusIndicator tone="information" label="Loading order..." />
          )}
        </Stack>
      </Modal>
    </div>
  );
}
