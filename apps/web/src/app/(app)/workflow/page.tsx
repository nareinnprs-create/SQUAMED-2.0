'use client'

import { useCallback, useEffect, useState } from 'react'
import {
  Badge,
  Button,
  ContentPanel,
  Input,
  Modal,
  PageHeader,
  Select,
  Spinner,
  Stack,
  StatusIndicator,
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
  Textarea,
  Timeline,
  TimelineItem,
} from '@squamed/ui'
import type { BadgeVariant, TimelineTone } from '@squamed/ui'
import { useApi } from '@/lib/use-api'

interface WorkflowStepConfig {
  stepKey: string
  stepType: string
  sequenceOrder: number
  config?: Record<string, unknown> | null
}

interface WorkflowDefinition {
  id: string | null
  code: string
  name: string
  description?: string | null
  triggerType: string
  status: string
  version: number
  steps: WorkflowStepConfig[]
}

interface WorkflowStepInstance {
  id?: string | null
  stepKey: string
  stepType: string
  status: string
  stepConfig?: string | null
  result?: string | null
  sequenceOrder: number
  startedAt?: string | null
  endedAt?: string | null
}

interface WorkflowApproval {
  id?: string | null
  stepInstanceId?: string | null
  approverId: string
  status: string
  comments?: string | null
  decidedBy?: string | null
  decidedAt?: string | null
}

interface WorkflowInstance {
  id?: string | null
  definitionId?: string | null
  definitionCode?: string | null
  status: string
  triggerPayload?: string | null
  currentStepKey?: string | null
  startedAt?: string | null
  endedAt?: string | null
  stepInstances: WorkflowStepInstance[]
  approvals: WorkflowApproval[]
}

interface WorkflowPendingApproval {
  approvalId?: string | null
  instanceId?: string | null
  instanceStatus?: string | null
  definitionCode?: string | null
  stepKey: string
  stepType: string
  approverId: string
  status: string
  comments?: string | null
  decidedBy?: string | null
  decidedAt?: string | null
  createdAt?: string | null
}

interface StepDraft {
  stepKey: string
  stepType: string
}

const STEP_TYPES = ['TASK', 'APPROVAL', 'NOTIFICATION', 'ESCALATION', 'ACTION', 'CONDITION', 'WAIT']

const INSTANCE_STATUSES = ['RUNNING', 'WAITING_APPROVAL', 'WAITING', 'COMPLETED', 'TERMINATED', 'FAILED']

const definitionStatusVariants: Record<string, BadgeVariant> = {
  DRAFT: 'neutral',
  ACTIVE: 'success',
  DISABLED: 'neutral',
}

const instanceStatusVariants: Record<string, BadgeVariant> = {
  RUNNING: 'information',
  WAITING_APPROVAL: 'warning',
  WAITING: 'warning',
  COMPLETED: 'success',
  TERMINATED: 'neutral',
  FAILED: 'critical',
}

const stepStatusVariants: Record<string, BadgeVariant> = {
  PENDING: 'neutral',
  RUNNING: 'information',
  COMPLETED: 'success',
  WAITING: 'warning',
  WAITING_APPROVAL: 'warning',
  FAILED: 'critical',
  TERMINATED: 'critical',
  SKIPPED: 'neutral',
}

const stepStatusTones: Record<string, TimelineTone> = {
  PENDING: 'default',
  RUNNING: 'information',
  COMPLETED: 'success',
  WAITING: 'warning',
  WAITING_APPROVAL: 'warning',
  FAILED: 'critical',
  TERMINATED: 'critical',
  SKIPPED: 'default',
}

const approvalStatusVariants: Record<string, BadgeVariant> = {
  PENDING: 'warning',
  APPROVED: 'success',
  REJECTED: 'critical',
}

const emptyCreateForm = {
  code: '',
  name: '',
  description: '',
  triggerType: 'MANUAL',
  steps: [{ stepKey: '', stepType: 'TASK' }] as StepDraft[],
}

const emptyTriggerForm = { definitionId: '', payload: '' }

function formatDateTime(value: string | null | undefined): string {
  if (!value) return '—'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return '—'
  return d.toLocaleString(undefined, {
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  })
}

function shortId(value: string | null | undefined): string {
  if (!value) return '—'
  return value.length > 8 ? `${value.slice(0, 8)}…` : value
}

function parseJsonPayload(raw: string): Record<string, unknown> | undefined {
  const trimmed = raw.trim()
  if (!trimmed) return undefined
  return JSON.parse(trimmed) as Record<string, unknown>
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

const sectionLabelStyle = {
  fontSize: 11,
  fontWeight: 600,
  color: 'var(--sq-fg-muted)',
  textTransform: 'uppercase' as const,
  letterSpacing: '0.04em',
  marginBottom: 8,
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

function EmptyState({ message }: { message: string }) {
  return (
    <div
      style={{
        padding: '28px 16px',
        textAlign: 'center',
        fontSize: 13,
        color: 'var(--sq-fg-muted)',
      }}
    >
      {message}
    </div>
  )
}

function ApprovalActions({
  busy,
  comments,
  onCommentsChange,
  onDecide,
}: {
  busy: boolean
  comments: string
  onCommentsChange: (comments: string) => void
  onDecide: (decision: 'APPROVED' | 'REJECTED') => void
}) {
  return (
    <Stack gap={8} align="flex-start">
      <Input
        size="sm"
        placeholder="Comments (optional)"
        value={comments}
        onChange={(e) => onCommentsChange(e.target.value)}
      />
      <Stack direction="row" gap={8}>
        <Button size="sm" variant="success" loading={busy} onClick={() => onDecide('APPROVED')}>
          Approve
        </Button>
        <Button size="sm" variant="danger" disabled={busy} onClick={() => onDecide('REJECTED')}>
          Reject
        </Button>
      </Stack>
    </Stack>
  )
}

export default function WorkflowPage() {
  const { apiFetch, isReady } = useApi()
  const [activeTab, setActiveTab] = useState('definitions')

  const [definitions, setDefinitions] = useState<WorkflowDefinition[]>([])
  const [definitionsLoading, setDefinitionsLoading] = useState(true)
  const [definitionsError, setDefinitionsError] = useState<string | null>(null)

  const [createOpen, setCreateOpen] = useState(false)
  const [creating, setCreating] = useState(false)
  const [createForm, setCreateForm] = useState<typeof emptyCreateForm>(emptyCreateForm)
  const [busyDefAction, setBusyDefAction] = useState<string | null>(null)

  const [instances, setInstances] = useState<WorkflowInstance[]>([])
  const [instancesLoading, setInstancesLoading] = useState(true)
  const [instancesError, setInstancesError] = useState<string | null>(null)
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [selectedInstanceId, setSelectedInstanceId] = useState<string | null>(null)

  const [triggerOpen, setTriggerOpen] = useState(false)
  const [triggering, setTriggering] = useState(false)
  const [triggerForm, setTriggerForm] = useState(emptyTriggerForm)

  const [detail, setDetail] = useState<WorkflowInstance | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [detailError, setDetailError] = useState<string | null>(null)
  const [busyApproval, setBusyApproval] = useState<string | null>(null)
  const [busyResume, setBusyResume] = useState(false)
  const [decisionComments, setDecisionComments] = useState<Record<string, string>>({})

  const [pending, setPending] = useState<WorkflowPendingApproval[]>([])
  const [pendingLoading, setPendingLoading] = useState(true)
  const [pendingError, setPendingError] = useState<string | null>(null)

  const loadDefinitions = useCallback(async () => {
    try {
      setDefinitionsLoading(true)
      const result = await apiFetch<WorkflowDefinition[]>('/workflow/definitions')
      setDefinitions(Array.isArray(result) ? result : [])
      setDefinitionsError(null)
    } catch (e) {
      setDefinitionsError(e instanceof Error ? e.message : 'Failed to load definitions')
    } finally {
      setDefinitionsLoading(false)
    }
  }, [apiFetch])

  const loadInstances = useCallback(
    async (filter: string, silent = false) => {
      if (!silent) setInstancesLoading(true)
      const query = filter === 'ALL' ? '' : `?status=${encodeURIComponent(filter)}`
      try {
        const result = await apiFetch<WorkflowInstance[]>(`/workflow/instances${query}`)
        setInstances(Array.isArray(result) ? result : [])
        setInstancesError(null)
      } catch (e) {
        setInstancesError(e instanceof Error ? e.message : 'Failed to load instances')
      } finally {
        if (!silent) setInstancesLoading(false)
      }
    },
    [apiFetch],
  )

  const loadPending = useCallback(async (silent = false) => {
    if (!silent) setPendingLoading(true)
    try {
      const result = await apiFetch<WorkflowPendingApproval[]>('/workflow/approvals/pending')
      setPending(Array.isArray(result) ? result : [])
      setPendingError(null)
    } catch (e) {
      setPendingError(e instanceof Error ? e.message : 'Failed to load pending approvals')
    } finally {
      if (!silent) setPendingLoading(false)
    }
  }, [apiFetch])

  const loadDetail = useCallback(
    async (instanceId: string) => {
      try {
        setDetailLoading(true)
        const result = await apiFetch<WorkflowInstance>(`/workflow/instances/${instanceId}`)
        setDetail(result)
        setDetailError(null)
      } catch (e) {
        setDetailError(e instanceof Error ? e.message : 'Failed to load instance detail')
      } finally {
        setDetailLoading(false)
      }
    },
    [apiFetch],
  )

  useEffect(() => {
    if (!isReady) return
    void loadDefinitions()
    void loadPending()
  }, [isReady, loadDefinitions, loadPending])

  useEffect(() => {
    if (!isReady) return
    void loadInstances(statusFilter)
  }, [isReady, loadInstances, statusFilter])

  useEffect(() => {
    if (!isReady || !selectedInstanceId) return
    void loadDetail(selectedInstanceId)
  }, [isReady, selectedInstanceId, loadDetail])

  function updateStep(index: number, step: StepDraft) {
    setCreateForm((previous) => ({
      ...previous,
      steps: previous.steps.map((s, i) => (i === index ? step : s)),
    }))
  }

  function removeStep(index: number) {
    setCreateForm((previous) => ({
      ...previous,
      steps: previous.steps.filter((_, i) => i !== index),
    }))
  }

  async function createDefinition() {
    setCreating(true)
    try {
      await apiFetch<WorkflowDefinition>('/workflow/definitions', {
        method: 'POST',
        body: JSON.stringify({
          code: createForm.code,
          name: createForm.name,
          description: createForm.description || undefined,
          triggerType: 'MANUAL',
          steps: createForm.steps
            .filter((step) => step.stepKey.trim())
            .map((step, index) => ({
              stepKey: step.stepKey.trim(),
              stepType: step.stepType,
              sequenceOrder: index,
            })),
        }),
      })
      setCreateOpen(false)
      setCreateForm(emptyCreateForm)
      void loadDefinitions()
    } catch (e) {
      setDefinitionsError(e instanceof Error ? e.message : 'Failed to create definition')
    } finally {
      setCreating(false)
    }
  }

  async function changeDefinitionStatus(def: WorkflowDefinition, action: 'activate' | 'deactivate') {
    if (!def.id) return
    setBusyDefAction(`${action}:${def.id}`)
    try {
      await apiFetch<WorkflowDefinition>(`/workflow/definitions/${def.id}/${action}`, {
        method: 'POST',
        body: '{}',
      })
      void loadDefinitions()
    } catch (e) {
      setDefinitionsError(e instanceof Error ? e.message : `Failed to ${action} definition`)
    } finally {
      setBusyDefAction(null)
    }
  }

  async function triggerInstance() {
    const definitionId = triggerForm.definitionId
    if (!definitionId) return
    let payload: Record<string, unknown> | undefined
    try {
      payload = parseJsonPayload(triggerForm.payload)
    } catch (e) {
      setInstancesError(e instanceof Error ? e.message : 'Payload must be valid JSON')
      return
    }
    setTriggering(true)
    try {
      const instance = await apiFetch<WorkflowInstance>('/workflow/instances', {
        method: 'POST',
        body: JSON.stringify({ definitionId, payload }),
      })
      setTriggerOpen(false)
      setTriggerForm(emptyTriggerForm)
      void loadInstances(statusFilter, true)
      if (instance.id) {
        setSelectedInstanceId(instance.id)
        void loadDetail(instance.id)
      }
    } catch (e) {
      setInstancesError(e instanceof Error ? e.message : 'Failed to trigger workflow')
    } finally {
      setTriggering(false)
    }
  }

  async function decide(instanceId: string, approvalId: string, decision: 'APPROVED' | 'REJECTED') {
    setBusyApproval(approvalId)
    try {
      await apiFetch<WorkflowInstance>(`/workflow/instances/${instanceId}/approvals/${approvalId}/decide`, {
        method: 'POST',
        body: JSON.stringify({
          decision,
          comments: decisionComments[approvalId] || undefined,
        }),
      })
      setDecisionComments((previous) => ({ ...previous, [approvalId]: '' }))
      void loadPending(true)
      void loadInstances(statusFilter, true)
      if (selectedInstanceId === instanceId) void loadDetail(instanceId)
    } catch (e) {
      setPendingError(e instanceof Error ? e.message : 'Failed to decide approval')
    } finally {
      setBusyApproval(null)
    }
  }

  async function resumeInstance() {
    if (!selectedInstanceId) return
    setBusyResume(true)
    try {
      await apiFetch<WorkflowInstance>(`/workflow/instances/${selectedInstanceId}/resume`, {
        method: 'POST',
        body: '{}',
      })
      void loadDetail(selectedInstanceId)
      void loadInstances(statusFilter, true)
    } catch (e) {
      setDetailError(e instanceof Error ? e.message : 'Failed to resume workflow')
    } finally {
      setBusyResume(false)
    }
  }

  if (!isReady) {
    return (
      <div style={{ padding: 24, display: 'flex', alignItems: 'center', gap: 12 }}>
        <Spinner size="sm" />
        <span style={{ fontSize: 14, color: 'var(--sq-fg-muted)' }}>Loading workflow…</span>
      </div>
    )
  }

  return (
    <div style={{ padding: 24 }}>
      <PageHeader
        title="Workflow"
        description="Automated clinical and operational workflows."
        actions={
          pending.length > 0 ? (
            <Button variant="outline" size="sm" onClick={() => setActiveTab('approvals')}>
              <Badge variant="warning" size="sm">{pending.length}</Badge>
              pending approval{pending.length === 1 ? '' : 's'}
            </Button>
          ) : null
        }
      />

      <Tabs value={activeTab} onValueChange={setActiveTab}>
        <TabsList style={{ marginBottom: 16 }}>
          <TabsTrigger value="definitions">Definitions</TabsTrigger>
          <TabsTrigger value="instances">Instances</TabsTrigger>
          <TabsTrigger value="approvals">
            Approvals{pending.length > 0 ? ` (${pending.length})` : ''}
          </TabsTrigger>
        </TabsList>

        <TabsContent value="definitions">
          <ContentPanel>
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                gap: 12,
                marginBottom: 12,
              }}
            >
              <strong style={{ fontSize: 14 }}>Definitions</strong>
              <Stack direction="row" gap={8} align="center">
                {pending.length > 0 ? (
                  <Button variant="ghost" size="sm" onClick={() => setActiveTab('approvals')}>
                    <Badge variant="warning" size="sm">{pending.length}</Badge>
                    pending approvals
                  </Button>
                ) : null}
                <Button size="sm" onClick={() => setCreateOpen(true)}>
                  New definition
                </Button>
              </Stack>
            </div>

            {definitionsError ? <StatusIndicator tone="critical" label={definitionsError} /> : null}

            {definitionsLoading ? (
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '16px 0' }}>
                <Spinner size="sm" />
                <span style={{ fontSize: 13, color: 'var(--sq-fg-muted)' }}>Loading definitions…</span>
              </div>
            ) : definitions.length === 0 ? (
              <EmptyState message="No definitions yet." />
            ) : (
              <div style={fullBorderStyle}>
                <table style={fullWidthTable}>
                  <thead>
                    <tr>
                      <th style={tableHeaderStyle}>Code</th>
                      <th style={tableHeaderStyle}>Name</th>
                      <th style={tableHeaderStyle}>Status</th>
                      <th style={tableHeaderStyle}>Version</th>
                      <th style={tableHeaderStyle}>Steps</th>
                      <th style={tableHeaderStyle}>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {definitions.map((def) => (
                      <tr key={def.id ?? def.code}>
                        <td style={tableCellStyle}>
                          <code style={{ fontSize: 12 }}>{def.code}</code>
                        </td>
                        <td style={tableCellStyle}>
                          <strong>{def.name}</strong>
                          {def.description ? (
                            <div style={{ ...mutedText, marginTop: 2 }}>{def.description}</div>
                          ) : null}
                        </td>
                        <td style={tableCellStyle}>
                          <Badge variant={definitionStatusVariants[def.status] ?? 'neutral'} size="sm">
                            {def.status}
                          </Badge>
                        </td>
                        <td style={tableCellStyle}>v{def.version}</td>
                        <td style={tableCellStyle}>{def.steps.length}</td>
                        <td style={tableCellStyle}>
                          {def.status !== 'ACTIVE' ? (
                            <Button
                              size="sm"
                              variant="success"
                              loading={busyDefAction === `activate:${def.id}`}
                              disabled={!def.id}
                              onClick={() => void changeDefinitionStatus(def, 'activate')}
                            >
                              Activate
                            </Button>
                          ) : (
                            <Button
                              size="sm"
                              variant="outline"
                              loading={busyDefAction === `deactivate:${def.id}`}
                              disabled={!def.id}
                              onClick={() => void changeDefinitionStatus(def, 'deactivate')}
                            >
                              Deactivate
                            </Button>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </ContentPanel>
        </TabsContent>

        <TabsContent value="instances">
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
              <strong style={{ fontSize: 14 }}>Instances</strong>
              <Stack direction="row" gap={8} align="flex-end">
                <div style={{ width: 240 }}>
                  <Select
                    label="Status"
                    value={statusFilter}
                    onChange={(e) => setStatusFilter(e.target.value)}
                  >
                    <option value="ALL">All</option>
                    {INSTANCE_STATUSES.map((status) => (
                      <option key={status} value={status}>{status}</option>
                    ))}
                  </Select>
                </div>
                <Button size="sm" onClick={() => setTriggerOpen(true)}>
                  Trigger
                </Button>
              </Stack>
            </div>

            {instancesError ? <StatusIndicator tone="critical" label={instancesError} /> : null}
            {pendingError ? <StatusIndicator tone="critical" label={pendingError} /> : null}

            {instancesLoading ? (
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '16px 0' }}>
                <Spinner size="sm" />
                <span style={{ fontSize: 13, color: 'var(--sq-fg-muted)' }}>Loading instances…</span>
              </div>
            ) : instances.length === 0 ? (
              <EmptyState message="No instances." />
            ) : (
              <div style={fullBorderStyle}>
                <table style={fullWidthTable}>
                  <thead>
                    <tr>
                      <th style={tableHeaderStyle}>ID</th>
                      <th style={tableHeaderStyle}>Definition</th>
                      <th style={tableHeaderStyle}>Status</th>
                      <th style={tableHeaderStyle}>Started</th>
                      <th style={tableHeaderStyle} />
                    </tr>
                  </thead>
                  <tbody>
                    {instances.map((instance) => (
                      <tr
                        key={instance.id ?? instance.startedAt}
                        onClick={() => {
                          if (instance.id) setSelectedInstanceId(instance.id)
                        }}
                        style={{
                          cursor: 'pointer',
                          backgroundColor:
                            instance.id === selectedInstanceId
                              ? 'color-mix(in srgb, var(--sq-primary) 6%, transparent)'
                              : undefined,
                        }}
                      >
                        <td style={tableCellStyle}>
                          <code style={{ fontSize: 12 }}>{shortId(instance.id)}</code>
                        </td>
                        <td style={tableCellStyle}>{instance.definitionCode ?? '—'}</td>
                        <td style={tableCellStyle}>
                          <Badge variant={instanceStatusVariants[instance.status] ?? 'neutral'} size="sm">
                            {instance.status}
                          </Badge>
                        </td>
                        <td style={tableCellStyle}>{formatDateTime(instance.startedAt)}</td>
                        <td style={tableCellStyle}>
                          <Button
                            size="sm"
                            variant="ghost"
                            onClick={(e) => {
                              e.stopPropagation()
                              if (instance.id) setSelectedInstanceId(instance.id)
                            }}
                          >
                            Open
                          </Button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </ContentPanel>

          {selectedInstanceId ? (
            <ContentPanel style={{ marginTop: 16 }}>
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  gap: 12,
                  marginBottom: 12,
                }}
              >
                <Stack gap={4}>
                  <strong style={{ fontSize: 14 }}>
                    Instance {shortId(selectedInstanceId)}
                    {detail ? (
                      <Badge
                        variant={instanceStatusVariants[detail.status] ?? 'neutral'}
                        size="sm"
                        style={{ marginLeft: 8 }}
                      >
                        {detail.status}
                      </Badge>
                    ) : null}
                  </strong>
                  <span style={mutedText}>
                    {detail?.definitionCode ?? ''}
                    {detail?.currentStepKey ? ` · current step: ${detail.currentStepKey}` : ''}
                  </span>
                </Stack>
                {detailLoading ? <Spinner size="sm" /> : null}
              </div>

              {detailError ? <StatusIndicator tone="critical" label={detailError} /> : null}
              {pendingError ? <StatusIndicator tone="critical" label={pendingError} /> : null}

              {detail ? (
                <Stack gap={16}>
                  <div>
                    <div style={sectionLabelStyle}>Steps</div>
                    {detail.stepInstances.length === 0 ? (
                      <EmptyState message="No steps recorded." />
                    ) : (
                      <Timeline>
                        {detail.stepInstances
                          .slice()
                          .sort((a, b) => a.sequenceOrder - b.sequenceOrder)
                          .map((step) => (
                            <TimelineItem
                              key={step.id ?? `${step.stepKey}-${step.sequenceOrder}`}
                              tone={stepStatusTones[step.status] ?? 'default'}
                              title={step.stepKey}
                              time={formatDateTime(step.endedAt ?? step.startedAt)}
                              description={
                                <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                                  <Badge variant={stepStatusVariants[step.status] ?? 'neutral'} size="sm">
                                    {step.status}
                                  </Badge>
                                  <span>{step.stepType}</span>
                                </span>
                              }
                            >
                              {step.result ? (
                                <div style={{ ...mutedText, marginTop: 4 }}>{step.result}</div>
                              ) : null}
                            </TimelineItem>
                          ))}
                      </Timeline>
                    )}
                  </div>

                  {detail.approvals.filter((approval) => approval.status === 'PENDING').length > 0 ? (
                    <div>
                      <div style={sectionLabelStyle}>Pending approvals</div>
                      <Stack gap={10}>
                        {detail.approvals
                          .filter((approval) => approval.status === 'PENDING')
                          .map((approval) => (
                            <div
                              key={approval.id ?? approval.stepInstanceId}
                              style={{
                                padding: 12,
                                border: '1px solid var(--sq-border)',
                                borderRadius: 8,
                              }}
                            >
                              <Stack gap={8}>
                                <Stack direction="row" gap={8} align="center" wrap>
                                  <Badge variant="warning" size="sm">PENDING</Badge>
                                  <span style={{ fontSize: 13, color: 'var(--sq-fg-muted)' }}>
                                    Approver {shortId(approval.approverId)}
                                  </span>
                                  {approval.comments ? (
                                    <span style={{ fontSize: 12, color: 'var(--sq-fg-muted)' }}>
                                      “{approval.comments}”
                                    </span>
                                  ) : null}
                                </Stack>
                                <ApprovalActions
                                  busy={busyApproval === approval.id}
                                  comments={decisionComments[approval.id ?? ''] ?? ''}
                                  onCommentsChange={(comments) =>
                                    setDecisionComments((previous) => ({
                                      ...previous,
                                      [approval.id ?? '']: comments,
                                    }))
                                  }
                                  onDecide={(decision) => {
                                    if (approval.id) void decide(selectedInstanceId, approval.id, decision)
                                  }}
                                />
                              </Stack>
                            </div>
                          ))}
                      </Stack>
                    </div>
                  ) : null}

                  {detail.status === 'WAITING' ? (
                    <div>
                      <Button loading={busyResume} onClick={() => void resumeInstance()}>
                        Resume
                      </Button>
                    </div>
                  ) : null}
                </Stack>
              ) : null}
            </ContentPanel>
          ) : null}
        </TabsContent>

        <TabsContent value="approvals">
          <ContentPanel>
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                gap: 12,
                marginBottom: 12,
              }}
            >
              <strong style={{ fontSize: 14 }}>Pending approvals</strong>
              <Button size="sm" variant="outline" onClick={() => void loadPending()}>
                Refresh
              </Button>
            </div>

            {pendingError ? <StatusIndicator tone="critical" label={pendingError} /> : null}

            {pendingLoading ? (
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '16px 0' }}>
                <Spinner size="sm" />
                <span style={{ fontSize: 13, color: 'var(--sq-fg-muted)' }}>Loading approvals…</span>
              </div>
            ) : pending.length === 0 ? (
              <EmptyState message="No pending approvals." />
            ) : (
              <div style={fullBorderStyle}>
                <table style={fullWidthTable}>
                  <thead>
                    <tr>
                      <th style={tableHeaderStyle}>Definition</th>
                      <th style={tableHeaderStyle}>Step</th>
                      <th style={tableHeaderStyle}>Instance</th>
                      <th style={tableHeaderStyle}>Status</th>
                      <th style={tableHeaderStyle}>Created</th>
                      <th style={tableHeaderStyle}>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {pending.map((approval) => (
                      <tr key={approval.approvalId ?? `${approval.instanceId}-${approval.stepKey}`}>
                        <td style={tableCellStyle}>
                          <code style={{ fontSize: 12 }}>{approval.definitionCode ?? '—'}</code>
                        </td>
                        <td style={tableCellStyle}>
                          <Stack direction="row" gap={6} align="center">
                            <Badge variant="neutral" size="sm">{approval.stepType}</Badge>
                            <span>{approval.stepKey}</span>
                          </Stack>
                        </td>
                        <td style={tableCellStyle}>
                          <code style={{ fontSize: 12 }}>{shortId(approval.instanceId)}</code>
                        </td>
                        <td style={tableCellStyle}>
                          <Badge variant={approvalStatusVariants[approval.status] ?? 'neutral'} size="sm">
                            {approval.status}
                          </Badge>
                        </td>
                        <td style={tableCellStyle}>{formatDateTime(approval.createdAt)}</td>
                        <td style={tableCellStyle}>
                          <ApprovalActions
                            busy={busyApproval === approval.approvalId}
                            comments={decisionComments[approval.approvalId ?? ''] ?? ''}
                            onCommentsChange={(comments) =>
                              setDecisionComments((previous) => ({
                                ...previous,
                                [approval.approvalId ?? '']: comments,
                              }))
                            }
                            onDecide={(decision) => {
                              if (approval.instanceId && approval.approvalId) {
                                void decide(approval.instanceId, approval.approvalId, decision)
                              }
                            }}
                          />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </ContentPanel>
        </TabsContent>
      </Tabs>

      <Modal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        size="lg"
        title="New workflow definition"
        description="Define the steps that run when this workflow is triggered."
        footer={
          <>
            <Button variant="outline" onClick={() => setCreateOpen(false)}>
              Cancel
            </Button>
            <Button
              loading={creating}
              disabled={!createForm.code.trim() || !createForm.name.trim()}
              onClick={() => void createDefinition()}
            >
              Create
            </Button>
          </>
        }
      >
        <Stack gap={12}>
          <Input
            label="Code"
            required
            value={createForm.code}
            onChange={(e) => setCreateForm({ ...createForm, code: e.target.value })}
          />
          <Input
            label="Name"
            required
            value={createForm.name}
            onChange={(e) => setCreateForm({ ...createForm, name: e.target.value })}
          />
          <Textarea
            label="Description"
            value={createForm.description}
            onChange={(e) => setCreateForm({ ...createForm, description: e.target.value })}
          />
          <Select
            label="Trigger type"
            value={createForm.triggerType}
            onChange={(e) => setCreateForm({ ...createForm, triggerType: e.target.value })}
          >
            <option value="MANUAL">MANUAL</option>
          </Select>

          <div>
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                marginBottom: 8,
              }}
            >
              <strong style={{ fontSize: 13 }}>Steps</strong>
              <Button
                size="sm"
                variant="outline"
                onClick={() =>
                  setCreateForm({
                    ...createForm,
                    steps: [...createForm.steps, { stepKey: '', stepType: 'TASK' }],
                  })
                }
              >
                Add step
              </Button>
            </div>
            <Stack gap={8}>
              {createForm.steps.map((step, index) => (
                <Stack key={`${index}-${step.stepKey}`} direction="row" gap={8} align="flex-end">
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <Input
                      label={`Step ${index + 1} key`}
                      value={step.stepKey}
                      onChange={(e) => updateStep(index, { ...step, stepKey: e.target.value })}
                    />
                  </div>
                  <div style={{ width: 180 }}>
                    <Select
                      label="Type"
                      value={step.stepType}
                      onChange={(e) => updateStep(index, { ...step, stepType: e.target.value })}
                    >
                      {STEP_TYPES.map((type) => (
                        <option key={type} value={type}>{type}</option>
                      ))}
                    </Select>
                  </div>
                  <Button size="sm" variant="outline" onClick={() => removeStep(index)}>
                    Remove
                  </Button>
                </Stack>
              ))}
            </Stack>
          </div>
        </Stack>
      </Modal>

      <Modal
        open={triggerOpen}
        onClose={() => setTriggerOpen(false)}
        title="Trigger workflow"
        description="Start a new instance of an active workflow definition."
        footer={
          <>
            <Button variant="outline" onClick={() => setTriggerOpen(false)}>
              Cancel
            </Button>
            <Button loading={triggering} disabled={!triggerForm.definitionId} onClick={() => void triggerInstance()}>
              Run
            </Button>
          </>
        }
      >
        <Stack gap={12}>
          <Select
            label="Definition"
            required
            value={triggerForm.definitionId}
            onChange={(e) => setTriggerForm({ ...triggerForm, definitionId: e.target.value })}
          >
            <option value="">Select a definition…</option>
            {definitions
              .filter((def) => def.status === 'ACTIVE' && def.id)
              .map((def) => (
                <option key={def.id} value={def.id ?? ''}>
                  {def.name} ({def.code})
                </option>
              ))}
          </Select>
          <Textarea
            label="Payload (JSON)"
            value={triggerForm.payload}
            onChange={(e) => setTriggerForm({ ...triggerForm, payload: e.target.value })}
            placeholder='{"patientId": "…"}'
          />
        </Stack>
      </Modal>
    </div>
  )
}