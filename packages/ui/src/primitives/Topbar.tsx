'use client';

import type { CSSProperties, ReactNode } from 'react';
import { cn } from '../lib/cn';
import { useFocusVisible } from './internal';

export interface TopbarProps {
  title?: ReactNode;
  leftSlot?: ReactNode;
  centerSlot?: ReactNode;
  rightSlot?: ReactNode;
  onMenuClick?: () => void;
  className?: string;
  style?: CSSProperties;
}

export function Topbar({
  title,
  leftSlot,
  centerSlot,
  rightSlot,
  onMenuClick,
  className,
  style,
}: TopbarProps) {
  const focus = useFocusVisible();

  return (
    <header
      className={cn(className)}
      style={{
        position: 'relative',
        display: 'flex',
        alignItems: 'center',
        gap: 12,
        height: 56,
        flexShrink: 0,
        boxSizing: 'border-box',
        padding: '0 16px',
        backgroundColor: 'var(--sq-surface)',
        borderBottom: '1px solid var(--sq-border)',
        ...style,
      }}
    >
      {onMenuClick ? (
        <button
          type="button"
          aria-label="Toggle menu"
          onClick={onMenuClick}
          onPointerDown={focus.onPointerDown}
          onFocus={focus.onFocus}
          onBlur={focus.onBlur}
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            width: 36,
            height: 36,
            padding: 0,
            border: 'none',
            borderRadius: 8,
            backgroundColor: 'transparent',
            color: 'var(--sq-fg)',
            cursor: 'pointer',
            ...(focus.focusVisible ? { outline: '2px solid var(--sq-focus-ring)', outlineOffset: 2 } : null),
          }}
        >
          <svg width="18" height="18" viewBox="0 0 18 18" fill="none" aria-hidden="true">
            <path d="M2 4.5 L16 4.5 M2 9 L16 9 M2 13.5 L16 13.5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
          </svg>
        </button>
      ) : null}
      {leftSlot ? (
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>{leftSlot}</div>
      ) : null}
      {title ? (
        <div
          style={{
            fontSize: 16,
            fontWeight: 600,
            color: 'var(--sq-fg)',
            whiteSpace: 'nowrap',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
          }}
        >
          {title}
        </div>
      ) : null}
      {centerSlot ? (
        <div
          style={{
            position: 'absolute',
            left: '50%',
            top: '50%',
            transform: 'translate(-50%, -50%)',
            display: 'flex',
            alignItems: 'center',
            gap: 8,
          }}
        >
          {centerSlot}
        </div>
      ) : null}
      {rightSlot ? (
        <div style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: 8 }}>{rightSlot}</div>
      ) : null}
    </header>
  );
}