import type { CSSProperties, ReactNode } from 'react';
import { cn } from '../lib/cn';

type GridProps = {
  children: ReactNode;
  cols?: 1 | 2 | 3 | 4 | 5 | 6 | 8 | 12;
  gap?: number | string;
  className?: string;
  style?: CSSProperties;
};

export function Grid({ children, cols = 12, gap = '16px', className, style }: GridProps) {
  return (
    <div
      className={cn('sq-grid', className)}
      style={{
        display: 'grid',
        gridTemplateColumns: `repeat(${cols}, minmax(0, 1fr))`,
        gap: typeof gap === 'number' ? `${gap}px` : gap,
        ...style,
      }}
    >
      {children}
    </div>
  );
}

type GridItemProps = {
  children: ReactNode;
  span?: number;
  start?: number;
  className?: string;
  style?: CSSProperties;
};

export function GridItem({ children, span = 1, start, className, style }: GridItemProps) {
  return (
    <div
      className={cn('sq-grid-item', className)}
      style={{
        gridColumn: start ? `${start} / span ${span}` : `span ${span}`,
        minWidth: 0,
        ...style,
      }}
    >
      {children}
    </div>
  );
}