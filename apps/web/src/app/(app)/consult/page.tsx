'use client';

import { useCallback, useState } from 'react';
import { useRouter } from 'next/navigation';
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
  Textarea,
} from '@squamed/ui';
import type { BadgeVariant, StatusTone } from '@squamed/ui';
import { useApi } from '../../../lib/use-api';

const TABS = ['Overview', 'Vitals', 'Note', 'Orders', 'Rx', 'Procedures', 'Referrals'];

const orderStatusVariants: Record<string, BadgeVariant> = {
  pending: 'warning',
  'in-progress': 'information',
  completed: 'success',
  cancelled: 'critical',
};

interface PatientSummary {
  id: string | null;
  mrn: string | null;
  firstName: string | null;
  lastName: string | null;
  dateOfBirth: string | null;
  gender: string | null;
  phone: string | null;
}

interface EncounterView {
  id: string | null;
  patientId: string;
  status: string;
  reason: string | null;
  chiefComplaint: string | null;
  startedAt: string | null;
  endedAt: string | null;
}

interface VitalSign {
  id: string | null;
  patientId: string;
  bloodPressureSystolic: number | null;
  bloodPressureDiastolic: number | null;
  heartRate: number | null;
  temperatureC: number | null;
  spo2Percent: number | null;
  weightKg: number | null;
  heightCm: number | null;
  bmi: number | null;
  recordedAt: string | null;
}

interface ClinicalNote {
  id: string | null;
  noteType: string;
  title: string | null;
  subjective: string | null;
  objective: string | null;
  assessment: string | null;
  plan: string | null;
  isSigned: boolean;
  signedAt: string | null;
  signatureMethod: string | null;
  createdAt: string | null;
}

interface Diagnosis {
  id: string | null;
  code: string;
  name: string;
  type: string;
  system: string;
}

interface OrderEntry {
  id: string | null;
  orderType: string;
  orderCode: string | null;
  orderName: string;
  status: string;
}

interface Prescription {
  id: string | null;
  drugName: string;
  dose: string | null;
  frequency: string | null;
  route: string | null;
  duration: string | null;
  quantity: number | null;
  status: string;
  overrideReason: string | null;
  allergyStatus: string | null;
}

interface ProcedureEntry {
  id: string | null;
  procedureCode: string | null;
  procedureName: string;
  status: string;
  performedAt: string | null;
  notes: string | null;
}

interface Referral {
  id: string | null;
  toSpecialty: string | null;
  targetFacility: string | null;
  reason: string;
  urgency: string;
  status: string;
}

interface NoteTemplate {
  id: string | null;
  specialty: string;
  name: string;
  subjectiveTemplate: string | null;
  objectiveTemplate: string | null;
  assessmentTemplate: string | null;
  planTemplate: string | null;
}

interface EncounterDetail {
  encounter: EncounterView;
  latestVitals: VitalSign | null;
  notes: ClinicalNote[];
  diagnoses: Diagnosis[];
  orders: OrderEntry[];
  prescriptions: Prescription[];
  procedures: ProcedureEntry[];
  referrals: Referral[];
}

interface Notice {
  tone: StatusTone;
  label: string;
}

function fullName(patient: PatientSummary): string {
  return [patient.firstName, patient.lastName].filter(Boolean).join(' ') || 'Unnamed patient';
}

function displayNumber(value: number | null | undefined): string {
  return value !== null && value !== undefined ? String(value) : '—';
}

function formatDateTime(value: string | null | undefined): string {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

export default function ConsultBoardPage() {
  const { apiFetch, isReady } = useApi();
  const router = useRouter();
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<PatientSummary[]>([]);
  const [searching, setSearching] = useState(false);
  const [selectedPatient, setSelectedPatient] = useState<PatientSummary | null>(null);
  const [encounterId, setEncounterId] = useState<string | null>(null);
  const [detail, setDetail] = useState<EncounterDetail | null>(null);
  const [vitalsHistory, setVitalsHistory] = useState<VitalSign[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [tab, setTab] = useState('Overview');

  const [vitalsOpen, setVitalsOpen] = useState(false);
  const [savingVitals, setSavingVitals] = useState(false);
  const [vitals, setVitals] = useState({
    heartRate: '',
    bloodPressureSystolic: '',
    bloodPressureDiastolic: '',
    temperatureC: '',
    spo2Percent: '',
    weightKg: '',
    heightCm: '',
  });

  const [soap, setSoap] = useState({ subjective: '', objective: '', assessment: '', plan: '' });
  const [savingNote, setSavingNote] = useState(false);

  const [orderOpen, setOrderOpen] = useState(false);
  const [savingOrder, setSavingOrder] = useState(false);
  const [orderForm, setOrderForm] = useState({ orderType: 'lab', orderCode: '', orderName: '' });

  const [rxOpen, setRxOpen] = useState(false);
  const [savingRx, setSavingRx] = useState(false);
  const [rxWarnRequired, setRxWarnRequired] = useState(false);
  const [rxForm, setRxForm] = useState({
    drugName: '',
    dose: '',
    frequency: '',
    route: 'oral',
    duration: '',
    quantity: '',
    overrideReason: '',
  });

  const [procedureOpen, setProcedureOpen] = useState(false);
  const [savingProcedure, setSavingProcedure] = useState(false);
  const [procedureForm, setProcedureForm] = useState({ procedureCode: '', procedureName: '', status: 'completed', notes: '' });

  const [referralOpen, setReferralOpen] = useState(false);
  const [savingReferral, setSavingReferral] = useState(false);
  const [referralForm, setReferralForm] = useState({ toSpecialty: '', targetFacility: '', reason: '', urgency: 'routine' });

  const [templates, setTemplates] = useState<NoteTemplate[]>([]);
  const [signOpen, setSignOpen] = useState(false);
  const [signing, setSigning] = useState(false);
  const [pendingNoteId, setPendingNoteId] = useState<string | null>(null);
  const [signPin, setSignPin] = useState('');

  const [completing, setCompleting] = useState(false);

  const loadTemplates = useCallback(async () => {
    try {
      const data = await apiFetch<NoteTemplate[]>('/encounters/note-templates');
      setTemplates(data ?? []);
    } catch {
      setTemplates([]);
    }
  }, [apiFetch]);

  const loadVitals = useCallback(
    async (patientId: string) => {
      try {
        const data = await apiFetch<VitalSign[]>(`/encounters/patients/${patientId}/vitals`);
        setVitalsHistory(data ?? []);
      } catch {
        setVitalsHistory([]);
      }
    },
    [apiFetch],
  );

  const loadDetail = useCallback(
    async (id: string) => {
      try {
        const data = await apiFetch<EncounterDetail>(`/encounters/${id}`);
        setDetail(data);
        setError(null);
        if (data?.encounter?.patientId) void loadVitals(data.encounter.patientId);
      } catch (e) {
        setError(e instanceof Error ? e.message : 'Failed to load encounter');
      }
    },
    [apiFetch, loadVitals],
  );

  async function searchPatients() {
    const term = query.trim();
    if (!term || !isReady) return;
    setSearching(true);
    setError(null);
    try {
      const data = await apiFetch<PatientSummary[]>(`/patients?query=${encodeURIComponent(term)}`);
      setResults(data ?? []);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to search patients');
      setResults([]);
    } finally {
      setSearching(false);
    }
  }

  async function startEncounter(patient: PatientSummary) {
    if (!patient.id) return;
    setError(null);
    setNotice(null);
    try {
      const encounter = await apiFetch<EncounterView>('/encounters', {
        method: 'POST',
        body: JSON.stringify({ patientId: patient.id }),
      });
      setSelectedPatient(patient);
      setEncounterId(encounter.id ?? null);
      setTab('Overview');
      if (encounter.id) await loadDetail(encounter.id);
      await loadTemplates();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to start encounter');
    }
  }

  async function completeEncounter() {
    if (!encounterId) return;
    setCompleting(true);
    setError(null);
    try {
      await apiFetch(`/encounters/${encounterId}/complete`, { method: 'PATCH' });
      setNotice({ tone: 'success', label: 'Encounter completed' });
      await loadDetail(encounterId);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to complete encounter');
    } finally {
      setCompleting(false);
    }
  }

  async function saveVitals() {
    if (!encounterId) return;
    setSavingVitals(true);
    setError(null);
    try {
      await apiFetch(`/encounters/${encounterId}/vitals`, {
        method: 'POST',
        body: JSON.stringify({
          heartRate: vitals.heartRate !== '' ? Number(vitals.heartRate) : undefined,
          bloodPressureSystolic: vitals.bloodPressureSystolic !== '' ? Number(vitals.bloodPressureSystolic) : undefined,
          bloodPressureDiastolic: vitals.bloodPressureDiastolic !== '' ? Number(vitals.bloodPressureDiastolic) : undefined,
          temperatureC: vitals.temperatureC !== '' ? Number(vitals.temperatureC) : undefined,
          spo2Percent: vitals.spo2Percent !== '' ? Number(vitals.spo2Percent) : undefined,
          weightKg: vitals.weightKg !== '' ? Number(vitals.weightKg) : undefined,
          heightCm: vitals.heightCm !== '' ? Number(vitals.heightCm) : undefined,
        }),
      });
      setVitalsOpen(false);
      setVitals({ heartRate: '', bloodPressureSystolic: '', bloodPressureDiastolic: '', temperatureC: '', spo2Percent: '', weightKg: '', heightCm: '' });
      setNotice({ tone: 'success', label: 'Vitals recorded' });
      await loadDetail(encounterId);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to record vitals');
    } finally {
      setSavingVitals(false);
    }
  }

  function applyTemplate(templateId: string) {
    const template = templates.find((t) => t.id === templateId);
    if (!template) return;
    setSoap({
      subjective: template.subjectiveTemplate ?? '',
      objective: template.objectiveTemplate ?? '',
      assessment: template.assessmentTemplate ?? '',
      plan: template.planTemplate ?? '',
    });
  }

  async function saveAndSignNote() {
    if (!encounterId) return;
    setSavingNote(true);
    setError(null);
    try {
      const note = await apiFetch<ClinicalNote>(`/encounters/${encounterId}/notes`, {
        method: 'POST',
        body: JSON.stringify({ noteType: 'consultation', ...soap }),
      });
      setSoap({ subjective: '', objective: '', assessment: '', plan: '' });
      await loadDetail(encounterId);
      if (note?.id) {
        setPendingNoteId(note.id);
        setSignPin('');
        setSignOpen(true);
      } else {
        setNotice({ tone: 'success', label: 'Note saved' });
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to save note');
    } finally {
      setSavingNote(false);
    }
  }

  async function confirmSign() {
    if (!pendingNoteId || !encounterId) return;
    setSigning(true);
    setError(null);
    try {
      await apiFetch(`/encounters/notes/${pendingNoteId}/sign`, {
        method: 'POST',
        body: JSON.stringify({ method: 'PIN', pin: signPin }),
      });
      setSignOpen(false);
      setPendingNoteId(null);
      setNotice({ tone: 'success', label: 'Note saved and signed' });
      await loadDetail(encounterId);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to sign note');
    } finally {
      setSigning(false);
    }
  }

  async function saveOrder() {
    if (!encounterId) return;
    setSavingOrder(true);
    setError(null);
    try {
      await apiFetch(`/encounters/${encounterId}/orders`, {
        method: 'POST',
        body: JSON.stringify({
          orderType: orderForm.orderType,
          orderCode: orderForm.orderCode !== '' ? orderForm.orderCode : undefined,
          orderName: orderForm.orderName,
          patientMrn: selectedPatient?.mrn ?? undefined,
        }),
      });
      setOrderOpen(false);
      setOrderForm({ orderType: 'lab', orderCode: '', orderName: '' });
      setNotice({ tone: 'success', label: 'Order placed' });
      await loadDetail(encounterId);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to place order');
    } finally {
      setSavingOrder(false);
    }
  }

  async function savePrescription() {
    if (!encounterId) return;
    setSavingRx(true);
    setError(null);
    try {
      await apiFetch(`/encounters/${encounterId}/prescriptions`, {
        method: 'POST',
        body: JSON.stringify({
          drugName: rxForm.drugName,
          dose: rxForm.dose !== '' ? rxForm.dose : undefined,
          frequency: rxForm.frequency !== '' ? rxForm.frequency : undefined,
          route: rxForm.route,
          duration: rxForm.duration !== '' ? rxForm.duration : undefined,
          quantity: rxForm.quantity !== '' ? Number(rxForm.quantity) : undefined,
          patientMrn: selectedPatient?.mrn ?? undefined,
          overrideReason: rxForm.overrideReason !== '' ? rxForm.overrideReason : undefined,
        }),
      });
      setRxOpen(false);
      setRxWarnRequired(false);
      setRxForm({ drugName: '', dose: '', frequency: '', route: 'oral', duration: '', quantity: '', overrideReason: '' });
      setNotice({ tone: 'success', label: 'Prescription saved' });
      await loadDetail(encounterId);
    } catch (e) {
      if (e instanceof Error && e.message.includes('ALLERGY_WARN_REASON_REQUIRED')) {
        // CS-03: allergy warn — keep the modal open and require a typed override reason.
        setRxWarnRequired(true);
        setNotice(null);
        setError('Allergy interaction detected — enter an override reason to proceed.');
      } else if (e instanceof Error && e.message.includes('ALLERGY_BLOCK')) {
        setNotice(null);
        setError('Blocked: the patient has a documented allergy to this drug.');
      } else if (e instanceof Error && e.message.includes('PATIENT_MISMATCH')) {
        setNotice(null);
        setError('Patient identity confirm failed — check the MRN.');
      } else {
        setError(e instanceof Error ? e.message : 'Failed to save prescription');
      }
    } finally {
      setSavingRx(false);
    }
  }

  const encounterActive = Boolean(encounterId) && detail?.encounter.status !== 'completed';

  async function saveProcedure() {
    if (!encounterId) return;
    setSavingProcedure(true);
    setError(null);
    try {
      await apiFetch(`/encounters/${encounterId}/procedures`, {
        method: 'POST',
        body: JSON.stringify({
          procedureCode: procedureForm.procedureCode !== '' ? procedureForm.procedureCode : undefined,
          procedureName: procedureForm.procedureName,
          status: procedureForm.status,
          notes: procedureForm.notes !== '' ? procedureForm.notes : undefined,
        }),
      });
      setProcedureOpen(false);
      setProcedureForm({ procedureCode: '', procedureName: '', status: 'completed', notes: '' });
      setNotice({ tone: 'success', label: 'Procedure recorded' });
      await loadDetail(encounterId);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to record procedure');
    } finally {
      setSavingProcedure(false);
    }
  }

  async function saveReferral() {
    if (!encounterId) return;
    setSavingReferral(true);
    setError(null);
    try {
      await apiFetch(`/encounters/${encounterId}/referrals`, {
        method: 'POST',
        body: JSON.stringify({
          toSpecialty: referralForm.toSpecialty !== '' ? referralForm.toSpecialty : undefined,
          targetFacility: referralForm.targetFacility !== '' ? referralForm.targetFacility : undefined,
          reason: referralForm.reason,
          urgency: referralForm.urgency,
        }),
      });
      setReferralOpen(false);
      setReferralForm({ toSpecialty: '', targetFacility: '', reason: '', urgency: 'routine' });
      setNotice({ tone: 'success', label: 'Referral created' });
      await loadDetail(encounterId);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to create referral');
    } finally {
      setSavingReferral(false);
    }
  }

  return (
    <div style={{ padding: 24 }}>
      <PageHeader
        title="Consultation"
        description="Find a patient and run an outpatient consultation."
        actions={
          encounterActive ? (
            <>
              <Button variant="outline" onClick={() => router.push('/appointments')}>
                Book follow-up
              </Button>
              <Button variant="outline" loading={completing} onClick={() => void completeEncounter()}>
                Complete encounter
              </Button>
            </>
          ) : null
        }
      />

      {notice ? <StatusIndicator tone={notice.tone} label={notice.label} /> : null}
      {error ? <StatusIndicator tone="critical" label={error} /> : null}

      <div style={{ height: 16 }} />

      <Stack direction="row" gap={8} align="flex-end" wrap>
        <div style={{ width: 360 }}>
          <Input
            label="Find patient"
            placeholder="Name, MRN, phone..."
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') void searchPatients();
            }}
          />
        </div>
        <Button loading={searching} onClick={() => void searchPatients()}>
          Search
        </Button>
      </Stack>

      {results.length > 0 ? (
        <>
          <div style={{ height: 12 }} />
          <ContentPanel>
            <Stack gap={4}>
              {results.map((patient) => (
                <button
                  key={patient.id ?? `${patient.mrn ?? ''}-${patient.firstName ?? ''}-${patient.lastName ?? ''}`}
                  type="button"
                  onClick={() => void startEncounter(patient)}
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    gap: 12,
                    width: '100%',
                    padding: '8px 10px',
                    border: 'none',
                    borderBottom: '1px solid var(--sq-border)',
                    background: 'transparent',
                    color: 'var(--sq-fg)',
                    textAlign: 'left',
                    fontFamily: 'inherit',
                    fontSize: 14,
                    cursor: 'pointer',
                  }}
                >
                  <span>
                    <strong>{fullName(patient)}</strong>
                    <span style={{ marginLeft: 8, fontSize: 12, color: 'var(--sq-fg-muted)' }}>MRN {patient.mrn ?? '—'}</span>
                  </span>
                  <span style={{ fontSize: 12, color: 'var(--sq-fg-muted)' }}>
                    {patient.gender ?? ''}
                    {patient.dateOfBirth ? ` · ${patient.dateOfBirth}` : ''}
                  </span>
                </button>
              ))}
            </Stack>
          </ContentPanel>
        </>
      ) : null}

      {selectedPatient ? (
        <>
          <div style={{ height: 16 }} />
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 14,
              padding: '10px 16px',
              background: 'var(--sq-surface)',
              border: '1px solid var(--sq-border)',
              borderLeft: '4px solid var(--sq-primary)',
              borderRadius: 8,
              flexWrap: 'wrap',
            }}
          >
            <strong style={{ fontSize: 15 }}>{fullName(selectedPatient)}</strong>
            <span style={{ fontSize: 12, color: 'var(--sq-fg-muted)' }}>MRN {selectedPatient.mrn ?? '—'}</span>
            {detail ? (
              <Badge variant={detail.encounter.status === 'completed' ? 'success' : 'information'}>
                {detail.encounter.status}
              </Badge>
            ) : null}
            {detail?.encounter.chiefComplaint ? (
              <span style={{ fontSize: 13, color: 'var(--sq-fg-muted)' }}>
                Chief complaint: {detail.encounter.chiefComplaint}
              </span>
            ) : null}
          </div>
        </>
      ) : null}

      {detail ? (
        <>
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

          {tab === 'Overview' ? (
            <Stack gap={12}>
              <ContentPanel>
                <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--sq-fg-muted)', marginBottom: 8 }}>
                  LATEST VITALS
                </div>
                {detail.latestVitals ? (
                  <Stack direction="row" gap={16} wrap>
                    <span>BP {displayNumber(detail.latestVitals.bloodPressureSystolic)}/{displayNumber(detail.latestVitals.bloodPressureDiastolic)}</span>
                    <span>HR {displayNumber(detail.latestVitals.heartRate)} bpm</span>
                    <span>Temp {displayNumber(detail.latestVitals.temperatureC)} C</span>
                    <span>SpO2 {displayNumber(detail.latestVitals.spo2Percent)}%</span>
                    <span>BMI {displayNumber(detail.latestVitals.bmi)}</span>
                  </Stack>
                ) : (
                  <span style={{ color: 'var(--sq-fg-muted)' }}>No vitals recorded yet.</span>
                )}
              </ContentPanel>
              <ContentPanel>
                <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--sq-fg-muted)', marginBottom: 8 }}>
                  DIAGNOSES
                </div>
                {detail.diagnoses.length === 0 ? (
                  <span style={{ color: 'var(--sq-fg-muted)' }}>No diagnoses recorded.</span>
                ) : (
                  <Stack gap={8}>
                    {detail.diagnoses.map((diagnosis) => (
                      <Badge key={diagnosis.id ?? `${diagnosis.code}-${diagnosis.name}`} variant="neutral">
                        {diagnosis.code} · {diagnosis.name} ({diagnosis.type})
                      </Badge>
                    ))}
                  </Stack>
                )}
              </ContentPanel>
              <ContentPanel>
                <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--sq-fg-muted)', marginBottom: 8 }}>
                  PRESCRIPTIONS
                </div>
                {detail.prescriptions.length === 0 ? (
                  <span style={{ color: 'var(--sq-fg-muted)' }}>No prescriptions.</span>
                ) : (
                  <Stack gap={8}>
                    {detail.prescriptions.map((rx) => (
                      <div key={rx.id ?? rx.drugName}>
                        <span style={{ fontWeight: 600 }}>{rx.drugName}</span>
                        <span style={{ color: 'var(--sq-fg-muted)' }}>
                          {' '}— {[rx.dose, rx.frequency, rx.route].filter(Boolean).join(' · ')}
                        </span>
                      </div>
                    ))}
                  </Stack>
                )}
              </ContentPanel>
            </Stack>
          ) : null}

          {tab === 'Vitals' ? (
            <Stack gap={12}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <strong>Vitals history</strong>
                <Button size="sm" onClick={() => setVitalsOpen(true)}>
                  Record vitals
                </Button>
              </div>
              <ContentPanel>
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Recorded</TableHead>
                      <TableHead>BP (mmHg)</TableHead>
                      <TableHead>HR (bpm)</TableHead>
                      <TableHead>Temp (C)</TableHead>
                      <TableHead>SpO2 (%)</TableHead>
                      <TableHead>Weight (kg)</TableHead>
                      <TableHead>Height (cm)</TableHead>
                      <TableHead>BMI</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {vitalsHistory.length === 0 ? (
                      <TableRow>
                        <TableCell>No vitals recorded yet.</TableCell>
                      </TableRow>
                    ) : (
                      vitalsHistory.map((vital) => (
                        <TableRow key={vital.id ?? formatDateTime(vital.recordedAt)}>
                          <TableCell>{formatDateTime(vital.recordedAt)}</TableCell>
                          <TableCell>
                            {displayNumber(vital.bloodPressureSystolic)}/{displayNumber(vital.bloodPressureDiastolic)}
                          </TableCell>
                          <TableCell>{displayNumber(vital.heartRate)}</TableCell>
                          <TableCell>{displayNumber(vital.temperatureC)}</TableCell>
                          <TableCell>{displayNumber(vital.spo2Percent)}</TableCell>
                          <TableCell>{displayNumber(vital.weightKg)}</TableCell>
                          <TableCell>{displayNumber(vital.heightCm)}</TableCell>
                          <TableCell>{displayNumber(vital.bmi)}</TableCell>
                        </TableRow>
                      ))
                    )}
                  </TableBody>
                </Table>
              </ContentPanel>
            </Stack>
          ) : null}

          {tab === 'Note' ? (
            <Stack gap={12}>
              <ContentPanel>
                <Stack gap={12}>
                  <Select
                    label="Specialty template"
                    value=""
                    onChange={(e) => {
                      if (e.target.value) applyTemplate(e.target.value);
                    }}
                  >
                    <option value="">Blank note</option>
                    {templates.map((template) => (
                      <option key={template.id ?? template.name} value={template.id ?? ''}>
                        {template.specialty} — {template.name}
                      </option>
                    ))}
                  </Select>
                  <Textarea
                    label="Subjective"
                    placeholder="Symptoms and history"
                    value={soap.subjective}
                    onChange={(e) => setSoap({ ...soap, subjective: e.target.value })}
                  />
                  <Textarea
                    label="Objective"
                    placeholder="Examination findings"
                    value={soap.objective}
                    onChange={(e) => setSoap({ ...soap, objective: e.target.value })}
                  />
                  <Textarea
                    label="Assessment"
                    placeholder="Assessment and diagnosis"
                    value={soap.assessment}
                    onChange={(e) => setSoap({ ...soap, assessment: e.target.value })}
                  />
                  <Textarea
                    label="Plan"
                    placeholder="Treatment plan"
                    value={soap.plan}
                    onChange={(e) => setSoap({ ...soap, plan: e.target.value })}
                  />
                  <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
                    <Button loading={savingNote} onClick={() => void saveAndSignNote()}>
                      Save &amp; sign
                    </Button>
                  </div>
                </Stack>
              </ContentPanel>
              <ContentPanel>
                <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--sq-fg-muted)', marginBottom: 8 }}>
                  NOTES
                </div>
                {detail.notes.length === 0 ? (
                  <span style={{ color: 'var(--sq-fg-muted)' }}>No notes yet.</span>
                ) : (
                  <Stack gap={10}>
                    {detail.notes.map((note) => (
                      <div key={note.id ?? formatDateTime(note.createdAt)}>
                        <Badge variant={note.isSigned ? 'success' : 'neutral'} size="sm">
                          {note.isSigned ? `Signed${note.signatureMethod ? ` · ${note.signatureMethod}` : ''}` : 'Draft'}
                        </Badge>
                        <div style={{ marginTop: 4 }}>
                          {[note.subjective, note.objective, note.assessment, note.plan].filter(Boolean).join(' ')}
                        </div>
                      </div>
                    ))}
                  </Stack>
                )}
              </ContentPanel>
            </Stack>
          ) : null}

          {tab === 'Orders' ? (
            <Stack gap={12}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <strong>Orders</strong>
                <Button size="sm" onClick={() => setOrderOpen(true)}>
                  New order
                </Button>
              </div>
              <ContentPanel>
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Type</TableHead>
                      <TableHead>Name</TableHead>
                      <TableHead>Code</TableHead>
                      <TableHead>Status</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {detail.orders.length === 0 ? (
                      <TableRow>
                        <TableCell>No orders yet.</TableCell>
                      </TableRow>
                    ) : (
                      detail.orders.map((order) => (
                        <TableRow key={order.id ?? `${order.orderType}-${order.orderName}`}>
                          <TableCell>{order.orderType}</TableCell>
                          <TableCell>{order.orderName}</TableCell>
                          <TableCell>{order.orderCode ?? '—'}</TableCell>
                          <TableCell>
                            <Badge variant={orderStatusVariants[order.status] ?? 'neutral'} size="sm">
                              {order.status}
                            </Badge>
                          </TableCell>
                        </TableRow>
                      ))
                    )}
                  </TableBody>
                </Table>
              </ContentPanel>
            </Stack>
          ) : null}

          {tab === 'Rx' ? (
            <Stack gap={12}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <strong>Prescriptions</strong>
                <Button size="sm" onClick={() => setRxOpen(true)}>
                  Prescribe
                </Button>
              </div>
              <ContentPanel>
                {detail.prescriptions.length === 0 ? (
                  <span style={{ color: 'var(--sq-fg-muted)' }}>No prescriptions yet.</span>
                ) : (
                  <Stack gap={10}>
                    {detail.prescriptions.map((rx) => (
                      <div
                        key={rx.id ?? `${rx.drugName}-${rx.dose ?? ''}`}
                        style={{
                          display: 'flex',
                          justifyContent: 'space-between',
                          alignItems: 'center',
                          gap: 12,
                          padding: '8px 0',
                          borderBottom: '1px solid var(--sq-border)',
                        }}
                      >
                        <div>
                          <strong>{rx.drugName}</strong>
                          <span style={{ color: 'var(--sq-fg-muted)' }}>
                            {' '}— {[rx.dose, rx.frequency, rx.route, rx.duration].filter(Boolean).join(' · ')}
                            {rx.quantity !== null && rx.quantity !== undefined ? ` · Qty ${rx.quantity}` : ''}
                          </span>
                        </div>
                        <Badge variant={rx.allergyStatus === 'warn_override' ? 'warning' : 'success'} size="sm">
                          {rx.allergyStatus === 'warn_override' ? 'allergy override' : rx.status}
                        </Badge>
                      </div>
                    ))}
                  </Stack>
                )}
              </ContentPanel>
            </Stack>
          ) : null}

          {tab === 'Procedures' ? (
            <Stack gap={12}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <strong>Procedures</strong>
                <Button size="sm" onClick={() => setProcedureOpen(true)}>
                  Record procedure
                </Button>
              </div>
              <ContentPanel>
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Name</TableHead>
                      <TableHead>Code</TableHead>
                      <TableHead>Status</TableHead>
                      <TableHead>Performed</TableHead>
                      <TableHead>Notes</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {detail.procedures.length === 0 ? (
                      <TableRow>
                        <TableCell>No procedures recorded.</TableCell>
                      </TableRow>
                    ) : (
                      detail.procedures.map((procedure) => (
                        <TableRow key={procedure.id ?? `${procedure.procedureName}-${procedure.performedAt ?? ''}`}>
                          <TableCell>{procedure.procedureName}</TableCell>
                          <TableCell>{procedure.procedureCode ?? '—'}</TableCell>
                          <TableCell>
                            <Badge variant={procedure.status === 'completed' ? 'success' : 'warning'} size="sm">
                              {procedure.status}
                            </Badge>
                          </TableCell>
                          <TableCell>{formatDateTime(procedure.performedAt)}</TableCell>
                          <TableCell>{procedure.notes ?? '—'}</TableCell>
                        </TableRow>
                      ))
                    )}
                  </TableBody>
                </Table>
              </ContentPanel>
            </Stack>
          ) : null}

          {tab === 'Referrals' ? (
            <Stack gap={12}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <strong>Referrals</strong>
                <Button size="sm" onClick={() => setReferralOpen(true)}>
                  New referral
                </Button>
              </div>
              <ContentPanel>
                {detail.referrals.length === 0 ? (
                  <span style={{ color: 'var(--sq-fg-muted)' }}>No referrals yet.</span>
                ) : (
                  <Stack gap={10}>
                    {detail.referrals.map((referral) => (
                      <div
                        key={referral.id ?? `${referral.toSpecialty ?? ''}-${referral.reason}`}
                        style={{ padding: '8px 0', borderBottom: '1px solid var(--sq-border)' }}
                      >
                        <div>
                          <strong>{referral.toSpecialty ?? referral.targetFacility ?? 'Referral'}</strong>
                          <span style={{ color: 'var(--sq-fg-muted)' }}> — {referral.reason}</span>
                        </div>
                        <div style={{ marginTop: 4 }}>
                          <Badge variant={referral.urgency === 'urgent' ? 'warning' : 'neutral'} size="sm">
                            {referral.urgency}
                          </Badge>{' '}
                          <Badge variant="information" size="sm">
                            {referral.status}
                          </Badge>
                        </div>
                      </div>
                    ))}
                  </Stack>
                )}
              </ContentPanel>
            </Stack>
          ) : null}
        </>
      ) : null}

      <Modal
        open={vitalsOpen}
        onClose={() => setVitalsOpen(false)}
        title="Record vitals"
        description={selectedPatient ? `${fullName(selectedPatient)} — MRN ${selectedPatient.mrn ?? '—'}` : undefined}
        footer={
          <>
            <Button variant="outline" onClick={() => setVitalsOpen(false)}>
              Cancel
            </Button>
            <Button loading={savingVitals} onClick={() => void saveVitals()}>
              Save
            </Button>
          </>
        }
      >
        <Stack gap={12}>
          <Input
            label="Heart rate (bpm)"
            type="number"
            value={vitals.heartRate}
            onChange={(e) => setVitals({ ...vitals, heartRate: e.target.value })}
          />
          <Stack direction="row" gap={12}>
            <Input
              label="BP systolic"
              type="number"
              value={vitals.bloodPressureSystolic}
              onChange={(e) => setVitals({ ...vitals, bloodPressureSystolic: e.target.value })}
            />
            <Input
              label="BP diastolic"
              type="number"
              value={vitals.bloodPressureDiastolic}
              onChange={(e) => setVitals({ ...vitals, bloodPressureDiastolic: e.target.value })}
            />
          </Stack>
          <Stack direction="row" gap={12}>
            <Input
              label="Temperature (C)"
              type="number"
              step="0.1"
              value={vitals.temperatureC}
              onChange={(e) => setVitals({ ...vitals, temperatureC: e.target.value })}
            />
            <Input
              label="SpO2 (%)"
              type="number"
              value={vitals.spo2Percent}
              onChange={(e) => setVitals({ ...vitals, spo2Percent: e.target.value })}
            />
          </Stack>
          <Stack direction="row" gap={12}>
            <Input
              label="Weight (kg)"
              type="number"
              step="0.1"
              value={vitals.weightKg}
              onChange={(e) => setVitals({ ...vitals, weightKg: e.target.value })}
            />
            <Input
              label="Height (cm)"
              type="number"
              step="0.1"
              value={vitals.heightCm}
              onChange={(e) => setVitals({ ...vitals, heightCm: e.target.value })}
            />
          </Stack>
        </Stack>
      </Modal>

      <Modal
        open={orderOpen}
        onClose={() => setOrderOpen(false)}
        title="New order"
        description={selectedPatient ? `${fullName(selectedPatient)} — MRN ${selectedPatient.mrn ?? '—'}` : undefined}
        footer={
          <>
            <Button variant="outline" onClick={() => setOrderOpen(false)}>
              Cancel
            </Button>
            <Button loading={savingOrder} onClick={() => void saveOrder()}>
              Place order
            </Button>
          </>
        }
      >
        <Stack gap={12}>
          <Select
            label="Order type"
            value={orderForm.orderType}
            onChange={(e) => setOrderForm({ ...orderForm, orderType: e.target.value })}
          >
            {['lab', 'imaging', 'procedure', 'medication', 'referral'].map((type) => (
              <option key={type} value={type}>
                {type}
              </option>
            ))}
          </Select>
          <Input
            label="Order code"
            placeholder="Optional order code"
            value={orderForm.orderCode}
            onChange={(e) => setOrderForm({ ...orderForm, orderCode: e.target.value })}
          />
          <Input
            label="Order name"
            placeholder="Order description"
            value={orderForm.orderName}
            onChange={(e) => setOrderForm({ ...orderForm, orderName: e.target.value })}
          />
        </Stack>
      </Modal>

      <Modal
        open={rxOpen}
        onClose={() => setRxOpen(false)}
        title="Prescribe"
        description={selectedPatient ? `${fullName(selectedPatient)} — MRN ${selectedPatient.mrn ?? '—'}` : undefined}
        footer={
          <>
            <Button variant="outline" onClick={() => setRxOpen(false)}>
              Cancel
            </Button>
            <Button loading={savingRx} onClick={() => void savePrescription()}>
              Prescribe
            </Button>
          </>
        }
      >
        <Stack gap={12}>
          <Input
            label="Drug name"
            value={rxForm.drugName}
            onChange={(e) => setRxForm({ ...rxForm, drugName: e.target.value })}
          />
          <Stack direction="row" gap={12}>
            <Input
              label="Dose"
              placeholder="e.g. 500 mg"
              value={rxForm.dose}
              onChange={(e) => setRxForm({ ...rxForm, dose: e.target.value })}
            />
            <Input
              label="Frequency"
              placeholder="e.g. twice daily"
              value={rxForm.frequency}
              onChange={(e) => setRxForm({ ...rxForm, frequency: e.target.value })}
            />
          </Stack>
          <Stack direction="row" gap={12}>
            <Select
              label="Route"
              value={rxForm.route}
              onChange={(e) => setRxForm({ ...rxForm, route: e.target.value })}
            >
              {['oral', 'IV', 'IM', 'topical'].map((route) => (
                <option key={route} value={route}>
                  {route}
                </option>
              ))}
            </Select>
            <Input
              label="Duration"
              placeholder="e.g. 7 days"
              value={rxForm.duration}
              onChange={(e) => setRxForm({ ...rxForm, duration: e.target.value })}
            />
          </Stack>
          <Input
            label="Quantity"
            type="number"
            value={rxForm.quantity}
            onChange={(e) => setRxForm({ ...rxForm, quantity: e.target.value })}
          />
          {rxWarnRequired ? (
            <Input
              label="Allergy override reason (required)"
              placeholder="Why is this drug safe despite the allergy?"
              value={rxForm.overrideReason}
              onChange={(e) => setRxForm({ ...rxForm, overrideReason: e.target.value })}
            />
          ) : null}
        </Stack>
      </Modal>

      <Modal
        open={procedureOpen}
        onClose={() => setProcedureOpen(false)}
        title="Record procedure"
        description={selectedPatient ? `${fullName(selectedPatient)} — MRN ${selectedPatient.mrn ?? '—'}` : undefined}
        footer={
          <>
            <Button variant="outline" onClick={() => setProcedureOpen(false)}>
              Cancel
            </Button>
            <Button loading={savingProcedure} onClick={() => void saveProcedure()}>
              Record
            </Button>
          </>
        }
      >
        <Stack gap={12}>
          <Input
            label="Procedure name"
            value={procedureForm.procedureName}
            onChange={(e) => setProcedureForm({ ...procedureForm, procedureName: e.target.value })}
          />
          <Stack direction="row" gap={12}>
            <Input
              label="Code"
              placeholder="Optional code"
              value={procedureForm.procedureCode}
              onChange={(e) => setProcedureForm({ ...procedureForm, procedureCode: e.target.value })}
            />
            <Select
              label="Status"
              value={procedureForm.status}
              onChange={(e) => setProcedureForm({ ...procedureForm, status: e.target.value })}
            >
              {['planned', 'completed', 'cancelled'].map((status) => (
                <option key={status} value={status}>
                  {status}
                </option>
              ))}
            </Select>
          </Stack>
          <Textarea
            label="Notes"
            value={procedureForm.notes}
            onChange={(e) => setProcedureForm({ ...procedureForm, notes: e.target.value })}
          />
        </Stack>
      </Modal>

      <Modal
        open={referralOpen}
        onClose={() => setReferralOpen(false)}
        title="New referral"
        description={selectedPatient ? `${fullName(selectedPatient)} — MRN ${selectedPatient.mrn ?? '—'}` : undefined}
        footer={
          <>
            <Button variant="outline" onClick={() => setReferralOpen(false)}>
              Cancel
            </Button>
            <Button loading={savingReferral} onClick={() => void saveReferral()}>
              Create referral
            </Button>
          </>
        }
      >
        <Stack gap={12}>
          <Stack direction="row" gap={12}>
            <Input
              label="To specialty"
              placeholder="e.g. Cardiology"
              value={referralForm.toSpecialty}
              onChange={(e) => setReferralForm({ ...referralForm, toSpecialty: e.target.value })}
            />
            <Select
              label="Urgency"
              value={referralForm.urgency}
              onChange={(e) => setReferralForm({ ...referralForm, urgency: e.target.value })}
            >
              {['routine', 'urgent', 'emergency'].map((urgency) => (
                <option key={urgency} value={urgency}>
                  {urgency}
                </option>
              ))}
            </Select>
          </Stack>
          <Input
            label="Target facility"
            placeholder="Optional external facility"
            value={referralForm.targetFacility}
            onChange={(e) => setReferralForm({ ...referralForm, targetFacility: e.target.value })}
          />
          <Textarea
            label="Reason"
            value={referralForm.reason}
            onChange={(e) => setReferralForm({ ...referralForm, reason: e.target.value })}
          />
        </Stack>
      </Modal>

      <Modal
        open={signOpen}
        onClose={() => setSignOpen(false)}
        title="Sign note"
        description="Enter your signing PIN. The note becomes immutable once signed."
        footer={
          <>
            <Button variant="outline" onClick={() => setSignOpen(false)}>
              Sign later
            </Button>
            <Button loading={signing} onClick={() => void confirmSign()}>
              Sign note
            </Button>
          </>
        }
      >
        <Input
          label="Signing PIN"
          type="password"
          value={signPin}
          onChange={(e) => setSignPin(e.target.value)}
        />
      </Modal>
    </div>
  );
}