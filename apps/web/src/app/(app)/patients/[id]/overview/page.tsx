'use client'

import { useCallback, useEffect, useState } from 'react'
import { useParams } from 'next/navigation'
import {
  Badge,
  ContentPanel,
  PageHeader,
  Spinner,
  Stack,
  StatusIndicator,
} from '@squamed/ui'
import type { BadgeVariant } from '@squamed/ui'
import { useApi } from '../../../../../lib/use-api'
import { useShellStore } from '@/lib/shell-store'

interface Patient360Demographics {
  id: string
  mrn: string
  firstName: string
  lastName: string
  dateOfBirth: string
  gender: string
  phone?: string | null
  qid?: string | null
  email?: string | null
  nationality?: string | null
  city?: string | null
  allergies: Allergy360[]
  notes: PatientNote360[]
  medicalHistory: MedicalHistoryEntry360[]
}

interface VitalSign360 {
  id: string
  encounterId?: string | null
  patientId?: string | null
  recordedAt: string
  bloodPressureSystolic?: number | null
  bloodPressureDiastolic?: number | null
  heartRate?: number | null
  respiratoryRate?: number | null
  temperatureC?: number | null
  spo2Percent?: number | null
  heightCm?: number | null
  weightKg?: number | null
  bmi?: number | null
  painScore?: number | null
  notes?: string | null
}

interface Encounter360 {
  encounterId: string
  date: string
  type: string
  status: string
  reason?: string | null
  diagnoses: Diagnosis360[]
  prescriptions: Prescription360[]
  orders: Order360[]
  noteCount: number
}

interface Diagnosis360 {
  id?: string
  code: string
  description?: string | null
}

interface Prescription360 {
  id?: string
  drugName?: string
  dosage?: string | null
  frequency?: string | null
  status?: string
}

interface Order360 {
  id?: string
  description?: string
  type?: string
  status?: string
  orderedAt?: string
}

interface Allergy360 {
  id: string
  allergen: string
  reaction?: string | null
  severity: string
}

interface PatientNote360 {
  id: string
  title: string
  body?: string | null
  createdAt?: string
}

interface MedicalHistoryEntry360 {
  id: string
  conditionName: string
  diagnosisCode?: string | null
  onsetDate?: string | null
}

interface Appointment360 {
  id: string | null
  patientId?: string | null
  doctorId?: string | null
  scheduledStart?: string | null
  scheduledEnd?: string | null
  status?: string | null
  type?: string | null
  reason?: string | null
  priority?: string | null
  visitType?: string | null
  telemedicine?: boolean | null
  roomId?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}

interface Visit360 {
  id: string | null
  hospitalId?: string | null
  patientId?: string
  appointmentId?: string | null
  visitNumber: string
  visitType: string
  departmentId?: string | null
  doctorId?: string | null
  specialtyId?: string | null
  reason?: string | null
  priority: string
  source: string
  status: string
  insuranceVerification?: string | null
  registrationStartedAt?: string | null
  registeredAt?: string | null
  createdAt?: string | null
}

interface LabOrder360 {
  id: string
  orderNumber: string
  status: string
  resultStatus?: string | null
  priority?: string | null
  clinicalNote?: string | null
  orderedAt: string
  specimenTakenAt?: string | null
  completedAt?: string | null
  releasedAt?: string | null
}

interface Medication360 {
  id: string
  drugName: string
  quantity: number
  status: string
  dispensedAt?: string | null
  unitPrice?: number | null
  totalPrice?: number | null
  notes?: string | null
}

interface Charge360 {
  id: string
  chargeName?: string | null
  chargeCode?: string | null
  quantity: number
  unitPriceQar?: number | null
  totalQar?: number | null
  status: string
  postedAt?: string | null
}

interface Invoice360 {
  id: string
  invoiceNumber: string
  subtotalQar: number
  discountQar: number
  taxQar: number
  totalQar: number
  amountPaidQar: number
  amountDueQar: number
  status: string
  issuedAt?: string | null
}

interface Task360 {
  id: string | null
  title: string
  description?: string | null
  status: string
  priority?: string | null
  assigneeId?: string | null
  dueAt?: string | null
  createdAt?: string | null
  completedAt?: string | null
  updatedAt?: string | null
}

interface TaskSummary360 {
  open: number
  completed: number
}

interface Patient360View {
  demographics: Patient360Demographics
  latestVitals: VitalSign360 | null
  recentVitals: VitalSign360[]
  encounters: Encounter360[]
  totalEncounters: number
  allergies: Allergy360[]
  notes: PatientNote360[]
  medicalHistory: MedicalHistoryEntry360[]
  appointments: Appointment360[]
  visits: Visit360[]
  labOrders: LabOrder360[]
  totalLabOrders: number
  medications: Medication360[]
  charges: Charge360[]
  invoices: Invoice360[]
  totalInvoices: number
  tasks: Task360[]
  taskSummary: TaskSummary360
}

const allergyVariants: Record<string, BadgeVariant> = {
  mild: 'information',
  moderate: 'warning',
  severe: 'critical',
  life_threatening: 'critical',
}

const encounterStatusVariants: Record<string, BadgeVariant> = {
  'in-progress': 'warning',
  completed: 'success',
  cancelled: 'critical',
  scheduled: 'information',
  'no-show': 'neutral',
}

const labResultVariants: Record<string, BadgeVariant> = {
  released: 'success',
  pending: 'warning',
  resulted: 'information',
  ordered: 'neutral',
}

const medStatusVariants: Record<string, BadgeVariant> = {
  dispensed: 'success',
  verified: 'information',
  pending: 'warning',
  cancelled: 'critical',
}

const invoiceStatusVariants: Record<string, BadgeVariant> = {
  paid: 'success',
  due: 'warning',
  partially_paid: 'warning',
  unpaid: 'warning',
  cancelled: 'critical',
  draft: 'neutral',
}

const chargeStatusVariants: Record<string, BadgeVariant> = {
  invoiced: 'information',
  posted: 'neutral',
  cancelled: 'critical',
}

const taskStatusVariants: Record<string, BadgeVariant> = {
  open: 'warning',
  completed: 'success',
  cancelled: 'critical',
  'in-progress': 'information',
}

const taskPriorityVariants: Record<string, BadgeVariant> = {
  high: 'critical',
  medium: 'warning',
  low: 'information',
  urgent: 'critical',
  normal: 'neutral',
}

function formatDate(value: string | null | undefined): string {
  if (!value) return '—'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return '—'
  return d.toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' })
}

function formatDateTime(value: string | null | undefined): string {
  if (!value) return '—'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return '—'
  return d.toLocaleString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  })
}

function computeAge(dob: string): string {
  const birth = new Date(dob)
  if (Number.isNaN(birth.getTime())) return ''
  const now = new Date()
  let years = now.getFullYear() - birth.getFullYear()
  const monthDiff = now.getMonth() - birth.getMonth()
  if (monthDiff < 0 || (monthDiff === 0 && now.getDate() < birth.getDate())) {
    years--
  }
  return `${years}y`
}

function formatQar(value: number | null | undefined): string {
  if (value == null) return '—'
  return `QAR ${value.toFixed(2)}`
}

function formatVital(
  value: number | null | undefined,
  suffix: string,
): string {
  if (value == null) return '—'
  return `${value}${suffix}`
}

function VitalReading({
  label,
  value,
}: {
  label: string
  value: string
}) {
  return (
    <div style={{ textAlign: 'center', minWidth: 72 }}>
      <div
        style={{
          fontSize: 11,
          fontWeight: 600,
          color: 'var(--sq-fg-muted)',
          textTransform: 'uppercase',
          letterSpacing: '0.04em',
          marginBottom: 4,
        }}
      >
        {label}
      </div>
      <div style={{ fontSize: 18, fontWeight: 600, color: 'var(--sq-fg)' }}>{value}</div>
    </div>
  )
}

function SectionHeading({
  title,
  count,
  total,
  suffix,
}: {
  title: string
  count?: number
  total?: number
  suffix?: string
}) {
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        marginBottom: 12,
      }}
    >
      <strong style={{ fontSize: 14 }}>{title}</strong>
      {total != null && count != null && total > count ? (
        <span style={{ fontSize: 12, color: 'var(--sq-fg-muted)' }}>
          Showing {count} of {total}{suffix ? ` ${suffix}` : ''}
        </span>
      ) : null}
    </div>
  )
}

function EmptyState({ message }: { message: string }) {
  return (
    <div
      style={{
        padding: '20px 0',
        textAlign: 'center',
        fontSize: 13,
        color: 'var(--sq-fg-muted)',
      }}
    >
      {message}
    </div>
  )
}

function StatCard({ label, value, variant }: { label: string; value: string | number; variant?: BadgeVariant }) {
  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        gap: 4,
        padding: '10px 16px',
        borderRadius: 8,
        background: 'color-mix(in srgb, var(--sq-fg) 5%, transparent)',
        minWidth: 100,
      }}
    >
      <div style={{ fontSize: 18, fontWeight: 700, color: 'var(--sq-fg)' }}>{value}</div>
      <div
        style={{
          fontSize: 11,
          fontWeight: 600,
          color: 'var(--sq-fg-muted)',
          textTransform: 'uppercase',
          letterSpacing: '0.04em',
        }}
      >
        {label}
      </div>
      {variant ? <Badge variant={variant} size="sm" /> : null}
    </div>
  )
}

const labelStyle = {
  fontSize: 11,
  fontWeight: 600,
  color: 'var(--sq-fg-muted)',
  textTransform: 'uppercase',
  letterSpacing: '0.04em' as const,
}

const twoColGrid = {
  display: 'grid',
  gridTemplateColumns: 'repeat(auto-fit, minmax(340px, 1fr))',
  gap: 16,
}

const tableHeaderStyle = {
  fontSize: 11,
  padding: '8px 12px',
  textAlign: 'left' as const,
  fontWeight: 600,
  color: 'var(--sq-fg-muted)',
  textTransform: 'uppercase',
  letterSpacing: '0.04em',
  borderBottom: '1px solid var(--sq-border)',
}

const tableCellStyle = {
  padding: '8px 12px',
  fontSize: 13,
  borderBottom: '1px solid var(--sq-border)',
  color: 'var(--sq-fg)',
}

const mutedText = {
  fontSize: 12,
  color: 'var(--sq-fg-muted)',
}

const fullBorderStyle = {
  border: '1px solid var(--sq-border)',
  borderRadius: 8,
  overflow: 'auto' as const,
}

const fullWidthTable = {
  width: '100%',
  borderCollapse: 'collapse' as const,
}

function renderVitalsGrid(vitals: VitalSign360) {
  return (
    <div
      style={{
        display: 'flex',
        flexWrap: 'wrap',
        gap: 16,
        justifyContent: 'center',
        padding: '8px 0',
      }}
    >
      <VitalReading label="BP" value={formatVital(vitals.bloodPressureSystolic, '/')} />
      <VitalReading label="Heart Rate" value={formatVital(vitals.heartRate, ' bpm')} />
      <VitalReading label="Temp" value={formatVital(vitals.temperatureC, '°C')} />
      <VitalReading label="SpO₂" value={formatVital(vitals.spo2Percent, '%')} />
      <VitalReading label="RR" value={formatVital(vitals.respiratoryRate, '/min')} />
      <VitalReading label="Pain" value={formatVital(vitals.painScore, '/10')} />
      <VitalReading label="Weight" value={formatVital(vitals.weightKg, ' kg')} />
      <VitalReading label="Height" value={formatVital(vitals.heightCm, ' cm')} />
    </div>
  )
}

export default function Patient360OverviewPage() {
  const params = useParams<{ id: string }>()
  const id = params.id
  const { apiFetch, isReady } = useApi()
  const [data, setData] = useState<Patient360View | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const setPatientContext = useShellStore((state) => state.setPatient)

  const load = useCallback(async () => {
    try {
      setLoading(true)
      const result = await apiFetch<Patient360View>(`/patient360/${id}`)
      setData(result)
      setError(null)
      const d = result.demographics
      if (d) {
        const birth = new Date(d.dateOfBirth).getTime()
        setPatientContext({
          id: d.id,
          name: `${d.firstName} ${d.lastName}`.trim(),
          mrn: d.mrn,
          dateOfBirth: d.dateOfBirth,
          gender: d.gender,
          age: Number.isNaN(birth) ? 0 : Math.max(0, Math.floor((Date.now() - birth) / 31_557_600_000)),
          allergySummary:
            result.allergies.length > 0
              ? result.allergies.map((a) => a.allergen).join(', ')
              : undefined,
        })
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load patient 360 view')
    } finally {
      setLoading(false)
    }
  }, [apiFetch, id, setPatientContext])

  useEffect(() => {
    if (isReady) void load()
  }, [isReady, load])

  const patient = data?.demographics
  const fullName = patient ? `${patient.firstName} ${patient.lastName}` : 'Patient'

  if (!isReady || loading) {
    return (
      <div style={{ padding: 24, display: 'flex', alignItems: 'center', gap: 12 }}>
        <Spinner size="sm" />
        <span style={{ fontSize: 14, color: 'var(--sq-fg-muted)' }}>
          Loading patient 360…
        </span>
      </div>
    )
  }

  if (error) {
    return (
      <div style={{ padding: 24 }}>
        <StatusIndicator tone="critical" label={error} />
      </div>
    )
  }

  if (!data) {
    return (
      <div style={{ padding: 24 }}>
        <StatusIndicator tone="critical" label="No data available." />
      </div>
    )
  }

  const totalBilled =
    (data.charges ?? []).reduce((sum, c) => sum + (c.totalQar ?? 0), 0)

  const age = patient?.dateOfBirth ? computeAge(patient.dateOfBirth) : ''
  const genderDisplay = patient?.gender
    ? patient.gender.charAt(0).toUpperCase() + patient.gender.slice(1)
    : ''

  return (
    <div style={{ padding: 24 }}>
      <PageHeader
        title={
          <Stack direction="row" gap={8} align="center" wrap>
            <span>{fullName}</span>
            {patient?.mrn ? <Badge variant="neutral">{patient.mrn}</Badge> : null}
          </Stack>
        }
        description={
          <span style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
            {genderDisplay ? <span>{genderDisplay}</span> : null}
            {patient?.dateOfBirth ? (
              <span>{patient.dateOfBirth}{age ? ` (${age})` : ''}</span>
            ) : null}
            {patient?.nationality ? (
              <span style={{ color: 'var(--sq-fg-muted)' }}>· {patient.nationality}</span>
            ) : null}
          </span>
        }
        actions={
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            <StatCard label="Encounters" value={data.totalEncounters} />
            <StatCard
              label="Open Tasks"
              value={data.taskSummary.open}
              variant={data.taskSummary.open > 0 ? 'warning' : undefined}
            />
            <StatCard label="Lab Orders" value={data.totalLabOrders} />
            <StatCard label="Billed" value={formatQar(totalBilled)} />
          </div>
        }
      />

      {error ? <StatusIndicator tone="critical" label={error} /> : null}

      <Stack gap={16}>
        <ContentPanel>
          <div
            style={{
              fontSize: 11,
              fontWeight: 600,
              color: 'var(--sq-fg-muted)',
              textTransform: 'uppercase',
              letterSpacing: '0.04em',
              marginBottom: 8,
            }}
          >
            Demographics
          </div>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px 24px' }}>
            <DemographicField label="MRN" value={patient?.mrn} />
            <DemographicField label="QID" value={patient?.qid} />
            <DemographicField label="Phone" value={patient?.phone} />
            <DemographicField label="Email" value={patient?.email} />
            <DemographicField label="City" value={patient?.city} />
          </div>
        </ContentPanel>

        <ContentPanel bordered={false}>
          <SectionHeading title="Latest Vitals" />
          {data.latestVitals ? (
            <>
              <div style={{ ...mutedText, marginBottom: 8 }}>
                Recorded {formatDateTime(data.latestVitals.recordedAt)}
              </div>
              {renderVitalsGrid(data.latestVitals)}
            </>
          ) : (
            <EmptyState message="No vitals recorded." />
          )}
        </ContentPanel>

        <ContentPanel>
          <SectionHeading
            title="Recent Encounters"
            count={data.encounters.length}
            total={data.totalEncounters}
          />
          {data.encounters.length === 0 ? (
            <EmptyState message="No encounters recorded." />
          ) : (
            <div style={fullBorderStyle}>
              <table style={fullWidthTable}>
                <thead>
                  <tr>
                    <th style={tableHeaderStyle}>Date</th>
                    <th style={tableHeaderStyle}>Type</th>
                    <th style={tableHeaderStyle}>Status</th>
                    <th style={tableHeaderStyle}>Reason</th>
                    <th style={tableHeaderStyle}>Diagnoses</th>
                    <th style={tableHeaderStyle}>Notes</th>
                  </tr>
                </thead>
                <tbody>
                  {data.encounters.map((enc) => (
                    <tr key={enc.encounterId}>
                      <td style={tableCellStyle}>{formatDateTime(enc.date)}</td>
                      <td style={tableCellStyle}>{enc.type}</td>
                      <td style={tableCellStyle}>
                        <Badge variant={encounterStatusVariants[enc.status] ?? 'neutral'} size="sm">
                          {enc.status}
                        </Badge>
                      </td>
                      <td style={{ ...tableCellStyle, maxWidth: 220, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        {enc.reason ?? '—'}
                      </td>
                      <td style={tableCellStyle}>
                        <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
                          {enc.diagnoses.slice(0, 3).map((d, i) => (
                            <Badge key={d.id ?? i} variant="information" size="sm">
                              {d.code}
                            </Badge>
                          ))}
                          {enc.diagnoses.length > 3 ? (
                            <Badge variant="neutral" size="sm">+{enc.diagnoses.length - 3}</Badge>
                          ) : null}
                          {enc.diagnoses.length === 0 ? (
                            <span style={mutedText}>—</span>
                          ) : null}
                        </div>
                      </td>
                      <td style={tableCellStyle}>
                        {enc.noteCount > 0 ? (
                          <Badge variant="neutral" size="sm">{enc.noteCount}</Badge>
                        ) : (
                          <span style={mutedText}>0</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </ContentPanel>

        <div style={twoColGrid}>
          <ContentPanel>
            <SectionHeading title="Allergies" />
            {data.allergies.length === 0 ? (
              <EmptyState message="No allergies recorded." />
            ) : (
              <Stack gap={8}>
                {data.allergies.map((a) => (
                  <div
                    key={a.id}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 8,
                      paddingBottom: 8,
                      borderBottom: '1px solid var(--sq-border)',
                    }}
                  >
                    <Badge variant={allergyVariants[a.severity] ?? 'neutral'} size="sm">
                      {a.severity}
                    </Badge>
                    <span style={{ fontSize: 13, fontWeight: 600 }}>{a.allergen}</span>
                    {a.reaction ? (
                      <span style={mutedText}>{a.reaction}</span>
                    ) : null}
                  </div>
                ))}
              </Stack>
            )}
          </ContentPanel>

          <ContentPanel>
            <SectionHeading title="Medical History" />
            {data.medicalHistory.length === 0 ? (
              <EmptyState message="No medical history recorded." />
            ) : (
              <Stack gap={8}>
                {data.medicalHistory.map((entry) => (
                  <div
                    key={entry.id}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 8,
                      paddingBottom: 8,
                      borderBottom: '1px solid var(--sq-border)',
                    }}
                  >
                    <span style={{ fontSize: 13, fontWeight: 600 }}>{entry.conditionName}</span>
                    {entry.diagnosisCode ? (
                      <Badge variant="neutral" size="sm">{entry.diagnosisCode}</Badge>
                    ) : null}
                    {entry.onsetDate ? (
                      <span style={mutedText}>{entry.onsetDate}</span>
                    ) : null}
                  </div>
                ))}
              </Stack>
            )}
          </ContentPanel>
        </div>

        <ContentPanel>
          <SectionHeading
            title="Lab Orders"
            count={data.labOrders.length}
            total={data.totalLabOrders}
          />
          {data.labOrders.length === 0 ? (
            <EmptyState message="No lab orders." />
          ) : (
            <div style={fullBorderStyle}>
              <table style={fullWidthTable}>
                <thead>
                  <tr>
                    <th style={tableHeaderStyle}>Order #</th>
                    <th style={tableHeaderStyle}>Priority</th>
                    <th style={tableHeaderStyle}>Status</th>
                    <th style={tableHeaderStyle}>Result</th>
                    <th style={tableHeaderStyle}>Ordered</th>
                  </tr>
                </thead>
                <tbody>
                  {data.labOrders.map((lo) => (
                    <tr key={lo.id}>
                      <td style={tableCellStyle}>{lo.orderNumber}</td>
                      <td style={tableCellStyle}>
                        <Badge variant="neutral" size="sm">{lo.priority ?? '—'}</Badge>
                      </td>
                      <td style={tableCellStyle}>
                        <Badge variant={labResultVariants[lo.status] ?? 'neutral'} size="sm">
                          {lo.status}
                        </Badge>
                      </td>
                      <td style={tableCellStyle}>
                        <Badge variant={labResultVariants[lo.resultStatus ?? ''] ?? 'neutral'} size="sm">
                          {lo.resultStatus ?? '—'}
                        </Badge>
                      </td>
                      <td style={tableCellStyle}>{formatDateTime(lo.orderedAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </ContentPanel>

        <div style={twoColGrid}>
          <ContentPanel>
            <SectionHeading title="Medications" count={data.medications.length} />
            {data.medications.length === 0 ? (
              <EmptyState message="No medications dispensed." />
            ) : (
              <div style={fullBorderStyle}>
                <table style={fullWidthTable}>
                  <thead>
                    <tr>
                      <th style={tableHeaderStyle}>Drug</th>
                      <th style={tableHeaderStyle}>Qty</th>
                      <th style={tableHeaderStyle}>Status</th>
                      <th style={tableHeaderStyle}>Total</th>
                      <th style={tableHeaderStyle}>Date</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.medications.map((m) => (
                      <tr key={m.id}>
                        <td style={tableCellStyle}>{m.drugName}</td>
                        <td style={tableCellStyle}>{m.quantity}</td>
                        <td style={tableCellStyle}>
                          <Badge variant={medStatusVariants[m.status] ?? 'neutral'} size="sm">
                            {m.status}
                          </Badge>
                        </td>
                        <td style={tableCellStyle}>{formatQar(m.totalPrice)}</td>
                        <td style={tableCellStyle}>{formatDate(m.dispensedAt)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </ContentPanel>

          <ContentPanel>
            <SectionHeading
              title="Tasks"
              count={data.tasks.length}
              suffix="—"
            />
            <div style={{ display: 'flex', gap: 8, marginBottom: 12 }}>
              <Badge variant="warning" size="sm">{data.taskSummary.open} open</Badge>
              <Badge variant="success" size="sm">{data.taskSummary.completed} completed</Badge>
            </div>
            {data.tasks.length === 0 ? (
              <EmptyState message="No tasks." />
            ) : (
              <div style={fullBorderStyle}>
                <table style={fullWidthTable}>
                  <thead>
                    <tr>
                      <th style={tableHeaderStyle}>Title</th>
                      <th style={tableHeaderStyle}>Status</th>
                      <th style={tableHeaderStyle}>Priority</th>
                      <th style={tableHeaderStyle}>Due</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.tasks.map((t) => (
                      <tr key={t.id ?? t.title}>
                        <td style={{ ...tableCellStyle, maxWidth: 220, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                          {t.title}
                        </td>
                        <td style={tableCellStyle}>
                          <Badge variant={taskStatusVariants[t.status] ?? 'neutral'} size="sm">
                            {t.status}
                          </Badge>
                        </td>
                        <td style={tableCellStyle}>
                          {t.priority ? (
                            <Badge variant={taskPriorityVariants[t.priority] ?? 'neutral'} size="sm">
                              {t.priority}
                            </Badge>
                          ) : (
                            <span style={mutedText}>—</span>
                          )}
                        </td>
                        <td style={tableCellStyle}>{formatDate(t.dueAt)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </ContentPanel>
        </div>

        <div style={twoColGrid}>
          <ContentPanel>
            <SectionHeading title="Charges" count={data.charges.length} />
            {data.charges.length === 0 ? (
              <EmptyState message="No charges recorded." />
            ) : (
              <div style={fullBorderStyle}>
                <table style={fullWidthTable}>
                  <thead>
                    <tr>
                      <th style={tableHeaderStyle}>Name</th>
                      <th style={tableHeaderStyle}>Qty</th>
                      <th style={tableHeaderStyle}>Unit Price</th>
                      <th style={tableHeaderStyle}>Total</th>
                      <th style={tableHeaderStyle}>Status</th>
                      <th style={tableHeaderStyle}>Date</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.charges.map((c) => (
                      <tr key={c.id}>
                        <td style={tableCellStyle}>{c.chargeName ?? '—'}</td>
                        <td style={tableCellStyle}>{c.quantity}</td>
                        <td style={tableCellStyle}>{formatQar(c.unitPriceQar)}</td>
                        <td style={tableCellStyle}>{formatQar(c.totalQar)}</td>
                        <td style={tableCellStyle}>
                          <Badge variant={chargeStatusVariants[c.status] ?? 'neutral'} size="sm">
                            {c.status}
                          </Badge>
                        </td>
                        <td style={tableCellStyle}>{formatDate(c.postedAt)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </ContentPanel>

          <ContentPanel>
            <SectionHeading
              title="Invoices"
              count={data.invoices.length}
              total={data.totalInvoices}
            />
            {data.invoices.length === 0 ? (
              <EmptyState message="No invoices generated." />
            ) : (
              <div style={fullBorderStyle}>
                <table style={fullWidthTable}>
                  <thead>
                    <tr>
                      <th style={tableHeaderStyle}>Invoice #</th>
                      <th style={tableHeaderStyle}>Total</th>
                      <th style={tableHeaderStyle}>Paid</th>
                      <th style={tableHeaderStyle}>Due</th>
                      <th style={tableHeaderStyle}>Status</th>
                      <th style={tableHeaderStyle}>Issued</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.invoices.map((inv) => (
                      <tr key={inv.id}>
                        <td style={tableCellStyle}>{inv.invoiceNumber}</td>
                        <td style={tableCellStyle}>{formatQar(inv.totalQar)}</td>
                        <td style={tableCellStyle}>{formatQar(inv.amountPaidQar)}</td>
                        <td style={{ ...tableCellStyle, fontWeight: inv.amountDueQar > 0 ? 600 : 400 }}>
                          {formatQar(inv.amountDueQar)}
                        </td>
                        <td style={tableCellStyle}>
                          <Badge variant={invoiceStatusVariants[inv.status] ?? 'neutral'} size="sm">
                            {inv.status}
                          </Badge>
                        </td>
                        <td style={tableCellStyle}>{formatDate(inv.issuedAt)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </ContentPanel>
        </div>

        <div style={twoColGrid}>
          <ContentPanel>
            <SectionHeading title="Appointments" count={data.appointments.length} />
            {data.appointments.length === 0 ? (
              <EmptyState message="No appointments scheduled." />
            ) : (
              <div style={fullBorderStyle}>
                <table style={fullWidthTable}>
                  <thead>
                    <tr>
                      <th style={tableHeaderStyle}>Date</th>
                      <th style={tableHeaderStyle}>Type</th>
                      <th style={tableHeaderStyle}>Status</th>
                      <th style={tableHeaderStyle}>Doctor</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.appointments.map((appt) => (
                      <tr key={appt.id}>
                        <td style={tableCellStyle}>{formatDateTime(appt.scheduledStart)}</td>
                        <td style={tableCellStyle}>{appt.type ?? '—'}</td>
                        <td style={tableCellStyle}>
                          <Badge variant={encounterStatusVariants[appt.status ?? ''] ?? 'neutral'} size="sm">
                            {appt.status ?? '—'}
                          </Badge>
                        </td>
                        <td style={tableCellStyle}>
                          {appt.doctorId ? (
                            <Badge variant="neutral" size="sm">Doctor</Badge>
                          ) : (
                            <span style={mutedText}>—</span>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </ContentPanel>

          <ContentPanel>
            <SectionHeading title="Visits" count={data.visits.length} />
            {data.visits.length === 0 ? (
              <EmptyState message="No visits recorded." />
            ) : (
              <div style={fullBorderStyle}>
                <table style={fullWidthTable}>
                  <thead>
                    <tr>
                      <th style={tableHeaderStyle}>Visit #</th>
                      <th style={tableHeaderStyle}>Type</th>
                      <th style={tableHeaderStyle}>Status</th>
                      <th style={tableHeaderStyle}>Priority</th>
                      <th style={tableHeaderStyle}>Source</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.visits.map((v) => (
                      <tr key={v.id ?? v.visitNumber}>
                        <td style={tableCellStyle}>{v.visitNumber}</td>
                        <td style={tableCellStyle}>{v.visitType}</td>
                        <td style={tableCellStyle}>
                          <Badge variant={encounterStatusVariants[v.status] ?? 'neutral'} size="sm">
                            {v.status}
                          </Badge>
                        </td>
                        <td style={tableCellStyle}>
                          <Badge variant="neutral" size="sm">{v.priority}</Badge>
                        </td>
                        <td style={tableCellStyle}>{v.source}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </ContentPanel>
        </div>
      </Stack>
    </div>
  )
}

function DemographicField({ label, value }: { label: string; value?: string | null }) {
  if (!value) return null
  return (
    <div>
      <div style={labelStyle}>{label}</div>
      <div style={{ fontSize: 13 }}>{value}</div>
    </div>
  )
}
