'use client';

import { useCallback, useEffect, useState } from 'react';
import {
  Badge,
  Button,
  ContentPanel,
  Input,
  Modal,
  PageHeader,
  Radio,
  RadioGroup,
  Select,
  Stack,
  StatusIndicator,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  t,
  type BadgeVariant,
} from '@squamed/ui';
import { useApi } from '@/lib/use-api';

interface Appointment {
  id: string | null;
  patientId: string | null;
  doctorId: string | null;
  scheduledStart: string | null;
  scheduledEnd: string | null;
  status: string | null;
  type: string | null;
  reason: string | null;
  priority: string | null;
  visitType: string | null;
  telemedicine: boolean | null;
  roomId: string | null;
  createdAt: string | null;
  updatedAt: string | null;
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

function formatDateTime(iso: string | null): string {
  if (!iso) return '-';
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? '-' : d.toLocaleString();
}

function statusVariant(status: string | null): BadgeVariant {
  switch (status) {
    case 'scheduled':
      return 'information';
    case 'confirmed':
      return 'success';
    case 'arrived':
      return 'information';
    case 'completed':
      return 'neutral';
    case 'cancelled':
      return 'critical';
    case 'no-show':
      return 'warning';
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

interface AppointmentForm {
  doctorId: string;
  appointmentType: string;
  date: string;
  time: string;
  durationMinutes: string;
  priority: string;
  reason: string;
}

const initialForm: AppointmentForm = {
  doctorId: '',
  appointmentType: 'consultation',
  date: '',
  time: '',
  durationMinutes: '30',
  priority: 'routine',
  reason: '',
};

// 01.01 scheduling engine: bookable session slot returned by GET /schedules/{doctorId}/slots.
interface SlotOption {
  id: string | null;
  startTime: string;
  endTime: string;
  capacity: number;
  bookedCount: number;
  status: string;
  sessionType: string;
}

export default function AppointmentsPage() {
  const { apiFetch, isReady } = useApi();
  const [appointments, setAppointments] = useState<Appointment[]>([]);
  const [statusFilter, setStatusFilter] = useState('all');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [modalOpen, setModalOpen] = useState(false);
  const [selectedPatient, setSelectedPatient] = useState<SelectedPatient | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [form, setForm] = useState<AppointmentForm>(initialForm);

  // 01.03: reschedule an existing appointment onto another session slot.
  const [rescheduleTarget, setRescheduleTarget] = useState<Appointment | null>(null);
  const [rescheduleForm, setRescheduleForm] = useState({ doctorId: '', date: '' });
  const [rescheduleSlots, setRescheduleSlots] = useState<SlotOption[]>([]);
  const [rescheduleSlotId, setRescheduleSlotId] = useState('');
  const [rescheduleSlotsLoading, setRescheduleSlotsLoading] = useState(false);
  const [rescheduleSubmitting, setRescheduleSubmitting] = useState(false);

  const loadAppointments = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const query = statusFilter === 'all' ? '' : `?status=${encodeURIComponent(statusFilter)}`;
      const data = await apiFetch<Appointment[]>(`/appointments${query}`);
      setAppointments(data ?? []);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load appointments');
    } finally {
      setLoading(false);
    }
  }, [apiFetch, statusFilter]);

  useEffect(() => {
    if (isReady) void loadAppointments();
  }, [isReady, loadAppointments]);

  const [slots, setSlots] = useState<SlotOption[]>([]);
  const [slotsLoading, setSlotsLoading] = useState(false);
  const [selectedSlotId, setSelectedSlotId] = useState<string>('');

  // 01.01: doctor + date chosen → materialize that day's session slots; pick one to book.
  useEffect(() => {
    const doctorId = form.doctorId.trim();
    if (!isReady || !doctorId || !form.date) {
      setSlots([]);
      setSelectedSlotId('');
      return;
    }
    let cancelled = false;
    setSlotsLoading(true);
    apiFetch<SlotOption[]>(`/schedules/${encodeURIComponent(doctorId)}/slots?from=${form.date}&to=${form.date}`)
      .then((data) => {
        if (!cancelled) {
          setSlots(data ?? []);
          setSelectedSlotId('');
        }
      })
      .catch(() => {
        // Slot preview is best-effort: empty list falls back to the legacy direct-time form.
        if (!cancelled) setSlots([]);
      })
      .finally(() => {
        if (!cancelled) setSlotsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [apiFetch, isReady, form.doctorId, form.date]);

  // 01.03: reschedule picker — same slot machinery, its own state.
  useEffect(() => {
    const doctorId = rescheduleForm.doctorId.trim();
    if (!isReady || rescheduleTarget === null || !doctorId || !rescheduleForm.date) {
      setRescheduleSlots([]);
      setRescheduleSlotId('');
      return;
    }
    let cancelled = false;
    setRescheduleSlotsLoading(true);
    apiFetch<SlotOption[]>(`/schedules/${encodeURIComponent(doctorId)}/slots?from=${rescheduleForm.date}&to=${rescheduleForm.date}`)
      .then((data) => {
        if (!cancelled) {
          setRescheduleSlots(data ?? []);
          setRescheduleSlotId('');
        }
      })
      .catch(() => {
        if (!cancelled) setRescheduleSlots([]);
      })
      .finally(() => {
        if (!cancelled) setRescheduleSlotsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [apiFetch, isReady, rescheduleTarget, rescheduleForm.doctorId, rescheduleForm.date]);

  function openReschedule(appointment: Appointment) {
    setRescheduleForm({ doctorId: '', date: '' });
    setRescheduleSlots([]);
    setRescheduleSlotId('');
    setRescheduleTarget(appointment);
  }

  function openModal() {
    setForm(initialForm);
    setSelectedPatient(null);
    setError(null);
    setModalOpen(true);
  }

  function closeModal() {
    setModalOpen(false);
  }

  async function createAppointment() {
    if (!selectedPatient) {
      setError('Select a patient');
      return;
    }
    if (!form.date) {
      setError('Pick a date');
      return;
    }
    const slotFlow = form.doctorId.trim() !== '' && slots.length > 0;
    if (slotFlow && !selectedSlotId) {
      setError('Select a slot');
      return;
    }
    if (!slotFlow && !form.time) {
      setError('Pick a date and time');
      return;
    }
    const start = `${form.date}T${form.time}:00Z`;
    const durationMinutes = Number(form.durationMinutes);
    const end = new Date(new Date(start).getTime() + durationMinutes * 60_000).toISOString();
    setSubmitting(true);
    setError(null);
    try {
      const slot = slots.find((s) => s.id === selectedSlotId);
      await apiFetch<Appointment>('/appointments', {
        method: 'POST',
        body: JSON.stringify(
          slotFlow && slot
            ? // Slot booking: the backend derives doctor/time/duration/telemedicine from the slot.
              {
                slotId: slot.id,
                patientId: selectedPatient.id,
                appointmentType: form.appointmentType,
                reason: form.reason,
                priority: form.priority,
                visitType: 'outpatient',
              }
            : {
                patientId: selectedPatient.id,
                ...(form.doctorId.trim() ? { doctorId: form.doctorId.trim() } : {}),
                appointmentType: form.appointmentType,
                scheduledStart: start,
                scheduledEnd: end,
                durationMinutes,
                reason: form.reason,
                priority: form.priority,
                visitType: 'outpatient',
              },
        ),
      });
      closeModal();
      await loadAppointments();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to create appointment');
    } finally {
      setSubmitting(false);
    }
  }

  async function updateStatus(appointment: Appointment, status: string) {
    if (!appointment.id) return;
    setError(null);
    try {
      await apiFetch<Appointment>(`/appointments/${appointment.id}/status`, {
        method: 'PATCH',
        body: JSON.stringify({ status }),
      });
      await loadAppointments();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to update appointment status');
    }
  }

  async function rescheduleAppointment() {
    if (!rescheduleTarget?.id) return;
    if (!rescheduleSlotId) {
      setError('Select a new slot');
      return;
    }
    setRescheduleSubmitting(true);
    setError(null);
    try {
      await apiFetch<Appointment>(`/appointments/${rescheduleTarget.id}/reschedule`, {
        method: 'POST',
        body: JSON.stringify({ slotId: rescheduleSlotId }),
      });
      setRescheduleTarget(null);
      await loadAppointments();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to reschedule appointment');
    } finally {
      setRescheduleSubmitting(false);
    }
  }

  return (
    <div style={{ padding: 24 }}>
      <PageHeader
        title="Appointments"
        description="Schedule, confirm and manage clinic appointments."
        actions={<Button onClick={openModal}>New appointment</Button>}
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
              <option value="scheduled">Scheduled</option>
              <option value="confirmed">Confirmed</option>
              <option value="arrived">Arrived</option>
              <option value="completed">Completed</option>
              <option value="cancelled">Cancelled</option>
              <option value="no-show">No-show</option>
            </Select>
            {loading ? <StatusIndicator tone="neutral" label="Loading&hellip;" /> : null}
          </div>

          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Date/Time</TableHead>
                <TableHead>Type</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Priority</TableHead>
                <TableHead>Reason</TableHead>
                <TableHead>Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {appointments.length === 0 ? (
                <TableRow>
                  <TableCell>No appointments found.</TableCell>
                </TableRow>
              ) : (
                appointments.map((appointment) => (
                  <TableRow key={appointment.id ?? 'row'}>
                    <TableCell>{formatDateTime(appointment.scheduledStart)}</TableCell>
                    <TableCell>{appointment.type ?? '-'}</TableCell>
                    <TableCell>
                      <Badge variant={statusVariant(appointment.status)}>{appointment.status ?? '-'}</Badge>
                    </TableCell>
                    <TableCell>
                      <Badge variant="information">{appointment.priority ?? '-'}</Badge>
                    </TableCell>
                    <TableCell>{appointment.reason || '-'}</TableCell>
                    <TableCell>
                      <div style={{ display: 'flex', gap: 8 }}>
                        {appointment.status === 'scheduled' ? (
                          <Button size="sm" variant="outline" onClick={() => void updateStatus(appointment, 'confirmed')}>
                            Confirm
                          </Button>
                        ) : null}
                        {appointment.status === 'confirmed' ? (
                          <Button size="sm" variant="outline" onClick={() => void updateStatus(appointment, 'arrived')}>
                            Arrive
                          </Button>
                        ) : null}
                        {appointment.status === 'arrived' ? (
                          <Button size="sm" variant="outline" onClick={() => void updateStatus(appointment, 'completed')}>
                            Complete
                          </Button>
                        ) : null}
                        {appointment.status === 'scheduled' || appointment.status === 'confirmed' ? (
                          <>
                            <Button size="sm" variant="outline" onClick={() => openReschedule(appointment)}>
                              Reschedule
                            </Button>
                            <Button size="sm" variant="ghost" onClick={() => void updateStatus(appointment, 'cancelled')}>
                              Cancel
                            </Button>
                          </>
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
        title="New appointment"
        description="Schedule a new outpatient appointment."
        footer={
          <>
            <Button variant="outline" onClick={closeModal}>
              Cancel
            </Button>
            <Button loading={submitting} onClick={() => void createAppointment()}>
              Create appointment
            </Button>
          </>
        }
      >
        {modalOpen ? (
          <Stack gap={12}>
            <PatientPicker apiFetch={apiFetch} value={selectedPatient} onSelect={setSelectedPatient} />
            <Input
              label="Doctor ID (optional)"
              placeholder="UUID of the doctor"
              value={form.doctorId}
              onChange={(e) => setForm({ ...form, doctorId: e.target.value })}
            />
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
              <Select
                label="Type"
                value={form.appointmentType}
                onChange={(e) => setForm({ ...form, appointmentType: e.target.value })}
              >
                <option value="consultation">Consultation</option>
                <option value="follow-up">Follow-up</option>
                <option value="review">Review</option>
                <option value="emergency">Emergency</option>
              </Select>
              <Select
                label="Duration"
                value={form.durationMinutes}
                onChange={(e) => setForm({ ...form, durationMinutes: e.target.value })}
              >
                <option value="15">15 minutes</option>
                <option value="30">30 minutes</option>
                <option value="45">45 minutes</option>
                <option value="60">60 minutes</option>
              </Select>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
              <Input
                type="date"
                label="Date"
                value={form.date}
                onChange={(e) => setForm({ ...form, date: e.target.value })}
              />
              <Input
                type="time"
                label="Time"
                value={form.time}
                onChange={(e) => setForm({ ...form, time: e.target.value })}
              />
            </div>
            {form.doctorId.trim() !== '' && form.date !== '' ? (
              slotsLoading ? (
                <p style={{ margin: 0, fontSize: 14, color: 'var(--sq-fg-muted)' }}>{t('appointments.slot.loading')}</p>
              ) : slots.length > 0 ? (
                <RadioGroup
                  aria-label={t('appointments.slot.label')}
                  orientation="horizontal"
                  value={selectedSlotId}
                  onValueChange={setSelectedSlotId}
                  style={{ flexWrap: 'wrap' }}
                >
                  {slots.map((slot) => (
                    <Radio
                      key={slot.id ?? slot.startTime}
                      value={slot.id ?? slot.startTime}
                      disabled={slot.status !== 'open' || slot.bookedCount >= slot.capacity}
                      label={
                        <>
                          {slot.startTime}–{slot.endTime}
                          {slot.sessionType === 'teleconsult' ? ' · Tele' : null}
                          {slot.bookedCount >= slot.capacity ? ` · ${t('appointments.slot.full')}` : null}
                        </>
                      }
                    />
                  ))}
                </RadioGroup>
              ) : (
                <p style={{ margin: 0, fontSize: 14, color: 'var(--sq-fg-muted)' }}>{t('appointments.slot.empty')}</p>
              )
            ) : null}
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
              placeholder="Reason for the appointment"
              value={form.reason}
              onChange={(e) => setForm({ ...form, reason: e.target.value })}
            />
          </Stack>
        ) : null}
      </Modal>

      <Modal
        open={rescheduleTarget !== null}
        onClose={() => setRescheduleTarget(null)}
        title="Reschedule appointment"
        description={
          rescheduleTarget
            ? `Pick a new session slot for ${formatDateTime(rescheduleTarget.scheduledStart)}. The old slot is released.`
            : ''
        }
        footer={
          <>
            <Button variant="outline" onClick={() => setRescheduleTarget(null)}>
              Close
            </Button>
            <Button
              loading={rescheduleSubmitting}
              disabled={rescheduleSlotId === ''}
              onClick={() => void rescheduleAppointment()}
            >
              Reschedule
            </Button>
          </>
        }
      >
        {rescheduleTarget ? (
          <Stack gap={12}>
            <Input
              label="Doctor ID"
              placeholder="UUID of the doctor"
              value={rescheduleForm.doctorId}
              onChange={(e) => setRescheduleForm({ ...rescheduleForm, doctorId: e.target.value })}
            />
            <Input
              type="date"
              label="New date"
              value={rescheduleForm.date}
              onChange={(e) => setRescheduleForm({ ...rescheduleForm, date: e.target.value })}
            />
            {rescheduleForm.doctorId.trim() !== '' && rescheduleForm.date !== '' ? (
              rescheduleSlotsLoading ? (
                <p style={{ margin: 0, fontSize: 14, color: 'var(--sq-fg-muted)' }}>{t('appointments.slot.loading')}</p>
              ) : rescheduleSlots.length > 0 ? (
                <RadioGroup
                  aria-label={t('appointments.slot.label')}
                  orientation="horizontal"
                  value={rescheduleSlotId}
                  onValueChange={setRescheduleSlotId}
                  style={{ flexWrap: 'wrap' }}
                >
                  {rescheduleSlots.map((slot) => (
                    <Radio
                      key={slot.id ?? slot.startTime}
                      value={slot.id ?? slot.startTime}
                      disabled={slot.status !== 'open' || slot.bookedCount >= slot.capacity}
                      label={
                        <>
                          {slot.startTime}–{slot.endTime}
                          {slot.sessionType === 'teleconsult' ? ' · Tele' : null}
                          {slot.bookedCount >= slot.capacity ? ` · ${t('appointments.slot.full')}` : null}
                        </>
                      }
                    />
                  ))}
                </RadioGroup>
              ) : (
                <p style={{ margin: 0, fontSize: 14, color: 'var(--sq-fg-muted)' }}>{t('appointments.slot.empty')}</p>
              )
            ) : null}
          </Stack>
        ) : null}
      </Modal>
    </div>
  );
}