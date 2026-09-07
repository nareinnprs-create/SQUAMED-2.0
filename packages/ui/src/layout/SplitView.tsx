import type { CSSProperties, ReactNode } from 'react';
import { cn } from '../lib/cn';

type SplitViewProps = {
  children: ReactNode;
  orientation?: 'horizontal' | 'vertical';
  ratio?: [number, number];
  minSize?: number | string;
  className?: string;
  style?: CSSProperties;
};

export function SplitView({
  children,
  orientation = 'horizontal',
  ratio = [1, 1],
  minSize = '240px',
  className,
  style,
}: SplitViewProps) {
  const childrenArray = Array.isArray(children) ? children : [children];
  return (
    <div
      className={cn('sq-split-view', className)}
      style={{
        display: 'flex',
        flexDirection: orientation === 'horizontal' ? 'row' : 'column',
        minHeight: 0,
        minWidth: 0,
        gap: '1px',
        ...style,
      }}
    >
      {childrenArray.map((child, index) => (
        <div
          key={index}
          style={{
            flexGrow: ratio[index] ?? 1,
            flexShrink: 1,
            flexBasis: 0,
            minWidth: orientation === 'horizontal' ? minSize : undefined,
            minHeight: orientation === 'vertical' ? minSize : undefined,
            overflow: 'auto',
          }}
        >
          {child}
        </div>
      ))}
    </div>
  );
}