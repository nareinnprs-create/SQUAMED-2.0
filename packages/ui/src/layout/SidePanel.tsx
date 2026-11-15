import type { CSSProperties, ReactNode } from 'react';
import { cn } from '../lib/cn';

type SidePanelProps = {
  children: ReactNode;
  side?: 'left' | 'right';
  width?: number | string;
  className?: string;
  style?: CSSProperties;
};

export function SidePanel({ children, side = 'right', width = 320, className, style }: SidePanelProps) {
  return (
    <aside
      className={cn('sq-side-panel', className)}
      style={{
        width: typeof width === 'number' ? `${width}px` : width,
        flexShrink: 0,
        background: 'var(--sq-surface)',
        borderInline: side === 'right' ? '1px solid var(--sq-border)' : undefined,
        borderInlineStart: side === 'left' ? '1px solid var(--sq-border)' : undefined,
        borderInlineEnd: side === 'right' ? '1px solid var(--sq-border)' : undefined,
        overflowY: 'auto',
        ...style,
      }}
    >
      {children}
    </aside>
  );
}