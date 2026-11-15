'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { Input, Spinner, t } from '@squamed/ui';
import { usePermission } from '@squamed/auth';
import { useApi } from '@/lib/use-api';

interface SearchHit {
  id: string;
  type: string;
  title: string;
  subtitle: string | null;
  fields: Record<string, unknown>;
}

interface SearchResponse {
  total: number;
  hits: SearchHit[];
}

function hitTarget(hit: SearchHit): string | null {
  if (hit.type === 'patient') return `/patients/${hit.id}`;
  const patientId = hit.fields?.patientId;
  if (hit.type === 'encounter' && typeof patientId === 'string') {
    return `/patients/${patientId}`;
  }
  return null;
}

/**
 * OpenSearch-backed universal search over patients and encounters. Debounced
 * (250 ms, min 2 chars); results navigate to the patient record. The backend
 * scopes by the caller's hospital pin; a 403 or outage degrades to the empty
 * state rather than blocking the shell.
 */
export function UniversalSearch() {
  const canSearch = usePermission('patient:read:patient');
  const { apiFetch } = useApi();
  const router = useRouter();
  const [query, setQuery] = useState('');
  const [hits, setHits] = useState<SearchHit[]>([]);
  const [open, setOpen] = useState(false);
  const [searching, setSearching] = useState(false);
  const [error, setError] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);

  const run = useCallback(
    async (q: string) => {
      setSearching(true);
      try {
        const data = await apiFetch<SearchResponse>(`/search/search?q=${encodeURIComponent(q)}&size=8`);
        setHits(Array.isArray(data?.hits) ? data.hits : []);
        setError(false);
        setOpen(true);
      } catch {
        setHits([]);
        setError(true);
        setOpen(true);
      } finally {
        setSearching(false);
      }
    },
    [apiFetch],
  );

  useEffect(() => {
    const q = query.trim();
    if (q.length < 2) {
      setHits([]);
      setOpen(false);
      setError(false);
      return;
    }
    const timer = setTimeout(() => void run(q), 250);
    return () => clearTimeout(timer);
  }, [query, run]);

  useEffect(() => {
    function onPointerDown(event: PointerEvent) {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener('pointerdown', onPointerDown);
    return () => document.removeEventListener('pointerdown', onPointerDown);
  }, []);

  function navigate(hit: SearchHit) {
    const target = hitTarget(hit);
    setOpen(false);
    setQuery('');
    if (target) router.push(target);
  }

  if (!canSearch) return null;

  const hasResults = hits.length > 0;

  return (
    <div ref={rootRef} style={{ position: 'relative', width: 320, maxWidth: '40vw' }}>
      <Input
        type="search"
        role="combobox"
        aria-expanded={open}
        aria-label={t('shell.search.title')}
        placeholder={t('shell.search.placeholder')}
        value={query}
        onChange={(event) => setQuery(event.target.value)}
        onKeyDown={(event) => {
          if (event.key === 'Escape') setOpen(false);
        }}
        style={{ fontSize: 13 }}
      />
      {searching ? (
        <span style={{ position: 'absolute', right: 10, top: 9, display: 'inline-flex' }}>
          <Spinner size="sm" />
        </span>
      ) : null}
      {open && (query.trim().length >= 2) ? (
        <div
          role="listbox"
          aria-label={t('shell.search.title')}
          style={{
            position: 'absolute',
            top: 'calc(100% + 6px)',
            left: 0,
            right: 0,
            zIndex: 900,
            backgroundColor: 'var(--sq-surface)',
            border: '1px solid var(--sq-border)',
            borderRadius: 10,
            boxShadow: '0 16px 48px color-mix(in srgb, var(--sq-fg) 20%, transparent)',
            overflow: 'hidden',
          }}
        >
          {error ? (
            <div style={{ padding: '12px 14px', fontSize: 13, color: 'var(--sq-fg-muted)' }}>
              {t('shell.search.error')}
            </div>
          ) : hasResults ? (
            hits.map((hit) => (
              <button
                key={`${hit.type}-${hit.id}`}
                type="button"
                role="option"
                aria-selected={false}
                onClick={() => navigate(hit)}
                style={{
                  display: 'block',
                  width: '100%',
                  padding: '9px 14px',
                  border: 'none',
                  borderBottom: '1px solid var(--sq-border)',
                  backgroundColor: 'transparent',
                  cursor: 'pointer',
                  textAlign: 'left',
                  fontFamily: 'inherit',
                  fontSize: 13,
                  color: 'var(--sq-fg)',
                }}
              >
                <span style={{ display: 'block', fontWeight: 600 }}>{hit.title}</span>
                <span style={{ display: 'block', fontSize: 12, color: 'var(--sq-fg-muted)' }}>
                  {hit.subtitle ?? hit.type}
                </span>
              </button>
            ))
          ) : (
            <div style={{ padding: '12px 14px', fontSize: 13, color: 'var(--sq-fg-muted)' }}>
              {t('shell.search.empty')}
            </div>
          )}
        </div>
      ) : null}
    </div>
  );
}
