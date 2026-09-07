import type { CSSProperties } from 'react';
import { Badge, type BadgeVariant } from '../primitives/Badge';
import { cn } from '../lib/cn';

export type TokenStatus = 'waiting' | 'called' | 'completed' | 'skipped';

export interface TokenDisplayProps {
  tokenNumber: string | number;
  counter?: string;
  department?: string;
  patientName?: string;
  status: TokenStatus;
  queueName?: string;
  className?: string;
  style?: CSSProperties;
}

let tokenStylesInjected = false;

function injectTokenStyles(): void {
  if (tokenStylesInjected || typeof document === 'undefined') return;
  tokenStylesInjected = true;
  const styleEl = document.createElement('style');
  styleEl.textContent =
    '@keyframes sq-token-call{0%{transform:scale(1);opacity:1}50%{transform:scale(1.06);opacity:0.85}100%{transform:scale(1);opacity:1}}';
  document.head.appendChild(styleEl);
}

injectTokenStyles();

const statusMeta: Record<TokenStatus, { label: string; color: string; badge: BadgeVariant; pulse: boolean }> = {
  waiting: { label: 'Waiting', color: 'var(--sq-information)', badge: 'information', pulse: false },
  called: { label: 'Called', color: 'var(--sq-success)', badge: 'success', pulse: true },
  completed: { label: 'Completed', color: 'var(--sq-fg-muted)', badge: 'neutral', pulse: false },
  skipped: { label: 'Skipped', color: 'var(--sq-critical)', badge: 'critical', pulse: false },
};

export function TokenDisplay({
  tokenNumber,
  counter,
  department,
  patientName,
  status,
  queueName,
  className,
  style,
}: TokenDisplayProps) {
  const meta = statusMeta[status];

  return (
    <div
      role="status"
      aria-live="polite"
      aria-label={`Token ${tokenNumber}, ${meta.label}`}
      className={cn(className)}
      style={{
        boxSizing: 'border-box',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 10,
        padding: '40px 32px',
        borderRadius: 16,
        backgroundColor: 'var(--sq-surface)',
        border: meta.pulse
          ? '1px solid color-mix(in srgb, var(--sq-success) 45%, transparent)'
          : '1px solid var(--sq-border)',
        boxShadow: 'var(--sq-shadow-md)',
        textAlign: 'center',
        ...style,
      }}
    >
      {queueName ? (
        <span
          style={{
            fontSize: 12,
            fontWeight: 600,
            letterSpacing: '0.08em',
            textTransform: 'uppercase',
            color: 'var(--sq-fg-muted)',
          }}
        >
          {queueName}
        </span>
      ) : null}
      <span
        style={{
          fontSize: 72,
          lineHeight: 1,
          fontWeight: 700,
          color: meta.color,
          animation: meta.pulse ? 'sq-token-call 1.4s ease-in-out infinite' : undefined,
        }}
      >
        {tokenNumber}
      </span>
      <Badge variant={meta.badge} appearance="soft">
        {meta.label}
      </Badge>
      {patientName ? (
        <span style={{ fontSize: 15, fontWeight: 600, color: 'var(--sq-fg)', lineHeight: 1.3 }}>{patientName}</span>
      ) : null}
      {counter || department ? (
        <span style={{ fontSize: 13, color: 'var(--sq-fg-muted)' }}>
          {counter ? `Counter ${counter}` : ''}
          {counter && department ? ' · ' : ''}
          {department ?? ''}
        </span>
      ) : null}
    </div>
  );
}