import type { CSSProperties, ReactNode } from 'react';
import { cn } from '../lib/cn';

type StackProps = {
  children: ReactNode;
  direction?: 'row' | 'column';
  gap?: number | string;
  align?: CSSProperties['alignItems'];
  justify?: CSSProperties['justifyContent'];
  wrap?: boolean;
  className?: string;
  style?: CSSProperties;
};

export function Stack({
  children,
  direction = 'column',
  gap = '8px',
  align,
  justify,
  wrap = false,
  className,
  style,
}: StackProps) {
  return (
    <div
      className={cn('sq-stack', className)}
      style={{
        display: 'flex',
        flexDirection: direction,
        gap: typeof gap === 'number' ? `${gap}px` : gap,
        alignItems: align,
        justifyContent: justify,
        flexWrap: wrap ? 'wrap' : undefined,
        ...style,
      }}
    >
      {children}
    </div>
  );
}