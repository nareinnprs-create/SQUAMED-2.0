import type { CSSProperties, HTMLAttributes, ReactNode } from 'react';
import { cn } from '../lib/cn';

export type BadgeVariant = 'neutral' | 'success' | 'warning' | 'critical' | 'information' | 'primary';
export type BadgeAppearance = 'filled' | 'soft';
export type BadgeSize = 'sm' | 'md';

export interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  variant?: BadgeVariant;
  appearance?: BadgeAppearance;
  size?: BadgeSize;
  children?: ReactNode;
}

const filledColors: Record<BadgeVariant, CSSProperties> = {
  neutral: { backgroundColor: 'var(--sq-secondary)', color: 'var(--sq-fg)' },
  success: { backgroundColor: 'var(--sq-success)', color: 'var(--sq-fg)' },
  warning: { backgroundColor: 'var(--sq-warning)', color: 'var(--sq-fg)' },
  critical: { backgroundColor: 'var(--sq-critical)', color: 'var(--sq-fg)' },
  information: { backgroundColor: 'var(--sq-information)', color: 'var(--sq-fg)' },
  primary: { backgroundColor: 'var(--sq-primary)', color: 'var(--sq-fg)' },
};

const softColors: Record<BadgeVariant, CSSProperties> = {
  neutral: {
    backgroundColor: 'color-mix(in srgb, var(--sq-fg) 9%, transparent)',
    color: 'var(--sq-fg-muted)',
  },
  success: { backgroundColor: 'var(--sq-success-soft)', color: 'var(--sq-success)' },
  warning: { backgroundColor: 'var(--sq-warning-soft)', color: 'var(--sq-warning)' },
  critical: { backgroundColor: 'var(--sq-critical-soft)', color: 'var(--sq-critical)' },
  information: { backgroundColor: 'var(--sq-information-soft)', color: 'var(--sq-information)' },
  primary: { backgroundColor: 'var(--sq-primary-soft)', color: 'var(--sq-primary)' },
};

const sizeStyles: Record<BadgeSize, CSSProperties> = {
  sm: { padding: '1px 7px', fontSize: 11, borderRadius: 999 },
  md: { padding: '3px 10px', fontSize: 12, borderRadius: 999 },
};

export function Badge({
  variant = 'neutral',
  appearance = 'soft',
  size = 'md',
  className,
  style,
  children,
  ...rest
}: BadgeProps) {
  return (
    <span
      {...rest}
      className={cn(className)}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 4,
        fontFamily: 'inherit',
        fontWeight: 600,
        lineHeight: 1.6,
        whiteSpace: 'nowrap',
        boxSizing: 'border-box',
        ...(appearance === 'filled' ? filledColors[variant] : softColors[variant]),
        ...sizeStyles[size],
        ...style,
      }}
    >
      {children}
    </span>
  );
}