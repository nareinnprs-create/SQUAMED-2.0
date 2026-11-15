import type { ComponentType, CSSProperties } from 'react';
import { format } from 'date-fns';
import { PriorityIndicator, type PriorityLevel } from '../primitives/PriorityIndicator';
import { Card, CardContent } from '../primitives/Card';
import { StatusIndicator, type StatusTone } from '../primitives/StatusIndicator';
import { t } from '../i18n';
import { cn } from '../lib/cn';
import { ClockIcon, type IconProps } from './icons';

/**
 * Chunk-00 item 2 core component: task card (task inbox / ward round list).
 * One card = one actionable task with status, priority, due time and assignee.
 */
export type TaskStatus = 'pending' | 'in_progress' | 'escalated' | 'completed';

export interface Task {
  id: string;
  title: string;
  status: TaskStatus;
  priority?: PriorityLevel;
  dueAt?: Date | string;
  assigneeName?: string;
  escalationCount?: number;
}

export interface TaskCardProps {
  task: Task;
  onOpen?: (task: Task) => void;
  className?: string;
  style?: CSSProperties;
}

const statusTones: Record<TaskStatus, StatusTone> = {
  pending: 'neutral',
  in_progress: 'warning',
  escalated: 'critical',
  completed: 'success',
};

const statusLabels: Record<TaskStatus, string> = {
  pending: 'Pending',
  in_progress: 'In progress',
  escalated: 'Escalated',
  completed: 'Completed',
};

const OpenIcon: ComponentType<IconProps> = ClockIcon;

function formatDue(value: Date | string): string {
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  // Tabular mono per §3: due times align in a column for scanning.
  return format(date, 'dd MMM, h:mm');
}

export function TaskCard({ task, onOpen, className, style }: TaskCardProps) {
  return (
    <Card className={cn(className)} style={{ ...style }}>
      <CardContent style={{ display: 'flex', gap: 12, alignItems: 'flex-start' }}>
        <div
          aria-hidden="true"
          style={{
            width: 34,
            height: 34,
            borderRadius: 8,
            flexShrink: 0,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            backgroundColor: 'color-mix(in srgb, var(--sq-primary) 12%, transparent)',
            color: 'var(--sq-primary)',
            boxSizing: 'border-box',
          }}
        >
          <OpenIcon size={17} />
        </div>
        <div style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', gap: 6 }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12 }}>
            <span style={{ fontSize: 15, fontWeight: 600, color: 'var(--sq-fg)', lineHeight: 1.3 }}>
              {task.title}
            </span>
            <StatusIndicator tone={statusTones[task.status]} label={statusLabels[task.status]} />
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, fontSize: 12, color: 'var(--sq-fg-muted)' }}>
            {task.dueAt ? <span style={{ fontVariantNumeric: 'tabular-nums' }}>{formatDue(task.dueAt)}</span> : null}
            {task.priority ? <PriorityIndicator level={task.priority} /> : null}
            {task.assigneeName ? <span>{task.assigneeName}</span> : null}
            {task.status === 'escalated' && task.escalationCount ? (
              <span style={{ color: 'var(--sq-critical)', fontWeight: 600 }}>
                {t('taskcard.escalated', { count: task.escalationCount })}
              </span>
            ) : null}
          </div>
        </div>
        {onOpen ? (
          <button
            type="button"
            onClick={() => onOpen(task)}
            style={{
              flexShrink: 0,
              padding: '6px 12px',
              fontSize: 12,
              fontWeight: 600,
              borderRadius: 6,
              border: '1px solid var(--sq-border)',
              backgroundColor: 'var(--sq-surface)',
              color: 'var(--sq-fg)',
              cursor: 'pointer',
              boxSizing: 'border-box',
            }}
          >
            {t('taskcard.open')}
          </button>
        ) : null}
      </CardContent>
    </Card>
  );
}
