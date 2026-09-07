'use client';

import { Children, cloneElement, useId, useState } from 'react';
import type { CSSProperties, ReactElement, ReactNode } from 'react';
import { ensureKeyframes, usePrefersReducedMotion } from './internal';

export type TooltipSide = 'top' | 'bottom';
export type TooltipAlign = 'start' | 'center' | 'end';

export interface TooltipProps {
  label: ReactNode;
  children: ReactNode;
  side?: TooltipSide;
  align?: TooltipAlign;
}

ensureKeyframes();

export function Tooltip({ label, children, side = 'top', align = 'center' }: TooltipProps) {
  const [visible, setVisible] = useState(false);
  const reduced = usePrefersReducedMotion();
  const tipId = useId();

  const show = () => setVisible(true);
  const hide = () => setVisible(false);

  const child = Children.only(children) as unknown as ReactElement<Record<string, unknown>>;
  const trigger = cloneElement(child, {
    'aria-describedby': tipId,
    'data-state': visible ? 'visible' : 'hidden',
    onMouseEnter: show,
    onMouseLeave: hide,
    onFocus: show,
    onBlur: hide,
  });

  const positionStyle: CSSProperties =
    side === 'top' ? { bottom: 'calc(100% + 6px)' } : { top: 'calc(100% + 6px)' };

  const alignStyle: CSSProperties =
    align === 'start'
      ? { left: 0 }
      : align === 'end'
        ? { right: 0 }
        : { left: '50%', transform: 'translateX(-50%)' };

  return (
    <div style={{ position: 'relative', display: 'inline-flex' }}>
      {trigger}
      {visible ? (
        <span
          id={tipId}
          role="tooltip"
          style={{
            position: 'absolute',
            zIndex: 1200,
            maxWidth: 280,
            padding: '4px 10px',
            borderRadius: 6,
            backgroundColor: 'var(--sq-fg)',
            color: 'var(--sq-bg)',
            fontSize: 12,
            lineHeight: 1.4,
            whiteSpace: 'nowrap',
            pointerEvents: 'none',
            ...positionStyle,
            ...alignStyle,
            animation: reduced ? 'none' : 'sq-fade-in 100ms ease-out',
          }}
        >
          {label}
        </span>
      ) : null}
    </div>
  );
}