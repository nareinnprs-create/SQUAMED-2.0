'use client';

import type { CSSProperties, ReactNode } from 'react';
import { cn } from '../lib/cn';
import { useFocusVisible } from './internal';

export interface BreadcrumbProps {
  children: ReactNode;
  className?: string;
  style?: CSSProperties;
}

export interface BreadcrumbItemProps {
  href?: string;
  children?: ReactNode;
  ariaCurrent?: 'page' | 'step' | 'location' | 'date' | 'time' | boolean;
  className?: string;
  style?: CSSProperties;
}

export interface BreadcrumbSeparatorProps {
  children?: ReactNode;
  className?: string;
  style?: CSSProperties;
}

export function Breadcrumb({ children, className, style }: BreadcrumbProps) {
  return (
    <nav
      aria-label="Breadcrumb"
      className={cn(className)}
      style={{
        display: 'flex',
        alignItems: 'center',
        flexWrap: 'wrap',
        gap: 8,
        boxSizing: 'border-box',
        ...style,
      }}
    >
      {children}
    </nav>
  );
}

export function BreadcrumbItem({
  href,
  children,
  ariaCurrent,
  className,
  style,
}: BreadcrumbItemProps) {
  const focus = useFocusVisible();
  const sharedStyle: CSSProperties = {
    display: 'inline-flex',
    alignItems: 'center',
    fontSize: 13,
    lineHeight: 1.4,
    color: href ? 'var(--sq-fg-muted)' : 'var(--sq-fg)',
    fontFamily: 'inherit',
    ...style,
  };

  if (href) {
    return (
      <a
        href={href}
        aria-current={ariaCurrent}
        onPointerDown={focus.onPointerDown}
        onFocus={focus.onFocus}
        onBlur={focus.onBlur}
        className={cn(className)}
        style={{
          ...sharedStyle,
          textDecoration: 'none',
          transition: 'color 150ms ease',
          ...(focus.focusVisible ? { outline: '2px solid var(--sq-focus-ring)', outlineOffset: 2 } : null),
        }}
        onMouseEnter={(event) => {
          event.currentTarget.style.color = 'var(--sq-fg)';
        }}
        onMouseLeave={(event) => {
          event.currentTarget.style.color = 'var(--sq-fg-muted)';
        }}
      >
        {children}
      </a>
    );
  }

  return (
    <span
      aria-current={ariaCurrent}
      role="link"
      aria-disabled="true"
      className={cn(className)}
      style={sharedStyle}
    >
      {children}
    </span>
  );
}

export function BreadcrumbSeparator({ children, className, style }: BreadcrumbSeparatorProps) {
  return (
    <span
      aria-hidden="true"
      className={cn(className)}
      style={{
        fontSize: 13,
        color: 'var(--sq-fg-muted)',
        userSelect: 'none',
        ...style,
      }}
    >
      {children ?? '/'}
    </span>
  );
}