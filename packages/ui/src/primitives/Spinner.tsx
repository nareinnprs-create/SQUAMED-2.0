import type { CSSProperties } from 'react';
import { cn } from '../lib/cn';
import { ensureKeyframes } from './internal';

ensureKeyframes();

export type SpinnerSize = 'sm' | 'md' | 'lg';

export interface SpinnerProps {
  size?: SpinnerSize;
  color?: string;
  label?: string;
  className?: string;
  style?: CSSProperties;
}

const sizeMap: Record<SpinnerSize, { size: number; borderWidth: number }> = {
  sm: { size: 16, borderWidth: 2 },
  md: { size: 22, borderWidth: 2 },
  lg: { size: 28, borderWidth: 3 },
};

export function Spinner({
  size = 'md',
  color = 'var(--sq-primary)',
  label,
  className,
  style,
}: SpinnerProps) {
  const { size: px, borderWidth } = sizeMap[size];

  return (
    <span
      role={label ? 'status' : undefined}
      aria-label={label}
      aria-hidden={label ? undefined : true}
      className={cn(className)}
      style={{
        display: 'inline-block',
        width: px,
        height: px,
        boxSizing: 'border-box',
        borderRadius: '50%',
        border: `${borderWidth}px solid ${color}`,
        borderTopColor: 'transparent',
        animation: 'sq-spin 0.7s linear infinite',
        flexShrink: 0,
        ...style,
      }}
    />
  );
}