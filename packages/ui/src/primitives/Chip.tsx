'use client';

import type { CSSProperties, MouseEvent, ReactNode } from 'react';
import { cn } from '../lib/cn';
import { useFocusVisible } from './internal';

export type ChipVariant = 'neutral' | 'primary' | 'success' | 'warning' | 'critical';

export interface ChipProps {
  variant?: ChipVariant;
  selected?: boolean;
  onRemove?: () => void;
  onClick?: () => void;
  disabled?: boolean;
  children: ReactNode;
  className?: string;
  style?: CSSProperties;
}

const softStyles: Record<ChipVariant, CSSProperties> = {
  neutral: {
    backgroundColor: 'var(--sq-secondary)',
    color: 'var(--sq-fg)',
    borderColor: 'var(--sq-border)',
  },
  primary: {
    backgroundColor: 'var(--sq-primary-soft)',
    color: 'var(--sq-primary-on-soft)',
    borderColor: 'color-mix(in srgb, var(--sq-primary) 35%, transparent)',
  },
  success: {
    backgroundColor: 'var(--sq-success-soft)',
    color: 'var(--sq-success-on-soft)',
    borderColor: 'color-mix(in srgb, var(--sq-success) 35%, transparent)',
  },
  warning: {
    backgroundColor: 'var(--sq-warning-soft)',
    color: 'var(--sq-warning-on-soft)',
    borderColor: 'color-mix(in srgb, var(--sq-warning) 35%, transparent)',
  },
  critical: {
    backgroundColor: 'var(--sq-critical-soft)',
    color: 'var(--sq-critical-on-soft)',
    borderColor: 'color-mix(in srgb, var(--sq-critical) 35%, transparent)',
  },
};

const selectedStyles: Record<ChipVariant, CSSProperties> = {
  neutral: { backgroundColor: 'var(--sq-fg)', color: 'var(--sq-bg)', borderColor: 'var(--sq-fg)' },
  primary: { backgroundColor: 'var(--sq-primary)', color: 'var(--sq-primary-fg)', borderColor: 'var(--sq-primary)' },
  success: { backgroundColor: 'var(--sq-success)', color: 'var(--sq-success-fg)', borderColor: 'var(--sq-success)' },
  warning: { backgroundColor: 'var(--sq-warning)', color: 'var(--sq-warning-fg)', borderColor: 'var(--sq-warning)' },
  critical: { backgroundColor: 'var(--sq-critical)', color: 'var(--sq-critical-fg)', borderColor: 'var(--sq-critical)' },
};

const baseStyle: CSSProperties = {
  display: 'inline-flex',
  alignItems: 'center',
  gap: 6,
  boxSizing: 'border-box',
  height: 28,
  padding: '0 10px',
  border: '1px solid',
  borderRadius: 999,
  fontFamily: 'inherit',
  fontSize: 13,
  fontWeight: 500,
  lineHeight: 1,
  whiteSpace: 'nowrap',
  userSelect: 'none',
};

export function Chip({
  variant = 'neutral',
  selected = false,
  onRemove,
  onClick,
  disabled = false,
  children,
  className,
  style,
}: ChipProps) {
  const mainFocus = useFocusVisible();
  const removeFocus = useFocusVisible();

  const chipStyle: CSSProperties = {
    ...baseStyle,
    ...softStyles[variant],
    ...(selected ? selectedStyles[variant] : null),
    cursor: onRemove ? 'default' : onClick && !disabled ? 'pointer' : 'default',
    ...(disabled ? { opacity: 0.6 } : null),
    ...style,
  };

  if (onRemove) {
    return (
      <span className={cn(className)} style={chipStyle}>
        <span>{children}</span>
        <button
          type="button"
          aria-label="Remove"
          disabled={disabled}
          onClick={(event: MouseEvent<HTMLButtonElement>) => {
            event.stopPropagation();
            onRemove();
          }}
          onPointerDown={removeFocus.onPointerDown}
          onFocus={removeFocus.onFocus}
          onBlur={removeFocus.onBlur}
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            width: 14,
            height: 14,
            padding: 0,
            border: 'none',
            borderRadius: '50%',
            backgroundColor: 'transparent',
            color: 'inherit',
            cursor: disabled ? 'not-allowed' : 'pointer',
            ...(removeFocus.focusVisible ? { outline: '2px solid var(--sq-focus-ring)', outlineOffset: 1 } : null),
          }}
        >
          <svg width="10" height="10" viewBox="0 0 16 16" fill="none" aria-hidden="true">
            <path d="M4 4 L12 12 M12 4 L4 12" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
          </svg>
        </button>
      </span>
    );
  }

  return (
    <button
      type="button"
      disabled={disabled}
      onClick={onClick}
      onPointerDown={mainFocus.onPointerDown}
      onFocus={mainFocus.onFocus}
      onBlur={mainFocus.onBlur}
      className={cn(className)}
      style={{
        ...chipStyle,
        ...(mainFocus.focusVisible ? { outline: '2px solid var(--sq-focus-ring)', outlineOffset: 2 } : null),
      }}
    >
      {children}
    </button>
  );
}