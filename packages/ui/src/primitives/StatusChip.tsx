'use client';

import type { CSSProperties, ReactNode } from 'react';
import { cn } from '../lib/cn';
import { toneColors, type StatusTone } from './StatusIndicator';

/**
 * 02-DESIGN-SYSTEM §6: StatusChip — the pill form of the color+glyph+text
 * primitive. Tint backgrounds carry chips only (§2); text on tints uses the
 * on-soft token, never ink-on-tint shortcuts.
 */
export interface StatusChipProps {
  tone?: StatusTone;
  /** Status word — required so color is never the only carrier of meaning. */
  label: string;
  /** Optional status glyph (triangle, trend…), colored to match the tone. */
  glyph?: ReactNode;
  className?: string;
  style?: CSSProperties;
}

const tintBackgrounds: Record<StatusTone, string> = {
  success: 'var(--sq-success-soft)',
  warning: 'var(--sq-warning-soft)',
  critical: 'var(--sq-critical-soft)',
  information: 'var(--sq-information-soft)',
  neutral: 'color-mix(in srgb, var(--sq-fg-muted) 12%, transparent)',
};

const textColors: Record<StatusTone, string> = {
  success: 'var(--sq-success-on-soft)',
  warning: 'var(--sq-warning-on-soft)',
  critical: 'var(--sq-critical-on-soft)',
  information: 'var(--sq-information-on-soft)',
  neutral: 'var(--sq-fg)',
};

export function StatusChip({ tone = 'neutral', label, glyph, className, style }: StatusChipProps) {
  const color = toneColors[tone];
  const textColor = textColors[tone];

  return (
    <span
      className={cn(className)}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 6,
        boxSizing: 'border-box',
        maxWidth: '100%',
        padding: '3px 10px',
        borderRadius: 999,
        backgroundColor: tintBackgrounds[tone],
        ...style,
      }}
    >
      <span
        aria-hidden="true"
        style={{ width: 8, height: 8, borderRadius: '50%', flexShrink: 0, backgroundColor: color }}
      />
      {glyph ? (
        <span aria-hidden="true" style={{ display: 'inline-flex', color, flexShrink: 0 }}>
          {glyph}
        </span>
      ) : null}
      <span
        style={{
          fontSize: 12,
          fontWeight: 500,
          color: textColor,
          lineHeight: 1.35,
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
        }}
      >
        {label}
      </span>
    </span>
  );
}
