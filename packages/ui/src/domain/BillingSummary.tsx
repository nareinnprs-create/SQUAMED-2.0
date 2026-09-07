import type { CSSProperties } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '../primitives/Card';
import { Progress } from '../primitives/Progress';
import { cn } from '../lib/cn';

export interface BillingSummaryData {
  totalAmount: number;
  paidAmount: number;
  pendingAmount: number;
  insuranceCovered?: number;
  currency?: string;
  items?: number;
  invoices?: number;
}

export interface BillingSummaryProps {
  summary: BillingSummaryData;
  className?: string;
  style?: CSSProperties;
}

function formatAmount(amount: number, currency: string): string {
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(amount);
}

export function BillingSummary({ summary, className, style }: BillingSummaryProps) {
  const currency = summary.currency ?? 'INR';
  const hasPending = summary.pendingAmount > 0;
  const paidPercent =
    summary.totalAmount > 0 ? Math.min(100, Math.round((summary.paidAmount / summary.totalAmount) * 100)) : 0;

  return (
    <Card className={cn(className)} style={{ ...style }}>
      <CardHeader>
        <CardTitle>Billing Summary</CardTitle>
      </CardHeader>
      <CardContent style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, minmax(0, 1fr))', gap: 12 }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            <span style={{ fontSize: 12, color: 'var(--sq-fg-muted)' }}>Total</span>
            <span style={{ fontSize: 17, fontWeight: 700, color: 'var(--sq-fg)', lineHeight: 1.2 }}>
              {formatAmount(summary.totalAmount, currency)}
            </span>
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            <span style={{ fontSize: 12, color: 'var(--sq-fg-muted)' }}>Paid</span>
            <span
              style={{
                fontSize: 17,
                fontWeight: 700,
                lineHeight: 1.2,
                color: hasPending ? 'var(--sq-success)' : 'var(--sq-fg)',
              }}
            >
              {formatAmount(summary.paidAmount, currency)}
            </span>
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            <span style={{ fontSize: 12, color: 'var(--sq-fg-muted)' }}>Pending</span>
            <span
              style={{
                fontSize: 17,
                fontWeight: 700,
                lineHeight: 1.2,
                color: hasPending ? 'var(--sq-critical)' : 'var(--sq-fg-muted)',
              }}
            >
              {formatAmount(summary.pendingAmount, currency)}
            </span>
          </div>
        </div>
        <Progress
          value={paidPercent}
          variant={hasPending ? 'warning' : 'success'}
          label={`Paid ${paidPercent}%`}
          style={{ height: 6 }}
        />
        <div style={{ display: 'flex', gap: 14, fontSize: 12, color: 'var(--sq-fg-muted)', flexWrap: 'wrap' }}>
          {summary.items !== undefined ? <span>{summary.items} items</span> : null}
          {summary.invoices !== undefined ? <span>{summary.invoices} invoices</span> : null}
          {summary.insuranceCovered !== undefined ? (
            <span>Insurance covered: {formatAmount(summary.insuranceCovered, currency)}</span>
          ) : null}
        </div>
      </CardContent>
    </Card>
  );
}