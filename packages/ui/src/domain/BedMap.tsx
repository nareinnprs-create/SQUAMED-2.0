import type { CSSProperties } from 'react';
import { cn } from '../lib/cn';

export type BedStatus = 'available' | 'occupied' | 'reserved' | 'maintenance' | 'cleaning';

export interface Bed {
  id: string;
  label: string;
  status: BedStatus;
  patientName?: string;
  department?: string;
}

export interface BedMapProps {
  beds: Bed[];
  columns?: number;
  className?: string;
  style?: CSSProperties;
}

const statusStyles: Record<BedStatus, { background: string; border: string; color: string; label: string }> = {
  available: {
    background: 'color-mix(in srgb, var(--sq-success) 9%, transparent)',
    border: 'color-mix(in srgb, var(--sq-success) 35%, transparent)',
    color: 'var(--sq-success)',
    label: 'Available',
  },
  occupied: {
    background: 'color-mix(in srgb, var(--sq-critical) 10%, transparent)',
    border: 'color-mix(in srgb, var(--sq-critical) 40%, transparent)',
    color: 'var(--sq-critical)',
    label: 'Occupied',
  },
  reserved: {
    background: 'color-mix(in srgb, var(--sq-warning) 10%, transparent)',
    border: 'color-mix(in srgb, var(--sq-warning) 35%, transparent)',
    color: 'var(--sq-warning)',
    label: 'Reserved',
  },
  maintenance: {
    background: 'color-mix(in srgb, var(--sq-fg) 6%, transparent)',
    border: 'var(--sq-border)',
    color: 'var(--sq-fg-muted)',
    label: 'Maintenance',
  },
  cleaning: {
    background: 'color-mix(in srgb, var(--sq-information) 10%, transparent)',
    border: 'color-mix(in srgb, var(--sq-information) 35%, transparent)',
    color: 'var(--sq-information)',
    label: 'Cleaning',
  },
};

export function BedMap({ beds, columns = 4, className, style }: BedMapProps) {
  if (beds.length === 0) {
    return (
      <div className={cn(className)} style={{ fontSize: 13, color: 'var(--sq-fg-muted)', ...style }}>
        No beds configured
      </div>
    );
  }

  return (
    <div
      className={cn(className)}
      style={{
        display: 'grid',
        gridTemplateColumns: `repeat(${columns}, minmax(0, 1fr))`,
        gap: 10,
        ...style,
      }}
    >
      {beds.map((bed) => {
        const meta = statusStyles[bed.status];
        return (
          <div
            key={bed.id}
            aria-label={`Bed ${bed.id}, ${meta.label}${bed.patientName ? `, ${bed.patientName}` : ''}`}
            style={{
              boxSizing: 'border-box',
              display: 'flex',
              flexDirection: 'column',
              gap: 3,
              minHeight: 72,
              padding: 10,
              borderRadius: 10,
              backgroundColor: meta.background,
              border: `1px solid ${meta.border}`,
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 6 }}>
              <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--sq-fg)' }}>{bed.label}</span>
              <span
                style={{
                  fontSize: 10,
                  fontWeight: 700,
                  color: meta.color,
                  letterSpacing: '0.05em',
                  textTransform: 'uppercase',
                }}
              >
                {meta.label}
              </span>
            </div>
            {bed.patientName ? (
              <span
                style={{
                  fontSize: 13,
                  fontWeight: 600,
                  color: meta.color,
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                }}
              >
                {bed.patientName}
              </span>
            ) : null}
            {bed.department ? (
              <span style={{ fontSize: 11, color: 'var(--sq-fg-muted)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                {bed.department}
              </span>
            ) : null}
          </div>
        );
      })}
    </div>
  );
}