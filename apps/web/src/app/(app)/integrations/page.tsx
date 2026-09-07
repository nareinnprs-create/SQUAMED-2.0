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
} from '@squamed/ui'
import type { BadgeVariant } from '@squamed/ui'
import { useApi } from '@/lib/use-api'

interface FhirBundle {
  resourceType: string
  total: number
  entry?: { resource: Record<string, unknown> }[]
}

interface FhirResourceSummary {
  total: number
  sample: Record<string, unknown> | null
}

interface Hl7ParseField {
  index: number
  value: string
  components?: string[]
}

interface Hl7ParseSegment {
  type: string
  fields: Hl7ParseField[]
}

interface Hl7ParseResult {
  messageType: string
  controlId: string
  sendingApplication: string
  sendingFacility: string
  segments: Hl7ParseSegment[]
  ack: string
}

interface Hl7MessageResult {
  messageType: string
  controlId: string
  ackCode: string
  importedPatientId?: string | null
  importError?: string | null
  ack: string
  logId?: string | null
}

interface Hl7MessageRecord {
  id: string
  messageType: string
  controlId: string
  direction: string
  source: string
  ackCode: string
  createdAt: string
}

interface Hl7Health {
  mllpRunning: boolean
  mllpPort: number
  detail?: string
}

interface InteropConnection {
  id: string
  name: string
  connectionType: string
  endpoint?: string | null
  enabled: boolean
  lastHealthCheckAt?: string | null
  lastSuccessAt?: string | null
  lastError?: string | null
  errorCount?: number
}

interface InteropConnectionHealth {
  id: string
  name: string
  connectionType: string
  enabled: boolean
  status: string
}

interface InteropHealth {
  mllp: Hl7Health
  connections: InteropConnectionHealth[]
}

const FHIR_RESOURCES = [
  'Patient',
  'Practitioner',
  'Organization',
  'Encounter',
  'Condition',
  'Observation',
  'MedicationRequest',
  'ServiceRequest',
  'AllergyIntolerance',
  'DiagnosticReport',
]

const FHIR_BASE_URL = 'http://localhost:8080/api/v1/fhir'

const HL7_SEGMENT_SEP = String.fromCharCode(13)

const SAMPLE_ADT_A04 = [
  'MSH|^~\\&|MIRDIF_HIS|MIRDIF|EPIC|HOSPITAL|20240906120000||ADT^A04|MSG000123|P|2.5',
  'EVN|A04|20240906120000',
  'PID|1||MRN0000123||Smith^John||19800101|M',
].join(HL7_SEGMENT_SEP)

const CONNECTION_TYPES = ['MLLP', 'HL7', 'FHIR']

const emptyConnectionForm = {
  name: '',
  connectionType: 'MLLP',
  endpoint: '',
  username: '',
  password: '',
}

const connectionTypeVariants: Record<string, BadgeVariant> = {
  MLLP: 'information',
  HL7: 'warning',
  FHIR: 'success',
}

const connectionStatusVariants: Record<string, BadgeVariant> = {
  healthy: 'success',
  unreachable: 'critical',
  disabled: 'neutral',
  misconfigured: 'warning',
}

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

function summarizeResource(resource: Record<string, unknown>): string {
  const keys = Object.keys(resource).filter((key) => key !== 'resourceType' && key !== 'id')
  const fields: string[] = []
  for (const key of keys) {
    if (fields.length >= 2) break
    const value = resource[key]
    if (value == null || typeof value === 'object') continue
    fields.push(`${key}: ${String(value)}`)
  }
  return fields.length > 0 ? fields.join(' · ') : '—'
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

export default function IntegrationsPage() {
  const { apiFetch, isReady } = useApi()
  const [activeTab, setActiveTab] = useState('fhir')
  const [hl7Health, setHl7Health] = useState<Hl7Health | null>(null)
  const [healthError, setHealthError] = useState<string | null>(null)

  const [fhirData, setFhirData] = useState<Record<string, FhirResourceSummary>>({})
  const [fhirLoading, setFhirLoading] = useState(true)
  const [fhirError, setFhirError] = useState<string | null>(null)
  const [previewOpen, setPreviewOpen] = useState<Record<string, boolean>>({})

  const [raw, setRaw] = useState('')
  const [parseResult, setParseResult] = useState<Hl7ParseResult | null>(null)
  const [parseError, setParseError] = useState<string | null>(null)
  const [parsing, setParsing] = useState(false)
  const [sendResult, setSendResult] = useState<Hl7MessageResult | null>(null)
  const [sendError, setSendError] = useState<string | null>(null)
  const [sending, setSending] = useState(false)
  const [messages, setMessages] = useState<Hl7MessageRecord[]>([])
  const [messagesLoading, setMessagesLoading] = useState(true)
  const [messagesError, setMessagesError] = useState<string | null>(null)

  const [connections, setConnections] = useState<InteropConnection[]>([])
  const [connectionsLoading, setConnectionsLoading] = useState(true)
  const [connectionsError, setConnectionsError] = useState<string | null>(null)
  const [healthByConnection, setHealthByConnection] = useState<Record<string, string>>({})
  const [healthChecking, setHealthChecking] = useState(false)
  const [busyConnection, setBusyConnection] = useState<string | null>(null)
  const [addOpen, setAddOpen] = useState(false)
  const [adding, setAdding] = useState(false)
  const [connectionForm, setConnectionForm] = useState(emptyConnectionForm)

  const loadFhir = useCallback(async () => {
    try {
      setFhirLoading(true)
      const results = await Promise.allSettled(
        FHIR_RESOURCES.map((resource) => apiFetch<FhirBundle>(`/fhir/${resource}`)),
      )
      const next: Record<string, FhirResourceSummary> = {}
      let partialFailure = false
      FHIR_RESOURCES.forEach((resource, index) => {
        const settled = results[index]
        if (settled.status === 'fulfilled') {
          const bundle = settled.value
          next[resource] = {
            total: bundle.total,
            sample: bundle.entry?.[0]?.resource ?? null,
          }
        } else {
          partialFailure = true
          next[resource] = { total: 0, sample: null }
        }
      })
      setFhirData(next)
      setFhirError(partialFailure ? 'One or more FHIR resources failed to load.' : null)
    } catch (e) {
      setFhirError(e instanceof Error ? e.message : 'Failed to load FHIR resources')
    } finally {
      setFhirLoading(false)
    }
  }, [apiFetch])

  const loadMessages = useCallback(
    async (silent = false) => {
      if (!silent) setMessagesLoading(true)
      try {
        const result = await apiFetch<Hl7MessageRecord[]>('/hl7/messages')
        setMessages(Array.isArray(result) ? result : [])
        setMessagesError(null)
      } catch (e) {
        setMessagesError(e instanceof Error ? e.message : 'Failed to load HL7 messages')
      } finally {
        if (!silent) setMessagesLoading(false)
      }
    },
    [apiFetch],
  )

  const loadConnections = useCallback(
    async (silent = false) => {
      if (!silent) setConnectionsLoading(true)
      try {
        const result = await apiFetch<InteropConnection[]>('/interop/connections')
        setConnections(Array.isArray(result) ? result : [])
        setConnectionsError(null)
      } catch (e) {
        setConnectionsError(e instanceof Error ? e.message : 'Failed to load connections')
      } finally {
        if (!silent) setConnectionsLoading(false)
      }
    },
    [apiFetch],
  )

  const loadHealth = useCallback(
    async (silent = false) => {
      if (!silent) setHealthChecking(true)
      try {
        const result = await apiFetch<InteropHealth>('/interop/health')
        const next: Record<string, string> = {}
        result.connections.forEach((conn) => {
          next[conn.id] = conn.status
        })
        setHealthByConnection(next)
        setHl7Health(result.mllp)
        setHealthError(null)
        setConnectionsError(null)
      } catch (e) {
        setHealthError(e instanceof Error ? e.message : 'Failed to run health check')
      } finally {
        if (!silent) setHealthChecking(false)
      }
    },
    [apiFetch],
  )

  const loadHl7Health = useCallback(async () => {
    try {
      const result = await apiFetch<Hl7Health>('/hl7/health')
      setHl7Health(result)
      setHealthError(null)
    } catch (e) {
      setHealthError(e instanceof Error ? e.message : 'Failed to load HL7 health')
    }
  }, [apiFetch])

  useEffect(() => {
    if (!isReady) return
    void loadFhir()
    void loadMessages()
    void loadConnections()
    void loadHl7Health()
  }, [isReady, loadFhir, loadMessages, loadConnections, loadHl7Health])

  async function parseMessage() {
    if (!raw.trim()) {
      setParseError('Enter an HL7 message to parse.')
      return
    }
    setParsing(true)
    try {
      const result = await apiFetch<Hl7ParseResult>('/hl7/parse', {
        method: 'POST',
        body: JSON.stringify({ raw }),
      })
      setParseResult(result)
      setParseError(null)
    } catch (e) {
      setParseError(e instanceof Error ? e.message : 'Failed to parse message')
    } finally {
      setParsing(false)
    }
  }

  async function sendMessage() {
    if (!raw.trim()) {
      setSendError('Enter an HL7 message to send.')
      return
    }
    setSending(true)
    try {
      const result = await apiFetch<Hl7MessageResult>('/hl7/messages', {
        method: 'POST',
        body: JSON.stringify({ raw }),
      })
      setSendResult(result)
      setSendError(null)
      void loadMessages(true)
    } catch (e) {
      setSendError(e instanceof Error ? e.message : 'Failed to send message')
    } finally {
      setSending(false)
    }
  }

  async function addConnection() {
    setAdding(true)
    try {
      await apiFetch<{ id: string; name: string; connectionType: string }>('/interop/connections', {
        method: 'POST',
        body: JSON.stringify({
          name: connectionForm.name,
          connectionType: connectionForm.connectionType,
          endpoint: connectionForm.endpoint.trim() || undefined,
          username: connectionForm.username.trim() || undefined,
          password: connectionForm.password || undefined,
        }),
      })
      setAddOpen(false)
      setConnectionForm(emptyConnectionForm)
      void loadConnections(true)
      void loadHealth(true)
    } catch (e) {
      setConnectionsError(e instanceof Error ? e.message : 'Failed to create connection')
    } finally {
      setAdding(false)
    }
  }

  async function toggleConnection(conn: InteropConnection) {
    setBusyConnection(`toggle:${conn.id}`)
    try {
      await apiFetch<{ id: string; name: string; enabled: boolean }>(`/interop/connections/${conn.id}`, {
        method: 'PUT',
        body: JSON.stringify({ enabled: !conn.enabled }),
      })
      void loadConnections(true)
      void loadHealth(true)
    } catch (e) {
      setConnectionsError(e instanceof Error ? e.message : 'Failed to update connection')
    } finally {
      setBusyConnection(null)
    }
  }

  async function deleteConnection(id: string) {
    setBusyConnection(`delete:${id}`)
    try {
      await apiFetch<{ deleted: boolean }>(`/interop/connections/${id}`, {
        method: 'DELETE',
      })
      void loadConnections(true)
      void loadHealth(true)
    } catch (e) {
      setConnectionsError(e instanceof Error ? e.message : 'Failed to delete connection')
    } finally {
      setBusyConnection(null)
    }
  }

  if (!isReady) {
    return (
      <div style={{ padding: 24, display: 'flex', alignItems: 'center', gap: 12 }}>
        <Spinner size="sm" />
        <span style={{ fontSize: 14, color: 'var(--sq-fg-muted)' }}>Loading integrations…</span>
      </div>
    )
  }

  return (
    <div style={{ padding: 24 }}>
      <PageHeader
        title="Integrations"
        description="FHIR, HL7 and connection management."
        actions={
          hl7Health ? (
            <Badge variant={hl7Health.mllpRunning ? 'success' : 'critical'} size="md">
              {hl7Health.mllpRunning
                ? `MLLP listening on 127.0.0.1:${hl7Health.mllpPort}`
                : 'MLLP down'}
            </Badge>
          ) : null
        }
      />

      {healthError ? <StatusIndicator tone="critical" label={healthError} /> : null}

      <Tabs value={activeTab} onValueChange={setActiveTab}>
        <TabsList style={{ marginBottom: 16 }}>
          <TabsTrigger value="fhir">FHIR Resources</TabsTrigger>
          <TabsTrigger value="hl7">HL7 Sandbox</TabsTrigger>
          <TabsTrigger value="connections">Connections</TabsTrigger>
        </TabsList>

        <TabsContent value="fhir">
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
              <Stack gap={6}>
                <strong style={{ fontSize: 14 }}>FHIR Resources</strong>
                <code
                  style={{
                    fontSize: 11,
                    padding: '2px 8px',
                    borderRadius: 6,
                    backgroundColor: 'color-mix(in srgb, var(--sq-fg) 8%, transparent)',
                    color: 'var(--sq-fg-muted)',
                    fontFamily: 'var(--sq-font-mono, ui-monospace, SFMono-Regular, Menlo, monospace)',
                  }}
                >
                  {FHIR_BASE_URL}
                </code>
              </Stack>
              <Button size="sm" variant="outline" loading={fhirLoading} onClick={() => void loadFhir()}>
                Refresh
              </Button>
            </div>

            {fhirError ? <StatusIndicator tone="critical" label={fhirError} /> : null}

            {fhirLoading ? (
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '16px 0' }}>
                <Spinner size="sm" />
                <span style={{ fontSize: 13, color: 'var(--sq-fg-muted)' }}>Loading resources…</span>
              </div>
            ) : (
              <div style={fullBorderStyle}>
                <table style={fullWidthTable}>
                  <thead>
                    <tr>
                      <th style={tableHeaderStyle}>Resource</th>
                      <th style={tableHeaderStyle}>Count</th>
                      <th style={tableHeaderStyle}>Sample</th>
                      <th style={tableHeaderStyle} />
                    </tr>
                  </thead>
                  <tbody>
                    {FHIR_RESOURCES.map((resource) => {
                      const summary = fhirData[resource]
                      const open = Boolean(previewOpen[resource])
                      return (
                        <tr key={resource}>
                          <td style={tableCellStyle}>
                            <strong>{resource}</strong>
                          </td>
                          <td style={tableCellStyle}>
                            {summary ? (
                              <Badge variant="neutral" size="sm">{summary.total}</Badge>
                            ) : (
                              <span style={mutedText}>—</span>
                            )}
                          </td>
                          <td style={tableCellStyle}>
                            {summary?.sample ? (
                              <Stack gap={2}>
                                <span>
                                  <code style={{ fontSize: 12 }}>{String(summary.sample.resourceType)}</code>
                                  {summary.sample.id ? (
                                    <code style={{ fontSize: 12, marginLeft: 8 }}>{String(summary.sample.id)}</code>
                                  ) : null}
                                </span>
                                <span style={mutedText}>{summarizeResource(summary.sample)}</span>
                              </Stack>
                            ) : (
                              <span style={mutedText}>No entries</span>
                            )}
                          </td>
                          <td style={{ ...tableCellStyle, textAlign: 'right' }}>
                            <Button
                              size="sm"
                              variant="ghost"
                              disabled={!summary?.sample}
                              onClick={() =>
                                setPreviewOpen((previous) => ({ ...previous, [resource]: !open }))
                              }
                            >
                              {open ? 'Hide preview' : 'Preview'}
                            </Button>
                          </td>
                        </tr>
                      )
                    })}
                    {FHIR_RESOURCES.map((resource) => {
                      if (!previewOpen[resource] || !fhirData[resource]?.sample) return null
                      return (
                        <tr key={`${resource}-preview`}>
                          <td style={tableCellStyle} colSpan={4}>
                            <pre
                              style={{
                                margin: 0,
                                padding: '10px 12px',
                                maxHeight: 320,
                                overflow: 'auto',
                                borderRadius: 6,
                                backgroundColor: 'color-mix(in srgb, var(--sq-fg) 5%, transparent)',
                                color: 'var(--sq-fg)',
                                fontSize: 12,
                                lineHeight: 1.5,
                                fontFamily: 'var(--sq-font-mono, ui-monospace, SFMono-Regular, Menlo, monospace)',
                                border: '1px solid var(--sq-border)',
                              }}
                            >
                              {JSON.stringify(fhirData[resource].sample, null, 2)}
                            </pre>
                          </td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </ContentPanel>
        </TabsContent>

        <TabsContent value="hl7">
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
              <strong style={{ fontSize: 14 }}>HL7 Sandbox</strong>
              <Button size="sm" variant="outline" onClick={() => setRaw(SAMPLE_ADT_A04)}>
                Load sample ADT
              </Button>
            </div>

            <Textarea
              label="HL7 message (raw)"
              rows={8}
              value={raw}
              onChange={(e) => setRaw(e.target.value)}
              placeholder="MSH|^~\&|...|ADT^A04|..."
              style={{ fontFamily: 'var(--sq-font-mono, ui-monospace, SFMono-Regular, Menlo, monospace)', fontSize: 12 }}
            />

            <Stack direction="row" gap={8} align="center" style={{ marginTop: 12 }}>
              <Button loading={parsing} disabled={!raw.trim()} onClick={() => void parseMessage()}>
                Parse
              </Button>
              <Button loading={sending} disabled={!raw.trim()} onClick={() => void sendMessage()}>
                Send / Import
              </Button>
            </Stack>

            {parseError ? (
              <div style={{ marginTop: 12 }}>
                <StatusIndicator tone="critical" label={parseError} />
              </div>
            ) : null}
            {sendError ? (
              <div style={{ marginTop: 12 }}>
                <StatusIndicator tone="critical" label={sendError} />
              </div>
            ) : null}

            {parseResult ? (
              <Stack gap={12} style={{ marginTop: 16 }}>
                <div>
                  <div style={{ ...mutedText, marginBottom: 4 }}>
                    {parseResult.sendingApplication} → {parseResult.sendingFacility}
                  </div>
                  <Stack direction="row" gap={8} align="center">
                    <Badge variant="information" size="sm">{parseResult.messageType}</Badge>
                    <code style={{ fontSize: 12 }}>{parseResult.controlId}</code>
                  </Stack>
                </div>
                {parseResult.segments.length === 0 ? (
                  <EmptyState message="No segments parsed." />
                ) : (
                  <div style={fullBorderStyle}>
                    <table style={fullWidthTable}>
                      <thead>
                        <tr>
                          <th style={tableHeaderStyle}>Type</th>
                          <th style={tableHeaderStyle}>Fields</th>
                        </tr>
                      </thead>
                      <tbody>
                        {parseResult.segments.map((segment, index) => (
                          <tr key={`${segment.type}-${index}`}>
                            <td style={tableCellStyle}>
                              <code style={{ fontSize: 12 }}>{segment.type}</code>
                            </td>
                            <td style={tableCellStyle}>
                              <Stack gap={4}>
                                {segment.fields.map((field) => (
                                  <div key={field.index}>
                                    <code style={{ fontSize: 12 }}>
                                      {field.index} → {field.value}
                                    </code>
                                    {field.components && field.components.length > 0 ? (
                                      <div style={mutedText}>{field.components.join(' | ')}</div>
                                    ) : null}
                                  </div>
                                ))}
                              </Stack>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
                <div>
                  <div style={mutedText}>ACK</div>
                  <pre
                    style={{
                      margin: 0,
                      padding: 10,
                      maxHeight: 160,
                      overflow: 'auto',
                      borderRadius: 6,
                      backgroundColor: 'color-mix(in srgb, var(--sq-fg) 5%, transparent)',
                      color: 'var(--sq-fg)',
                      fontSize: 12,
                      lineHeight: 1.5,
                      fontFamily: 'var(--sq-font-mono, ui-monospace, SFMono-Regular, Menlo, monospace)',
                      border: '1px solid var(--sq-border)',
                    }}
                  >
                    {parseResult.ack}
                  </pre>
                </div>
              </Stack>
            ) : null}

            {sendResult ? (
              <Stack gap={12} style={{ marginTop: 16 }}>
                <div>
                  <div style={mutedText}>Result</div>
                  <Stack direction="row" gap={8} align="center" wrap style={{ marginTop: 4 }}>
                    <Badge variant={sendResult.ackCode === 'AA' ? 'success' : 'critical'} size="sm">
                      {sendResult.ackCode}
                    </Badge>
                    <Badge variant="information" size="sm">{sendResult.messageType}</Badge>
                    {sendResult.importedPatientId ? (
                      <code style={{ fontSize: 12 }}>MRN: {sendResult.importedPatientId}</code>
                    ) : null}
                    {sendResult.logId ? (
                      <span style={mutedText}>log {sendResult.logId}</span>
                    ) : null}
                  </Stack>
                </div>
                {sendResult.importError ? (
                  <StatusIndicator tone="critical" label={sendResult.importError} />
                ) : null}
                <pre
                  style={{
                    margin: 0,
                    padding: 10,
                    maxHeight: 240,
                    overflow: 'auto',
                    borderRadius: 6,
                    backgroundColor: 'color-mix(in srgb, var(--sq-fg) 5%, transparent)',
                    color: 'var(--sq-fg)',
                    fontSize: 12,
                    lineHeight: 1.5,
                    fontFamily: 'var(--sq-font-mono, ui-monospace, SFMono-Regular, Menlo, monospace)',
                    border: '1px solid var(--sq-border)',
                  }}
                >
                  {sendResult.ack}
                </pre>
              </Stack>
            ) : null}
          </ContentPanel>

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
              <strong style={{ fontSize: 14 }}>Recent messages</strong>
              <Button size="sm" variant="outline" onClick={() => void loadMessages()}>
                Refresh
              </Button>
            </div>

            {messagesError ? <StatusIndicator tone="critical" label={messagesError} /> : null}

            {messagesLoading ? (
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '16px 0' }}>
                <Spinner size="sm" />
                <span style={{ fontSize: 13, color: 'var(--sq-fg-muted)' }}>Loading messages…</span>
              </div>
            ) : messages.length === 0 ? (
              <EmptyState message="No messages yet." />
            ) : (
              <div style={fullBorderStyle}>
                <table style={fullWidthTable}>
                  <thead>
                    <tr>
                      <th style={tableHeaderStyle}>Control ID</th>
                      <th style={tableHeaderStyle}>Type</th>
                      <th style={tableHeaderStyle}>Direction</th>
                      <th style={tableHeaderStyle}>Source</th>
                      <th style={tableHeaderStyle}>ACK</th>
                      <th style={tableHeaderStyle}>Created</th>
                    </tr>
                  </thead>
                  <tbody>
                    {messages.map((message) => (
                      <tr key={message.id}>
                        <td style={tableCellStyle}>
                          <code style={{ fontSize: 12 }}>{message.controlId}</code>
                        </td>
                        <td style={tableCellStyle}>
                          <Badge variant="information" size="sm">{message.messageType}</Badge>
                        </td>
                        <td style={tableCellStyle}>{message.direction}</td>
                        <td style={tableCellStyle}>{message.source}</td>
                        <td style={tableCellStyle}>
                          <Badge
                            variant={message.ackCode === 'AA' ? 'success' : 'critical'}
                            size="sm"
                          >
                            {message.ackCode}
                          </Badge>
                        </td>
                        <td style={tableCellStyle}>{formatDateTime(message.createdAt)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </ContentPanel>
        </TabsContent>

        <TabsContent value="connections">
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
              <strong style={{ fontSize: 14 }}>Integration Connections</strong>
              <Stack direction="row" gap={8} align="center">
                <Button
                  size="sm"
                  variant="outline"
                  loading={healthChecking}
                  onClick={() => void loadHealth()}
                >
                  Run health check
                </Button>
                <Button size="sm" onClick={() => setAddOpen(true)}>
                  Add connection
                </Button>
              </Stack>
            </div>

            {connectionsError ? <StatusIndicator tone="critical" label={connectionsError} /> : null}
            {healthError ? <StatusIndicator tone="critical" label={healthError} /> : null}

            {connectionsLoading ? (
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '16px 0' }}>
                <Spinner size="sm" />
                <span style={{ fontSize: 13, color: 'var(--sq-fg-muted)' }}>Loading connections…</span>
              </div>
            ) : connections.length === 0 ? (
              <EmptyState message="No connections yet." />
            ) : (
              <div style={fullBorderStyle}>
                <table style={fullWidthTable}>
                  <thead>
                    <tr>
                      <th style={tableHeaderStyle}>Name</th>
                      <th style={tableHeaderStyle}>Type</th>
                      <th style={tableHeaderStyle}>Endpoint</th>
                      <th style={tableHeaderStyle}>Enabled</th>
                      <th style={tableHeaderStyle}>Status</th>
                      <th style={tableHeaderStyle} />
                    </tr>
                  </thead>
                  <tbody>
                    {connections.map((conn) => {
                      const status = healthByConnection[conn.id] ?? 'unknown'
                      return (
                        <tr key={conn.id}>
                          <td style={tableCellStyle}>
                            <strong>{conn.name}</strong>
                            {conn.lastHealthCheckAt ? (
                              <div style={mutedText}>checked {formatDateTime(conn.lastHealthCheckAt)}</div>
                            ) : null}
                          </td>
                          <td style={tableCellStyle}>
                            <Badge variant={connectionTypeVariants[conn.connectionType] ?? 'neutral'} size="sm">
                              {conn.connectionType}
                            </Badge>
                          </td>
                          <td style={tableCellStyle}>
                            <code style={{ fontSize: 12 }}>{conn.endpoint ?? '—'}</code>
                          </td>
                          <td style={tableCellStyle}>
                            <Button
                              size="sm"
                              variant={conn.enabled ? 'success' : 'outline'}
                              loading={busyConnection === `toggle:${conn.id}`}
                              title={conn.enabled ? 'Click to disable' : 'Click to enable'}
                              onClick={() => void toggleConnection(conn)}
                            >
                              {conn.enabled ? 'Enabled' : 'Disabled'}
                            </Button>
                          </td>
                          <td
                            style={tableCellStyle}
                            title={conn.lastError ?? undefined}
                          >
                            <Badge variant={connectionStatusVariants[status] ?? 'neutral'} size="sm">
                              {status}
                            </Badge>
                          </td>
                          <td style={{ ...tableCellStyle, textAlign: 'right' }}>
                            <Button
                              size="sm"
                              variant="danger"
                              loading={busyConnection === `delete:${conn.id}`}
                              onClick={() => void deleteConnection(conn.id)}
                            >
                              Delete
                            </Button>
                          </td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </ContentPanel>
        </TabsContent>
      </Tabs>

      <Modal
        open={addOpen}
        onClose={() => setAddOpen(false)}
        title="Add connection"
        description="Create a new integration connection endpoint."
        footer={
          <>
            <Button variant="outline" onClick={() => setAddOpen(false)}>
              Cancel
            </Button>
            <Button
              loading={adding}
              disabled={!connectionForm.name.trim()}
              onClick={() => void addConnection()}
            >
              Add
            </Button>
          </>
        }
      >
        <Stack gap={12}>
          <Input
            label="Name"
            required
            value={connectionForm.name}
            onChange={(e) => setConnectionForm({ ...connectionForm, name: e.target.value })}
          />
          <Select
            label="Connection type"
            value={connectionForm.connectionType}
            onChange={(e) => setConnectionForm({ ...connectionForm, connectionType: e.target.value })}
          >
            {CONNECTION_TYPES.map((type) => (
              <option key={type} value={type}>{type}</option>
            ))}
          </Select>
          <Input
            label="Endpoint"
            value={connectionForm.endpoint}
            placeholder="127.0.0.1:2575"
            onChange={(e) => setConnectionForm({ ...connectionForm, endpoint: e.target.value })}
          />
          <Input
            label="Username"
            value={connectionForm.username}
            onChange={(e) => setConnectionForm({ ...connectionForm, username: e.target.value })}
          />
          <Input
            label="Password"
            type="password"
            value={connectionForm.password}
            onChange={(e) => setConnectionForm({ ...connectionForm, password: e.target.value })}
          />
        </Stack>
      </Modal>
    </div>
  )
}