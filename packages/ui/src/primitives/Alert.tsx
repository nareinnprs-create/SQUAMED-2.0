'use client';

import type { CSSProperties, ReactNode } from 'react';
import { cn } from '../lib/cn';
import { useFocusVisible } from './internal';

export type AlertVariant = 'info' | 'success' | 'warning' | 'critical';

export interface AlertProps {
  variant?: AlertVariant;
  title?: ReactNode;
  children?: ReactNode;
  onClose?: () => void;
  className?: string;
  style?: CSSProperties;
}

const accentColors: Record<AlertVariant, string> = {
  info: 'var(--sq-information)',
  success: 'var(--sq-success)',
  warning: 'var(--sq-warning)',
  critical: 'var(--sq-critical)',
};

const softColors: Record<AlertVariant, string> = {
  info: 'var(--sq-information-soft)',
  success: 'var(--sq-success-soft)',
  warning: 'var(--sq-warning-soft)',
  critical: 'var(--sq-critical-soft)',
};

const icons: Record<AlertVariant, ReactNode> = {
  info: (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
      <circle cx="8" cy="8" r="6.5" stroke="currentColor" strokeWidth="1.5" />
      <path d="M8 7.5 L8 11" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
      <circle cx="8" cy="5" r="0.9" fill="currentColor" />
    </svg>
  ),
  success: (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
      <circle cx="8" cy="8" r="6.5" stroke="currentColor" strokeWidth="1.5" />
      <path d="M5 8.2 L7.2 10.3 L11 6" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  ),
  warning: (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
      <path d="M8 2.2 L15 13.5 L1 13.5 Z" stroke="currentColor" strokeWidth="1.5" strokeLinejoin="round" />
      <path d="M8 6.5 L8 9.5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
      <circle cx="8" cy="11.5" r="0.9" fill="currentColor" />
    </svg>
  ),
  critical: (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
      <circle cx="8" cy="8" r="6.5" stroke="currentColor" strokeWidth="1.5" />
      <path d="M5.5 5.5 L10.5 10.5 M10.5 5.5 L5.5 10.5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
    </svg>
  ),
};

export function Alert({
  variant = 'info',
  title,
  children,
  onClose,
  className,
  style,
}: AlertProps) {
  const focus = useFocusVisible();
  const accent = accentColors[variant];
  const isAlertRole = variant === 'critical' || variant === 'warning';

  return (
    <div
      role={isAlertRole ? 'alert' : 'status'}
      className={cn(className)}
      style={{
        display: 'flex',
        alignItems: 'flex-start',
        gap: 10,
        padding: '12px 14px',
        boxSizing: 'border-box',
        borderRadius: 8,
        backgroundColor: softColors[variant],
        border: '1px solid',
        borderColor: `color-mix(in srgb, ${accent} 35%, transparent)`,
        color: 'var(--sq-fg)',
        ...style,
      }}
    >
      <span style={{ display: 'inline-flex', flexShrink: 0, marginTop: 1, color: accent }}>
        {icons[variant]}
      </span>
      <div style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', gap: 2 }}>
        {title ? (
          <div style={{ fontSize: 14, fontWeight: 600, lineHeight: 1.4 }}>{title}</div>
        ) : null}
        {children ? (
          <div style={{ fontSize: 13, lineHeight: 1.5, color: 'var(--sq-fg-muted)' }}>{children}</div>
        ) : null}
      </div>
      {onClose ? (
        <button
          type="button"
          aria-label="Dismiss"
          onClick={onClose}
          onPointerDown={focus.onPointerDown}
          onFocus={focus.onFocus}
          onBlur={focus.onBlur}
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            flexShrink: 0,
            width: 26,
            height: 26,
            padding: 0,
            border: 'none',
            borderRadius: 6,
            backgroundColor: 'transparent',
            color: 'var(--sq-fg-muted)',
            cursor: 'pointer',
            ...(focus.focusVisible ? { outline: '2px solid var(--sq-focus-ring)', outlineOffset: 2 } : null),
          }}
        >
          <svg width="14" height="14" viewBox="0 0 16 16" fill="none" aria-hidden="true">
            <path d="M4 4 L12 12 M12 4 L4 12" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
          </svg>
        </button>
      ) : null}
    </div>
  );
}