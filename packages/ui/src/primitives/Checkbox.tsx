'use client';

import { forwardRef, useCallback, useEffect, useId, useRef } from 'react';
import type { InputHTMLAttributes, ReactNode } from 'react';
import { useFocusVisible } from './internal';

export interface CheckboxProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type' | 'onChange'> {
  checked: boolean;
  onCheckedChange?: (checked: boolean) => void;
  indeterminate?: boolean;
  label?: ReactNode;
}

export const Checkbox = forwardRef<HTMLInputElement, CheckboxProps>(function Checkbox(
  { checked, onCheckedChange, indeterminate = false, label, disabled = false, className, style, ...rest },
  ref,
) {
  const inputId = useId();
  const boxRef = useRef<HTMLInputElement | null>(null);
  const { focusVisible, onPointerDown, onFocus, onBlur } = useFocusVisible();

  const setRefs = useCallback(
    (node: HTMLInputElement | null) => {
      boxRef.current = node;
      if (typeof ref === 'function') ref(node);
      else if (ref) ref.current = node;
    },
    [ref],
  );

  useEffect(() => {
    if (boxRef.current) boxRef.current.indeterminate = indeterminate;
  }, [indeterminate]);

  return (
    <label
      className={className}
      onPointerDown={onPointerDown}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 10,
        position: 'relative',
        cursor: disabled ? 'not-allowed' : 'pointer',
        color: 'var(--sq-fg)',
        fontSize: 14,
        ...style,
      }}
    >
      <input
        {...rest}
        id={inputId}
        ref={setRefs}
        type="checkbox"
        checked={checked}
        disabled={disabled}
        onFocus={onFocus}
        onBlur={onBlur}
        onChange={(event) => onCheckedChange?.(event.target.checked)}
        style={{
          position: 'absolute',
          width: 1,
          height: 1,
          margin: -1,
          padding: 0,
          overflow: 'hidden',
          clip: 'rect(0 0 0 0)',
          clipPath: 'inset(50%)',
          whiteSpace: 'nowrap',
          border: 0,
        }}
      />
      <span
        aria-hidden="true"
        style={{
          width: 18,
          height: 18,
          flexShrink: 0,
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'center',
          borderRadius: 5,
          border: '1.5px solid',
          borderColor: checked || indeterminate ? 'var(--sq-primary)' : 'var(--sq-border)',
          backgroundColor: checked || indeterminate ? 'var(--sq-primary)' : 'var(--sq-surface)',
          color: 'var(--sq-fg)',
          transition: 'background-color 120ms ease, border-color 120ms ease',
          ...(disabled ? { opacity: 0.6 } : null),
          ...(focusVisible ? { outline: '2px solid var(--sq-focus-ring)', outlineOffset: 2 } : null),
        }}
      >
        {checked && !indeterminate ? (
          <svg width="12" height="12" viewBox="0 0 12 12" fill="none" aria-hidden="true">
            <path
              d="M2.5 6.5 L5 9 L9.5 3.5"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </svg>
        ) : indeterminate ? (
          <span style={{ width: 8, height: 2, borderRadius: 1, backgroundColor: 'currentColor' }} />
        ) : null}
      </span>
      {label ? <span>{label}</span> : null}
    </label>
  );
});