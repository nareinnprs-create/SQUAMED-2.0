'use client'

import { useCallback, useEffect, useRef, useState } from 'react'
import type { KeyboardEvent as ReactKeyboardEvent, ReactNode } from 'react'
import { useAuth } from '@squamed/auth'
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

interface AiAgent {
  id: string
  code: string
  name: string
  description: string | null
  systemPrompt: string | null
  modelId: string | null
  temperature: number | null
  enabled: boolean
  scopes: string | null
  createdAt: string | null
}

interface AiModel {
  id: string
  name: string
  provider: string
  modelName: string | null
  baseUrl: string | null
  hasApiKey: boolean
  temperature: number | null
  maxTokens: number | null
  enabled: boolean
  isDefault: boolean
  createdAt: string | null
}

interface GovernanceCostRow {
  name: string
  requests: number
  tokens: number
  costUsd: number
}

interface GovernanceCosts {
  from: string
  to: string
  byCapability: GovernanceCostRow[]
  byModel: GovernanceCostRow[]
  totalCostUsd: number
}

interface GovernanceIncident {
  id: string
  capability: string
  severity: string
  summary: string
  detail: string | null
  status: string
  reportedAt: string
  closedAt: string | null
  resolution: string | null
}

interface GovernanceEval {
  id: string
  capability: string
  subject: string
  suiteName: string
  casesPassed: number
  casesTotal: number
  score: number
  verdict: string
  notes: string | null
  recordedAt: string
}

interface AiLog {
  id: string
  agentCode: string | null
  modelName: string | null
  userId: string | null
  userMessage: string
  assistantMessage: string
  intent: string | null
  latencyMs: number | null
  createdAt: string | null
}

interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  meta?: string
}

import { SseStreamDecoder, type SseEvent } from '@/lib/sse'

const API_STREAM_URL = `${process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080/api/v1'}/ai/chat/stream`

const SAMPLE_PROMPTS = [
  'How many patients?',
  'Pending approvals',
  'Revenue today',
  'Search patient Sara',
  'What can you do?',
]

const PROVIDERS = ['ECHO', 'OPENAI', 'ANTHROPIC', 'LOCAL']

const emptyAgentForm = {
  code: '',
  name: '',
  description: '',
  systemPrompt: '',
  temperature: '0.2',
  modelId: '',
  enabled: 'true',
}

const emptyModelForm = {
  name: '',
  provider: 'ECHO',
  modelName: '',
  baseUrl: '',
  apiKey: '',
  temperature: '',
  maxTokens: '',
  isDefault: 'false',
}

const intentVariants: Record<string, BadgeVariant> = {
  navigate: 'information',
  count: 'neutral',
  search_patients: 'success',
  workflow: 'warning',
  analytics: 'information',
  help: 'neutral',
  chat: 'neutral',
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

function truncate(text: string, max: number): string {
  return text.length <= max ? text : `${text.slice(0, max)}…`
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

function renderRich(text: string): ReactNode {
  const parts = text.split('**')
  return parts.map((part, index) =>
    index % 2 === 1 ? <strong key={index}>{part}</strong> : <span key={index}>{part}</span>,
  )
}

export default function AiAssistantPage() {
  const { apiFetch, isReady } = useApi()
  const { accessToken } = useAuth()
  const idCounter = useRef(0)

  const [activeTab, setActiveTab] = useState('chat')

  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [prompt, setPrompt] = useState('')
  const [streaming, setStreaming] = useState(false)
  const [streamingText, setStreamingText] = useState('')
  const [streamError, setStreamError] = useState<string | null>(null)
  const [agentCode, setAgentCode] = useState('ops')

  const [agents, setAgents] = useState<AiAgent[]>([])
  const [agentsLoading, setAgentsLoading] = useState(true)
  const [agentsError, setAgentsError] = useState<string | null>(null)
  const [busyAgent, setBusyAgent] = useState<string | null>(null)
  const [agentOpen, setAgentOpen] = useState(false)
  const [agentCreating, setAgentCreating] = useState(false)
  const [agentForm, setAgentForm] = useState(emptyAgentForm)

  const [models, setModels] = useState<AiModel[]>([])
  const [modelsLoading, setModelsLoading] = useState(true)
  const [modelsError, setModelsError] = useState<string | null>(null)
  const [busyModel, setBusyModel] = useState<string | null>(null)
  const [modelOpen, setModelOpen] = useState(false)
  const [modelCreating, setModelCreating] = useState(false)
  const [modelForm, setModelForm] = useState(emptyModelForm)

  const [logs, setLogs] = useState<AiLog[]>([])
  const [logsLoading, setLogsLoading] = useState(true)
  const [logsError, setLogsError] = useState<string | null>(null)

  const [costs, setCosts] = useState<GovernanceCosts | null>(null)
  const [incidents, setIncidents] = useState<GovernanceIncident[]>([])
  const [evals, setEvals] = useState<GovernanceEval[]>([])
  const [govLoading, setGovLoading] = useState(false)
  const [govError, setGovError] = useState<string | null>(null)
  const [incidentBusy, setIncidentBusy] = useState(false)
  const [incidentForm, setIncidentForm] = useState({
    capability: 'chat',
    severity: 'medium',
    summary: '',
    detail: '',
  })

  const threadRef = useRef<HTMLDivElement>(null)

  const nextId = useCallback(() => {
    idCounter.current += 1
    return `${Date.now()}-${idCounter.current}`
  }, [])

  const loadAgents = useCallback(
    async (silent = false) => {
      if (!silent) setAgentsLoading(true)
      try {
        const result = await apiFetch<AiAgent[]>('/ai/agents')
        setAgents(Array.isArray(result) ? result : [])
        setAgentsError(null)
      } catch (e) {
        setAgentsError(e instanceof Error ? e.message : 'Failed to load agents')
      } finally {
        if (!silent) setAgentsLoading(false)
      }
    },
    [apiFetch],
  )

  const loadModels = useCallback(
    async (silent = false) => {
      if (!silent) setModelsLoading(true)
      try {
        const result = await apiFetch<AiModel[]>('/ai/models')
        setModels(Array.isArray(result) ? result : [])
        setModelsError(null)
      } catch (e) {
        setModelsError(e instanceof Error ? e.message : 'Failed to load models')
      } finally {
        if (!silent) setModelsLoading(false)
      }
    },
    [apiFetch],
  )

  const loadLogs = useCallback(
    async (silent = false) => {
      if (!silent) setLogsLoading(true)
      try {
        const result = await apiFetch<AiLog[]>('/ai/logs')
        setLogs(Array.isArray(result) ? result : [])
        setLogsError(null)
      } catch (e) {
        setLogsError(e instanceof Error ? e.message : 'Failed to load logs')
      } finally {
        if (!silent) setLogsLoading(false)
      }
    },
    [apiFetch],
  )

  const loadGovernance = useCallback(async () => {
    setGovLoading(true)
    try {
      const [costResult, incidentResult, evalResult] = await Promise.all([
        apiFetch<GovernanceCosts>('/ai/governance/costs'),
        apiFetch<GovernanceIncident[]>('/ai/governance/incidents'),
        apiFetch<GovernanceEval[]>('/ai/governance/evals'),
      ])
      setCosts(costResult)
      setIncidents(Array.isArray(incidentResult) ? incidentResult : [])
      setEvals(Array.isArray(evalResult) ? evalResult : [])
      setGovError(null)
    } catch (e) {
      setGovError(e instanceof Error ? e.message : 'Failed to load governance data')
    } finally {
      setGovLoading(false)
    }
  }, [apiFetch])

  const reportIncident = useCallback(async () => {
    if (!incidentForm.summary.trim()) return
    setIncidentBusy(true)
    try {
      await apiFetch<GovernanceIncident>('/ai/governance/incidents', {
        method: 'POST',
        body: JSON.stringify(incidentForm),
      })
      setIncidentForm({ capability: 'chat', severity: 'medium', summary: '', detail: '' })
      await loadGovernance()
    } catch (e) {
      setGovError(e instanceof Error ? e.message : 'Failed to report the incident')
    } finally {
      setIncidentBusy(false)
    }
  }, [apiFetch, incidentForm, loadGovernance])

  useEffect(() => {
    if (!isReady) return
    void loadAgents()
    void loadModels()
    void loadLogs()
  }, [isReady, loadAgents, loadModels, loadLogs])

  useEffect(() => {
    if (!isReady || activeTab !== 'governance') return
    void loadGovernance()
  }, [isReady, activeTab, loadGovernance])

  useEffect(() => {
    if (activeTab !== 'chat' || !streaming) return
    threadRef.current?.scrollTo({ top: threadRef.current.scrollHeight, behavior: 'smooth' })
  }, [streaming, streamingText, activeTab])

  const send = useCallback(
    async (text?: string) => {
      const content = (text ?? prompt).trim()
      if (!content || streaming) return
      const userMessage: ChatMessage = { id: nextId(), role: 'user', content }
      setMessages((previous) => [...previous, userMessage])
      setPrompt('')
      setStreamError(null)
      setStreaming(true)
      setStreamingText('')
      let accumulated = ''
      let finalMeta = ''
      let failed = false
      try {
        const response = await fetch(API_STREAM_URL, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
          },
          body: JSON.stringify({
            agentCode: agentCode || null,
            messages: [{ role: 'user', content }],
          }),
        })
        if (!response.ok || !response.body) {
          throw new Error(`Chat stream failed (${response.status})`)
        }
        const reader = response.body.getReader()
        const decoder = new TextDecoder('utf-8')
        const sse = new SseStreamDecoder()
        const handleEvent = (event: SseEvent) => {
          if (event.type === 'token' && typeof event.text === 'string') {
            accumulated += event.text
            setStreamingText(accumulated)
          } else if (event.type === 'done') {
            finalMeta = [event.model, event.intent, event.latencyMs != null ? `${event.latencyMs} ms` : null]
              .filter(Boolean)
              .join(' · ')
          } else if (event.type === 'error') {
            failed = true
            setStreamError(event.message ?? 'The assistant encountered an error.')
          }
        }
        for (;;) {
          const { done, value } = await reader.read()
          if (done) break
          sse.push(decoder.decode(value, { stream: true }), handleEvent)
        }
        sse.flush(handleEvent)
      } catch (e) {
        failed = true
        setStreamError(e instanceof Error ? e.message : 'Failed to reach the assistant.')
      } finally {
        setStreaming(false)
      }
      if (!failed) {
        setMessages((previous) => [
          ...previous,
          { id: nextId(), role: 'assistant', content: accumulated, meta: finalMeta },
        ])
      }
      setStreamingText('')
    },
    [accessToken, agentCode, nextId, prompt, streaming],
  )

  function onPromptKeyDown(event: ReactKeyboardEvent<HTMLInputElement>) {
    if (event.key === 'Enter') {
      event.preventDefault()
      void send()
    }
  }

  function clearConversation() {
    setMessages([])
    setStreamError(null)
  }

  async function toggleAgent(agent: AiAgent) {
    setBusyAgent(`toggle:${agent.id}`)
    try {
      await apiFetch<AiAgent>(`/ai/agents/${agent.id}`, {
        method: 'PATCH',
        body: JSON.stringify({ enabled: !agent.enabled }),
      })
      void loadAgents(true)
    } catch (e) {
      setAgentsError(e instanceof Error ? e.message : 'Failed to update agent')
    } finally {
      setBusyAgent(null)
    }
  }

  async function createAgent() {
    setAgentCreating(true)
    try {
      await apiFetch<{ id: string; code: string; name: string }>('/ai/agents', {
        method: 'POST',
        body: JSON.stringify({
          code: agentForm.code.trim(),
          name: agentForm.name.trim(),
          description: agentForm.description.trim() || undefined,
          systemPrompt: agentForm.systemPrompt.trim() || undefined,
          modelId: agentForm.modelId || undefined,
          temperature: agentForm.temperature ? Number(agentForm.temperature) : undefined,
          enabled: agentForm.enabled === 'true',
        }),
      })
      setAgentOpen(false)
      setAgentForm(emptyAgentForm)
      void loadAgents(true)
    } catch (e) {
      setAgentsError(e instanceof Error ? e.message : 'Failed to create agent')
    } finally {
      setAgentCreating(false)
    }
  }

  async function createModel() {
    setModelCreating(true)
    try {
      await apiFetch<{ id: string; name: string; provider: string }>('/ai/models', {
        method: 'POST',
        body: JSON.stringify({
          name: modelForm.name.trim(),
          provider: modelForm.provider,
          modelName: modelForm.modelName.trim() || undefined,
          baseUrl: modelForm.baseUrl.trim() || undefined,
          apiKey: modelForm.apiKey || undefined,
          temperature: modelForm.temperature ? Number(modelForm.temperature) : undefined,
          maxTokens: modelForm.maxTokens ? Number(modelForm.maxTokens) : undefined,
          isDefault: modelForm.isDefault === 'true',
        }),
      })
      setModelOpen(false)
      setModelForm(emptyModelForm)
      void loadModels(true)
    } catch (e) {
      setModelsError(e instanceof Error ? e.message : 'Failed to create model')
    } finally {
      setModelCreating(false)
    }
  }

  async function deleteModel(model: AiModel) {
    if (!window.confirm(`Delete AI model "${model.name}"?`)) return
    setBusyModel(`delete:${model.id}`)
    try {
      await apiFetch<{ deleted: boolean }>(`/ai/models/${model.id}`, {
        method: 'DELETE',
      })
      void loadModels(true)
    } catch (e) {
      setModelsError(e instanceof Error ? e.message : 'Failed to delete model')
    } finally {
      setBusyModel(null)
    }
  }

  function modelNameFor(modelId: string | null | undefined): string {
    if (!modelId) return 'default'
    const model = models.find((m) => m.id === modelId)
    return model ? model.name : shortId(modelId)
  }

  if (!isReady) {
    return (
      <div style={{ padding: 24, display: 'flex', alignItems: 'center', gap: 12 }}>
        <Spinner size="sm" />
        <span style={{ fontSize: 14, color: 'var(--sq-fg-muted)' }}>Loading AI assistant…</span>
      </div>
    )
  }

  return (
    <div style={{ padding: 24 }}>
      <PageHeader
        title="AI Assistant"
        description="Ask SQUAMED anything, manage assistant agents and review interaction logs."
        actions={
          <Badge variant={streaming ? 'warning' : 'success'} size="md">
            {streaming ? 'Streaming…' : 'Online'}
          </Badge>
        }
      />

      <Tabs value={activeTab} onValueChange={setActiveTab}>
        <TabsList style={{ marginBottom: 16 }}>
          <TabsTrigger value="chat">Chat</TabsTrigger>
          <TabsTrigger value="agents">Agents</TabsTrigger>
          <TabsTrigger value="logs">Logs</TabsTrigger>
          <TabsTrigger value="governance">Governance</TabsTrigger>
        </TabsList>

        <TabsContent value="chat">
          <ContentPanel
            style={{
              display: 'flex',
              flexDirection: 'column',
              height: 'calc(100vh - 240px)',
              minHeight: 440,
            }}
          >
            <div
              style={{
                display: 'flex',
                alignItems: 'flex-end',
                justifyContent: 'space-between',
                gap: 12,
                marginBottom: 12,
              }}
            >
              <div style={{ width: 220 }}>
                <Select
                  label="Agent"
                  size="sm"
                  value={agentCode}
                  onChange={(event) => setAgentCode(event.target.value)}
                >
                  {agents.map((agent) => (
                    <option key={agent.id} value={agent.code}>
                      {agent.name} ({agent.code})
                    </option>
                  ))}
                  {!agents.some((agent) => agent.code === agentCode) ? (
                    <option value={agentCode}>{agentCode || 'ops'}</option>
                  ) : null}
                </Select>
              </div>
              <Button size="sm" variant="outline" onClick={clearConversation}>
                Clear conversation
              </Button>
            </div>

            <div
              ref={threadRef}
              style={{
                flex: 1,
                minHeight: 0,
                overflowY: 'auto',
                display: 'flex',
                flexDirection: 'column',
                gap: 10,
                padding: 12,
                borderRadius: 8,
                border: '1px solid var(--sq-border)',
                marginBottom: 12,
                backgroundColor: 'color-mix(in srgb, var(--sq-fg) 2%, transparent)',
              }}
            >
              {messages.length === 0 && !streaming ? (
                <EmptyState message="Ask the assistant anything about the hospital — patients, approvals, revenue, or say “help” for commands." />
              ) : null}

              {messages.map((message) =>
                message.role === 'user' ? (
                  <div
                    key={message.id}
                    style={{
                      alignSelf: 'flex-end',
                      maxWidth: '78%',
                      padding: '10px 14px',
                      borderRadius: 12,
                      borderTopRightRadius: 4,
                      backgroundColor: 'color-mix(in srgb, var(--sq-primary) 14%, transparent)',
                      border: '1px solid color-mix(in srgb, var(--sq-primary) 28%, transparent)',
                      fontSize: 13.5,
                      lineHeight: 1.5,
                      whiteSpace: 'pre-line',
                      overflowWrap: 'break-word',
                    }}
                  >
                    {message.content}
                  </div>
                ) : (
                  <div
                    key={message.id}
                    style={{
                      alignSelf: 'flex-start',
                      maxWidth: '82%',
                      padding: '10px 14px',
                      borderRadius: 12,
                      borderTopLeftRadius: 4,
                      backgroundColor: 'var(--sq-surface)',
                      border: '1px solid var(--sq-border)',
                      fontSize: 13.5,
                      lineHeight: 1.55,
                    }}
                  >
                    <span style={{ whiteSpace: 'pre-line', overflowWrap: 'break-word' }}>
                      {renderRich(message.content)}
                    </span>
                    {message.meta ? (
                      <div style={{ ...mutedText, marginTop: 6, fontSize: 11 }}>{message.meta}</div>
                    ) : null}
                  </div>
                ),
              )}

              {streaming ? (
                <div
                  style={{
                    alignSelf: 'flex-start',
                    maxWidth: '82%',
                    padding: '10px 14px',
                    borderRadius: 12,
                    borderTopLeftRadius: 4,
                    backgroundColor: 'var(--sq-surface)',
                    border: '1px solid var(--sq-border)',
                    fontSize: 13.5,
                    lineHeight: 1.55,
                  }}
                >
                  <span style={{ whiteSpace: 'pre-line', overflowWrap: 'break-word' }}>
                    {streamingText || (
                      <span style={{ ...mutedText, textTransform: 'uppercase', letterSpacing: '0.05em', fontSize: 11 }}>
                        Assistant is typing…
                      </span>
                    )}
                  </span>
                </div>
              ) : null}

              {streamError ? <StatusIndicator tone="critical" label={streamError} /> : null}
            </div>

            <Stack gap={8}>
              <Stack direction="row" gap={6} wrap>
                {SAMPLE_PROMPTS.map((sample) => (
                  <button
                    key={sample}
                    type="button"
                    disabled={streaming}
                    onClick={() => void send(sample)}
                    style={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      padding: '4px 12px',
                      borderRadius: 999,
                      border: '1px solid var(--sq-border)',
                      backgroundColor: 'transparent',
                      color: 'var(--sq-fg-muted)',
                      fontFamily: 'inherit',
                      fontSize: 12,
                      cursor: streaming ? 'not-allowed' : 'pointer',
                      opacity: streaming ? 0.6 : 1,
                    }}
                  >
                    {sample}
                  </button>
                ))}
              </Stack>
              <Stack direction="row" gap={8} align="flex-end">
                <Input
                  value={prompt}
                  placeholder="Ask the assistant…  (Enter to send)"
                  disabled={streaming}
                  onChange={(event) => setPrompt(event.target.value)}
                  onKeyDown={onPromptKeyDown}
                />
                <Button loading={streaming} disabled={!prompt.trim()} onClick={() => void send()}>
                  Send
                </Button>
              </Stack>
            </Stack>
          </ContentPanel>
        </TabsContent>

        <TabsContent value="agents">
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
              <strong style={{ fontSize: 14 }}>Agents</strong>
              <Button size="sm" onClick={() => setAgentOpen(true)}>
                New agent
              </Button>
            </div>

            {agentsError ? <StatusIndicator tone="critical" label={agentsError} /> : null}

            {agentsLoading ? (
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '16px 0' }}>
                <Spinner size="sm" />
                <span style={{ fontSize: 13, color: 'var(--sq-fg-muted)' }}>Loading agents…</span>
              </div>
            ) : agents.length === 0 ? (
              <EmptyState message="No agents configured." />
            ) : (
              <div style={fullBorderStyle}>
                <table style={fullWidthTable}>
                  <thead>
                    <tr>
                      <th style={tableHeaderStyle}>Code</th>
                      <th style={tableHeaderStyle}>Name</th>
                      <th style={tableHeaderStyle}>Description</th>
                      <th style={tableHeaderStyle}>Model</th>
                      <th style={tableHeaderStyle}>Enabled</th>
                      <th style={tableHeaderStyle}>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {agents.map((agent) => (
                      <tr key={agent.id}>
                        <td style={tableCellStyle}>
                          <code style={{ fontSize: 12 }}>{agent.code}</code>
                        </td>
                        <td style={tableCellStyle}>
                          <strong>{agent.name}</strong>
                        </td>
                        <td style={tableCellStyle}>
                          <span title={agent.description ?? undefined}>
                            {truncate(agent.description ?? '—', 60)}
                          </span>
                        </td>
                        <td style={tableCellStyle}>
                          <code style={{ fontSize: 12 }}>{modelNameFor(agent.modelId)}</code>
                        </td>
                        <td style={tableCellStyle}>
                          <Badge variant={agent.enabled ? 'success' : 'neutral'} size="sm">
                            {agent.enabled ? 'Enabled' : 'Disabled'}
                          </Badge>
                        </td>
                        <td style={tableCellStyle}>
                          <Button
                            size="sm"
                            variant={agent.enabled ? 'outline' : 'success'}
                            loading={busyAgent === `toggle:${agent.id}`}
                            onClick={() => void toggleAgent(agent)}
                          >
                            {agent.enabled ? 'Disable' : 'Enable'}
                          </Button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
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
              <strong style={{ fontSize: 14 }}>Models</strong>
              <Button size="sm" onClick={() => setModelOpen(true)}>
                New model
              </Button>
            </div>

            {modelsError ? <StatusIndicator tone="critical" label={modelsError} /> : null}

            {modelsLoading ? (
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '16px 0' }}>
                <Spinner size="sm" />
                <span style={{ fontSize: 13, color: 'var(--sq-fg-muted)' }}>Loading models…</span>
              </div>
            ) : models.length === 0 ? (
              <EmptyState message="No AI models configured." />
            ) : (
              <div style={fullBorderStyle}>
                <table style={fullWidthTable}>
                  <thead>
                    <tr>
                      <th style={tableHeaderStyle}>Name</th>
                      <th style={tableHeaderStyle}>Provider</th>
                      <th style={tableHeaderStyle}>Model name</th>
                      <th style={tableHeaderStyle}>Temperature</th>
                      <th style={tableHeaderStyle}>Max tokens</th>
                      <th style={tableHeaderStyle}>Default</th>
                      <th style={tableHeaderStyle}>Enabled</th>
                      <th style={tableHeaderStyle}>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {models.map((model) => (
                      <tr key={model.id}>
                        <td style={tableCellStyle}>
                          <strong>{model.name}</strong>
                        </td>
                        <td style={tableCellStyle}>
                          <Badge variant="information" size="sm">{model.provider}</Badge>
                        </td>
                        <td style={tableCellStyle}>
                          <code style={{ fontSize: 12 }}>{model.modelName ?? '—'}</code>
                        </td>
                        <td style={tableCellStyle}>{model.temperature?.toFixed(1) ?? '—'}</td>
                        <td style={tableCellStyle}>{model.maxTokens ?? '—'}</td>
                        <td style={tableCellStyle}>
                          {model.isDefault ? (
                            <Badge variant="primary" size="sm">Default</Badge>
                          ) : (
                            <span style={mutedText}>—</span>
                          )}
                        </td>
                        <td style={tableCellStyle}>
                          <Badge variant={model.enabled ? 'success' : 'neutral'} size="sm">
                            {model.enabled ? 'Enabled' : 'Disabled'}
                          </Badge>
                        </td>
                        <td style={{ ...tableCellStyle, textAlign: 'right' }}>
                          <Button
                            size="sm"
                            variant="danger"
                            loading={busyModel === `delete:${model.id}`}
                            onClick={() => void deleteModel(model)}
                          >
                            Delete
                          </Button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </ContentPanel>
        </TabsContent>

        <TabsContent value="logs">
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
              <strong style={{ fontSize: 14 }}>Interaction logs</strong>
              <Button size="sm" variant="outline" loading={logsLoading} onClick={() => void loadLogs()}>
                Refresh
              </Button>
            </div>

            {logsError ? <StatusIndicator tone="critical" label={logsError} /> : null}

            {logsLoading ? (
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '16px 0' }}>
                <Spinner size="sm" />
                <span style={{ fontSize: 13, color: 'var(--sq-fg-muted)' }}>Loading logs…</span>
              </div>
            ) : logs.length === 0 ? (
              <EmptyState message="No assistant interactions yet." />
            ) : (
              <div style={fullBorderStyle}>
                <table style={fullWidthTable}>
                  <thead>
                    <tr>
                      <th style={tableHeaderStyle}>Time</th>
                      <th style={tableHeaderStyle}>User message</th>
                      <th style={tableHeaderStyle}>Assistant message</th>
                      <th style={tableHeaderStyle}>Intent</th>
                      <th style={tableHeaderStyle}>Model</th>
                      <th style={tableHeaderStyle}>Latency</th>
                      <th style={tableHeaderStyle}>Agent</th>
                    </tr>
                  </thead>
                  <tbody>
                    {logs.map((log) => (
                      <tr key={log.id}>
                        <td style={tableCellStyle}>{formatDateTime(log.createdAt)}</td>
                        <td style={tableCellStyle}>
                          <span title={log.userMessage}>{truncate(log.userMessage, 80)}</span>
                        </td>
                        <td style={tableCellStyle}>
                          <span title={log.assistantMessage}>
                            {truncate(log.assistantMessage, 120)}
                          </span>
                        </td>
                        <td style={tableCellStyle}>
                          {log.intent ? (
                            <Badge variant={intentVariants[log.intent] ?? 'neutral'} size="sm">
                              {log.intent}
                            </Badge>
                          ) : (
                            <span style={mutedText}>—</span>
                          )}
                        </td>
                        <td style={tableCellStyle}>{log.modelName ?? '—'}</td>
                        <td style={tableCellStyle}>
                          {log.latencyMs != null ? `${log.latencyMs} ms` : '—'}
                        </td>
                        <td style={tableCellStyle}>
                          <code style={{ fontSize: 12 }}>{log.agentCode ?? '—'}</code>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </ContentPanel>
        </TabsContent>

        <TabsContent value="governance">
          <Stack gap={16}>
            {govError ? (
              <ContentPanel>
                <span style={{ color: 'var(--sq-critical)', fontSize: 13 }}>{govError}</span>
              </ContentPanel>
            ) : null}
            <ContentPanel>
              <h2 style={{ fontSize: 15, fontWeight: 600, marginBottom: 12 }}>Cost per capability (7 days)</h2>
              {govLoading && !costs ? (
                <Spinner size="sm" />
              ) : (
                <table style={fullWidthTable}>
                  <thead>
                    <tr>
                      <th style={tableHeaderStyle}>Capability</th>
                      <th style={tableHeaderStyle}>Requests</th>
                      <th style={tableHeaderStyle}>Tokens</th>
                      <th style={tableHeaderStyle}>Cost (USD)</th>
                    </tr>
                  </thead>
                  <tbody>
                    {(costs?.byCapability ?? []).map((row) => (
                      <tr key={row.name}>
                        <td style={tableCellStyle}>{row.name}</td>
                        <td style={tableCellStyle}>{row.requests}</td>
                        <td style={tableCellStyle}>{row.tokens}</td>
                        <td style={tableCellStyle}>{row.costUsd.toFixed(4)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
              <div style={{ ...mutedText, marginTop: 8, fontSize: 12 }}>
                Total: {costs ? costs.totalCostUsd.toFixed(4) : '0.0000'} USD ·{' '}
                {(costs?.byModel ?? []).map((row) => `${row.name}: ${row.tokens} tokens`).join(' · ') || 'no model calls'}
              </div>
            </ContentPanel>

            <ContentPanel>
              <h2 style={{ fontSize: 15, fontWeight: 600, marginBottom: 12 }}>Incidents</h2>
              <div style={{ display: 'grid', gridTemplateColumns: '140px 140px 1fr auto', gap: 8, marginBottom: 12 }}>
                <Select
                  aria-label="Incident capability"
                  value={incidentForm.capability}
                  onChange={(event) => setIncidentForm({ ...incidentForm, capability: event.target.value })}
                >
                  <option value="chat">chat</option>
                  <option value="command">command</option>
                  <option value="scribe">scribe</option>
                  <option value="report">report</option>
                </Select>
                <Select
                  aria-label="Incident severity"
                  value={incidentForm.severity}
                  onChange={(event) => setIncidentForm({ ...incidentForm, severity: event.target.value })}
                >
                  <option value="low">low</option>
                  <option value="medium">medium</option>
                  <option value="high">high</option>
                  <option value="critical">critical</option>
                </Select>
                <Input
                  aria-label="Incident summary"
                  placeholder="What went wrong?"
                  value={incidentForm.summary}
                  onChange={(event) => setIncidentForm({ ...incidentForm, summary: event.target.value })}
                />
                <Button size="sm" loading={incidentBusy} onClick={() => void reportIncident()}>
                  Report
                </Button>
              </div>
              {incidents.length === 0 ? (
                <EmptyState message="No AI incidents reported." />
              ) : (
                <table style={fullWidthTable}>
                  <thead>
                    <tr>
                      <th style={tableHeaderStyle}>Reported</th>
                      <th style={tableHeaderStyle}>Capability</th>
                      <th style={tableHeaderStyle}>Severity</th>
                      <th style={tableHeaderStyle}>Summary</th>
                      <th style={tableHeaderStyle}>Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {incidents.map((incident) => (
                      <tr key={incident.id}>
                        <td style={tableCellStyle}>{formatDateTime(incident.reportedAt)}</td>
                        <td style={tableCellStyle}>{incident.capability}</td>
                        <td style={tableCellStyle}>
                          <Badge
                            size="sm"
                            variant={incident.severity === 'critical' || incident.severity === 'high' ? 'critical' : 'warning'}
                          >
                            {incident.severity}
                          </Badge>
                        </td>
                        <td style={tableCellStyle}>{truncate(incident.summary, 80)}</td>
                        <td style={tableCellStyle}>{incident.status}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </ContentPanel>

            <ContentPanel>
              <h2 style={{ fontSize: 15, fontWeight: 600, marginBottom: 12 }}>Eval results</h2>
              {evals.length === 0 ? (
                <EmptyState message="No eval runs recorded for this tenant." />
              ) : (
                <table style={fullWidthTable}>
                  <thead>
                    <tr>
                      <th style={tableHeaderStyle}>Recorded</th>
                      <th style={tableHeaderStyle}>Subject</th>
                      <th style={tableHeaderStyle}>Suite</th>
                      <th style={tableHeaderStyle}>Cases</th>
                      <th style={tableHeaderStyle}>Score</th>
                      <th style={tableHeaderStyle}>Verdict</th>
                    </tr>
                  </thead>
                  <tbody>
                    {evals.map((run) => (
                      <tr key={run.id}>
                        <td style={tableCellStyle}>{formatDateTime(run.recordedAt)}</td>
                        <td style={tableCellStyle}>
                          <code style={{ fontSize: 12 }}>{run.subject}</code>
                        </td>
                        <td style={tableCellStyle}>{run.suiteName}</td>
                        <td style={tableCellStyle}>
                          {run.casesPassed}/{run.casesTotal}
                        </td>
                        <td style={tableCellStyle}>{run.score.toFixed(2)}</td>
                        <td style={tableCellStyle}>
                          <Badge size="sm" variant={run.verdict === 'PASS' ? 'success' : 'critical'}>
                            {run.verdict}
                          </Badge>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </ContentPanel>
          </Stack>
        </TabsContent>
      </Tabs>

      <Modal
        open={agentOpen}
        onClose={() => setAgentOpen(false)}
        title="New agent"
        description="Configure an assistant agent and route it to a model."
        footer={
          <>
            <Button variant="outline" onClick={() => setAgentOpen(false)}>
              Cancel
            </Button>
            <Button
              loading={agentCreating}
              disabled={!agentForm.code.trim() || !agentForm.name.trim()}
              onClick={() => void createAgent()}
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
            value={agentForm.code}
            onChange={(event) => setAgentForm({ ...agentForm, code: event.target.value })}
          />
          <Input
            label="Name"
            required
            value={agentForm.name}
            onChange={(event) => setAgentForm({ ...agentForm, name: event.target.value })}
          />
          <Textarea
            label="Description"
            value={agentForm.description}
            onChange={(event) => setAgentForm({ ...agentForm, description: event.target.value })}
          />
          <Textarea
            label="System prompt"
            rows={5}
            value={agentForm.systemPrompt}
            onChange={(event) => setAgentForm({ ...agentForm, systemPrompt: event.target.value })}
          />
          <Stack direction="row" gap={12}>
            <div style={{ flex: 1 }}>
              <Input
                label="Temperature"
                type="number"
                min={0}
                max={2}
                step={0.1}
                value={agentForm.temperature}
                onChange={(event) => setAgentForm({ ...agentForm, temperature: event.target.value })}
              />
            </div>
            <div style={{ flex: 1 }}>
              <Select
                label="Model"
                value={agentForm.modelId}
                onChange={(event) => setAgentForm({ ...agentForm, modelId: event.target.value })}
              >
                <option value="">Default</option>
                {models.map((model) => (
                  <option key={model.id} value={model.id}>
                    {model.name}
                  </option>
                ))}
              </Select>
            </div>
          </Stack>
          <Select
            label="Enabled"
            value={agentForm.enabled}
            onChange={(event) => setAgentForm({ ...agentForm, enabled: event.target.value })}
          >
            <option value="true">Yes</option>
            <option value="false">No</option>
          </Select>
        </Stack>
      </Modal>

      <Modal
        open={modelOpen}
        onClose={() => setModelOpen(false)}
        title="New model"
        description="Register an LLM provider configuration."
        footer={
          <>
            <Button variant="outline" onClick={() => setModelOpen(false)}>
              Cancel
            </Button>
            <Button
              loading={modelCreating}
              disabled={!modelForm.name.trim()}
              onClick={() => void createModel()}
            >
              Create
            </Button>
          </>
        }
      >
        <Stack gap={12}>
          <Input
            label="Name"
            required
            value={modelForm.name}
            onChange={(event) => setModelForm({ ...modelForm, name: event.target.value })}
          />
          <Select
            label="Provider"
            value={modelForm.provider}
            onChange={(event) => setModelForm({ ...modelForm, provider: event.target.value })}
          >
            {PROVIDERS.map((provider) => (
              <option key={provider} value={provider}>{provider}</option>
            ))}
          </Select>
          <Input
            label="Model name"
            value={modelForm.modelName}
            placeholder="echo-v2 / gpt-4o / claude-sonnet-4"
            onChange={(event) => setModelForm({ ...modelForm, modelName: event.target.value })}
          />
          <Input
            label="Base URL"
            value={modelForm.baseUrl}
            placeholder="https://…"
            onChange={(event) => setModelForm({ ...modelForm, baseUrl: event.target.value })}
          />
          <Input
            label="API key"
            type="password"
            value={modelForm.apiKey}
            onChange={(event) => setModelForm({ ...modelForm, apiKey: event.target.value })}
          />
          <Stack direction="row" gap={12}>
            <div style={{ flex: 1 }}>
              <Input
                label="Temperature"
                type="number"
                min={0}
                max={2}
                step={0.1}
                value={modelForm.temperature}
                onChange={(event) => setModelForm({ ...modelForm, temperature: event.target.value })}
              />
            </div>
            <div style={{ flex: 1 }}>
              <Input
                label="Max tokens"
                type="number"
                min={1}
                step={1}
                value={modelForm.maxTokens}
                onChange={(event) => setModelForm({ ...modelForm, maxTokens: event.target.value })}
              />
            </div>
          </Stack>
          <Select
            label="Default model"
            value={modelForm.isDefault}
            onChange={(event) => setModelForm({ ...modelForm, isDefault: event.target.value })}
          >
            <option value="false">No</option>
            <option value="true">Yes</option>
          </Select>
        </Stack>
      </Modal>
    </div>
  )
}