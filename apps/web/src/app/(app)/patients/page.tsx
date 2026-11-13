'use client';

import { useCallback, useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import {
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
import { useApi } from '../../../lib/use-api';

interface PatientSummary {
  id: string;
  mrn: string;
  firstName: string;
  lastName: string;
  dateOfBirth: string;
  gender: string;
  phone?: string | null;
  qid?: string | null;
  email?: string | null;
  nationalId?: string | null;
}

interface DuplicateMatch {
  patientId: string;
  mrn: string;
  name: string;
  score: number;
  matchedOn: string;
}

interface RegisterResponse {
  patient: PatientSummary;
  matches: DuplicateMatch[];
}

const emptyForm = {
  firstName: '',
  lastName: '',
  dateOfBirth: '',
  gender: '',
  phone: '',
  qid: '',
  email: '',
  nationality: '',
  city: '',
};

export default function PatientsPage() {
  const router = useRouter();
  const { apiFetch, isReady } = useApi();
  const [patients, setPatients] = useState<PatientSummary[]>([]);
  const [query, setQuery] = useState('');
  const [registerOpen, setRegisterOpen] = useState(false);
  const [form, setForm] = useState(emptyForm);
  const [submitting, setSubmitting] = useState(false);
  const [matches, setMatches] = useState<DuplicateMatch[]>([]);
  const [error, setError] = useState<string | null>(null);

  const loadPatients = useCallback(
    async (search: string) => {
      try {
        const data = await apiFetch<PatientSummary[]>(`/patients?query=${encodeURIComponent(search)}`);
        setPatients(data ?? []);
        setError(null);
      } catch (e) {
        setError(e instanceof Error ? e.message : 'Failed to load patients');
      }
    },
    [apiFetch],
  );

  useEffect(() => {
    if (isReady) void loadPatients('');
  }, [isReady, loadPatients]);

  function updateForm(key: keyof typeof emptyForm, value: string) {
    setForm((prev) => ({ ...prev, [key]: value }));
  }

  const requiredPresent = Boolean(form.firstName.trim() && form.lastName.trim() && form.dateOfBirth && form.gender);

  async function registerPatient() {
    if (!requiredPresent) return;
    setSubmitting(true);
    try {
      const data = await apiFetch<RegisterResponse>('/patients', {
        method: 'POST',
        body: JSON.stringify({
          firstName: form.firstName,
          lastName: form.lastName,
          dateOfBirth: form.dateOfBirth,
          gender: form.gender,
          phone: form.phone || undefined,
          qid: form.qid || undefined,
          email: form.email || undefined,
          nationality: form.nationality || undefined,
          city: form.city || undefined,
        }),
      });
      const found = data.matches ?? [];
      setMatches(found);
      if (found.length === 0) {
        setRegisterOpen(false);
        setForm(emptyForm);
        await loadPatients('');
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to register patient');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div style={{ padding: 24 }}>
      <PageHeader
        title="Patients"
        description="Search the patient registry and register new patients."
        actions={<Button onClick={() => setRegisterOpen(true)}>Register patient</Button>}
      />

      {error ? <StatusIndicator tone="critical" label={error} /> : null}

      <Stack gap={16}>
        <Stack direction="row" gap={8}>
          <div style={{ flex: 1, maxWidth: 480 }}>
            <Input
              placeholder="Search patients"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
            />
          </div>
          <Button onClick={() => void loadPatients(query)}>Search</Button>
        </Stack>

        <ContentPanel>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>MRN</TableHead>
                <TableHead>Name</TableHead>
                <TableHead>Date of Birth</TableHead>
                <TableHead>Gender</TableHead>
                <TableHead>Phone</TableHead>
                <TableHead>QID</TableHead>
                <TableHead>Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {patients.length === 0 ? (
                <TableRow>
                  <TableCell>No patients found.</TableCell>
                </TableRow>
              ) : (
                patients.map((patient) => (
                  <TableRow key={patient.id}>
                    <TableCell>{patient.mrn}</TableCell>
                    <TableCell>
                      {patient.firstName} {patient.lastName}
                    </TableCell>
                    <TableCell>{patient.dateOfBirth}</TableCell>
                    <TableCell>{patient.gender}</TableCell>
                    <TableCell>{patient.phone ?? '-'}</TableCell>
                    <TableCell>{patient.qid ?? '-'}</TableCell>
                    <TableCell>
                      <Button variant="outline" size="sm" onClick={() => router.push(`/patients/${patient.id}`)}>
                        Open
                      </Button>
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </ContentPanel>
      </Stack>

      <Modal
        open={registerOpen}
        onClose={() => setRegisterOpen(false)}
        title="Register patient"
        description="Create a new patient record. Required fields are marked."
        footer={
          <>
            <Button variant="outline" onClick={() => setRegisterOpen(false)}>
              Cancel
            </Button>
            <Button onClick={() => void registerPatient()} loading={submitting} disabled={!requiredPresent}>
              Register
            </Button>
          </>
        }
      >
        <Stack gap={12}>
          <Stack direction="row" gap={12}>
            <Input
              label="First name"
              required
              value={form.firstName}
              onChange={(e) => updateForm('firstName', e.target.value)}
            />
            <Input
              label="Last name"
              required
              value={form.lastName}
              onChange={(e) => updateForm('lastName', e.target.value)}
            />
          </Stack>
          <Stack direction="row" gap={12}>
            <Input
              label="Date of birth"
              required
              type="date"
              value={form.dateOfBirth}
              onChange={(e) => updateForm('dateOfBirth', e.target.value)}
            />
            <Select
              label="Gender"
              required
              value={form.gender}
              onChange={(e) => updateForm('gender', e.target.value)}
            >
              <option value="" disabled>
                Select gender
              </option>
              <option value="female">female</option>
              <option value="male">male</option>
              <option value="other">other</option>
            </Select>
          </Stack>
          <Stack direction="row" gap={12}>
            <Input label="Phone" value={form.phone} onChange={(e) => updateForm('phone', e.target.value)} />
            <Input label="QID" value={form.qid} onChange={(e) => updateForm('qid', e.target.value)} />
          </Stack>
          <Stack direction="row" gap={12}>
            <Input label="Email" value={form.email} onChange={(e) => updateForm('email', e.target.value)} />
            <Input
              label="Nationality"
              value={form.nationality}
              onChange={(e) => updateForm('nationality', e.target.value)}
            />
          </Stack>
          <Input label="City" value={form.city} onChange={(e) => updateForm('city', e.target.value)} />
          {matches.length > 0 ? (
            <Stack gap={8}>
              <StatusIndicator
                tone="critical"
                label={`${matches.length} possible duplicate(s) detected — review separately.`}
              />
              {matches.map((match) => (
                <div key={match.patientId} style={{ fontSize: 13, color: 'var(--sq-fg-muted)' }}>
                  {match.name} ({match.mrn}) — score {match.score} on {match.matchedOn}
                </div>
              ))}
            </Stack>
          ) : null}
        </Stack>
      </Modal>
    </div>
  );
}