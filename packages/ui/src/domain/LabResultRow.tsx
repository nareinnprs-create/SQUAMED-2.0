'use client';

import type { CSSProperties } from 'react';
import { cn } from '../lib/cn';
import { t } from '../i18n';
import { RangeBar } from './RangeBar';
import { TriangleIcon } from './icons';

/**
 * 02-DESIGN-SYSTEM §6: LabResultRow — the value rendered on a reference-range
 * track, the instrument vernacular. Abnormal ends are flagged by glyph +
 * status word, not color alone (§2). Clinical density: 44px row.
 */
export interface LabResultRowProps {
  /** Test name, e.g. 'Sodium'. */
  test: string;
  value: number;
  unit?: string;
  /** Full scale of the reference track. */
  scale: { min: number; max: number };
  /** Normal band within the scale. */
  band?: { low: number; high: number };
  status?: 'normal' | 'high' | 'low';
  className?: string;
  style?: CSSProperties;
}

const statusLabels: Record<'normal' | 'high' | 'low', { key: string; tone: string }> = {
  normal: { key: 'labresult.normal', tone: 'var(--sq-fg-muted)' },
  high: { key: 'labresult.high', tone: 'var(--sq-critical)' },
  low: { key: 'labresult.low', tone: 'var(--sq-critical)' },
};

export function LabResultRow({
  test,
  value,
  unit,
  scale,
  band,
  status = 'normal',
  className,
  style,
}: LabResultRowProps) {
  const meta = statusLabels[status];
  const abnormal = status !== 'normal';

  return (
    <div
      className={cn(className)}
      style={{
        boxSizing: 'border-box',
        display: 'grid',
        gridTemplateColumns: 'minmax(120px, 1.2fr) minmax(90px, max-content) minmax(120px, 1.6fr) max-content',
        alignItems: 'center',
        gap: 14,
        minHeight: 44,
        borderBottom: '1px solid var(--sq-border)',
        ...style,
      }}
    >
      <span
        style={{ fontSize: 14, color: 'var(--sq-fg)', lineHeight: 1.45, overflow: 'hidden', textOverflow: 'ellipsis' }}
      >
        {test}
      </span>
      <span
        style={{
          display: 'inline-flex',
          alignItems: 'baseline',
          gap: 3,
          fontFamily: 'var(--sq-font-mono)',
          fontVariantNumeric: 'tabular-nums',
          fontSize: 13,
          fontWeight: 500,
          color: abnormal ? 'var(--sq-critical)' : 'var(--sq-fg)',
          lineHeight: 1.4,
        }}
      >
        {value}
        {unit ? <span style={{ fontSize: 11, color: 'var(--sq-fg-muted)' }}>{unit}</span> : null}
      </span>
      <span aria-hidden="true">
        <RangeBar min={scale.min} max={scale.max} low={band?.low} high={band?.high} value={value} />
      </span>
      <span
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          gap: 4,
          fontSize: 12,
          fontWeight: 500,
          color: meta.tone,
          lineHeight: 1.35,
        }}
      >
        {abnormal ? <TriangleIcon size={13} /> : null}
        {t(meta.key)}
      </span>
    </div>
  );
}
