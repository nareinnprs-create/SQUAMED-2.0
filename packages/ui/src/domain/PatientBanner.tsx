'use client';

import type { CSSProperties, KeyboardEvent, ReactNode } from 'react';
import { Fragment } from 'react';
import { format } from 'date-fns';
import { Badge } from '../primitives/Badge';
import { useFocusVisible } from '../primitives/internal';
import { ChevronRightIcon } from './icons';

export interface PatientBannerProps {
  name: string;
  mrn: string;
  dateOfBirth: Date | string;
  gender: string;
  age: number;
  bloodGroup?: string;
  allergySummary?: string;
  isEmergency: boolean;
  onNavigate?: () => void;
  className?: string;
  style?: CSSProperties;
}

function formatDate(value: Date | string, pattern: string): string {
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  return format(date, pattern);
}

const divider = (
  <span
    aria-hidden="true"
    style={{ width: 1, height: 16, backgroundColor: 'var(--sq-border)', flexShrink: 0 }}
  />
);

export function PatientBanner({
  name,
  mrn,
  dateOfBirth,
  gender,
  age,
  bloodGroup,
  allergySummary,
  isEmergency,
  onNavigate,
  className,
  style,
}: PatientBannerProps) {
  const interactive = Boolean(onNavigate);
  const { focusVisible, onPointerDown, onFocus, onBlur } = useFocusVisible();

  const handleKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (!interactive) return;
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      onNavigate?.();
    }
  };

  const facts: ReactNode[] = [
    <span key="age">{age} yrs</span>,
    <span key="gender">{gender}</span>,
    <span key="dob">DOB {formatDate(dateOfBirth, 'dd MMM yyyy')}</span>,
  ];
  if (bloodGroup) {
    facts.push(<span key="blood">Blood {bloodGroup}</span>);
  }
  if (allergySummary) {
    facts.push(
      <span
        key="allergy"
        style={{ color: 'var(--sq-warning)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
      >
        Allergies: {allergySummary}
      </span>,
    );
  }

  return (
    <div
      role={interactive ? 'button' : undefined}
      tabIndex={interactive ? 0 : undefined}
      aria-label={interactive ? `Open patient ${name}` : `Patient ${name}`}
      onClick={onNavigate}
      onKeyDown={handleKeyDown}
      onPointerDown={onPointerDown}
      onFocus={onFocus}
      onBlur={onBlur}
      className={className}
      style={{
        boxSizing: 'border-box',
        display: 'flex',
        alignItems: 'center',
        gap: 14,
        minHeight: 46,
        padding: '10px 16px',
        backgroundColor: isEmergency
          ? 'color-mix(in srgb, var(--sq-critical) 6%, var(--sq-surface))'
          : 'var(--sq-surface)',
        border: '1px solid var(--sq-border)',
        borderLeft: isEmergency ? '4px solid var(--sq-critical)' : '4px solid var(--sq-primary)',
        borderRadius: 8,
        cursor: interactive ? 'pointer' : undefined,
        ...(focusVisible ? { outline: '2px solid var(--sq-focus-ring)', outlineOffset: 2 } : null),
        ...style,
      }}
    >
      {isEmergency ? (
        <Badge variant="critical" appearance="filled" size="sm" style={{ flexShrink: 0 }}>
          Emergency
        </Badge>
      ) : null}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 12,
          minWidth: 0,
          overflow: 'hidden',
          whiteSpace: 'nowrap',
        }}
      >
        <span style={{ fontSize: 15, fontWeight: 700, color: 'var(--sq-fg)', lineHeight: 1.3, flexShrink: 0 }}>
          {name}
        </span>
        <span style={{ fontSize: 12, color: 'var(--sq-fg-muted)', flexShrink: 0 }}>MRN {mrn}</span>
        {facts.map((fact, index) => (
          <Fragment key={index}>
            {divider}
            <span style={{ fontSize: 13, color: 'var(--sq-fg)', lineHeight: 1.4, flexShrink: 0 }}>{fact}</span>
          </Fragment>
        ))}
      </div>
      {interactive ? (
        <ChevronRightIcon
          size={16}
          style={{ color: 'var(--sq-fg-muted)', flexShrink: 0, marginLeft: 'auto' }}
        />
      ) : null}
    </div>
  );
}