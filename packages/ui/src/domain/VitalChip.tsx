'use client';

import type { CSSProperties } from 'react';
import { cn } from '../lib/cn';
import { t } from '../i18n';
import { TriangleIcon, TrendDownIcon, TrendUpIcon } from './icons';

/**
 * 02-DESIGN-SYSTEM §6: VitalChip — value + unit in tabular mono (§3 instrument
 * numerals), trend glyph, out-of-range state flagged by glyph + color, never
 * color alone (§2). The trend/out-of-range meaning is announced via aria-label.
 */
export interface VitalChipProps {
  /** Vital name, e.g. 'BP', 'SpO2', 'HR'. */
  label: string;
  /** The reading, e.g. '128/82'. Rendered in tabular mono. */
  value: string;
  unit?: string;
  trend?: 'up' | 'down' | 'flat';
  outOfRange?: boolean;
  className?: string;
  style?: CSSProperties;
}

export function VitalChip({ label, value, unit, trend, outOfRange = false, className, style }: VitalChipProps) {
  const trendText =
    trend === 'up'
      ? t('vitalchip.trendUp')
      : trend === 'down'
        ? t('vitalchip.trendDown')
        : trend === 'flat'
          ? t('vitalchip.trendFlat')
          : null;

  const TrendGlyph = trend === 'up' ? TrendUpIcon : TrendDownIcon;

  return (
    <span
      className={cn(className)}
      aria-label={`${label} ${value}${unit ? ` ${unit}` : ''}${trendText ? `, ${trendText}` : ''}${
        outOfRange ? `, ${t('vitalchip.outOfRange')}` : ''
      }`}
      style={{
        display: 'inline-flex',
        alignItems: 'baseline',
        gap: 6,
        boxSizing: 'border-box',
        padding: '4px 10px',
        borderRadius: 6,
        backgroundColor: 'var(--sq-secondary)',
        ...style,
      }}
    >
      <span style={{ fontSize: 12, fontWeight: 500, color: 'var(--sq-fg-muted)', lineHeight: 1.35 }}>{label}</span>
      <span
        style={{
          display: 'inline-flex',
          alignItems: 'baseline',
          gap: 3,
          fontFamily: 'var(--sq-font-mono)',
          fontVariantNumeric: 'tabular-nums',
          fontSize: 13,
          fontWeight: 500,
          color: outOfRange ? 'var(--sq-critical)' : 'var(--sq-fg)',
          lineHeight: 1.4,
        }}
      >
        {value}
        {unit ? <span style={{ fontSize: 11, color: 'var(--sq-fg-muted)' }}>{unit}</span> : null}
      </span>
      {trend === 'flat' ? (
        <svg width={14} height={14} viewBox="0 0 16 16" fill="none" aria-hidden="true">
          <path d="M3 8h10" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
        </svg>
      ) : trend ? (
        <span aria-hidden="true" style={{ display: 'inline-flex', color: 'var(--sq-fg-muted)' }}>
          <TrendGlyph size={14} />
        </span>
      ) : null}
      {outOfRange ? (
        <span aria-hidden="true" style={{ display: 'inline-flex', color: 'var(--sq-critical)' }}>
          <TriangleIcon size={14} />
        </span>
      ) : null}
    </span>
  );
}
