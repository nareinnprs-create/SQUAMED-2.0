'use client';

import { useState } from 'react';
import type { ButtonHTMLAttributes, CSSProperties, ReactNode } from 'react';
import { ensureKeyframes, useFocusVisible, usePrefersReducedMotion } from './internal';

export type ButtonVariant = 'primary' | 'secondary' | 'outline' | 'ghost' | 'danger' | 'success';
export type ButtonSize = 'sm' | 'md' | 'lg';

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  loading?: boolean;
  fullWidth?: boolean;
  children: ReactNode;
}

const variantStyles: Record<ButtonVariant, CSSProperties> = {
  primary: { backgroundColor: 'var(--sq-primary)', color: 'var(--sq-fg)', borderColor: 'var(--sq-primary)' },
  secondary: { backgroundColor: 'var(--sq-surface)', color: 'var(--sq-fg)', borderColor: 'var(--sq-border)' },
  outline: { backgroundColor: 'transparent', color: 'var(--sq-fg)', borderColor: 'var(--sq-border)' },
  ghost: { backgroundColor: 'transparent', color: 'var(--sq-fg)', borderColor: 'transparent' },
  danger: { backgroundColor: 'var(--sq-critical)', color: 'var(--sq-fg)', borderColor: 'var(--sq-critical)' },
  success: { backgroundColor: 'var(--sq-success)', color: 'var(--sq-fg)', borderColor: 'var(--sq-success)' },
};

const sizeStyles: Record<ButtonSize, CSSProperties> = {
  sm: { height: 32, padding: '0 12px', fontSize: 13, borderRadius: 6 },
  md: { height: 38, padding: '0 16px', fontSize: 14, borderRadius: 8 },
  lg: { height: 46, padding: '0 24px', fontSize: 16, borderRadius: 10 },
};

ensureKeyframes();

export function Button({
  variant = 'primary',
  size = 'md',
  loading = false,
  fullWidth = false,
  disabled = false,
  children,
  type = 'button',
  className,
  style,
  onClick,
  onPointerDown,
  onFocus,
  onBlur,
  ...rest
}: ButtonProps) {
  const [hovered, setHovered] = useState(false);
  const reduced = usePrefersReducedMotion();
  const {
    focusVisible,
    onPointerDown: trackPointerDown,
    onFocus: trackFocus,
    onBlur: trackBlur,
  } = useFocusVisible();

  const isDisabled = disabled || loading;

  return (
    <button
      {...rest}
      type={type}
      disabled={isDisabled}
      aria-busy={loading || undefined}
      className={className}
      onPointerDown={(event) => {
        trackPointerDown();
        onPointerDown?.(event);
      }}
      onFocus={(event) => {
        trackFocus();
        onFocus?.(event);
      }}
      onBlur={(event) => {
        trackBlur();
        onBlur?.(event);
      }}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      onClick={(event) => {
        if (isDisabled) return;
        onClick?.(event);
      }}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 8,
        border: '1px solid',
        fontFamily: 'inherit',
        fontWeight: 600,
        lineHeight: 1,
        cursor: isDisabled ? 'not-allowed' : 'pointer',
        userSelect: 'none',
        whiteSpace: 'nowrap',
        boxSizing: 'border-box',
        transition: reduced
          ? 'none'
          : 'background-color 150ms ease, border-color 150ms ease, box-shadow 150ms ease, filter 150ms ease',
        ...variantStyles[variant],
        ...sizeStyles[size],
        ...(isDisabled ? { opacity: 0.6 } : null),
        ...(fullWidth ? { width: '100%' } : null),
        ...(hovered && !isDisabled ? { filter: 'brightness(0.95)' } : null),
        ...(focusVisible ? { outline: '2px solid var(--sq-focus-ring)', outlineOffset: 2 } : null),
        ...style,
      }}
    >
      {loading ? (
        <span
          aria-hidden="true"
          style={{
            display: 'inline-block',
            width: '1em',
            height: '1em',
            flexShrink: 0,
            borderRadius: '50%',
            border: '2px solid currentColor',
            borderTopColor: 'transparent',
            animation: reduced ? 'none' : 'sq-spin 0.7s linear infinite',
          }}
        />
      ) : null}
      {children}
    </button>
  );
}