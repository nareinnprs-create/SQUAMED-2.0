'use client';

import { forwardRef, useId, useState } from 'react';
import type { SelectHTMLAttributes } from 'react';
import { fieldBase, fieldSizes, type FieldSize } from './internal';

export interface SelectProps extends Omit<SelectHTMLAttributes<HTMLSelectElement>, 'size'> {
  label?: string;
  error?: boolean | string;
  hint?: string;
  errorMessage?: string;
  size?: FieldSize;
}

export const Select = forwardRef<HTMLSelectElement, SelectProps>(function Select(
  {
    label,
    error,
    hint,
    errorMessage,
    size = 'md',
    required,
    disabled,
    id,
    style,
    className,
    onFocus,
    onBlur,
    children,
    ...rest
  },
  ref,
) {
  const generatedId = useId();
  const selectId = id ?? generatedId;
  const hintId = `${generatedId}-hint`;
  const errorId = `${generatedId}-error`;
  const [focused, setFocused] = useState(false);
  const errorText = errorMessage ?? (typeof error === 'string' ? error : undefined);
  const showError = error === true || Boolean(errorText);
  const describedBy = showError ? errorId : hint ? hintId : undefined;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 6, width: '100%' }}>
      {label ? (
        <label htmlFor={selectId} style={{ fontSize: 13, fontWeight: 500, color: 'var(--sq-fg)' }}>
          {label}
          {required ? <span aria-hidden="true" style={{ color: 'var(--sq-critical)' }}> *</span> : null}
        </label>
      ) : null}
      <select
        {...rest}
        id={selectId}
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
          ...fieldSizes[size],
          paddingInlineEnd: 38,
          cursor: 'pointer',
          transition: 'border-color 150ms ease, box-shadow 150ms ease',
          borderColor: showError ? 'var(--sq-critical)' : focused ? 'var(--sq-primary)' : 'var(--sq-border)',
          boxShadow: focused ? '0 0 0 3px var(--sq-focus-ring)' : undefined,
          outline: 'none',
          ...(disabled ? { opacity: 0.6, cursor: 'not-allowed' } : null),
          ...style,
        }}
      >
        {children}
      </select>
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