import type { CSSProperties } from 'react';
import { cn } from '../lib/cn';

export interface DividerProps {
  orientation?: 'horizontal' | 'vertical';
  label?: string;
  className?: string;
  style?: CSSProperties;
}

export function Divider({ orientation = 'horizontal', label, className, style }: DividerProps) {
  if (orientation === 'vertical') {
    return (
      <div
        role="separator"
        aria-orientation="vertical"
        className={cn(className)}
        style={{
          alignSelf: 'stretch',
          width: 1,
          minHeight: '100%',
          backgroundColor: 'var(--sq-border)',
          flexShrink: 0,
          ...style,
        }}
      />
    );
  }

  if (label) {
    return (
      <div
        role="separator"
        aria-orientation="horizontal"
        className={cn(className)}
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 12,
          width: '100%',
          ...style,
        }}
      >
        <span style={{ flex: 1, height: 1, backgroundColor: 'var(--sq-border)' }} />
        <span
          style={{
            fontSize: 12,
            fontWeight: 600,
            color: 'var(--sq-fg-muted)',
            whiteSpace: 'nowrap',
            lineHeight: 1,
          }}
        >
          {label}
        </span>
        <span style={{ flex: 1, height: 1, backgroundColor: 'var(--sq-border)' }} />
      </div>
    );
  }

  return (
    <div
      role="separator"
      aria-orientation="horizontal"
      className={cn(className)}
      style={{ width: '100%', height: 1, backgroundColor: 'var(--sq-border)', ...style }}
    />
  );
}