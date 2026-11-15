'use client';

import { useId } from 'react';
import type { ButtonHTMLAttributes, ReactNode } from 'react';
import { useFocusVisible, usePrefersReducedMotion } from './internal';

export interface ToggleProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'onChange'> {
  checked: boolean;
  onCheckedChange?: (checked: boolean) => void;
  label?: ReactNode;
  size?: 'sm' | 'md';
}

export function Toggle({
  checked,
  onCheckedChange,
  label,
  size = 'md',
  disabled = false,
  className,
  style,
  ...rest
}: ToggleProps) {
  const toggleId = useId();
  const reduced = usePrefersReducedMotion();
  const { focusVisible, onPointerDown, onFocus, onBlur } = useFocusVisible();
  const isSmall = size === 'sm';
  const trackWidth = isSmall ? 36 : 44;
  const trackHeight = isSmall ? 20 : 24;
  const thumbSize = trackHeight - 4;
  const thumbOffset = checked ? trackWidth - thumbSize - 4 : 0;

  return (
    <label
      htmlFor={toggleId}
      className={className}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 10,
        cursor: disabled ? 'not-allowed' : 'pointer',
        color: 'var(--sq-fg)',
        fontSize: 14,
        ...style,
      }}
    >
      <button
        {...rest}
        id={toggleId}
        type="button"
        role="switch"
        aria-checked={checked}
        disabled={disabled}
        onClick={() => {
          if (!disabled) onCheckedChange?.(!checked);
        }}
        onPointerDown={onPointerDown}
        onFocus={onFocus}
        onBlur={onBlur}
        style={{
          position: 'relative',
          width: trackWidth,
          height: trackHeight,
          flexShrink: 0,
          padding: 0,
          borderRadius: 999,
          border: 'none',
          backgroundColor: checked ? 'var(--sq-primary)' : 'var(--sq-border)',
          cursor: disabled ? 'not-allowed' : 'pointer',
          transition: reduced ? 'none' : 'background-color 150ms ease',
          ...(disabled ? { opacity: 0.6 } : null),
          ...(focusVisible ? { outline: '2px solid var(--sq-focus-ring)', outlineOffset: 2 } : null),
        }}
      >
        <span
          aria-hidden="true"
          style={{
            position: 'absolute',
            top: 2,
            insetInlineStart: 2,
            width: thumbSize,
            height: thumbSize,
            borderRadius: '50%',
            backgroundColor: 'var(--sq-surface)',
            transform: `translateX(${thumbOffset}px)`,
            transition: reduced ? 'none' : 'transform 150ms ease',
          }}
        />
      </button>
      {label ? <span>{label}</span> : null}
    </label>
  );
}