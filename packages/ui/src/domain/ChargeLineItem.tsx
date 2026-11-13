import type { CSSProperties } from 'react';
import { format } from 'date-fns';
import { Badge, type BadgeVariant } from '../primitives/Badge';
import { cn } from '../lib/cn';

export type ChargeType = 'service' | 'drug' | 'procedure' | 'lab' | 'discount';
export type ChargeStatus = 'paid' | 'pending' | 'due' | 'cancelled';

export interface Charge {
  date: Date | string;
  service: string;
  code?: string;
  qty: number;
  unitPrice: number;
  amount: number;
  status: ChargeStatus;
  type: ChargeType;
}

export interface ChargeLineItemProps {
  charge: Charge;
  className?: string;
  style?: CSSProperties;
}

const statusVariants: Record<ChargeStatus, BadgeVariant> = {
  paid: 'success',
  pending: 'warning',
  due: 'critical',
  cancelled: 'neutral',
};

const statusLabels: Record<ChargeStatus, string> = {
  paid: 'Paid',
  pending: 'Pending',
  due: 'Due',
  cancelled: 'Cancelled',
};

function formatDate(value: Date | string): string {
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  return format(date, 'dd MMM yyyy');
}

function formatAmount(amount: number): string {
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(amount);
}

export function ChargeLineItem({ charge, className, style }: ChargeLineItemProps) {
  const isDiscount = charge.type === 'discount';

  return (
    <div
      className={cn(className)}
      style={{
        boxSizing: 'border-box',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: 12,
        padding: '10px 12px',
        borderRadius: 8,
        backgroundColor: isDiscount ? 'color-mix(in srgb, var(--sq-fg) 4%, transparent)' : undefined,
        ...style,
      }}
    >
      <div style={{ display: 'flex', flexDirection: 'column', gap: 2, minWidth: 0 }}>
        <span
          style={{
            fontSize: 14,
            fontWeight: 600,
            color: 'var(--sq-fg)',
            textDecoration: isDiscount ? 'line-through' : undefined,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
        >
          {charge.service}
        </span>
        <span style={{ fontSize: 12, color: 'var(--sq-fg-muted)' }}>
          {formatDate(charge.date)} · {charge.qty} × {formatAmount(charge.unitPrice)}
          {charge.code ? ` · ${charge.code}` : ''}
        </span>
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 4, flexShrink: 0 }}>
        <span
          style={{
            fontSize: 15,
            fontWeight: 700,
            lineHeight: 1.2,
            color: isDiscount ? 'var(--sq-success)' : 'var(--sq-fg)',
          }}
        >
          {isDiscount ? '−' : ''}
          {formatAmount(charge.amount)}
        </span>
        <Badge variant={statusVariants[charge.status]} size="sm">
          {statusLabels[charge.status]}
        </Badge>
      </div>
    </div>
  );
}