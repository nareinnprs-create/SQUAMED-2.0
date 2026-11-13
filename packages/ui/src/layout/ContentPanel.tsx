import type { CSSProperties, ReactNode } from 'react';
import { cn } from '../lib/cn';

type ContentPanelProps = {
  children: ReactNode;
  padding?: number | string;
  bordered?: boolean;
  className?: string;
  style?: CSSProperties;
};

export function ContentPanel({ children, padding = '16px', bordered = false, className, style }: ContentPanelProps) {
  return (
    <div
      className={cn('sq-content-panel', className)}
      style={{
        background: 'var(--sq-surface)',
        borderRadius: '8px',
        border: bordered ? '1px solid var(--sq-border)' : 'none',
        boxShadow: 'var(--sq-shadow-sm)',
        padding: typeof padding === 'number' ? `${padding}px` : padding,
        ...style,
      }}
    >
      {children}
    </div>
  );
}