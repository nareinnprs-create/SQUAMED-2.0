import type { CSSProperties } from 'react';
import { cn } from '../lib/cn';

export type StatusTone = 'success' | 'warning' | 'critical' | 'information' | 'neutral';

export interface StatusIndicatorProps {
  tone?: StatusTone;
  pulse?: boolean;
  label?: string;
  className?: string;
  style?: CSSProperties;
}

let statusStylesInjected = false;

function injectStatusStyles(): void {
  if (statusStylesInjected || typeof document === 'undefined') return;
  statusStylesInjected = true;
  const styleEl = document.createElement('style');
  styleEl.textContent =
    '@keyframes sq-status-pulse{0%{box-shadow:0 0 0 0 color-mix(in srgb, currentColor 45%, transparent)}70%{box-shadow:0 0 0 8px transparent}100%{box-shadow:0 0 0 0 transparent}}' +
    // 02-DESIGN-SYSTEM §5: reduced motion disables all but opacity fades.
    '@media (prefers-reduced-motion:reduce){.sq-status-dot{animation:none !important}}';
  document.head.appendChild(styleEl);
}

injectStatusStyles();

export const toneColors: Record<StatusTone, string> = {
  success: 'var(--sq-success)',
  warning: 'var(--sq-warning)',
  critical: 'var(--sq-critical)',
  information: 'var(--sq-information)',
  neutral: 'var(--sq-fg-muted)',
};

export function StatusIndicator({
  tone = 'neutral',
  pulse = false,
  label,
  className,
  style,
}: StatusIndicatorProps) {
  const color = toneColors[tone];

  return (
    <span
      className={cn(className)}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 8,
        boxSizing: 'border-box',
        ...style,
      }}
    >
      <span
        aria-hidden="true"
        className="sq-status-dot"
        style={{
          width: 8,
          height: 8,
          borderRadius: '50%',
          flexShrink: 0,
          backgroundColor: color,
          color,
          // §5 the one orchestrated moment: a single 300ms pulse, not a loop.
          animation: pulse ? 'sq-status-pulse 300ms ease-out 1' : undefined,
        }}
      />
      {label ? (
        <span style={{ fontSize: 13, fontWeight: 500, color: 'var(--sq-fg-muted)', lineHeight: 1.4 }}>
          {label}
        </span>
      ) : null}
    </span>
  );
}