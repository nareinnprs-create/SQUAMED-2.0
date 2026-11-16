'use client';

import type { CSSProperties, ReactNode } from 'react';
import { cn } from '../lib/cn';

/**
 * 02-DESIGN-SYSTEM §6: EmptyState — invites the next action instead of a dead end.
 * Copy is the consumer's (their domain vocabulary); the component owns the layout.
 */
export interface EmptyStateProps {
  title: string;
  description?: string;
  /** The invited next action (a Button, usually). */
  action?: ReactNode;
  /** Illustration/slot above the title, muted by default. */
  glyph?: ReactNode;
  className?: string;
  style?: CSSProperties;
}

export function EmptyState({ title, description, action, glyph, className, style }: EmptyStateProps) {
  return (
    <div
      className={cn(className)}
      style={{
        boxSizing: 'border-box',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 8,
        padding: '32px 24px',
        textAlign: 'center',
        border: '1px dashed var(--sq-border)',
        borderRadius: 10,
        backgroundColor: 'var(--sq-surface)',
        ...style,
      }}
    >
      {glyph ? (
        <span aria-hidden="true" style={{ display: 'inline-flex', color: 'var(--sq-fg-muted)' }}>
          {glyph}
        </span>
      ) : null}
      <span style={{ fontSize: 14, fontWeight: 600, color: 'var(--sq-fg)', lineHeight: 1.45 }}>{title}</span>
      {description ? (
        <span style={{ fontSize: 13, color: 'var(--sq-fg-muted)', lineHeight: 1.5, maxWidth: '48ch' }}>
          {description}
        </span>
      ) : null}
      {action ? <div style={{ marginTop: 6 }}>{action}</div> : null}
    </div>
  );
}
