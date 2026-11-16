'use client';

import { useState, type CSSProperties } from 'react';
import { cn } from '../lib/cn';
import { t } from '../i18n';
import { Button } from '../primitives/Button';
import { Checkbox } from '../primitives/Checkbox';

/**
 * 02-DESIGN-SYSTEM §6 signature: DualConfirm — the two-identifier confirmation
 * block hard-rendered in every patient-scoped mutation flow. The operator must
 * match name + MRN against the patient in front of them before the action
 * confirms; the confirm control stays disabled until the check is affirmed.
 */
export interface DualConfirmProps {
  name: string;
  mrn: string;
  abha?: string;
  /** What is being confirmed, e.g. 'Blood transfusion for this patient'. */
  action?: string;
  onConfirm: () => void;
  confirming?: boolean;
  className?: string;
  style?: CSSProperties;
}

export function DualConfirm({
  name,
  mrn,
  abha,
  action,
  onConfirm,
  confirming = false,
  className,
  style,
}: DualConfirmProps) {
  const [verified, setVerified] = useState(false);

  const monoStyle: CSSProperties = {
    fontFamily: 'var(--sq-font-mono)',
    fontVariantNumeric: 'tabular-nums',
    fontSize: 14,
    color: 'var(--sq-fg)',
    lineHeight: 1.45,
  };

  return (
    <section
      aria-label={t('dualconfirm.title')}
      className={cn(className)}
      style={{
        boxSizing: 'border-box',
        display: 'flex',
        flexDirection: 'column',
        gap: 12,
        padding: 16,
        borderRadius: 10,
        border: '1px solid var(--sq-border)',
        borderInlineStart: '4px solid var(--sq-primary)',
        backgroundColor: 'var(--sq-surface)',
        ...style,
      }}
    >
      <span style={{ fontSize: 15, fontWeight: 600, color: 'var(--sq-fg)', lineHeight: 1.35 }}>
        {t('dualconfirm.title')}
      </span>
      <span style={{ fontSize: 13, color: 'var(--sq-fg-muted)', lineHeight: 1.45 }}>
        {t('dualconfirm.instruction')}
      </span>
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          gap: 6,
          padding: '12px 14px',
          borderRadius: 6,
          backgroundColor: 'var(--sq-secondary)',
        }}
      >
        <span style={{ fontSize: 16, fontWeight: 600, color: 'var(--sq-fg)', lineHeight: 1.35 }}>{name}</span>
        <span style={monoStyle}>MRN {mrn}</span>
        {abha ? <span style={monoStyle}>ABHA {abha}</span> : null}
        {action ? (
          <span style={{ fontSize: 12, color: 'var(--sq-fg-muted)', lineHeight: 1.4 }}>{action}</span>
        ) : null}
      </div>
      <Checkbox checked={verified} onCheckedChange={setVerified} label={t('dualconfirm.verified')} />
      <div>
        <Button variant="primary" disabled={!verified} loading={confirming} onClick={onConfirm}>
          {t('dualconfirm.confirm')}
        </Button>
      </div>
    </section>
  );
}
