'use client';

import { useEffect, useState } from 'react';
import { ContentPanel, Stack, PageHeader, Badge, StatusIndicator, Table, TableHeader, TableBody, TableRow, TableHead, TableCell, Button, Input, Modal, } from '@squamed/ui';
import { useAuth } from '@squamed/auth';

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080/api/v1';

interface Hospital {
  id: string;
  name: string;
  code: string;
  country: string;
  city: string;
  timezone: string;
  currency: string;
  status: string;
}

interface Department {
  id: string;
  name: string;
  code: string;
  type: string;
  status: string;
}

export default function HospitalConfigPage() {
  const { accessToken } = useAuth();
  const [hospitals, setHospitals] = useState<Hospital[]>([]);
  const [departments, setDepartments] = useState<Department[]>([]);
  const [selectedHospital, setSelectedHospital] = useState<Hospital | null>(null);
  const [hospitalModal, setHospitalModal] = useState(false);
  const [deptModal, setDeptModal] = useState(false);
  const [newHospital, setNewHospital] = useState({ name: '', code: '', country: '', city: '', timezone: 'UTC', currency: 'USD' });
  const [newDept, setNewDept] = useState({ name: '', code: '', type: 'outpatient' });
  const [error, setError] = useState<string | null>(null);

  async function authorizedFetch(path: string, options: RequestInit = {}) {
    const response = await fetch(`${API_BASE}${path}`, {
      ...options,
      headers: {
        'Content-Type': 'application/json',
        ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
        ...options.headers,
      },
    });
    if (!response.ok) {
      const body = await response.json().catch(() => null);
      throw new Error(body?.error?.message ?? `Request failed (${response.status})`);
    }
    const json = await response.json();
    return json.data;
  }

  async function loadHospitals() {
    try {
      const data = await authorizedFetch('/hospital-config/hospitals');
      setHospitals(data ?? []);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load hospitals');
    }
  }

  async function openHospital(hospital: Hospital) {
    setSelectedHospital(hospital);
    try {
      const data = await authorizedFetch(`/hospital-config/hospitals/${hospital.id}/departments`);
      setDepartments(data ?? []);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load departments');
    }
  }

  async function createHospital() {
    try {
      await authorizedFetch('/hospital-config/hospitals', {
        method: 'POST',
        body: JSON.stringify(newHospital),
      });
      setHospitalModal(false);
      setNewHospital({ name: '', code: '', country: '', city: '', timezone: 'UTC', currency: 'USD' });
      await loadHospitals();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to create hospital');
    }
  }

  async function createDepartment() {
    if (!selectedHospital) return;
    try {
      await authorizedFetch(`/hospital-config/hospitals/${selectedHospital.id}/departments`, {
        method: 'POST',
        body: JSON.stringify(newDept),
      });
      setDeptModal(false);
      setNewDept({ name: '', code: '', type: 'outpatient' });
      await openHospital(selectedHospital);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to create department');
    }
  }

  useEffect(() => {
    if (accessToken) void loadHospitals();
  }, [accessToken]);

  return (
    <div style={{ padding: 24 }}>
      <PageHeader
        title="Hospital Configuration"
        description="Build and maintain hospital, campus, department and service hierarchies."
        actions={<Button size="sm" onClick={() => setHospitalModal(true)}>Add hospital</Button>}
      />

      {error ? <StatusIndicator tone="critical" label={error} /> : null}

      <Stack gap={16}>
        <ContentPanel>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Code</TableHead>
                <TableHead>Name</TableHead>
                <TableHead>Country</TableHead>
                <TableHead>City</TableHead>
                <TableHead>Timezone</TableHead>
                <TableHead>Currency</TableHead>
                <TableHead>Status</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {hospitals.length === 0 ? (
                <TableRow>
                  <TableCell>No hospitals configured yet.</TableCell>
                </TableRow>
              ) : (
                hospitals.map((hospital) => (
                  <TableRow
                    key={hospital.id}
                    style={{ cursor: 'pointer', background: selectedHospital?.id === hospital.id ? 'var(--sq-primary-soft)' : undefined }}
                    onClick={() => void openHospital(hospital)}
                  >
                    <TableCell>{hospital.code}</TableCell>
                    <TableCell>{hospital.name}</TableCell>
                    <TableCell>{hospital.country}</TableCell>
                    <TableCell>{hospital.city}</TableCell>
                    <TableCell>{hospital.timezone}</TableCell>
                    <TableCell>{hospital.currency}</TableCell>
                    <TableCell>
                      <Badge variant={hospital.status === 'active' ? 'success' : 'neutral'}>{hospital.status}</Badge>
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </ContentPanel>

        {selectedHospital ? (
          <ContentPanel>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 }}>
              <strong>Departments — {selectedHospital.name}</strong>
              <Button size="sm" onClick={() => setDeptModal(true)}>Add department</Button>
            </div>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Code</TableHead>
                  <TableHead>Name</TableHead>
                  <TableHead>Type</TableHead>
                  <TableHead>Status</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {departments.length === 0 ? (
                  <TableRow>
                    <TableCell>No departments yet.</TableCell>
                  </TableRow>
                ) : (
                  departments.map((dept) => (
                    <TableRow key={dept.id}>
                      <TableCell>{dept.code}</TableCell>
                      <TableCell>{dept.name}</TableCell>
                      <TableCell>{dept.type}</TableCell>
                      <TableCell>
                        <Badge variant={dept.status === 'active' ? 'success' : 'neutral'}>{dept.status}</Badge>
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </ContentPanel>
        ) : null}
      </Stack>

      <Modal
        open={hospitalModal}
        onClose={() => setHospitalModal(false)}
        title="Add hospital"
        description="Register a hospital in the hierarchy."
        footer={
          <>
            <Button variant="outline" onClick={() => setHospitalModal(false)}>Cancel</Button>
            <Button onClick={() => void createHospital()}>Create</Button>
          </>
        }
      >
        <Stack gap={12}>
          <Input label="Name" value={newHospital.name} onChange={(e) => setNewHospital({ ...newHospital, name: e.target.value })} />
          <Input label="Code" value={newHospital.code} onChange={(e) => setNewHospital({ ...newHospital, code: e.target.value })} />
          <Input label="Country" value={newHospital.country} onChange={(e) => setNewHospital({ ...newHospital, country: e.target.value })} />
          <Input label="City" value={newHospital.city} onChange={(e) => setNewHospital({ ...newHospital, city: e.target.value })} />
        </Stack>
      </Modal>

      <Modal
        open={deptModal}
        onClose={() => setDeptModal(false)}
        title="Add department"
        description={`Department under ${selectedHospital?.name ?? ''}.`}
        footer={
          <>
            <Button variant="outline" onClick={() => setDeptModal(false)}>Cancel</Button>
            <Button onClick={() => void createDepartment()}>Create</Button>
          </>
        }
      >
        <Stack gap={12}>
          <Input label="Name" value={newDept.name} onChange={(e) => setNewDept({ ...newDept, name: e.target.value })} />
          <Input label="Code" value={newDept.code} onChange={(e) => setNewDept({ ...newDept, code: e.target.value })} />
          <Input label="Type" value={newDept.type} onChange={(e) => setNewDept({ ...newDept, type: e.target.value })} />
        </Stack>
      </Modal>
    </div>
  );
}