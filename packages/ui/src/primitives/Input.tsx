'use client';

import { forwardRef, useId, useState } from 'react';
import type { InputHTMLAttributes, ReactNode } from 'react';
import { fieldBase, fieldSizes, type FieldSize } from './internal';

export interface InputProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'size'> {
  label?: string;
  error?: boolean | string;
  hint?: string;
  errorMessage?: string;
  size?: FieldSize;
  required?: boolean;
  leftSlot?: ReactNode;
  rightSlot?: ReactNode;
}

export const Input = forwardRef<HTMLInputElement, InputProps>(function Input(
  {
    label,
    error,
    hint,
    errorMessage,
    size = 'md',
    required,
    disabled,
    leftSlot,
    rightSlot,
    id,
    style,
    className,
    onFocus,
    onBlur,
    ...rest
  },
  ref,
) {
  const generatedId = useId();
  const inputId = id ?? generatedId;
  const hintId = `${generatedId}-hint`;
  const errorId = `${generatedId}-error`;
  const [focused, setFocused] = useState(false);
  const errorText = errorMessage ?? (typeof error === 'string' ? error : undefined);
  const showError = error === true || Boolean(errorText);
  const describedBy = showError ? errorId : hint ? hintId : undefined;
  const sizeStyle = fieldSizes[size];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 6, width: '100%' }}>
      {label ? (
        <label htmlFor={inputId} style={{ fontSize: 13, fontWeight: 500, color: 'var(--sq-fg)' }}>
          {label}
          {required ? <span aria-hidden="true" style={{ color: 'var(--sq-critical)' }}> *</span> : null}
        </label>
      ) : null}
      <div style={{ position: 'relative', width: '100%' }}>
        {leftSlot ? (
          <span
            aria-hidden="true"
            style={{
              position: 'absolute',
              insetInlineStart: 12,
              top: '50%',
              transform: 'translateY(-50%)',
              display: 'inline-flex',
              color: 'var(--sq-fg-muted)',
              pointerEvents: 'none',
            }}
          >
            {leftSlot}
          </span>
        ) : null}
        <input
          {...rest}
          id={inputId}
          ref={ref}
          className={className}
          required={required}
          disabled={disabled}
          aria-invalid={showError ? true : (rest['aria-invalid'] as boolean | undefined)}
          aria-describedby={
            [rest['aria-describedby'] as string | undefined, describedBy].filter(Boolean).join(' ') || undefined
          }
          onFocus={(event) => {
            setFocused(true);
            onFocus?.(event);
          }}
          onBlur={(event) => {
            setFocused(false);
            onBlur?.(event);
          }}
          style={{
            ...fieldBase,
            ...sizeStyle,
            paddingInlineStart: leftSlot ? 36 : (sizeStyle.paddingLeft as number),
            paddingInlineEnd: rightSlot ? 36 : (sizeStyle.paddingRight as number),
            transition: 'border-color 150ms ease, box-shadow 150ms ease',
            borderColor: showError ? 'var(--sq-critical)' : focused ? 'var(--sq-primary)' : 'var(--sq-border)',
            boxShadow: focused ? '0 0 0 3px var(--sq-focus-ring)' : undefined,
            outline: 'none',
            ...(disabled ? { opacity: 0.6, cursor: 'not-allowed' } : null),
            ...style,
          }}
        />
        {rightSlot ? (
          <span
            aria-hidden="true"
            style={{
              position: 'absolute',
              insetInlineEnd: 12,
              top: '50%',
              transform: 'translateY(-50%)',
              display: 'inline-flex',
              color: 'var(--sq-fg-muted)',
              pointerEvents: 'none',
            }}
          >
            {rightSlot}
          </span>
        ) : null}
      </div>
      {showError ? (
        <p id={errorId} style={{ margin: 0, fontSize: 12, color: 'var(--sq-critical)' }}>
          {errorText}
        </p>
      ) : hint ? (
        <p id={hintId} style={{ margin: 0, fontSize: 12, color: 'var(--sq-fg-muted)' }}>
          {hint}
        </p>
      ) : null}
    </div>
  );
});