'use client';

import { cloneElement, isValidElement, useId } from 'react';
import type { CSSProperties, ReactElement, ReactNode } from 'react';
import { cn } from '../lib/cn';
import { t } from '../i18n';

/**
 * Chunk-00 item 2 core component: form field wrapper (02-DESIGN-SYSTEM §6).
 * Real <label>, hint and inline error joined to the control through
 * aria-describedby, so any control (Input, Select, Textarea, Checkbox, custom)
 * gets the same accessible form anatomy without reimplementing the wiring.
 */

export interface FieldProps {
  label: string;
  /** The single form control to wire up (label htmlFor, aria-describedby, aria-invalid). */
  children: ReactElement;
  hint?: ReactNode;
  /** Inline error message; also flips the control to aria-invalid. */
  error?: string;
  required?: boolean;
  className?: string;
  style?: CSSProperties;
}

export function Field({ label, children, hint, error, required, className, style }: FieldProps) {
  const generatedId = useId();
  const hintId = `${generatedId}-hint`;
  const errorId = `${generatedId}-error`;
  const describedBy = [error ? errorId : undefined, hint ? hintId : undefined]
    .filter(Boolean)
    .join(' ');

  const control = isValidElement(children)
    ? cloneElement(children as ReactElement<Record<string, unknown>>, {
        id: (children.props as { id?: string }).id ?? generatedId,
        ...(describedBy
          ? {
              'aria-describedby': [
                (children.props as { 'aria-describedby'?: string })['aria-describedby'],
                describedBy,
              ]
                .filter(Boolean)
                .join(' '),
            }
          : {}),
        ...(error ? { 'aria-invalid': true } : {}),
        ...(required ? { 'aria-required': true } : {}),
      })
    : children;

  return (
    <div className={cn(className)} style={{ display: 'flex', flexDirection: 'column', gap: 6, width: '100%', ...style }}>
      <label
        htmlFor={(children as ReactElement<{ id?: string }>).props.id ?? generatedId}
        style={{ fontSize: 13, fontWeight: 500, color: 'var(--sq-fg)' }}
      >
        {label}
        {required ? (
          <span aria-hidden="true" style={{ color: 'var(--sq-critical)' }}>
            {' '}
            *
          </span>
        ) : null}
        {required ? (
          <span className="sq-sr-only" style={srOnly}>
            {' '}
            ({t('field.required')})
          </span>
        ) : null}
      </label>
      {control}
      {error ? (
        <span id={errorId} role="alert" style={{ fontSize: 12, color: 'var(--sq-critical)' }}>
          {error}
        </span>
      ) : hint ? (
        <span id={hintId} style={{ fontSize: 12, color: 'var(--sq-fg-muted)' }}>
          {hint}
        </span>
      ) : null}
    </div>
  );
}

const srOnly: CSSProperties = {
  position: 'absolute',
  width: 1,
  height: 1,
  padding: 0,
  margin: -1,
  overflow: 'hidden',
  clip: 'rect(0, 0, 0, 0)',
  whiteSpace: 'nowrap',
  border: 0,
};
