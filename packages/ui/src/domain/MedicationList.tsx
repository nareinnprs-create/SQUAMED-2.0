import type { CSSProperties } from 'react';
import { format } from 'date-fns';
import { Badge, type BadgeVariant } from '../primitives/Badge';
import { Card, CardHeader, CardTitle } from '../primitives/Card';
import { cn } from '../lib/cn';

export type MedicationStatus = 'active' | 'discontinued' | 'pending' | 'onHold';

export interface Medication {
  name: string;
  dose: string;
  frequency: string;
  route: string;
  status: MedicationStatus;
  orderedBy?: string;
  orderedAt?: Date | string;
  isPRN?: boolean;
  instructions?: string;
}

export interface MedicationListProps {
  medications: Medication[];
  className?: string;
  style?: CSSProperties;
}

const statusVariants: Record<MedicationStatus, BadgeVariant> = {
  active: 'success',
  discontinued: 'critical',
  pending: 'warning',
  onHold: 'information',
};

const statusLabels: Record<MedicationStatus, string> = {
  active: 'Active',
  discontinued: 'Discontinued',
  pending: 'Pending',
  onHold: 'On hold',
};

function formatDate(value: Date | string): string {
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  return format(date, 'dd MMM yyyy');
}

export function MedicationList({ medications, className, style }: MedicationListProps) {
  if (medications.length === 0) {
    return (
      <div className={cn(className)} style={{ fontSize: 13, color: 'var(--sq-fg-muted)', ...style }}>
        No medications recorded
      </div>
    );
  }

  return (
    <Card className={cn(className)} style={{ ...style }}>
      <CardHeader>
        <CardTitle>Medications</CardTitle>
      </CardHeader>
      <div style={{ display: 'flex', flexDirection: 'column' }}>
        {medications.map((medication, index) => (
          <div
            key={`${medication.name}-${index}`}
            style={{
              display: 'flex',
              flexDirection: 'column',
              gap: 4,
              padding: '14px 20px 16px',
              borderBottom: index < medications.length - 1 ? '1px solid var(--sq-border)' : undefined,
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12 }}>
              <span style={{ fontSize: 14, fontWeight: 600, color: 'var(--sq-fg)' }}>
                {medication.name}
                {medication.isPRN ? (
                  <Badge variant="neutral" appearance="soft" size="sm" style={{ marginLeft: 8 }}>
                    PRN
                  </Badge>
                ) : null}
              </span>
              <Badge variant={statusVariants[medication.status]} size="sm">
                {statusLabels[medication.status]}
              </Badge>
            </div>
            <div style={{ fontSize: 13, color: 'var(--sq-fg)', lineHeight: 1.4 }}>
              {medication.dose} · {medication.frequency} · {medication.route}
            </div>
            {medication.orderedBy || medication.orderedAt ? (
              <div style={{ fontSize: 12, color: 'var(--sq-fg-muted)', lineHeight: 1.4 }}>
                {medication.orderedBy ? `Ordered by ${medication.orderedBy}` : ''}
                {medication.orderedBy && medication.orderedAt ? ' · ' : ''}
                {medication.orderedAt ? formatDate(medication.orderedAt) : ''}
              </div>
            ) : null}
            {medication.instructions ? (
              <div style={{ fontSize: 12, color: 'var(--sq-fg-muted)', lineHeight: 1.4 }}>
                {medication.instructions}
              </div>
            ) : null}
          </div>
        ))}
      </div>
    </Card>
  );
}