'use client';

import type { CSSProperties } from 'react';
import { cn } from '../lib/cn';
import { t } from '../i18n';
import { Button } from '../primitives/Button';
import { TriangleIcon } from './icons';

/**
 * 02-DESIGN-SYSTEM §6: MedOrderLine — drug, dose, route, schedule in fixed
 * mono columns (§3 instrument numerals); high-alert drugs carry the
 * second-verification affordance built in.
 */
export interface MedOrderLineProps {
  drug: string;
  dose: string;
  route: string;
  schedule: string;
  /** High-alert drug → renders the second-verify affordance. */
  highAlert?: boolean;
  /** Set once the second verification is done; supersedes the affordance. */
  verifiedBy?: string;
  onVerify?: () => void;
  verifying?: boolean;
  className?: string;
  style?: CSSProperties;
}

const monoCell: CSSProperties = {
  fontFamily: 'var(--sq-font-mono)',
  fontVariantNumeric: 'tabular-nums',
  fontSize: 13,
  color: 'var(--sq-fg)',
  lineHeight: 1.4,
  overflow: 'hidden',
  textOverflow: 'ellipsis',
  whiteSpace: 'nowrap',
};

export function MedOrderLine({
  drug,
  dose,
  route,
  schedule,
  highAlert = false,
  verifiedBy,
  onVerify,
  verifying = false,
  className,
  style,
}: MedOrderLineProps) {
  const needsVerify = highAlert && !verifiedBy;

  return (
    <div
      className={cn(className)}
      style={{
        boxSizing: 'border-box',
        display: 'grid',
        gridTemplateColumns: 'minmax(140px, 1.6fr) minmax(90px, 0.8fr) 80px minmax(110px, 1fr) max-content',
        alignItems: 'center',
        gap: 14,
        minHeight: 44,
        borderBottom: '1px solid var(--sq-border)',
        ...(needsVerify
          ? { borderInlineStart: '3px solid var(--sq-critical)', paddingInlineStart: 10 }
          : null),
        ...style,
      }}
    >
      <span style={{ ...monoCell, fontWeight: 500 }}>{drug}</span>
      <span style={monoCell}>{dose}</span>
      <span style={{ ...monoCell, color: 'var(--sq-fg-muted)' }}>{route}</span>
      <span style={monoCell}>{schedule}</span>
      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
        {needsVerify ? (
          <>
            <span
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: 4,
                fontSize: 11,
                fontWeight: 600,
                letterSpacing: '0.04em',
                color: 'var(--sq-critical)',
                whiteSpace: 'nowrap',
              }}
            >
              <TriangleIcon size={13} />
              {t('medorderline.highAlert')}
            </span>
            {onVerify ? (
              <Button size="sm" variant="secondary" loading={verifying} onClick={onVerify}>
                {t('medorderline.verify')}
              </Button>
            ) : null}
          </>
        ) : null}
        {verifiedBy ? (
          <span
            style={{
              fontSize: 12,
              fontWeight: 500,
              color: 'var(--sq-success-on-soft)',
              backgroundColor: 'var(--sq-success-soft)',
              padding: '2px 8px',
              borderRadius: 999,
              whiteSpace: 'nowrap',
            }}
          >
            {t('medorderline.verifiedBy', { name: verifiedBy })}
          </span>
        ) : null}
      </span>
    </div>
  );
}
