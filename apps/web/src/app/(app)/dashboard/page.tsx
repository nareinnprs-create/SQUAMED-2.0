'use client'

import { useCallback, useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import {
  Badge,
  Button,
  ContentPanel,
  Grid,
  GridItem,
  PageHeader,
  Spinner,
  Stack,
  StatusIndicator,
} from '@squamed/ui'
import { useApi } from '../../../lib/use-api'

interface NameCount {
  name: string
  count: number
}

interface TimePoint {
  label: string
  count: number
}

interface OverviewStats {
  totalPatients: number
  patientsToday: number
  activeEncounters: number
  encountersToday: number
  completedEncountersToday: number
  appointmentsToday: number
  pendingAppointments: number
  queueWaiting: number
  avgWaitMinutes: number | null
  labOrdersToday: number
  labPending: number
  invoicesToday: number
  revenueTodayQar: number
  amountDueQar: number
  dispensationsToday: number
}

interface PatientsStats {
  total: number
  byGender: NameCount[]
  byBloodGroup: NameCount[]
  registrationsByMonth: TimePoint[]
  byNationality: NameCount[]
}

interface EncountersStats {
  total: number
  today: number
  byStatus: NameCount[]
  byType: NameCount[]
  avgDurationMinutes: number | null
}

interface AppointmentsStats {
  total: number
  today: number
  byStatus: NameCount[]
  byType: NameCount[]
  noShowRate: number | null
}

interface QueueStats {
  waiting: number
  calledToday: number
  completedToday: number
  avgWaitMinutes: number | null
  byServicePoint: NameCount[]
  byQueueType: NameCount[]
}

interface LabStats {
  total: number
  today: number
  byStatus: NameCount[]
  byResultStatus: NameCount[]
  releasedToday: number
}

interface BillingStats {
  revenueTodayQar: number
  revenueThisWeekQar: number
  revenueThisMonthQar: number
  outstandingQar: number
  byStatus: NameCount[]
  chargesPostedToday: number
  chargesByCategory: NameCount[]
}

interface AnalyticsData {
  overview: OverviewStats
  patients: PatientsStats
  encounters: EncountersStats
  appointments: AppointmentsStats
  queue: QueueStats
  lab: LabStats
  billing: BillingStats
}

const palette = [
  'var(--sq-primary)',
  'var(--sq-information)',
  'var(--sq-success)',
  'var(--sq-warning)',
  'var(--sq-critical)',
  'var(--sq-secondary)',
]

const statusColors: Record<string, string> = {
  'in-progress': 'var(--sq-information)',
  completed: 'var(--sq-success)',
  cancelled: 'var(--sq-critical)',
  scheduled: 'var(--sq-primary)',
  'no-show': 'var(--sq-warning)',
  waiting: 'var(--sq-warning)',
  called: 'var(--sq-information)',
  paid: 'var(--sq-success)',
  partially_paid: 'var(--sq-warning)',
  unpaid: 'var(--sq-warning)',
  due: 'var(--sq-warning)',
  released: 'var(--sq-success)',
  resulted: 'var(--sq-information)',
  pending: 'var(--sq-warning)',
  ordered: 'var(--sq-fg-muted)',
}

function formatQar(value: number): string {
  return `${value.toLocaleString(undefined, { maximumFractionDigits: 0 })} QAR`
}

function StatCard({ label, value }: { label: string; value: string | number }) {
  return (
    <ContentPanel>
      <Stack gap={8}>
        <span style={{ fontSize: 13, color: 'var(--sq-fg-muted)' }}>{label}</span>
        <strong style={{ fontSize: 22, lineHeight: 1.2 }}>{value}</strong>
      </Stack>
    </ContentPanel>
  )
}

function PanelHeading({ children }: { children: ReactNode }) {
  return <strong style={{ fontSize: 14 }}>{children}</strong>
}

function PanelLabel({ children }: { children: ReactNode }) {
  return (
    <div
      style={{
        fontSize: 11,
        fontWeight: 600,
        color: 'var(--sq-fg-muted)',
        textTransform: 'uppercase',
        letterSpacing: '0.04em',
        margin: '14px 0 6px',
      }}
    >
      {children}
    </div>
  )
}

function DistributionBar({
  items,
  colors,
}: {
  items: NameCount[]
  colors?: Record<string, string>
}) {
  const total = items.reduce((sum, item) => sum + item.count, 0)
  if (items.length === 0 || total === 0) {
    return (
      <div style={{ fontSize: 12, color: 'var(--sq-fg-muted)', padding: '8px 0' }}>
        No data.
      </div>
    )
  }
  const colorFor = (name: string, index: number) => colors?.[name] ?? palette[index % palette.length]
  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'flex-end', height: 16, marginBottom: 4 }}>
        {items.map((item, index) => (
          <div
            key={item.name}
            style={{
              flexGrow: item.count,
              flexBasis: 0,
              textAlign: 'center',
              fontSize: 10,
              fontWeight: 600,
              color: colorFor(item.name, index),
            }}
          >
            {Math.round((item.count / total) * 100)}%
          </div>
        ))}
      </div>
      <div
        style={{
          display: 'flex',
          width: '100%',
          height: 8,
          borderRadius: 4,
          overflow: 'hidden',
          backgroundColor: 'var(--sq-secondary)',
        }}
      >
        {items.map((item, index) => (
          <div
            key={item.name}
            style={{ flexGrow: item.count, flexBasis: 0, backgroundColor: colorFor(item.name, index) }}
          />
        ))}
      </div>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: '4px 14px', marginTop: 8 }}>
        {items.map((item, index) => (
          <span
            key={item.name}
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: 4,
              fontSize: 11,
              color: 'var(--sq-fg-muted)',
            }}
          >
            <span style={{ width: 8, height: 8, borderRadius: 2, backgroundColor: colorFor(item.name, index) }} />
            {item.name} → {item.count}
          </span>
        ))}
      </div>
    </div>
  )
}

function RegistrationsChart({ data }: { data: TimePoint[] }) {
  const width = 460
  const height = 140
  const slot = width / data.length
  const barArea = 108
  const baseline = 132
  const max = Math.max(1, ...data.map((tp) => tp.count))
  return (
    <svg
      width={width}
      height={height}
      viewBox={`0 0 ${width} ${height}`}
      style={{ width: '100%', maxWidth: width }}
      role="img"
      aria-label="Patient registrations per month"
    >
      {data.map((tp, index) => {
        const barWidth = Math.max(2, slot - 10)
        const barHeight = tp.count === 0 ? 2 : Math.max(2, (tp.count / max) * (barArea - 6))
        const x = index * slot + 5
        const y = barArea - barHeight
        return (
          <g key={tp.label}>
            <rect x={x} y={y} width={barWidth} height={barHeight} rx={2} fill="var(--sq-primary)">
              <title>
                {tp.label}: {tp.count}
              </title>
            </rect>
            <text x={x + barWidth / 2} y={baseline} textAnchor="middle" fontSize={10} fill="var(--sq-fg-muted)">
              {tp.label}
            </text>
          </g>
        )
      })}
    </svg>
  )
}

export default function DashboardPage() {
  const { apiFetch, isReady } = useApi()
  const [data, setData] = useState<AnalyticsData | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    try {
      setLoading(true)
      setError(null)
      const [overview, patients, encounters, appointments, queue, lab, billing] = await Promise.all([
        apiFetch<OverviewStats>('/analytics/overview'),
        apiFetch<PatientsStats>('/analytics/patients'),
        apiFetch<EncountersStats>('/analytics/encounters'),
        apiFetch<AppointmentsStats>('/analytics/appointments'),
        apiFetch<QueueStats>('/analytics/queue'),
        apiFetch<LabStats>('/analytics/lab'),
        apiFetch<BillingStats>('/analytics/billing'),
      ])
      setData({ overview, patients, encounters, appointments, queue, lab, billing })
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load analytics')
    } finally {
      setLoading(false)
    }
  }, [apiFetch])

  useEffect(() => {
    if (isReady) void load()
  }, [isReady, load])

  if (!isReady || (loading && !data)) {
    return (
      <div style={{ padding: 24, display: 'flex', alignItems: 'center', gap: 12 }}>
        <Spinner size="sm" />
        <span style={{ fontSize: 14, color: 'var(--sq-fg-muted)' }}>Loading analytics…</span>
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
        <StatusIndicator tone="critical" label="No analytics data available." />
      </div>
    )
  }

  return (
    <div style={{ padding: 24 }}>
      <PageHeader
        title="Operations Dashboard"
        description="Live hospital analytics."
        actions={
          <Button size="sm" variant="outline" onClick={() => void load()} loading={loading}>
            Refresh
          </Button>
        }
      />
      {loading ? <StatusIndicator tone="information" label="Loading analytics…" /> : null}
      <Stack gap={16}>
        <Grid cols={4} gap={16}>
          <GridItem span={1}>
            <StatCard label="Revenue today" value={formatQar(data.billing.revenueTodayQar)} />
          </GridItem>
          <GridItem span={1}>
            <StatCard label="Revenue this week" value={formatQar(data.billing.revenueThisWeekQar)} />
          </GridItem>
          <GridItem span={1}>
            <StatCard label="Revenue this month" value={formatQar(data.billing.revenueThisMonthQar)} />
          </GridItem>
          <GridItem span={1}>
            <StatCard label="Outstanding" value={formatQar(data.billing.outstandingQar)} />
          </GridItem>
        </Grid>

        <Grid cols={6} gap={16}>
          <GridItem span={1}>
            <StatCard label="Patients today" value={data.overview.patientsToday} />
          </GridItem>
          <GridItem span={1}>
            <StatCard label="Active encounters" value={data.overview.activeEncounters} />
          </GridItem>
          <GridItem span={1}>
            <StatCard label="Appointments today" value={data.overview.appointmentsToday} />
          </GridItem>
          <GridItem span={1}>
            <StatCard label="Queue waiting" value={data.overview.queueWaiting} />
          </GridItem>
          <GridItem span={1}>
            <StatCard label="Lab orders today" value={data.overview.labOrdersToday} />
          </GridItem>
          <GridItem span={1}>
            <StatCard label="Lab pending" value={data.overview.labPending} />
          </GridItem>
        </Grid>

        <ContentPanel>
          <PanelHeading>Patient registrations (6 months)</PanelHeading>
          {data.patients.registrationsByMonth.length === 0 ? (
            <div style={{ padding: '16px 0', textAlign: 'center', fontSize: 13, color: 'var(--sq-fg-muted)' }}>
              No registration data.
            </div>
          ) : (
            <div style={{ marginTop: 12 }}>
              <RegistrationsChart data={data.patients.registrationsByMonth} />
            </div>
          )}
        </ContentPanel>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: 16 }}>
          <ContentPanel>
            <PanelHeading>Encounters</PanelHeading>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap', marginTop: 10 }}>
              <Badge variant="information">{data.encounters.today} today</Badge>
              <span style={{ fontSize: 12, color: 'var(--sq-fg-muted)' }}>
                Avg duration:{' '}
                {data.encounters.avgDurationMinutes != null ? `${data.encounters.avgDurationMinutes} min` : '—'}
              </span>
            </div>
            <PanelLabel>By status</PanelLabel>
            <DistributionBar items={data.encounters.byStatus} colors={statusColors} />
            <PanelLabel>By type</PanelLabel>
            <DistributionBar items={data.encounters.byType} />
          </ContentPanel>

          <ContentPanel>
            <PanelHeading>Appointments</PanelHeading>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap', marginTop: 10 }}>
              <Badge variant="information">{data.appointments.today} today</Badge>
              <Badge variant="neutral">{data.appointments.total} total</Badge>
              <Badge variant="warning">
                no-show {data.appointments.noShowRate != null ? `${data.appointments.noShowRate}%` : '—'}
              </Badge>
            </div>
            <PanelLabel>By status</PanelLabel>
            <DistributionBar items={data.appointments.byStatus} colors={statusColors} />
            <PanelLabel>By type</PanelLabel>
            <DistributionBar items={data.appointments.byType} />
          </ContentPanel>

          <ContentPanel>
            <PanelHeading>Queue</PanelHeading>
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginTop: 10 }}>
              <Badge variant="warning">{data.queue.waiting} waiting</Badge>
              <Badge variant="information">{data.queue.calledToday} called today</Badge>
              <Badge variant="success">{data.queue.completedToday} completed today</Badge>
            </div>
            <PanelLabel>By queue type</PanelLabel>
            <DistributionBar items={data.queue.byQueueType} colors={statusColors} />
            <PanelLabel>By service point</PanelLabel>
            <DistributionBar items={data.queue.byServicePoint} />
          </ContentPanel>

          <ContentPanel>
            <PanelHeading>Laboratory</PanelHeading>
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginTop: 10 }}>
              <Badge variant="success">{data.lab.releasedToday} released today</Badge>
            </div>
            <PanelLabel>By status</PanelLabel>
            <DistributionBar items={data.lab.byStatus} colors={statusColors} />
            <PanelLabel>By result status</PanelLabel>
            <DistributionBar items={data.lab.byResultStatus} colors={statusColors} />
          </ContentPanel>

          <ContentPanel>
            <PanelHeading>Patients</PanelHeading>
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginTop: 10 }}>
              <Badge variant="neutral">{data.patients.total} total</Badge>
            </div>
            <PanelLabel>By gender</PanelLabel>
            <DistributionBar items={data.patients.byGender} />
            <PanelLabel>By blood group</PanelLabel>
            <DistributionBar items={data.patients.byBloodGroup} />
            <PanelLabel>By nationality (top 5)</PanelLabel>
            <DistributionBar items={data.patients.byNationality.slice(0, 5)} />
          </ContentPanel>

          <ContentPanel>
            <PanelHeading>Billing</PanelHeading>
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginTop: 10 }}>
              <Badge variant="neutral">{data.billing.chargesPostedToday} charges posted today</Badge>
            </div>
            <PanelLabel>By status</PanelLabel>
            <DistributionBar items={data.billing.byStatus} colors={statusColors} />
            <PanelLabel>By category</PanelLabel>
            <DistributionBar items={data.billing.chargesByCategory} />
          </ContentPanel>
        </div>
      </Stack>
    </div>
  )
}