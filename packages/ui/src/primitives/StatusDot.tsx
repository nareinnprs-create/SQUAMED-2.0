'use client';

import type { CSSProperties, ReactNode } from 'react';
import { cn } from '../lib/cn';
import { toneColors, type StatusTone } from './StatusIndicator';

/**
 * 02-DESIGN-SYSTEM §6: the color+glyph+text primitive behind every status.
 * §2 never-color-alone: the label is required text, the optional glyph gives
 * colorblind-sighted users a non-color cue; the dot alone never carries meaning.
 */
export interface StatusDotProps {
  tone?: StatusTone;
  /** Status word — required so color is never the only carrier of meaning. */
  label: string;
  /** Optional status glyph (triangle, trend…), colored to match the tone. */
  glyph?: ReactNode;
  className?: string;
  style?: CSSProperties;
}

export function StatusDot({ tone = 'neutral', label, glyph, className, style }: StatusDotProps) {
  const color = toneColors[tone];

  return (
    <span
      className={cn(className)}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 6,
        boxSizing: 'border-box',
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
      <span style={{ fontSize: 13, fontWeight: 500, color: 'var(--sq-fg)', lineHeight: 1.4 }}>{label}</span>
    </span>
  );
}
