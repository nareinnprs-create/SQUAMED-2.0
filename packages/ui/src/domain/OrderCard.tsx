import type { ComponentType, CSSProperties } from 'react';
import { format } from 'date-fns';
import { Badge, type BadgeVariant } from '../primitives/Badge';
import { Card, CardContent } from '../primitives/Card';
import { PriorityIndicator, type PriorityLevel } from '../primitives/PriorityIndicator';
import { cn } from '../lib/cn';
import {
  FlaskIcon,
  PillIcon,
  ProcedureIcon,
  ReferralIcon,
  ScanIcon,
  type IconProps,
} from './icons';

export type OrderType = 'lab' | 'imaging' | 'procedure' | 'medication' | 'referral';
export type OrderStatus = 'pending' | 'in-progress' | 'completed' | 'cancelled' | 'on-hold';

export interface Order {
  id?: string;
  type: OrderType;
  title: string;
  status: OrderStatus;
  priority?: PriorityLevel;
  orderedBy?: string;
  orderedAt?: Date | string;
  details?: string;
}

export interface OrderCardProps {
  order: Order;
  className?: string;
  style?: CSSProperties;
}

const typeMeta: Record<OrderType, { icon: ComponentType<IconProps>; color: string; label: string }> = {
  lab: { icon: FlaskIcon, color: 'var(--sq-information)', label: 'Lab' },
  imaging: { icon: ScanIcon, color: 'var(--sq-primary)', label: 'Imaging' },
  procedure: { icon: ProcedureIcon, color: 'var(--sq-warning)', label: 'Procedure' },
  medication: { icon: PillIcon, color: 'var(--sq-success)', label: 'Medication' },
  referral: { icon: ReferralIcon, color: 'var(--sq-fg-muted)', label: 'Referral' },
};

const statusVariants: Record<OrderStatus, BadgeVariant> = {
  pending: 'warning',
  'in-progress': 'information',
  completed: 'success',
  cancelled: 'critical',
  'on-hold': 'neutral',
};

const statusLabels: Record<OrderStatus, string> = {
  pending: 'Pending',
  'in-progress': 'In progress',
  completed: 'Completed',
  cancelled: 'Cancelled',
  'on-hold': 'On hold',
};

function formatDate(value: Date | string): string {
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  return format(date, 'dd MMM yyyy, h:mm a');
}

export function OrderCard({ order, className, style }: OrderCardProps) {
  const meta = typeMeta[order.type];
  const Icon = meta.icon;

  return (
    <Card className={cn(className)} style={{ ...style }}>
      <CardContent style={{ display: 'flex', gap: 14 }}>
        <div
          aria-hidden="true"
          style={{
            width: 38,
            height: 38,
            borderRadius: '50%',
            flexShrink: 0,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            backgroundColor: `color-mix(in srgb, ${meta.color} 14%, transparent)`,
            color: meta.color,
            border: `1px solid color-mix(in srgb, ${meta.color} 30%, transparent)`,
            boxSizing: 'border-box',
          }}
        >
          <Icon size={18} />
        </div>
        <div style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', gap: 6 }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12 }}>
            <span style={{ fontSize: 15, fontWeight: 600, color: 'var(--sq-fg)', lineHeight: 1.3 }}>{order.title}</span>
            <Badge variant={statusVariants[order.status]} size="sm">
              {statusLabels[order.status]}
            </Badge>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, fontSize: 12, color: 'var(--sq-fg-muted)' }}>
            <span>{meta.label}</span>
            {order.id ? <span>#{order.id}</span> : null}
            {order.priority ? <PriorityIndicator level={order.priority} /> : null}
          </div>
          {order.details ? (
            <p style={{ margin: 0, fontSize: 13, color: 'var(--sq-fg-muted)', lineHeight: 1.5 }}>{order.details}</p>
          ) : null}
          {order.orderedBy || order.orderedAt ? (
            <div style={{ fontSize: 12, color: 'var(--sq-fg-muted)' }}>
              {order.orderedBy ? `Ordered by ${order.orderedBy}` : ''}
              {order.orderedBy && order.orderedAt ? ' · ' : ''}
              {order.orderedAt ? formatDate(order.orderedAt) : ''}
            </div>
          ) : null}
        </div>
      </CardContent>
    </Card>
  );
}