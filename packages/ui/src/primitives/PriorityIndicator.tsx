import type { CSSProperties } from 'react';
import { cn } from '../lib/cn';

export type PriorityLevel = 'routine' | 'urgent' | 'emergency' | 'low' | 'high' | 'critical';

export interface PriorityIndicatorProps {
  level: PriorityLevel;
  label?: string;
  className?: string;
  style?: CSSProperties;
}

const priorityTones: Record<PriorityLevel, { color: string; background: string }> = {
  routine: { color: 'var(--sq-fg-muted)', background: 'color-mix(in srgb, var(--sq-fg) 9%, transparent)' },
  low: { color: 'var(--sq-information-on-soft)', background: 'var(--sq-information-soft)' },
  urgent: { color: 'var(--sq-warning-on-soft)', background: 'var(--sq-warning-soft)' },
  high: { color: 'var(--sq-warning-on-soft)', background: 'var(--sq-warning-soft)' },
  emergency: { color: 'var(--sq-critical-on-soft)', background: 'var(--sq-critical-soft)' },
  critical: { color: 'var(--sq-critical-on-soft)', background: 'var(--sq-critical-soft)' },
};

export function PriorityIndicator({ level, label, className, style }: PriorityIndicatorProps) {
  const tone = priorityTones[level];

  return (
    <span
      className={cn(className)}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 7,
        boxSizing: 'border-box',
        height: 24,
        padding: '0 10px',
        borderRadius: 999,
        backgroundColor: tone.background,
        color: tone.color,
        fontSize: 12,
        fontWeight: 600,
        lineHeight: 1,
        whiteSpace: 'nowrap',
        ...style,
      }}
    >
      <span
        aria-hidden="true"
        style={{ width: 6, height: 6, borderRadius: '50%', backgroundColor: tone.color, flexShrink: 0 }}
      />
      {label ?? level}
    </span>
  );
}