'use client'

import { useCallback, useEffect, useRef, useState } from 'react'
import { useRouter } from 'next/navigation'
import { Badge, Button, Spinner, Stack } from '@squamed/ui'
import { useApi } from '@/lib/use-api'

interface CommandOutput {
  intent: string
  target: string | null
  detail: string | null
  message: string
  suggestions: string[]
}

const ROUTE_BY_TARGET: Record<string, string> = {
  dashboard: '/dashboard',
  analytics: '/dashboard',
  patients: '/patients',
  'patient registry': '/patients',
  appointments: '/appointments',
  schedule: '/appointments',
  queue: '/queue',
  'waiting room': '/queue',
  laboratory: '/laboratory',
  labs: '/laboratory',
  lab: '/laboratory',
  pharmacy: '/pharmacy',
  medications: '/pharmacy',
  billing: '/billing',
  finance: '/billing',
  invoices: '/billing',
  orders: '/orders',
  services: '/orders',
  communication: '/communication',
  workflow: '/workflow',
  integrations: '/integrations',
  interop: '/integrations',
  fhir: '/integrations',
  hl7: '/integrations',
  ai: '/ai',
  assistant: '/ai',
  reception: '/reception',
  'master data': '/master-data',
}

const SAMPLES = [
  'How many patients?',
  'Open laboratory',
  'Pending approvals',
  'Search patient Sara',
  'Revenue today',
  'Help',
]

export function targetRoute(target: string | null): string {
  if (!target) return ''
  return ROUTE_BY_TARGET[target.toLowerCase()] ?? `/${target.toLowerCase().replace(/\s+/g, '-')}`
}

const intentVariants: Record<string, 'information' | 'success' | 'warning' | 'neutral'> = {
  navigate: 'information',
  count: 'success',
  workflow: 'warning',
  help: 'neutral',
}

export function renderMessage(message: string) {
  const parts = message.split('**')
  return parts.map((part, i) =>
    i % 2 === 1 ? (
      <strong key={i} style={{ fontWeight: 700 }}>
        {part}
      </strong>
    ) : (
      <span key={i}>{part}</span>
    ),
  )
}

export function CommandBar() {
  const router = useRouter()
  const { apiFetch, isReady } = useApi()
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState<CommandOutput | null>(null)
  const [error, setError] = useState<string | null>(null)
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault()
        setOpen((previous) => !previous)
        return
      }
      if (event.key === '/' && !open) {
        const target = event.target as HTMLElement | null
        const tag = target?.tagName
        const editable =
          tag === 'INPUT' ||
          tag === 'TEXTAREA' ||
          tag === 'SELECT' ||
          target?.isContentEditable
        if (!editable) {
          event.preventDefault()
          setOpen(true)
        }
      }
      if (event.key === 'Escape' && open) {
        setOpen(false)
      }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [open])

  useEffect(() => {
    if (open) {
      inputRef.current?.focus()
      setQuery('')
      setResult(null)
      setError(null)
    }
  }, [open])

  const run = useCallback(
    async (value: string) => {
      const q = value.trim()
      if (!q) return
      setLoading(true)
      setError(null)
      try {
        const output = await apiFetch<CommandOutput>('/ai/command', {
          method: 'POST',
          body: JSON.stringify({ query: q }),
        })
        setResult(output)
      } catch (e) {
        setError(e instanceof Error ? e.message : 'Command failed')
      } finally {
        setLoading(false)
      }
    },
    [apiFetch],
  )

  if (!isReady) return null

  return (
    <>
      {open ? (
        <div
          onClick={() => setOpen(false)}
          style={{
            position: 'fixed',
            inset: 0,
            zIndex: 100,
            backgroundColor: 'rgba(8, 11, 18, 0.55)',
            backdropFilter: 'blur(2px)',
          }}
        />
      ) : null}
      {open ? (
        <div
          role="dialog"
          aria-label="Command bar"
          style={{
            position: 'fixed',
            top: '18vh',
            left: '50%',
            transform: 'translateX(-50%)',
            width: 'min(640px, calc(100vw - 32px))',
            zIndex: 101,
            backgroundColor: 'var(--sq-surface)',
            border: '1px solid var(--sq-border)',
            borderRadius: 12,
            boxShadow: 'var(--sq-shadow-lg)',
            overflow: 'hidden',
          }}
        >
          <input
            ref={inputRef}
            value={query}
            placeholder="Ask SQUAMED or type a command…  (Enter to run)"
            onChange={(event) => {
              setQuery(event.target.value)
              setResult(null)
              setError(null)
            }}
            onKeyDown={(event) => {
              if (event.key === 'Enter') {
                event.preventDefault()
                void run(query)
              } else if (event.key === 'Escape') {
                event.preventDefault()
                setOpen(false)
              }
            }}
            style={{
              width: '100%',
              height: 46,
              boxSizing: 'border-box',
              padding: '0 16px',
              border: 'none',
              borderBottom: '1px solid var(--sq-border)',
              backgroundColor: 'transparent',
              color: 'var(--sq-fg)',
              fontSize: 14,
              outline: 'none',
            }}
          />
          <div style={{ maxHeight: '44vh', overflowY: 'auto', padding: 10 }}>
            {loading ? (
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '12px 8px' }}>
                <Spinner size="sm" />
                <span style={{ fontSize: 13, color: 'var(--sq-fg-muted)' }}>
                  Asking SQUAMED Operations Assistant…
                </span>
              </div>
            ) : error ? (
              <div style={{ padding: '10px 8px' }}>
                <span style={{ fontSize: 13, color: 'var(--sq-critical)' }}>{error}</span>
              </div>
            ) : result ? (
              <Stack gap={10}>
                <div
                  style={{
                    padding: '10px 12px',
                    backgroundColor: 'color-mix(in srgb, var(--sq-fg) 3%, transparent)',
                    borderRadius: 8,
                    fontSize: 13,
                    lineHeight: 1.55,
                    whiteSpace: 'pre-line',
                  }}
                >
                  {renderMessage(result.message)}
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <Badge variant={intentVariants[result.intent] ?? 'neutral'} size="sm">
                    {result.intent}
                  </Badge>
                  {result.intent === 'navigate' && result.target ? (
                    <Button
                      size="sm"
                      onClick={() => {
                        router.push(targetRoute(result.target))
                        setOpen(false)
                      }}
                    >
                      Open {result.target}
                    </Button>
                  ) : null}
                </div>
                {result.suggestions.length > 0 ? (
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                    {result.suggestions.map((suggestion) => (
                      <button
                        key={suggestion}
                        type="button"
                        onClick={() => {
                          setQuery(suggestion)
                          void run(suggestion)
                        }}
                        style={{
                          border: '1px solid var(--sq-border)',
                          backgroundColor: 'transparent',
                          color: 'var(--sq-fg-muted)',
                          fontSize: 12,
                          borderRadius: 999,
                          padding: '4px 10px',
                          cursor: 'pointer',
                        }}
                      >
                        {suggestion}
                      </button>
                    ))}
                  </div>
                ) : null}
              </Stack>
            ) : query.trim() === '' ? (
              <div>
                <div
                  style={{
                    fontSize: 11,
                    fontWeight: 600,
                    color: 'var(--sq-fg-muted)',
                    textTransform: 'uppercase',
                    letterSpacing: '0.04em',
                    padding: '6px 8px',
                  }}
                >
                  Suggested commands
                </div>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, padding: '0 8px 8px' }}>
                  {SAMPLES.map((sample) => (
                    <button
                      key={sample}
                      type="button"
                      onClick={() => {
                        setQuery(sample)
                        void run(sample)
                      }}
                      style={{
                        border: '1px solid var(--sq-border)',
                        backgroundColor: 'transparent',
                        color: 'var(--sq-fg)',
                        fontSize: 12,
                        borderRadius: 999,
                        padding: '5px 12px',
                        cursor: 'pointer',
                      }}
                    >
                      {sample}
                    </button>
                  ))}
                </div>
              </div>
            ) : (
              <div style={{ padding: '10px 8px' }}>
                <span style={{ fontSize: 13, color: 'var(--sq-fg-muted)' }}>
                  Press Enter to ask the SQUAMED Operations Assistant.
                </span>
              </div>
            )}
          </div>
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              padding: '8px 12px',
              borderTop: '1px solid var(--sq-border)',
              fontSize: 11,
              color: 'var(--sq-fg-muted)',
            }}
          >
            <span>Cmd+K · Enter to run · Esc to close</span>
            <span>SQUAMED Operations Assistant</span>
          </div>
        </div>
      ) : null}
    </>
  )
}