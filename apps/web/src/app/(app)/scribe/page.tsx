'use client'

import { useCallback, useEffect, useState } from 'react'
import {
  Badge,
  Button,
  ContentPanel,
  Input,
  Modal,
  PageHeader,
  Spinner,
  Stack,
  StatusIndicator,
  Textarea,
} from '@squamed/ui'
import type { BadgeVariant } from '@squamed/ui'
import { useApi } from '@/lib/use-api'

interface Segment {
  speaker: string
  text: string
}

interface SpeakerCount {
  speaker: string
  segments: number
}

interface ScribeSession {
  id: string
  patientId: string | null
  encounterId: string | null
  patientName: string | null
  chiefComplaint: string | null
  source: string
  status: string
  stage: string
  transcript: string | null
  segments: Segment[]
  speakerMap: SpeakerCount[]
  draft: string | null
  durationSeconds: number
  signedAt: string | null
  createdAt: string | null
}

const STAGES = ['CREATED', 'TRANSCRIBING', 'TRANSCRIBED', 'SPEAKERS_RESOLVED', 'DRAFTED', 'SIGNED']

const stageVariants: Record<string, BadgeVariant> = {
  CREATED: 'neutral',
  TRANSCRIBING: 'information',
  TRANSCRIBED: 'information',
  SPEAKERS_RESOLVED: 'information',
  DRAFTED: 'warning',
  SIGNED: 'success',
}

const statusVariants: Record<string, BadgeVariant> = {
  DRAFT: 'neutral',
  SIGNED: 'success',
}

const SAMPLE_TRANSCRIPT = `Patient: I have had this cough for five days and I cannot sleep at night.
Doctor: Airways sound clear, mild wheeze on expiration noted.
Patient: My temperature was 38 at home.
Doctor: Prescribing an inhaler and a chest X-ray.`

const emptyCreateForm = { patientName: '', chiefComplaint: '' }

function formatDateTime(value: string | null | undefined): string {
  if (!value) return '—'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return '—'
  return d.toLocaleString(undefined, { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' })
}

function formatDuration(seconds: number): string {
  if (seconds <= 0) return '—'
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${m}:${s.toString().padStart(2, '0')}`
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

function StageStepper({ stage }: { stage: string }) {
  const current = STAGES.indexOf(stage)
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 4,
        flexWrap: 'wrap',
        marginBottom: 4,
      }}
    >
      {STAGES.map((s, index) => (
        <div key={s} style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
          <span
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              padding: '3px 10px',
              borderRadius: 999,
              fontSize: 11,
              fontWeight: 600,
              backgroundColor:
                index === current
                  ? 'var(--sq-primary)'
                  : index < current
                    ? 'color-mix(in srgb, var(--sq-primary) 18%, transparent)'
                    : 'color-mix(in srgb, var(--sq-fg) 6%, transparent)',
              color: index === current ? 'var(--sq-surface)' : 'var(--sq-fg-muted)',
              border: '1px solid var(--sq-border)',
            }}
          >
            {s}
          </span>
          {index < STAGES.length - 1 ? (
            <span style={{ color: index < current ? 'var(--sq-primary)' : 'var(--sq-border)' }}>→</span>
          ) : null}
        </div>
      ))}
    </div>
  )
}

export default function ScribePage() {
  const { apiFetch, isReady } = useApi()
  const [sessions, setSessions] = useState<ScribeSession[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [detail, setDetail] = useState<ScribeSession | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [busy, setBusy] = useState<string | null>(null)

  const [createOpen, setCreateOpen] = useState(false)
  const [creating, setCreating] = useState(false)
  const [createForm, setCreateForm] = useState(emptyCreateForm)

  const [transcript, setTranscript] = useState('')
  const [draft, setDraft] = useState('')

  const loadSessions = useCallback(
    async (silent = false) => {
      if (!silent) setLoading(true)
      try {
        const result = await apiFetch<ScribeSession[]>('/scribe/sessions')
        setSessions(Array.isArray(result) ? result : [])
        setError(null)
      } catch (e) {
        setError(e instanceof Error ? e.message : 'Failed to load scribe sessions')
      } finally {
        if (!silent) setLoading(false)
      }
    },
    [apiFetch],
  )

  const loadDetail = useCallback(
    async (id: string) => {
      try {
        setDetailLoading(true)
        const result = await apiFetch<ScribeSession>(`/scribe/sessions/${id}`)
        setDetail(result)
        setTranscript(result.transcript ?? '')
        setDraft(result.draft ?? '')
        setError(null)
      } catch (e) {
        setError(e instanceof Error ? e.message : 'Failed to load session')
      } finally {
        setDetailLoading(false)
      }
    },
    [apiFetch],
  )

  useEffect(() => {
    if (!isReady) return
    void loadSessions()
  }, [isReady, loadSessions])

  useEffect(() => {
    if (!isReady || !selectedId) return
    void loadDetail(selectedId)
  }, [isReady, selectedId, loadDetail])

  function refreshDetail(result: ScribeSession) {
    setDetail(result)
    setTranscript(result.transcript ?? '')
    setDraft(result.draft ?? '')
  }

  async function createSession() {
    setCreating(true)
    try {
      const session = await apiFetch<ScribeSession>('/scribe/sessions', {
        method: 'POST',
        body: JSON.stringify({
          patientName: createForm.patientName.trim() || undefined,
          chiefComplaint: createForm.chiefComplaint.trim() || undefined,
        }),
      })
      setCreateOpen(false)
      setCreateForm(emptyCreateForm)
      await loadSessions(true)
      if (session.id) setSelectedId(session.id)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to create session')
    } finally {
      setCreating(false)
    }
  }

  async function runAction(action: string, body?: object) {
    if (!selectedId) return
    setBusy(action)
    try {
      const result = await apiFetch<ScribeSession>(`/scribe/sessions/${selectedId}/${action}`, {
        method: 'POST',
        body: JSON.stringify(body ?? {}),
      })
      refreshDetail(result)
      await loadSessions(true)
      setError(null)
    } catch (e) {
      setError(e instanceof Error ? e.message : `Failed: ${action}`)
    } finally {
      setBusy(null)
    }
  }

  async function saveTranscript() {
    if (!selectedId || !transcript.trim()) return
    setBusy('transcript')
    try {
      const result = await apiFetch<ScribeSession>(`/scribe/sessions/${selectedId}/transcript`, {
        method: 'POST',
        body: JSON.stringify({ text: transcript }),
      })
      refreshDetail(result)
      await loadSessions(true)
      setError(null)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to save transcript')
    } finally {
      setBusy(null)
    }
  }

  async function saveDraft() {
    if (!selectedId) return
    setBusy('draft')
    try {
      const result = await apiFetch<ScribeSession>(`/scribe/sessions/${selectedId}/draft`, {
        method: 'POST',
        body: JSON.stringify({ draft }),
      })
      refreshDetail(result)
      await loadSessions(true)
      setError(null)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to save draft')
    } finally {
      setBusy(null)
    }
  }

  async function deleteSession(id: string) {
    if (!window.confirm('Delete this scribe session?')) return
    setBusy(`delete:${id}`)
    try {
      await apiFetch<{ deleted: boolean }>(`/scribe/sessions/${id}`, { method: 'DELETE' })
      if (selectedId === id) {
        setSelectedId(null)
        setDetail(null)
      }
      await loadSessions(true)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to delete session')
    } finally {
      setBusy(null)
    }
  }

  if (!isReady) {
    return (
      <div style={{ padding: 24, display: 'flex', alignItems: 'center', gap: 12 }}>
        <Spinner size="sm" />
        <span style={{ fontSize: 14, color: 'var(--sq-fg-muted)' }}>Loading AI scribe…</span>
      </div>
    )
  }

  const canGenerate = detail && STAGES.indexOf(detail.stage) >= STAGES.indexOf('SPEAKERS_RESOLVED')
  const canSign = detail && detail.draft && detail.draft.trim().length > 0 && detail.status !== 'SIGNED'

  return (
    <div style={{ padding: 24 }}>
      <PageHeader
        title="AI Scribe"
        description="Record consultations, separate speakers, generate Clinical SOAP drafts and sign them."
        actions={
          <Button size="sm" onClick={() => setCreateOpen(true)}>
            New session
          </Button>
        }
      />

      <div style={{ display: 'grid', gridTemplateColumns: '340px 1fr', gap: 16 }}>
        <ContentPanel>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 }}>
            <strong style={{ fontSize: 14 }}>Sessions</strong>
            <Button size="sm" variant="ghost" loading={loading} onClick={() => void loadSessions()}>
              Refresh
            </Button>
          </div>
          {error ? <StatusIndicator tone="critical" label={error} /> : null}
          {loading ? (
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '16px 0' }}>
              <Spinner size="sm" />
              <span style={{ fontSize: 13, color: 'var(--sq-fg-muted)' }}>Loading…</span>
            </div>
          ) : sessions.length === 0 ? (
            <div style={{ padding: '28px 8px', textAlign: 'center', fontSize: 13, color: 'var(--sq-fg-muted)' }}>
              No scribe sessions yet. Start one to record a consultation.
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {sessions.map((session) => (
                <button
                  key={session.id}
                  type="button"
                  onClick={() => setSelectedId(session.id)}
                  style={{
                    textAlign: 'left',
                    padding: '10px 12px',
                    borderRadius: 8,
                    border: '1px solid var(--sq-border)',
                    backgroundColor:
                      session.id === selectedId
                        ? 'color-mix(in srgb, var(--sq-primary) 7%, transparent)'
                        : 'transparent',
                    cursor: 'pointer',
                    fontFamily: 'inherit',
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8 }}>
                    <strong style={{ fontSize: 13 }}>{session.patientName ?? 'Unnamed patient'}</strong>
                    <Badge variant={statusVariants[session.status] ?? 'neutral'} size="sm">
                      {session.status}
                    </Badge>
                  </div>
                  <div style={{ ...mutedText, marginTop: 4 }}>
                    {formatDateTime(session.createdAt)} · {session.stage}
                  </div>
                  {session.chiefComplaint ? (
                    <div style={{ ...mutedText, marginTop: 4, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      {session.chiefComplaint}
                    </div>
                  ) : null}
                </button>
              ))}
            </div>
          )}
        </ContentPanel>

        <ContentPanel>
          {detailLoading ? (
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '16px 0' }}>
              <Spinner size="sm" />
              <span style={{ fontSize: 13, color: 'var(--sq-fg-muted)' }}>Loading session…</span>
            </div>
          ) : !detail ? (
            <div style={{ padding: '48px 16px', textAlign: 'center', fontSize: 13, color: 'var(--sq-fg-muted)' }}>
              Select a session to run the scribe pipeline.
            </div>
          ) : (
            <Stack gap={18}>
              <div>
                <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 12 }}>
                  <Stack gap={4}>
                    <strong style={{ fontSize: 15 }}>{detail.patientName ?? 'Unnamed patient'}</strong>
                    <span style={mutedText}>
                      Created {formatDateTime(detail.createdAt)} · Duration {formatDuration(detail.durationSeconds)}
                    </span>
                  </Stack>
                  <Stack direction="row" gap={8} align="center">
                    <Badge variant={stageVariants[detail.stage] ?? 'neutral'} size="sm">{detail.stage}</Badge>
                    <Button size="sm" variant="danger" loading={busy === `delete:${detail.id}`} onClick={() => void deleteSession(detail.id)}>
                      Delete
                    </Button>
                  </Stack>
                </div>
                {detail.chiefComplaint ? (
                  <div style={{ ...mutedText, marginTop: 6 }}>
                    <strong>Chief complaint:</strong> {detail.chiefComplaint}
                  </div>
                ) : null}
              </div>

              <div>
                <div style={sectionLabelStyle}>Pipeline</div>
                <StageStepper stage={detail.stage} />
              </div>

              <div>
                <div style={sectionLabelStyle}>Recording control</div>
                <Stack direction="row" gap={8} align="flex-end">
                  {detail.stage === 'TRANSCRIBING' ? (
                    <Button
                      loading={busy === 'stop-recording'}
                      onClick={() => void runAction('stop-recording', { transcriptText: transcript, durationSeconds: 0 })}
                    >
                      Stop & transcribe
                    </Button>
                  ) : STAGES.indexOf(detail.stage) < STAGES.indexOf('TRANSCRIBING') ? (
                    <Button
                      loading={busy === 'start-recording'}
                      onClick={() => void runAction('start-recording')}
                      variant="success"
                    >
                      Start recording
                    </Button>
                  ) : (
                    <Badge variant="success" size="sm">Recording finished</Badge>
                  )}
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => setTranscript(SAMPLE_TRANSCRIPT)}
                    disabled={!selectedId}
                  >
                    Load sample transcript
                  </Button>
                </Stack>
              </div>

              <div>
                <div style={sectionLabelStyle}>Live transcript</div>
                <Stack gap={8}>
                  <Textarea
                    rows={7}
                    value={transcript}
                    disabled={!selectedId}
                    placeholder={
                      detail.stage === 'TRANSCRIBING'
                        ? 'Live transcription appears here as the clinician speaks…'
                        : 'Paste or type the consultation transcript (label speakers with “Patient:” / “Doctor:”).'
                    }
                    onChange={(e) => setTranscript(e.target.value)}
                  />
                  <Button loading={busy === 'transcript'} disabled={!selectedId || !transcript.trim()} onClick={() => void saveTranscript()}>
                    Save transcript & separate speakers
                  </Button>
                </Stack>
              </div>

              <div>
                <div style={sectionLabelStyle}>Speaker separation</div>
                {detail.segments.length === 0 ? (
                  <div style={{ ...mutedText, padding: '8px 0' }}>
                    No speaker segments yet — save a transcript first.
                  </div>
                ) : (
                  <Stack gap={6}>
                    {detail.segments.map((segment, index) => (
                      <div key={`${segment.speaker}-${index}`} style={{ display: 'flex', gap: 8, alignItems: 'flex-start' }}>
                        <Badge variant={segment.speaker === 'Doctor' ? 'information' : 'success'} size="sm" style={{ flexShrink: 0, marginTop: 2 }}>
                          {segment.speaker}
                        </Badge>
                        <span style={{ fontSize: 13, lineHeight: 1.5 }}>{segment.text}</span>
                      </div>
                    ))}
                  </Stack>
                )}
              </div>

              <div>
                <div style={sectionLabelStyle}>Clinical draft</div>
                <Stack gap={8}>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                    <Stack direction="row" gap={8} align="center">
                      <Button
                        size="sm"
                        loading={busy === 'generate'}
                        disabled={!canGenerate}
                        onClick={() => void runAction('generate')}
                      >
                        Generate SOAP draft
                      </Button>
                      <span style={mutedText}>Structured note assembled from transcript + chief complaint.</span>
                    </Stack>
                  </div>
                  <Textarea
                    rows={14}
                    value={draft}
                    disabled={!selectedId}
                    placeholder="Generated draft will appear here. You can edit it before signing."
                    onChange={(e) => setDraft(e.target.value)}
                  />
                  <Stack direction="row" gap={8}>
                    <Button loading={busy === 'draft'} disabled={!selectedId} onClick={() => void saveDraft()}>
                      Save draft
                    </Button>
                    <Button
                      variant="success"
                      loading={busy === 'sign'}
                      disabled={!canSign}
                      onClick={() => void runAction('sign')}
                    >
                      Sign & save
                    </Button>
                  </Stack>
                </Stack>
              </div>
            </Stack>
          )}
        </ContentPanel>
      </div>

      <Modal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        title="New scribe session"
        description="Start capturing a consultation for the AI scribe."
        footer={
          <>
            <Button variant="outline" onClick={() => setCreateOpen(false)}>
              Cancel
            </Button>
            <Button loading={creating} disabled={!createForm.patientName.trim()} onClick={() => void createSession()}>
              Create
            </Button>
          </>
        }
      >
        <Stack gap={12}>
          <Input
            label="Patient name"
            required
            value={createForm.patientName}
            onChange={(e) => setCreateForm({ ...createForm, patientName: e.target.value })}
          />
          <Textarea
            label="Chief complaint"
            rows={3}
            value={createForm.chiefComplaint}
            placeholder="e.g. Persistent cough and wheeze for 5 days"
            onChange={(e) => setCreateForm({ ...createForm, chiefComplaint: e.target.value })}
          />
        </Stack>
      </Modal>
    </div>
  )
}