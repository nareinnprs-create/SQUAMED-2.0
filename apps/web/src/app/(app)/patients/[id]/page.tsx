'use client';

import { useCallback, useEffect, useState } from 'react';
import { useParams } from 'next/navigation';
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
  Textarea,
} from '@squamed/ui';
import type { BadgeVariant } from '@squamed/ui';
import { useApi } from '../../../../lib/use-api';

interface Allergy {
  id: string;
  allergen: string;
  reaction?: string | null;
  severity: string;
}

interface PatientNote {
  id: string;
  title: string;
  body?: string | null;
  createdAt?: string;
}

interface MedicalHistoryEntry {
  id: string;
  conditionName: string;
  diagnosisCode?: string | null;
  onsetDate?: string | null;
}

interface PatientDetail {
  id: string;
  mrn: string;
  firstName: string;
  lastName: string;
  dateOfBirth: string;
  gender: string;
  phone?: string | null;
  qid?: string | null;
  email?: string | null;
  nationality?: string | null;
  city?: string | null;
  allergies: Allergy[];
  notes: PatientNote[];
  medicalHistory: MedicalHistoryEntry[];
}

const allergyVariants: Record<string, BadgeVariant> = {
  mild: 'information',
  moderate: 'warning',
  severe: 'critical',
  life_threatening: 'critical',
};

const emptyAllergyForm = { allergen: '', reaction: '', severity: 'mild' };
const emptyHistoryForm = { conditionName: '', diagnosisCode: '', onsetDate: '' };
const emptyNoteForm = { title: '', body: '' };

function Field({ label, value }: { label: string; value?: string | null }) {
  if (!value) return null;
  return (
    <div style={{ flex: '1 1 45%', minWidth: 220 }}>
      <div
        style={{
          fontSize: 12,
          fontWeight: 600,
          color: 'var(--sq-fg-muted)',
          textTransform: 'uppercase',
          letterSpacing: '0.04em',
          marginBottom: 4,
        }}
      >
        {label}
      </div>
      <div style={{ fontSize: 14 }}>{value}</div>
    </div>
  );
}

export default function PatientDetailPage() {
  const params = useParams<{ id: string }>();
  const id = params.id;
  const { apiFetch, isReady } = useApi();
  const [patient, setPatient] = useState<PatientDetail | null>(null);
  const [allergyOpen, setAllergyOpen] = useState(false);
  const [historyOpen, setHistoryOpen] = useState(false);
  const [noteOpen, setNoteOpen] = useState(false);
  const [allergyForm, setAllergyForm] = useState(emptyAllergyForm);
  const [historyForm, setHistoryForm] = useState(emptyHistoryForm);
  const [noteForm, setNoteForm] = useState(emptyNoteForm);
  const [savingAllergy, setSavingAllergy] = useState(false);
  const [savingHistory, setSavingHistory] = useState(false);
  const [savingNote, setSavingNote] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadPatient = useCallback(async () => {
    try {
      const data = await apiFetch<PatientDetail>(`/patients/${id}`);
      setPatient(data);
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load patient');
    }
  }, [apiFetch, id]);

  useEffect(() => {
    if (isReady) void loadPatient();
  }, [isReady, loadPatient]);

  async function addAllergy() {
    setSavingAllergy(true);
    try {
      await apiFetch<unknown>(`/patients/${id}/allergies`, {
        method: 'POST',
        body: JSON.stringify(allergyForm),
      });
      setAllergyOpen(false);
      setAllergyForm(emptyAllergyForm);
      await loadPatient();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to add allergy');
    } finally {
      setSavingAllergy(false);
    }
  }

  async function addHistory() {
    setSavingHistory(true);
    try {
      await apiFetch<unknown>(`/patients/${id}/history`, {
        method: 'POST',
        body: JSON.stringify({
          conditionName: historyForm.conditionName,
          diagnosisCode: historyForm.diagnosisCode || undefined,
          onsetDate: historyForm.onsetDate || undefined,
        }),
      });
      setHistoryOpen(false);
      setHistoryForm(emptyHistoryForm);
      await loadPatient();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to add medical history');
    } finally {
      setSavingHistory(false);
    }
  }

  async function addNote() {
    setSavingNote(true);
    try {
      await apiFetch<unknown>(`/patients/${id}/notes`, {
        method: 'POST',
        body: JSON.stringify(noteForm),
      });
      setNoteOpen(false);
      setNoteForm(emptyNoteForm);
      await loadPatient();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to add note');
    } finally {
      setSavingNote(false);
    }
  }

  const fullName = patient ? `${patient.firstName} ${patient.lastName}` : 'Patient';

  return (
    <div style={{ padding: 24 }}>
      <PageHeader
        title={
          <Stack direction="row" gap={8} align="center" wrap>
            <span>{fullName}</span>
            {patient ? <Badge variant="neutral">{patient.mrn}</Badge> : null}
          </Stack>
        }
        description="Patient demographics and clinical records."
      />

      {error ? <StatusIndicator tone="critical" label={error} /> : null}

      <Stack gap={16}>
        <ContentPanel>
          <strong style={{ fontSize: 14 }}>Demographics</strong>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '12px 24px', marginTop: 12 }}>
            <Field label="Name" value={fullName} />
            <Field label="Date of birth" value={patient?.dateOfBirth} />
            <Field label="Gender" value={patient?.gender} />
            <Field label="QID" value={patient?.qid} />
            <Field label="Phone" value={patient?.phone} />
            <Field label="Email" value={patient?.email} />
            <Field label="Nationality" value={patient?.nationality} />
            <Field label="City" value={patient?.city} />
          </div>
        </ContentPanel>

        <ContentPanel>
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              marginBottom: 12,
            }}
          >
            <strong style={{ fontSize: 14 }}>Allergies</strong>
            <Button size="sm" onClick={() => setAllergyOpen(true)}>
              Add allergy
            </Button>
          </div>
          {!patient || patient.allergies.length === 0 ? (
            <div style={{ fontSize: 13, color: 'var(--sq-fg-muted)' }}>No allergies recorded.</div>
          ) : (
            <Stack gap={8}>
              {patient.allergies.map((allergy) => (
                <Stack
                  key={allergy.id}
                  direction="row"
                  gap={8}
                  align="center"
                  wrap
                  style={{ paddingBottom: 8, borderBottom: '1px solid var(--sq-border)' }}
                >
                  <Badge variant={allergyVariants[allergy.severity] ?? 'neutral'}>{allergy.severity}</Badge>
                  <span style={{ fontSize: 14, fontWeight: 600 }}>{allergy.allergen}</span>
                  {allergy.reaction ? (
                    <span style={{ fontSize: 13, color: 'var(--sq-fg-muted)' }}>{allergy.reaction}</span>
                  ) : null}
                </Stack>
              ))}
            </Stack>
          )}
        </ContentPanel>

        <ContentPanel>
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              marginBottom: 12,
            }}
          >
            <strong style={{ fontSize: 14 }}>Medical History</strong>
            <Button size="sm" onClick={() => setHistoryOpen(true)}>
              Add condition
            </Button>
          </div>
          {!patient || patient.medicalHistory.length === 0 ? (
            <div style={{ fontSize: 13, color: 'var(--sq-fg-muted)' }}>No medical history recorded.</div>
          ) : (
            <Stack gap={8}>
              {patient.medicalHistory.map((entry) => (
                <Stack
                  key={entry.id}
                  direction="row"
                  gap={8}
                  align="center"
                  wrap
                  style={{ paddingBottom: 8, borderBottom: '1px solid var(--sq-border)' }}
                >
                  <span style={{ fontSize: 14, fontWeight: 600 }}>{entry.conditionName}</span>
                  {entry.diagnosisCode ? (
                    <Badge variant="neutral">{entry.diagnosisCode}</Badge>
                  ) : null}
                  {entry.onsetDate ? (
                    <span style={{ fontSize: 13, color: 'var(--sq-fg-muted)' }}>{entry.onsetDate}</span>
                  ) : null}
                </Stack>
              ))}
            </Stack>
          )}
        </ContentPanel>

        <ContentPanel>
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              marginBottom: 12,
            }}
          >
            <strong style={{ fontSize: 14 }}>Notes</strong>
            <Button size="sm" onClick={() => setNoteOpen(true)}>
              Add note
            </Button>
          </div>
          {!patient || patient.notes.length === 0 ? (
            <div style={{ fontSize: 13, color: 'var(--sq-fg-muted)' }}>No notes yet.</div>
          ) : (
            <Stack gap={12}>
              {patient.notes.map((note) => (
                <div
                  key={note.id}
                  style={{ paddingBottom: 12, borderBottom: '1px solid var(--sq-border)' }}
                >
                  <strong style={{ fontSize: 14 }}>{note.title}</strong>
                  {note.body ? (
                    <p style={{ margin: '4px 0 0', fontSize: 13, color: 'var(--sq-fg-muted)' }}>{note.body}</p>
                  ) : null}
                </div>
              ))}
            </Stack>
          )}
        </ContentPanel>
      </Stack>

      <Modal
        open={allergyOpen}
        onClose={() => setAllergyOpen(false)}
        title="Add allergy"
        description={`Record an allergy for ${fullName}.`}
        footer={
          <>
            <Button variant="outline" onClick={() => setAllergyOpen(false)}>
              Cancel
            </Button>
            <Button
              onClick={() => void addAllergy()}
              loading={savingAllergy}
              disabled={!allergyForm.allergen.trim()}
            >
              Add
            </Button>
          </>
        }
      >
        <Stack gap={12}>
          <Input
            label="Allergen"
            required
            value={allergyForm.allergen}
            onChange={(e) => setAllergyForm({ ...allergyForm, allergen: e.target.value })}
          />
          <Input
            label="Reaction"
            value={allergyForm.reaction}
            onChange={(e) => setAllergyForm({ ...allergyForm, reaction: e.target.value })}
          />
          <Select
            label="Severity"
            value={allergyForm.severity}
            onChange={(e) => setAllergyForm({ ...allergyForm, severity: e.target.value })}
          >
            <option value="mild">mild</option>
            <option value="moderate">moderate</option>
            <option value="severe">severe</option>
            <option value="life_threatening">life_threatening</option>
          </Select>
        </Stack>
      </Modal>

      <Modal
        open={historyOpen}
        onClose={() => setHistoryOpen(false)}
        title="Add medical history"
        description={`Record a condition for ${fullName}.`}
        footer={
          <>
            <Button variant="outline" onClick={() => setHistoryOpen(false)}>
              Cancel
            </Button>
            <Button
              onClick={() => void addHistory()}
              loading={savingHistory}
              disabled={!historyForm.conditionName.trim()}
            >
              Add
            </Button>
          </>
        }
      >
        <Stack gap={12}>
          <Input
            label="Condition name"
            required
            value={historyForm.conditionName}
            onChange={(e) => setHistoryForm({ ...historyForm, conditionName: e.target.value })}
          />
          <Input
            label="Diagnosis code"
            value={historyForm.diagnosisCode}
            onChange={(e) => setHistoryForm({ ...historyForm, diagnosisCode: e.target.value })}
          />
          <Input
            label="Onset date"
            type="date"
            value={historyForm.onsetDate}
            onChange={(e) => setHistoryForm({ ...historyForm, onsetDate: e.target.value })}
          />
        </Stack>
      </Modal>

      <Modal
        open={noteOpen}
        onClose={() => setNoteOpen(false)}
        title="Add note"
        description={`Add a clinical note for ${fullName}.`}
        footer={
          <>
            <Button variant="outline" onClick={() => setNoteOpen(false)}>
              Cancel
            </Button>
            <Button onClick={() => void addNote()} loading={savingNote}>
              Add
            </Button>
          </>
        }
      >
        <Stack gap={12}>
          <Input
            label="Title"
            value={noteForm.title}
            onChange={(e) => setNoteForm({ ...noteForm, title: e.target.value })}
          />
          <Textarea
            label="Body"
            value={noteForm.body}
            onChange={(e) => setNoteForm({ ...noteForm, body: e.target.value })}
          />
        </Stack>
      </Modal>
    </div>
  );
}