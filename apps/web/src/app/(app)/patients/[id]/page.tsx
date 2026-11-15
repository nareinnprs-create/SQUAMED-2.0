'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { useParams } from 'next/navigation';
import {
  Badge,
  Button,
  Checkbox,
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
import { useShellStore } from '@/lib/shell-store';

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

interface PatientIdentifier {
  id: string;
  idType: string;
  idValue: string;
  issuingAuthority?: string | null;
}

interface PatientRelationship {
  id: string;
  patientId: string;
  relatedPatientId: string;
  relationshipType: string;
  isPrimaryGuardian?: boolean;
  reverse?: boolean;
}

interface PatientListEntry {
  id: string;
  mrn: string;
  firstName: string;
  lastName: string;
  isUnknown?: boolean;
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
  isUnknown?: boolean;
  photoUrl?: string | null;
  allergies: Allergy[];
  notes: PatientNote[];
  // Not in the detail DTO yet (backend sends no medical history field);
  // optional until the API grows it.
  medicalHistory?: MedicalHistoryEntry[];
  identifiers?: PatientIdentifier[];
  relationships?: PatientRelationship[];
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
const emptyIdentifierForm = { idType: 'ABHA', idValue: '' };
const emptyRelationshipForm = { relatedPatientId: '', relationshipType: 'guardian', isPrimaryGuardian: false };

const identifierTypes = ['ABHA', 'ABHA_ADDRESS', 'NATIONAL_ID', 'PASSPORT', 'OTHER'];
const relationshipTypes = ['guardian', 'spouse', 'father', 'mother', 'son', 'daughter', 'sibling', 'other'];

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
  const { apiFetch, apiFetchRaw, isReady } = useApi();
  const [patient, setPatient] = useState<PatientDetail | null>(null);
  const [allergyOpen, setAllergyOpen] = useState(false);
  const [historyOpen, setHistoryOpen] = useState(false);
  const [noteOpen, setNoteOpen] = useState(false);
  const [identifierOpen, setIdentifierOpen] = useState(false);
  const [relationshipOpen, setRelationshipOpen] = useState(false);
  const [allergyForm, setAllergyForm] = useState(emptyAllergyForm);
  const [historyForm, setHistoryForm] = useState(emptyHistoryForm);
  const [noteForm, setNoteForm] = useState(emptyNoteForm);
  const [identifierForm, setIdentifierForm] = useState(emptyIdentifierForm);
  const [relationshipForm, setRelationshipForm] = useState(emptyRelationshipForm);
  const [patientOptions, setPatientOptions] = useState<PatientListEntry[]>([]);
  const [savingAllergy, setSavingAllergy] = useState(false);
  const [savingHistory, setSavingHistory] = useState(false);
  const [savingNote, setSavingNote] = useState(false);
  const [savingIdentifier, setSavingIdentifier] = useState(false);
  const [savingRelationship, setSavingRelationship] = useState(false);
  const [uploadingPhoto, setUploadingPhoto] = useState(false);
  const [photoObjectUrl, setPhotoObjectUrl] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const photoInputRef = useRef<HTMLInputElement | null>(null);
  const setPatientContext = useShellStore((state) => state.setPatient);

  function ageFrom(dob: string): number {
    const birth = new Date(dob).getTime();
    if (Number.isNaN(birth)) return 0;
    return Math.max(0, Math.floor((Date.now() - birth) / 31_557_600_000));
  }

  const loadPatient = useCallback(async () => {
    try {
      const data = await apiFetch<PatientDetail>(`/patients/${id}`);
      setPatient(data);
      setPatientContext({
        id: data.id,
        name: `${data.firstName} ${data.lastName}`.trim(),
        mrn: data.mrn,
        dateOfBirth: data.dateOfBirth,
        gender: data.gender,
        age: ageFrom(data.dateOfBirth),
        isEmergency: (data.allergies ?? []).some((a) => a.severity === 'life_threatening'),
        allergySummary:
          (data.allergies ?? []).length > 0
            ? (data.allergies ?? []).map((a) => a.allergen).join(', ')
            : undefined,
      });
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load patient');
    }
  }, [apiFetch, id, setPatientContext]);

  useEffect(() => {
    if (isReady) void loadPatient();
  }, [isReady, loadPatient]);

  // Same bounded load the list page does — powers the related-patient picker and name display.
  useEffect(() => {
    if (!isReady) return;
    apiFetch<PatientListEntry[]>('/patients?query=')
      .then((options) => setPatientOptions((options ?? []).filter((p) => p.id !== id)))
      .catch(() => setPatientOptions([]));
  }, [apiFetch, id, isReady]);

  // Fetch photo bytes only when a photo key exists (GET /photo 404s otherwise).
  useEffect(() => {
    if (!isReady || !patient?.photoUrl || !patient.photoUrl.startsWith('photos/')) {
      setPhotoObjectUrl(null);
      return;
    }
    let revoked = false;
    let url: string | null = null;
    apiFetchRaw(`/patients/${id}/photo`)
      .then((response) => response.blob())
      .then((blob) => {
        if (!revoked) {
          url = URL.createObjectURL(blob);
          setPhotoObjectUrl(url);
        }
      })
      .catch(() => setPhotoObjectUrl(null));
    return () => {
      revoked = true;
      if (url) URL.revokeObjectURL(url);
    };
  }, [apiFetchRaw, id, isReady, patient?.photoUrl]);

  async function uploadPhoto(file: File) {
    setUploadingPhoto(true);
    try {
      const body = new FormData();
      body.append('file', file);
      await apiFetch<unknown>(`/patients/${id}/photo`, { method: 'POST', body });
      await loadPatient();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to upload photo');
    } finally {
      setUploadingPhoto(false);
    }
  }

  async function addIdentifier() {
    setSavingIdentifier(true);
    try {
      await apiFetch<unknown>(`/patients/${id}/identifiers`, {
        method: 'POST',
        body: JSON.stringify({
          idType: identifierForm.idType,
          idValue:
            identifierForm.idType === 'ABHA_ADDRESS' ? identifierForm.idValue.trim().toLowerCase() : identifierForm.idValue.trim(),
        }),
      });
      setIdentifierOpen(false);
      setIdentifierForm(emptyIdentifierForm);
      await loadPatient();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to add identifier');
    } finally {
      setSavingIdentifier(false);
    }
  }

  async function deleteIdentifier(identifierId: string) {
    try {
      await apiFetch<unknown>(`/patients/${id}/identifiers/${identifierId}`, { method: 'DELETE' });
      await loadPatient();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to delete identifier');
    }
  }

  async function openRelationshipModal() {
    setRelationshipOpen(true);
  }

  async function addRelationship() {
    setSavingRelationship(true);
    try {
      await apiFetch<unknown>(`/patients/${id}/relationships`, {
        method: 'POST',
        body: JSON.stringify({
          relatedPatientId: relationshipForm.relatedPatientId,
          relationshipType: relationshipForm.relationshipType,
          isPrimaryGuardian: relationshipForm.isPrimaryGuardian,
        }),
      });
      setRelationshipOpen(false);
      setRelationshipForm(emptyRelationshipForm);
      await loadPatient();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to add relationship');
    } finally {
      setSavingRelationship(false);
    }
  }

  async function deleteRelationship(relationshipId: string) {
    try {
      await apiFetch<unknown>(`/patients/${id}/relationships/${relationshipId}`, { method: 'DELETE' });
      await loadPatient();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to delete relationship');
    }
  }

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

  function patientName(patientId: string): string {
    const entry = patientOptions.find((p) => p.id === patientId);
    return entry ? `${entry.firstName} ${entry.lastName} (${entry.mrn})` : patientId.slice(0, 8);
  }

  return (
    <div style={{ padding: 24 }}>
      <PageHeader
        title={
          <Stack direction="row" gap={8} align="center" wrap>
            {photoObjectUrl ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img
                src={photoObjectUrl}
                alt={`${fullName} photo`}
                style={{ width: 40, height: 40, borderRadius: '50%', objectFit: 'cover' }}
              />
            ) : null}
            <span>{fullName}</span>
            {patient ? <Badge variant="neutral">{patient.mrn}</Badge> : null}
            {patient?.isUnknown ? <Badge variant="warning">Unknown patient</Badge> : null}
          </Stack>
        }
        description="Patient demographics and clinical records."
        actions={
          <>
            <input
              ref={photoInputRef}
              type="file"
              accept="image/jpeg,image/png,image/webp"
              hidden
              onChange={(e) => {
                const file = e.target.files?.[0];
                if (file) void uploadPhoto(file);
                e.target.value = '';
              }}
            />
            <Button variant="outline" loading={uploadingPhoto} onClick={() => photoInputRef.current?.click()}>
              Upload photo
            </Button>
          </>
        }
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
            <strong style={{ fontSize: 14 }}>Identifiers</strong>
            <Button size="sm" onClick={() => setIdentifierOpen(true)}>
              Add identifier
            </Button>
          </div>
          {!patient || (patient.identifiers ?? []).length === 0 ? (
            <div style={{ fontSize: 13, color: 'var(--sq-fg-muted)' }}>No identifiers recorded.</div>
          ) : (
            <Stack gap={8}>
              {(patient.identifiers ?? []).map((identifier) => (
                <Stack
                  key={identifier.id}
                  direction="row"
                  gap={8}
                  align="center"
                  wrap
                  style={{ paddingBottom: 8, borderBottom: '1px solid var(--sq-border)' }}
                >
                  <Badge variant="neutral">{identifier.idType}</Badge>
                  <span style={{ fontSize: 14, fontFamily: 'monospace' }}>{identifier.idValue}</span>
                  <Button variant="ghost" size="sm" onClick={() => void deleteIdentifier(identifier.id)}>
                    Remove
                  </Button>
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
            <strong style={{ fontSize: 14 }}>Relationships &amp; guardians</strong>
            <Button size="sm" onClick={() => void openRelationshipModal()}>
              Add relationship
            </Button>
          </div>
          {!patient || (patient.relationships ?? []).length === 0 ? (
            <div style={{ fontSize: 13, color: 'var(--sq-fg-muted)' }}>No relationships recorded.</div>
          ) : (
            <Stack gap={8}>
              {(patient.relationships ?? []).map((relationship) => (
                <Stack
                  key={relationship.id}
                  direction="row"
                  gap={8}
                  align="center"
                  wrap
                  style={{ paddingBottom: 8, borderBottom: '1px solid var(--sq-border)' }}
                >
                  <Badge variant={relationship.isPrimaryGuardian ? 'success' : 'neutral'}>
                    {relationship.isPrimaryGuardian ? 'primary guardian' : relationship.relationshipType}
                  </Badge>
                  <span style={{ fontSize: 14 }}>
                    {patientName(relationship.reverse ? relationship.patientId : relationship.relatedPatientId)}
                  </span>
                  <span style={{ fontSize: 13, color: 'var(--sq-fg-muted)' }}>{relationship.relationshipType}</span>
                  <Button variant="ghost" size="sm" onClick={() => void deleteRelationship(relationship.id)}>
                    Remove
                  </Button>
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
          {!patient || (patient.medicalHistory ?? []).length === 0 ? (
            <div style={{ fontSize: 13, color: 'var(--sq-fg-muted)' }}>No medical history recorded.</div>
          ) : (
            <Stack gap={8}>
              {(patient.medicalHistory ?? []).map((entry) => (
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

      <Modal
        open={identifierOpen}
        onClose={() => setIdentifierOpen(false)}
        title="Add identifier"
        description="Attach an identity document, including ABHA (14-digit) or ABHA address."
        footer={
          <>
            <Button variant="outline" onClick={() => setIdentifierOpen(false)}>
              Cancel
            </Button>
            <Button
              onClick={() => void addIdentifier()}
              loading={savingIdentifier}
              disabled={!identifierForm.idValue.trim()}
            >
              Add
            </Button>
          </>
        }
      >
        <Stack gap={12}>
          <Select
            label="Identifier type"
            value={identifierForm.idType}
            onChange={(e) => setIdentifierForm({ ...identifierForm, idType: e.target.value })}
          >
            {identifierTypes.map((type) => (
              <option key={type} value={type}>
                {type}
              </option>
            ))}
          </Select>
          <Input
            label="Value"
            required
            value={identifierForm.idValue}
            onChange={(e) => setIdentifierForm({ ...identifierForm, idValue: e.target.value })}
          />
        </Stack>
      </Modal>

      <Modal
        open={relationshipOpen}
        onClose={() => setRelationshipOpen(false)}
        title="Add relationship"
        description={`Link ${fullName} to another patient (guardian, family member).`}
        footer={
          <>
            <Button variant="outline" onClick={() => setRelationshipOpen(false)}>
              Cancel
            </Button>
            <Button
              onClick={() => void addRelationship()}
              loading={savingRelationship}
              disabled={!relationshipForm.relatedPatientId}
            >
              Add
            </Button>
          </>
        }
      >
        <Stack gap={12}>
          <Select
            label="Related patient"
            required
            value={relationshipForm.relatedPatientId}
            onChange={(e) => setRelationshipForm({ ...relationshipForm, relatedPatientId: e.target.value })}
          >
            <option value="" disabled>
              Select patient
            </option>
            {patientOptions.map((option) => (
              <option key={option.id} value={option.id}>
                {option.firstName} {option.lastName} ({option.mrn}){option.isUnknown ? ' — Unknown' : ''}
              </option>
            ))}
          </Select>
          <Select
            label="Relationship type"
            value={relationshipForm.relationshipType}
            onChange={(e) => setRelationshipForm({ ...relationshipForm, relationshipType: e.target.value })}
          >
            {relationshipTypes.map((type) => (
              <option key={type} value={type}>
                {type}
              </option>
            ))}
          </Select>
          <Checkbox
            checked={relationshipForm.isPrimaryGuardian}
            onCheckedChange={(checked) => setRelationshipForm({ ...relationshipForm, isPrimaryGuardian: checked })}
            label="Primary guardian"
          />
        </Stack>
      </Modal>
    </div>
  );
}