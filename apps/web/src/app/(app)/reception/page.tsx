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
  Toggle,
  type BadgeVariant,
} from '@squamed/ui';
import { useApi } from '@/lib/use-api';

interface Visit {
  id: string | null;
  hospitalId: string | null;
  patientId: string;
  appointmentId: string | null;
  visitNumber: string;
  visitType: string;
  departmentId: string | null;
  doctorId: string | null;
  specialtyId: string | null;
  reason: string | null;
  priority: string;
  source: string;
  status: string;
  insuranceVerification: string | null;
  insuranceVerifiedAt: string | null;
  registrationStartedAt: string;
  registeredAt: string | null;
  createdAt: string | null;
  patientName?: string | null;
}

interface QueueEntryView {
  id: string | null;
  queueType: string;
  tokenNumber: string;
  patientId: string;
  patientName: string | null;
  priority: string;
  status: string;
  visitId: string | null;
  appointmentId: string | null;
}

interface VisitCreateResult {
  visit: Visit;
  queueEntry: QueueEntryView | null;
}

interface PatientSummary {
  id: string | null;
  mrn: string | null;
  hospitalId: string | null;
  firstName: string | null;
  middleName: string | null;
  lastName: string | null;
  preferredName: string | null;
  displayName?: string | null;
  dateOfBirth: string | null;
  gender: string | null;
  phone: string | null;
  email: string | null;
  qid: string | null;
  recordsStatus: string | null;
}

interface SelectedPatient {
  id: string;
  label: string;
}

type ApiFetch = <T>(path: string, options?: RequestInit) => Promise<T>;

function patientDisplayName(p: PatientSummary): string {
  if (p.displayName) return p.displayName;
  if (p.preferredName) return p.preferredName;
  const fullName = [p.firstName, p.lastName].filter((part): part is string => Boolean(part)).join(' ');
  return fullName || p.mrn || p.qid || 'Unnamed patient';
}

function shortId(id: string): string {
  return id.length > 8 ? id.slice(0, 8) : id;
}

function formatDateTime(iso: string | null): string {
  if (!iso) return '-';
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? '-' : d.toLocaleString();
}

function statusVariant(status: string): BadgeVariant {
  switch (status) {
    case 'registered':
      return 'information';
    case 'checked-in':
      return 'success';
    case 'completed':
      return 'neutral';
    case 'cancelled':
      return 'critical';
    default:
      return 'neutral';
  }
}

interface PatientPickerProps {
  apiFetch: ApiFetch;
  value: SelectedPatient | null;
  onSelect: (patient: SelectedPatient) => void;
}

function PatientPicker({ apiFetch, value, onSelect }: PatientPickerProps) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<PatientSummary[]>([]);
  const [searched, setSearched] = useState(false);
  const [searching, setSearching] = useState(false);
  const [searchError, setSearchError] = useState<string | null>(null);

  async function runSearch() {
    const q = query.trim();
    if (!q) return;
    setSearching(true);
    setSearchError(null);
    try {
      const data = await apiFetch<PatientSummary[]>(`/patients?query=${encodeURIComponent(q)}`);
      setResults(data ?? []);
      setSearched(true);
    } catch (e) {
      setSearchError(e instanceof Error ? e.message : 'Patient search failed');
      setResults([]);
      setSearched(true);
    } finally {
      setSearching(false);
    }
  }

  return (
    <Stack gap={8}>
      <div style={{ display: 'flex', gap: 8, alignItems: 'flex-end' }}>
        <Input
          label={value ? 'Change patient' : 'Patient search'}
          placeholder="Search by name or MRN"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') void runSearch();
          }}
        />
        <Button variant="outline" size="sm" loading={searching} onClick={() => void runSearch()}>
          Search
        </Button>
      </div>
      {value ? <StatusIndicator tone="success" label={`Selected: ${value.label}`} /> : null}
      {searchError ? <StatusIndicator tone="critical" label={searchError} /> : null}
      {searched && results.length === 0 ? (
        <span style={{ fontSize: 13, color: 'var(--sq-fg-muted)' }}>No patients found.</span>
      ) : null}
      {results.length > 0 ? (
        <div style={{ border: '1px solid var(--sq-border)', borderRadius: 8, maxHeight: 180, overflowY: 'auto' }}>
          {results.map((p) => {
            const id = p.id;
            if (!id) return null;
            return (
              <button
                key={id}
                type="button"
                onClick={() => onSelect({ id, label: patientDisplayName(p) })}
                style={{
                  display: 'block',
                  width: '100%',
                  padding: '8px 12px',
                  textAlign: 'left',
                  border: 'none',
                  borderBottom: '1px solid var(--sq-border)',
                  background: 'transparent',
                  color: 'var(--sq-fg)',
                  fontSize: 13,
                  cursor: 'pointer',
                  fontFamily: 'inherit',
                }}
              >
                {patientDisplayName(p)}
                {p.mrn ? <span style={{ color: 'var(--sq-fg-muted)' }}> &middot; {p.mrn}</span> : null}
              </button>
            );
          })}
        </div>
      ) : null}
    </Stack>
  );
}

interface VisitForm {
  visitType: string;
  priority: string;
  reason: string;
  addToQueue: boolean;
  queueType: string;
}

const initialForm: VisitForm = {
  visitType: 'outpatient',
  priority: 'routine',
  reason: '',
  addToQueue: true,
  queueType: 'registration',
};

export default function ReceptionPage() {
  const { apiFetch, isReady } = useApi();
  const [visits, setVisits] = useState<Visit[]>([]);
  const [statusFilter, setStatusFilter] = useState('all');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [modalOpen, setModalOpen] = useState(false);
  const [selectedPatient, setSelectedPatient] = useState<SelectedPatient | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [form, setForm] = useState<VisitForm>(initialForm);
  const [issuedToken, setIssuedToken] = useState<QueueEntryView | null>(null);

  const loadVisits = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const query = statusFilter === 'all' ? '' : `?status=${encodeURIComponent(statusFilter)}`;
      const data = await apiFetch<Visit[]>(`/reception/visits${query}`);
      setVisits(data ?? []);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load visits');
    } finally {
      setLoading(false);
    }
  }, [apiFetch, statusFilter]);

  useEffect(() => {
    if (isReady) void loadVisits();
  }, [isReady, loadVisits]);

  function openModal() {
    setForm(initialForm);
    setSelectedPatient(null);
    setIssuedToken(null);
    setError(null);
    setModalOpen(true);
  }

  function closeModal() {
    setModalOpen(false);
  }

  async function registerVisit() {
    if (!selectedPatient) {
      setError('Select a patient');
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const result = await apiFetch<VisitCreateResult>('/reception/visits', {
        method: 'POST',
        body: JSON.stringify({
          patientId: selectedPatient.id,
          visitType: form.visitType,
          priority: form.priority,
          reason: form.reason,
          addToQueue: form.addToQueue,
          queueType: form.addToQueue ? form.queueType : undefined,
        }),
      });
      if (result?.queueEntry) setIssuedToken(result.queueEntry);
      await loadVisits();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to register visit');
    } finally {
      setSubmitting(false);
    }
  }

  async function patchVisit(visit: Visit, action: 'check-in' | 'complete') {
    if (!visit.id) return;
    setError(null);
    try {
      await apiFetch<Visit>(`/reception/visits/${visit.id}/${action}`, { method: 'PATCH' });
      await loadVisits();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to update visit');
    }
  }

  return (
    <div style={{ padding: 24 }}>
      <PageHeader
        title="Reception &amp; Triage"
        description="Register walk-in patients and manage today's visit queue."
        actions={<Button onClick={openModal}>Register walk-in</Button>}
      />

      {error ? <StatusIndicator tone="critical" label={error} /> : null}

      <Stack gap={16}>
        <ContentPanel>
          <div
            style={{
              display: 'flex',
              alignItems: 'flex-end',
              justifyContent: 'space-between',
              gap: 12,
              marginBottom: 12,
            }}
          >
            <Select
              label="Status"
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              style={{ width: 200 }}
            >
              <option value="all">All</option>
              <option value="registered">Registered</option>
              <option value="checked-in">Checked in</option>
              <option value="completed">Completed</option>
              <option value="cancelled">Cancelled</option>
            </Select>
            {loading ? <StatusIndicator tone="neutral" label="Loading&hellip;" /> : null}
          </div>

          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Visit Number</TableHead>
                <TableHead>Patient</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Priority</TableHead>
                <TableHead>Reason</TableHead>
                <TableHead>Registered at</TableHead>
                <TableHead>Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {visits.length === 0 ? (
                <TableRow>
                  <TableCell>No visits registered today.</TableCell>
                </TableRow>
              ) : (
                visits.map((visit) => (
                  <TableRow key={visit.id ?? 'row'}>
                    <TableCell>{visit.visitNumber}</TableCell>
                    <TableCell>{visit.patientName ?? shortId(visit.patientId)}</TableCell>
                    <TableCell>
                      <Badge variant={statusVariant(visit.status)}>{visit.status}</Badge>
                    </TableCell>
                    <TableCell>{visit.priority}</TableCell>
                    <TableCell>{visit.reason || '-'}</TableCell>
                    <TableCell>{formatDateTime(visit.registeredAt ?? visit.createdAt)}</TableCell>
                    <TableCell>
                      <div style={{ display: 'flex', gap: 8 }}>
                        {visit.status === 'registered' ? (
                          <Button size="sm" variant="outline" onClick={() => void patchVisit(visit, 'check-in')}>
                            Check-in
                          </Button>
                        ) : null}
                        {visit.status === 'checked-in' ? (
                          <Button size="sm" variant="outline" onClick={() => void patchVisit(visit, 'complete')}>
                            Complete
                          </Button>
                        ) : null}
                      </div>
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </ContentPanel>
      </Stack>

      <Modal
        open={modalOpen}
        onClose={closeModal}
        title="Register walk-in"
        description="Register a patient and optionally add the visit to the queue."
        footer={
          issuedToken ? (
            <Button onClick={closeModal}>Close</Button>
          ) : (
            <>
              <Button variant="outline" onClick={closeModal}>
                Cancel
              </Button>
              <Button loading={submitting} onClick={() => void registerVisit()}>
                Register walk-in
              </Button>
            </>
          )
        }
      >
        {modalOpen ? (
          issuedToken ? (
            <Stack gap={16}>
              <StatusIndicator tone="success" label={`Queue token ${issuedToken.tokenNumber} issued`} />
              <div style={{ fontSize: 48, fontWeight: 700, textAlign: 'center' }}>{issuedToken.tokenNumber}</div>
            </Stack>
          ) : (
            <Stack gap={12}>
              <PatientPicker apiFetch={apiFetch} value={selectedPatient} onSelect={setSelectedPatient} />
              <Select
                label="Visit type"
                value={form.visitType}
                onChange={(e) => setForm({ ...form, visitType: e.target.value })}
              >
                <option value="outpatient">Outpatient</option>
                <option value="emergency">Emergency</option>
                <option value="follow-up">Follow-up</option>
              </Select>
              <Select
                label="Priority"
                value={form.priority}
                onChange={(e) => setForm({ ...form, priority: e.target.value })}
              >
                <option value="routine">Routine</option>
                <option value="urgent">Urgent</option>
                <option value="emergency">Emergency</option>
              </Select>
              <Input
                label="Reason"
                placeholder="Reason for the visit"
                value={form.reason}
                onChange={(e) => setForm({ ...form, reason: e.target.value })}
              />
              <Toggle
                checked={form.addToQueue}
                onCheckedChange={(checked) => setForm({ ...form, addToQueue: checked })}
                label="Add to queue"
              />
              {form.addToQueue ? (
                <Select
                  label="Queue"
                  value={form.queueType}
                  onChange={(e) => setForm({ ...form, queueType: e.target.value })}
                >
                  <option value="registration">Registration</option>
                  <option value="pharmacy">Pharmacy</option>
                  <option value="billing">Billing</option>
                  <option value="consult">Consult</option>
                </Select>
              ) : null}
            </Stack>
          )
        ) : null}
      </Modal>
    </div>
  );
}