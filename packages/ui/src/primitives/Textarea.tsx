'use client';

import { forwardRef, useId, useState } from 'react';
import type { TextareaHTMLAttributes } from 'react';
import { fieldBase, fieldSizes, type FieldSize } from './internal';

export interface TextareaProps extends Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, 'size'> {
  label?: string;
  error?: boolean | string;
  hint?: string;
  errorMessage?: string;
  size?: FieldSize;
  required?: boolean;
}

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaProps>(function Textarea(
  {
    label,
    error,
    hint,
    errorMessage,
    size = 'md',
    required,
    disabled,
    id,
    rows = 4,
    style,
    className,
    onFocus,
    onBlur,
    ...rest
  },
  ref,
) {
  const generatedId = useId();
  const textareaId = id ?? generatedId;
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
        <label htmlFor={textareaId} style={{ fontSize: 13, fontWeight: 500, color: 'var(--sq-fg)' }}>
          {label}
          {required ? <span aria-hidden="true" style={{ color: 'var(--sq-critical)' }}> *</span> : null}
        </label>
      ) : null}
      <textarea
        {...rest}
        id={textareaId}
        ref={ref}
        className={className}
        rows={rows}
        required={required}
        disabled={disabled}
        aria-invalid={showError || undefined}
        aria-describedby={describedBy}
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
          height: 'auto',
          minHeight: (sizeStyle.height as number) * 2,
          paddingTop: 10,
          paddingBottom: 10,
          resize: 'vertical',
          transition: 'border-color 150ms ease, box-shadow 150ms ease',
          borderColor: showError ? 'var(--sq-critical)' : focused ? 'var(--sq-primary)' : 'var(--sq-border)',
          boxShadow: focused ? '0 0 0 3px var(--sq-focus-ring)' : undefined,
          outline: 'none',
          ...(disabled ? { opacity: 0.6, cursor: 'not-allowed' } : null),
          ...style,
        }}
      />
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