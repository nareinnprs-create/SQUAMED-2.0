'use client';

import type { CSSProperties, KeyboardEvent, ReactNode } from 'react';
import { cn } from '../lib/cn';
import { t } from '../i18n';
import { useFocusVisible } from '../primitives/internal';
import { StatusDot } from '../primitives/StatusDot';
import type { StatusTone } from '../primitives/StatusIndicator';
import { TriangleIcon } from './icons';

/**
 * 02-DESIGN-SYSTEM §1 signature: the Context Spine — a persistent patient bar
 * on every patient-scoped screen: name + MRN/ABHA in tabular mono, allergy flag,
 * live status dots, encounter stage. Clinical two-identifier check AND the
 * design's memorable element.
 */
export interface ContextSpineStatus {
  tone: StatusTone;
  label: string;
  glyph?: ReactNode;
}

export interface ContextSpineProps {
  name: string;
  mrn: string;
  abha?: string;
  /** Age/gender/blood facts as short strings, e.g. ['42 yrs', 'Male', 'O+']. */
  facts?: string[];
  /** Allergy summary; renders the §2 never-color-alone flag (glyph + word + color). */
  allergy?: string;
  /** Live status dots, color+glyph+text each. */
  statuses?: ContextSpineStatus[];
  /** Encounter stage, e.g. 'Admitted · Day 2'. */
  encounterStage?: string;
  onNavigate?: () => void;
  className?: string;
  style?: CSSProperties;
}

const divider = (
  <span
    aria-hidden="true"
    style={{ width: 1, height: 16, backgroundColor: 'var(--sq-border)', flexShrink: 0 }}
  />
);

export function ContextSpine({
  name,
  mrn,
  abha,
  facts = [],
  allergy,
  statuses = [],
  encounterStage,
  onNavigate,
  className,
  style,
}: ContextSpineProps) {
  const interactive = Boolean(onNavigate);
  const { focusVisible, onPointerDown, onFocus, onBlur } = useFocusVisible();

  const handleKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (!interactive) return;
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      onNavigate?.();
    }
  };

  const monoStyle: CSSProperties = {
    fontFamily: 'var(--sq-font-mono)',
    fontVariantNumeric: 'tabular-nums',
    fontSize: 13,
    color: 'var(--sq-fg)',
    lineHeight: 1.4,
    flexShrink: 0,
  };

  return (
    <div
      role={interactive ? 'button' : undefined}
      tabIndex={interactive ? 0 : undefined}
      aria-label={interactive ? `Open patient ${name}` : `Patient ${name}`}
      onClick={onNavigate}
      onKeyDown={handleKeyDown}
      onPointerDown={onPointerDown}
      onFocus={onFocus}
      onBlur={onBlur}
      className={cn(className)}
      style={{
        boxSizing: 'border-box',
        display: 'flex',
        alignItems: 'center',
        gap: 12,
        minHeight: 48,
        padding: '10px 16px',
        backgroundColor: 'var(--sq-surface)',
        border: '1px solid var(--sq-border)',
        borderInlineStart: '4px solid var(--sq-primary)',
        borderRadius: 10,
        cursor: interactive ? 'pointer' : undefined,
        ...(focusVisible ? { outline: '2px solid var(--sq-focus-ring)', outlineOffset: 2 } : null),
        ...style,
      }}
    >
      <span style={{ fontSize: 15, fontWeight: 600, color: 'var(--sq-fg)', lineHeight: 1.3, flexShrink: 0 }}>
        {name}
      </span>
      {divider}
      <span style={{ ...monoStyle, color: 'var(--sq-fg-muted)', fontSize: 12 }}>MRN</span>
      <span style={{ ...monoStyle, overflow: 'hidden', textOverflow: 'ellipsis' }}>{mrn}</span>
      {abha ? (
        <>
          {divider}
          <span style={{ ...monoStyle, color: 'var(--sq-fg-muted)', fontSize: 12 }}>ABHA</span>
          <span style={{ ...monoStyle, overflow: 'hidden', textOverflow: 'ellipsis' }}>{abha}</span>
        </>
      ) : null}
      {facts.length > 0
        ? facts.map((fact, index) => (
            <span
              key={fact}
              style={{ fontSize: 13, color: 'var(--sq-fg-muted)', lineHeight: 1.4, flexShrink: 0 }}
            >
              {index === 0 ? null : '· '}
              {fact}
            </span>
          ))
        : null}
      {allergy ? (
        <span
          aria-label={t('contextspine.allergy', { summary: allergy })}
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: 4,
            fontSize: 12,
            fontWeight: 500,
            color: 'var(--sq-critical)',
            lineHeight: 1.4,
            flexShrink: 0,
            minWidth: 0,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
        >
          <TriangleIcon size={14} />
          {t('contextspine.allergy', { summary: allergy })}
        </span>
      ) : null}
      <span
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          gap: 12,
          marginInlineStart: 'auto',
          flexShrink: 0,
          minWidth: 0,
        }}
      >
        {statuses.map((status) => (
          <StatusDot key={status.label} tone={status.tone} label={status.label} glyph={status.glyph} />
        ))}
        {encounterStage ? (
          <span
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              padding: '3px 10px',
              borderRadius: 999,
              backgroundColor: 'var(--sq-primary-soft)',
              fontSize: 12,
              fontWeight: 500,
              color: 'var(--sq-primary-on-soft)',
              lineHeight: 1.35,
              whiteSpace: 'nowrap',
            }}
          >
            {encounterStage}
          </span>
        ) : null}
      </span>
    </div>
  );
}
