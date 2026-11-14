import type { CSSProperties } from 'react';
import { format } from 'date-fns';
import { Badge, type BadgeVariant } from '../primitives/Badge';
import { DataGrid, type DataGridColumn } from '../primitives/DataGrid';
import { cn } from '../lib/cn';

export type LabInterpretation = 'normal' | 'low' | 'high' | 'critical' | 'pending';
export type LabResultStatus = 'pending' | 'in-progress' | 'completed' | 'cancelled';

export interface LabResultRow {
  testName: string;
  value: string | number;
  unit?: string;
  referenceRange?: string;
  interpretation?: LabInterpretation;
}

export interface LabResultViewerProps {
  results: LabResultRow[];
  patientName?: string;
  orderedAt?: Date | string;
  status?: LabResultStatus;
  className?: string;
  style?: CSSProperties;
}

const interpretationVariants: Record<LabInterpretation, BadgeVariant> = {
  normal: 'success',
  low: 'information',
  high: 'warning',
  critical: 'critical',
  pending: 'neutral',
};

const interpretationLabels: Record<LabInterpretation, string> = {
  normal: 'Normal',
  low: 'Low',
  high: 'High',
  critical: 'Critical',
  pending: 'Pending',
};

const statusVariants: Record<LabResultStatus, BadgeVariant> = {
  pending: 'warning',
  'in-progress': 'information',
  completed: 'success',
  cancelled: 'neutral',
};

const statusLabels: Record<LabResultStatus, string> = {
  pending: 'Pending',
  'in-progress': 'In progress',
  completed: 'Completed',
  cancelled: 'Cancelled',
};

function formatDate(value: Date | string): string {
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  return format(date, 'dd MMM yyyy, h:mm a');
}

const columns: DataGridColumn<LabResultRow>[] = [
  {
    key: 'testName',
    header: 'Test',
    width: '32%',
    render: (row) => (
      <span
        style={
          row.interpretation === 'critical'
            ? { color: 'var(--sq-critical)', fontWeight: 600 }
            : undefined
        }
      >
        {row.testName}
      </span>
    ),
  },
  {
    key: 'value',
    header: 'Value',
    width: '16%',
    render: (row) => (
      <span
        style={
          row.interpretation === 'critical'
            ? { backgroundColor: 'var(--sq-critical-soft)', color: 'var(--sq-critical-on-soft)', fontWeight: 700, padding: '2px 8px', borderRadius: 6 }
            : undefined
        }
      >
        {row.value}
      </span>
    ),
  },
  { key: 'unit', header: 'Unit', width: '12%' },
  { key: 'referenceRange', header: 'Reference range', width: '24%' },
  {
    key: 'interpretation',
    header: 'Interpretation',
    width: '16%',
    render: (row) =>
      row.interpretation ? (
        <Badge variant={interpretationVariants[row.interpretation]} size="sm">
          {interpretationLabels[row.interpretation]}
        </Badge>
      ) : null,
  },
];

export function LabResultViewer({
  results,
  patientName,
  orderedAt,
  status,
  className,
  style,
}: LabResultViewerProps) {
  return (
    <div className={cn(className)} style={{ display: 'flex', flexDirection: 'column', gap: 12, ...style }}>
      {patientName || status || orderedAt ? (
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 12,
            flexWrap: 'wrap',
            fontSize: 13,
            color: 'var(--sq-fg-muted)',
          }}
        >
          {patientName ? (
            <span style={{ fontSize: 15, fontWeight: 600, color: 'var(--sq-fg)' }}>{patientName}</span>
          ) : null}
          {status ? (
            <Badge variant={statusVariants[status]} size="sm">
              {statusLabels[status]}
            </Badge>
          ) : null}
          {orderedAt ? <span>Ordered {formatDate(orderedAt)}</span> : null}
        </div>
      ) : null}
      <DataGrid columns={columns} rows={results} emptyMessage="No lab results" />
    </div>
  );
}