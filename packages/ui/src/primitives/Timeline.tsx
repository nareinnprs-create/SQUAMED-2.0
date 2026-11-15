import type { CSSProperties, ReactNode } from 'react';
import { cn } from '../lib/cn';

export type TimelineTone = 'default' | 'success' | 'warning' | 'critical' | 'information' | 'primary';

export interface TimelineProps {
  children: ReactNode;
  className?: string;
  style?: CSSProperties;
}

export interface TimelineItemProps {
  title: ReactNode;
  time?: ReactNode;
  description?: ReactNode;
  icon?: ReactNode;
  tone?: TimelineTone;
  children?: ReactNode;
  className?: string;
  style?: CSSProperties;
}

const toneColors: Record<TimelineTone, string> = {
  default: 'var(--sq-fg-muted)',
  success: 'var(--sq-success)',
  warning: 'var(--sq-warning)',
  critical: 'var(--sq-critical)',
  information: 'var(--sq-information)',
  primary: 'var(--sq-primary)',
};

export function Timeline({ children, className, style }: TimelineProps) {
  return (
    <div
      className={cn(className)}
      style={{
        position: 'relative',
        display: 'flex',
        flexDirection: 'column',
        boxSizing: 'border-box',
        borderInlineStart: '2px solid var(--sq-border)',
        paddingInlineStart: 16,
        ...style,
      }}
    >
      {children}
    </div>
  );
}

export function TimelineItem({
  title,
  time,
  description,
  icon,
  tone = 'default',
  children,
  className,
  style,
}: TimelineItemProps) {
  const toneColor = toneColors[tone];

  return (
    <div
      className={cn(className)}
      style={{
        position: 'relative',
        display: 'flex',
        flexDirection: 'column',
        gap: 4,
        paddingBottom: 20,
        minWidth: 0,
        ...style,
      }}
    >
      {icon ? (
        <span
          style={{
            position: 'absolute',
            top: 2,
            insetInlineStart: -25,
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            width: 24,
            height: 24,
            borderRadius: '50%',
            backgroundColor: 'var(--sq-surface)',
            border: '2px solid',
            borderColor: toneColor,
            color: toneColor,
            boxSizing: 'border-box',
            fontSize: 12,
          }}
        >
          {icon}
        </span>
      ) : (
        <span
          style={{
            position: 'absolute',
            top: 6,
            insetInlineStart: -19,
            width: 12,
            height: 12,
            borderRadius: '50%',
            backgroundColor: toneColor,
            boxSizing: 'border-box',
          }}
        />
      )}
      <div style={{ display: 'flex', alignItems: 'baseline', gap: 12 }}>
        <span style={{ fontSize: 14, fontWeight: 600, color: 'var(--sq-fg)', lineHeight: 1.4 }}>
          {title}
        </span>
        {time ? (
          <span style={{ fontSize: 12, color: 'var(--sq-fg-muted)', whiteSpace: 'nowrap' }}>{time}</span>
        ) : null}
      </div>
      {description ? (
        <p style={{ margin: 0, fontSize: 13, color: 'var(--sq-fg-muted)', lineHeight: 1.5 }}>
          {description}
        </p>
      ) : null}
      {children ? <div>{children}</div> : null}
    </div>
  );
}