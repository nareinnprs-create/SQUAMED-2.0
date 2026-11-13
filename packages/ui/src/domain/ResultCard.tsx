import type { CSSProperties } from 'react';
import { format } from 'date-fns';
import { Badge, type BadgeVariant } from '../primitives/Badge';
import { Card, CardContent } from '../primitives/Card';
import { cn } from '../lib/cn';

export type ResultInterpretation = 'normal' | 'low' | 'high' | 'critical' | 'pending';
export type ResultStatus = 'pending' | 'in-progress' | 'completed' | 'cancelled';

export interface LabResult {
  testName: string;
  value: string | number;
  unit?: string;
  referenceRange?: string;
  interpretation?: ResultInterpretation;
  status: ResultStatus;
  collectedAt?: Date | string;
  reportedAt?: Date | string;
  notes?: string;
}

export interface ResultCardProps {
  result: LabResult;
  className?: string;
  style?: CSSProperties;
}

const interpretationVariants: Record<ResultInterpretation, BadgeVariant> = {
  normal: 'success',
  low: 'information',
  high: 'warning',
  critical: 'critical',
  pending: 'neutral',
};

const interpretationLabels: Record<ResultInterpretation, string> = {
  normal: 'Normal',
  low: 'Low',
  high: 'High',
  critical: 'Critical',
  pending: 'Pending',
};

const statusVariants: Record<ResultStatus, BadgeVariant> = {
  pending: 'warning',
  'in-progress': 'information',
  completed: 'success',
  cancelled: 'neutral',
};

const statusLabels: Record<ResultStatus, string> = {
  pending: 'Pending',
  'in-progress': 'In progress',
  completed: 'Completed',
  cancelled: 'Cancelled',
};

function formatDate(value: Date | string, pattern: string): string {
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  return format(date, pattern);
}

export function ResultCard({ result, className, style }: ResultCardProps) {
  const isCritical = result.interpretation === 'critical';

  return (
    <Card className={cn(className)} style={{ ...style }}>
      <CardContent style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12 }}>
          <span style={{ fontSize: 15, fontWeight: 600, color: 'var(--sq-fg)', lineHeight: 1.3 }}>
            {result.testName}
          </span>
          <Badge variant={statusVariants[result.status]} size="sm">
            {statusLabels[result.status]}
          </Badge>
        </div>
        <div style={{ display: 'flex', alignItems: 'baseline', gap: 10, flexWrap: 'wrap' }}>
          <span
            style={{
              fontSize: 26,
              fontWeight: 700,
              lineHeight: 1.1,
              color: isCritical ? 'var(--sq-critical)' : 'var(--sq-fg)',
            }}
          >
            {result.value}
          </span>
          {result.unit ? <span style={{ fontSize: 13, color: 'var(--sq-fg-muted)' }}>{result.unit}</span> : null}
          {result.interpretation ? (
            <Badge variant={interpretationVariants[result.interpretation]} size="sm">
              {interpretationLabels[result.interpretation]}
            </Badge>
          ) : null}
        </div>
        {result.referenceRange ? (
          <div style={{ fontSize: 12, color: 'var(--sq-fg-muted)' }}>
            Reference range: {result.referenceRange}
          </div>
        ) : null}
        {result.collectedAt || result.reportedAt ? (
          <div style={{ fontSize: 12, color: 'var(--sq-fg-muted)' }}>
            {result.collectedAt ? `Collected ${formatDate(result.collectedAt, 'dd MMM, h:mm a')}` : ''}
            {result.collectedAt && result.reportedAt ? ' · ' : ''}
            {result.reportedAt ? `Reported ${formatDate(result.reportedAt, 'dd MMM, h:mm a')}` : ''}
          </div>
        ) : null}
        {result.notes ? (
          <p style={{ margin: 0, fontSize: 13, color: 'var(--sq-fg-muted)', lineHeight: 1.5 }}>{result.notes}</p>
        ) : null}
      </CardContent>
    </Card>
  );
}