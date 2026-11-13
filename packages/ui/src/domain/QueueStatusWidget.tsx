import type { CSSProperties } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '../primitives/Card';
import { Progress } from '../primitives/Progress';
import { StatusIndicator, type StatusTone } from '../primitives/StatusIndicator';
import { cn } from '../lib/cn';

export type QueueStatus = 'active' | 'paused' | 'closed';

export interface Queue {
  id: string;
  name: string;
  currentToken?: string | number;
  servedCount: number;
  waitingCount: number;
  avgWaitMinutes?: number;
  status: QueueStatus;
}

export interface QueueStatusWidgetProps {
  queues: Queue[];
  className?: string;
  style?: CSSProperties;
}

const statusMeta: Record<QueueStatus, { tone: StatusTone; label: string }> = {
  active: { tone: 'success', label: 'Active' },
  paused: { tone: 'warning', label: 'Paused' },
  closed: { tone: 'neutral', label: 'Closed' },
};

export function QueueStatusWidget({ queues, className, style }: QueueStatusWidgetProps) {
  if (queues.length === 0) {
    return (
      <div className={cn(className)} style={{ fontSize: 13, color: 'var(--sq-fg-muted)', ...style }}>
        No active queues
      </div>
    );
  }

  return (
    <Card className={cn(className)} style={{ ...style }}>
      <CardHeader>
        <CardTitle>Queues</CardTitle>
      </CardHeader>
      <CardContent style={{ display: 'flex', flexDirection: 'column' }}>
        {queues.map((queue, index) => {
          const meta = statusMeta[queue.status];
          const total = queue.servedCount + queue.waitingCount;
          const servedPercent = total > 0 ? Math.round((queue.servedCount / total) * 100) : 0;
          return (
            <div
              key={queue.id}
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                gap: 16,
                padding: '12px 0',
                borderBottom: index < queues.length - 1 ? '1px solid var(--sq-border)' : undefined,
              }}
            >
              <div style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', gap: 8 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 10, minWidth: 0 }}>
                  <span style={{ fontSize: 14, fontWeight: 600, color: 'var(--sq-fg)' }}>{queue.name}</span>
                  <StatusIndicator tone={meta.tone} label={meta.label} />
                </div>
                <Progress value={servedPercent} label={queue.name} style={{ height: 5, maxWidth: 220 }} />
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 3, flexShrink: 0 }}>
                <span style={{ fontSize: 11, color: 'var(--sq-fg-muted)' }}>Current token</span>
                <span
                  style={{
                    fontSize: 22,
                    fontWeight: 700,
                    lineHeight: 1.1,
                    color: 'var(--sq-fg)',
                  }}
                >
                  {queue.currentToken ?? '—'}
                </span>
                <span style={{ fontSize: 12, color: 'var(--sq-fg-muted)' }}>
                  {queue.waitingCount} waiting
                  {queue.avgWaitMinutes !== undefined ? ` · ~${queue.avgWaitMinutes} min` : ''}
                </span>
              </div>
            </div>
          );
        })}
      </CardContent>
    </Card>
  );
}