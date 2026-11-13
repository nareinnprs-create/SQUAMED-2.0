import type { CSSProperties } from 'react';
import { cn } from '../lib/cn';

export type ProgressVariant = 'default' | 'success' | 'warning' | 'critical';

export interface ProgressProps {
  value?: number;
  indeterminate?: boolean;
  variant?: ProgressVariant;
  label?: string;
  className?: string;
  style?: CSSProperties;
}

let progressStylesInjected = false;

function injectProgressStyles(): void {
  if (progressStylesInjected || typeof document === 'undefined') return;
  progressStylesInjected = true;
  const styleEl = document.createElement('style');
  styleEl.textContent =
    '@keyframes sq-progress-indeterminate{0%{transform:translateX(-100%)}100%{transform:translateX(400%)}}';
  document.head.appendChild(styleEl);
}

injectProgressStyles();

const variantColors: Record<ProgressVariant, string> = {
  default: 'var(--sq-primary)',
  success: 'var(--sq-success)',
  warning: 'var(--sq-warning)',
  critical: 'var(--sq-critical)',
};

export function Progress({
  value = 0,
  indeterminate = false,
  variant = 'default',
  label,
  className,
  style,
}: ProgressProps) {
  const clamped = Math.max(0, Math.min(100, value));
  const fillColor = variantColors[variant];

  return (
    <div
      role="progressbar"
      aria-label={label}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={indeterminate ? undefined : clamped}
      aria-valuetext={indeterminate ? undefined : `${clamped}%`}
      className={cn(className)}
      style={{
        width: '100%',
        height: 8,
        borderRadius: 999,
        backgroundColor: 'var(--sq-secondary)',
        overflow: 'hidden',
        boxSizing: 'border-box',
        ...style,
      }}
    >
      <div
        style={{
          height: '100%',
          borderRadius: 999,
          backgroundColor: fillColor,
          width: indeterminate ? '33%' : `${clamped}%`,
          animation: indeterminate ? 'sq-progress-indeterminate 1.4s ease-in-out infinite' : undefined,
          transition: indeterminate ? undefined : 'width 200ms ease',
        }}
      />
    </div>
  );
}