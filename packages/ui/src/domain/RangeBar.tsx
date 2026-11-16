'use client';

import type { CSSProperties } from 'react';
import { cn } from '../lib/cn';

/**
 * 02-DESIGN-SYSTEM §6: RangeBar — the reference-range track of the instrument
 * vernacular. The value is positioned on the scale; the normal band is shown;
 * an out-of-band marker turns critical. Flagging glyphs belong to the row
 * (LabResultRow), not the bar — the bar never uses color alone as meaning.
 */
export interface RangeBarProps {
  /** Full scale of the track. */
  min: number;
  max: number;
  /** Normal band within the scale; omitted renders a plain scale. */
  low?: number;
  high?: number;
  /** The reading, positioned on the track. */
  value: number;
  /** Accessible description, e.g. 'Sodium 142 mmol/L'. */
  ariaLabel?: string;
  className?: string;
  style?: CSSProperties;
}

const TRACK_HEIGHT = 6;
const MARKER_SIZE = 12;

export function RangeBar({ min, max, low, high, value, ariaLabel, className, style }: RangeBarProps) {
  const span = max - min || 1;
  const pct = (raw: number) => Math.min(100, Math.max(0, ((raw - min) / span) * 100));

  const bandLeft = low === undefined ? undefined : pct(low);
  const bandWidth = low === undefined || high === undefined ? undefined : pct(high) - pct(low);
  const markerLeft = pct(value);
  const outOfBand = (low !== undefined && value < low) || (high !== undefined && value > high);

  return (
    <span
      role="img"
      aria-label={ariaLabel}
      className={cn(className)}
      style={{
        position: 'relative',
        display: 'inline-block',
        width: '100%',
        height: TRACK_HEIGHT,
        borderRadius: 999,
        backgroundColor: 'var(--sq-border)',
        boxSizing: 'border-box',
        ...style,
      }}
    >
      {bandLeft !== undefined && bandWidth !== undefined && bandWidth > 0 ? (
        <span
          aria-hidden="true"
          style={{
            position: 'absolute',
            insetInlineStart: `${bandLeft}%`,
            width: `${bandWidth}%`,
            top: 0,
            bottom: 0,
            borderRadius: 999,
            backgroundColor: 'color-mix(in srgb, var(--sq-success) 30%, transparent)',
          }}
        />
      ) : null}
      <span
        aria-hidden="true"
        style={{
          position: 'absolute',
          insetInlineStart: `calc(${markerLeft}% - ${MARKER_SIZE / 2}px)`,
          top: (TRACK_HEIGHT - MARKER_SIZE) / 2,
          width: MARKER_SIZE,
          height: MARKER_SIZE,
          borderRadius: '50%',
          backgroundColor: outOfBand ? 'var(--sq-critical)' : 'var(--sq-fg)',
          border: '2px solid var(--sq-surface)',
          boxSizing: 'border-box',
        }}
      />
    </span>
  );
}
