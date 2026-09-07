'use client';

import type { CSSProperties, KeyboardEvent } from 'react';
import { format } from 'date-fns';
import { Card } from '../primitives/Card';
import { Avatar } from '../primitives/Avatar';
import { StatusIndicator, type StatusTone } from '../primitives/StatusIndicator';
import { useFocusVisible } from '../primitives/internal';
import { AllergyBadge, type AllergySeverity } from './AllergyBadge';

export interface PatientAllergy {
  label: string;
  severity: AllergySeverity;
}

export interface PatientCardProps {
  name: string;
  mrn: string;
  age: number;
  gender: string;
  dateOfBirth: Date | string;
  bloodGroup?: string;
  allergies?: PatientAllergy[];
  photoUrl?: string;
  status: string;
  statusTone?: StatusTone;
  onClick?: () => void;
  className?: string;
  style?: CSSProperties;
}

function formatDate(value: Date | string, pattern: string): string {
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  return format(date, pattern);
}

export function PatientCard({
  name,
  mrn,
  age,
  gender,
  dateOfBirth,
  bloodGroup,
  allergies,
  photoUrl,
  status,
  statusTone = 'neutral',
  onClick,
  className,
  style,
}: PatientCardProps) {
  const interactive = Boolean(onClick);
  const { focusVisible, onPointerDown, onFocus, onBlur } = useFocusVisible();
  const allergyList = allergies ?? [];

  const handleKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (!interactive) return;
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      onClick?.();
    }
  };

  return (
    <Card
      role={interactive ? 'button' : undefined}
      tabIndex={interactive ? 0 : undefined}
      aria-label={interactive ? `View patient ${name}` : undefined}
      onClick={onClick}
      onKeyDown={handleKeyDown}
      onPointerDown={onPointerDown}
      onFocus={onFocus}
      onBlur={onBlur}
      className={className}
      style={{
        display: 'flex',
        gap: 14,
        padding: 16,
        cursor: interactive ? 'pointer' : undefined,
        transition: 'background-color 150ms ease',
        ...(focusVisible ? { outline: '2px solid var(--sq-focus-ring)', outlineOffset: 2 } : null),
        ...style,
      }}
    >
      <Avatar name={name} src={photoUrl} alt={`${name} photo`} size="md" />
      <div
        style={{
          flex: 1,
          minWidth: 0,
          display: 'flex',
          flexDirection: 'column',
          gap: 4,
        }}
      >
        <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 10 }}>
          <span style={{ fontSize: 15, fontWeight: 700, color: 'var(--sq-fg)', lineHeight: 1.3 }}>{name}</span>
          <StatusIndicator tone={statusTone} label={status} />
        </div>
        <div style={{ fontSize: 12, color: 'var(--sq-fg-muted)', lineHeight: 1.4 }}>
          MRN {mrn}
          {bloodGroup ? ` · Blood ${bloodGroup}` : ''}
        </div>
        <div style={{ fontSize: 12, color: 'var(--sq-fg-muted)', lineHeight: 1.4 }}>
          {age} yrs · {gender} · DOB {formatDate(dateOfBirth, 'dd MMM yyyy')}
        </div>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4, marginTop: 2 }}>
          {allergyList.length > 0 ? (
            allergyList.map((allergy) => (
              <AllergyBadge key={allergy.label} label={allergy.label} severity={allergy.severity} />
            ))
          ) : (
            <span style={{ fontSize: 12, color: 'var(--sq-fg-muted)' }}>No known allergies</span>
          )}
        </div>
      </div>
    </Card>
  );
}