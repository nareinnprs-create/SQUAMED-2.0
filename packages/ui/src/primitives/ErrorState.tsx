'use client';

import type { CSSProperties, ReactNode } from 'react';
import { cn } from '../lib/cn';
import { t } from '../i18n';
import { Button } from './Button';
import { TriangleIcon } from '../domain/icons';

/**
 * 02-DESIGN-SYSTEM §6: ErrorState — states what happened and what to do,
 * never a dead end. Copy is the consumer's; the retry action label is the
 * component's fixed copy and flows through i18n.
 */
export interface ErrorStateProps {
  title: string;
  description?: string;
  /** Offering a retry renders the retry affordance. */
  onRetry?: () => void;
  retrying?: boolean;
  action?: ReactNode;
  className?: string;
  style?: CSSProperties;
}

export function ErrorState({
  title,
  description,
  onRetry,
  retrying = false,
  action,
  className,
  style,
}: ErrorStateProps) {
  return (
    <div
      role="alert"
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
        border: '1px solid var(--sq-border)',
        borderInlineStart: '4px solid var(--sq-critical)',
        borderRadius: 10,
        backgroundColor: 'var(--sq-surface)',
        ...style,
      }}
    >
      <span aria-hidden="true" style={{ display: 'inline-flex', color: 'var(--sq-critical)' }}>
        <TriangleIcon size={20} />
      </span>
      <span style={{ fontSize: 14, fontWeight: 600, color: 'var(--sq-fg)', lineHeight: 1.45 }}>{title}</span>
      {description ? (
        <span style={{ fontSize: 13, color: 'var(--sq-fg-muted)', lineHeight: 1.5, maxWidth: '48ch' }}>
          {description}
        </span>
      ) : null}
      {action ? <div style={{ marginTop: 6 }}>{action}</div> : null}
      {onRetry ? (
        <div style={{ marginTop: 6 }}>
          <Button size="sm" variant="secondary" loading={retrying} onClick={onRetry}>
            {t('errorstate.retry')}
          </Button>
        </div>
      ) : null}
    </div>
  );
}
