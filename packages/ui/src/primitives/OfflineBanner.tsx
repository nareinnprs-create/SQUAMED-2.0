'use client';

import type { CSSProperties } from 'react';
import { cn } from '../lib/cn';
import { t } from '../i18n';
import { Button } from './Button';
import { TriangleIcon } from '../domain/icons';

/**
 * 02-DESIGN-SYSTEM §6: OfflineBanner — the honest connectivity state.
 * Never lie about being online; offers the retry action.
 */
export interface OfflineBannerProps {
  /** Offering a retry renders the retry affordance. */
  onRetry?: () => void;
  retrying?: boolean;
  className?: string;
  style?: CSSProperties;
}

export function OfflineBanner({ onRetry, retrying = false, className, style }: OfflineBannerProps) {
  return (
    <div
      role="status"
      aria-live="polite"
      className={cn(className)}
      style={{
        boxSizing: 'border-box',
        display: 'flex',
        alignItems: 'center',
        gap: 10,
        flexWrap: 'wrap',
        padding: '8px 14px',
        borderRadius: 6,
        backgroundColor: 'var(--sq-warning-soft)',
        ...style,
      }}
    >
      <span aria-hidden="true" style={{ display: 'inline-flex', color: 'var(--sq-warning)', flexShrink: 0 }}>
        <TriangleIcon size={16} />
      </span>
      <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--sq-warning-on-soft)', lineHeight: 1.4 }}>
        {t('offlinebanner.title')}
      </span>
      <span style={{ fontSize: 12, color: 'var(--sq-warning-on-soft)', lineHeight: 1.4, minWidth: 0, flex: 1 }}>
        {t('offlinebanner.description')}
      </span>
      {onRetry ? (
        <Button size="sm" variant="outline" loading={retrying} onClick={onRetry} style={{ flexShrink: 0 }}>
          {t('offlinebanner.retry')}
        </Button>
      ) : null}
    </div>
  );
}
