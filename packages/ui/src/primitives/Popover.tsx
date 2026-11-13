'use client';

import { Children, cloneElement, useCallback, useId, useRef } from 'react';
import type { CSSProperties, ReactElement, ReactNode } from 'react';
import {
  ensureKeyframes,
  useClickOutside,
  useControllableState,
  useEscapeKey,
  useFocusRestore,
  usePrefersReducedMotion,
} from './internal';

export type PopoverSide = 'top' | 'bottom';
export type PopoverAlign = 'start' | 'center' | 'end';

export interface PopoverProps {
  trigger: ReactNode;
  children: ReactNode;
  open?: boolean;
  defaultOpen?: boolean;
  onOpenChange?: (open: boolean) => void;
  side?: PopoverSide;
  align?: PopoverAlign;
  className?: string;
  style?: CSSProperties;
}

ensureKeyframes();

export function Popover({
  trigger,
  children,
  open,
  defaultOpen = false,
  onOpenChange,
  side = 'bottom',
  align = 'center',
  className,
  style,
}: PopoverProps) {
  const [isOpen, setOpen] = useControllableState(open, defaultOpen, onOpenChange);
  const rootRef = useRef<HTMLDivElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);
  const reduced = usePrefersReducedMotion();
  const dialogId = useId();

  const close = useCallback(() => {
    setOpen(false);
  }, [setOpen]);

  useClickOutside(rootRef, isOpen, close);
  useEscapeKey(isOpen, close);
  useFocusRestore(isOpen, panelRef);

  const triggerElement = Children.only(trigger) as unknown as ReactElement<Record<string, unknown>>;
  const clonedTrigger = cloneElement(triggerElement, {
    'aria-expanded': isOpen,
    'aria-haspopup': 'dialog',
    'aria-controls': isOpen ? dialogId : undefined,
    'data-state': isOpen ? 'open' : 'closed',
    onClick: () => setOpen(!isOpen),
  });

  const positionStyle: CSSProperties =
    side === 'bottom' ? { top: 'calc(100% + 8px)' } : { bottom: 'calc(100% + 8px)' };

  const alignStyle: CSSProperties =
    align === 'start'
      ? { left: 0 }
      : align === 'end'
        ? { right: 0 }
        : { left: '50%', transform: 'translateX(-50%)' };

  return (
    <div ref={rootRef} className={className} style={{ position: 'relative', display: 'inline-flex', ...style }}>
      {clonedTrigger}
      {isOpen ? (
        <div
          id={dialogId}
          role="dialog"
          ref={panelRef}
          tabIndex={-1}
          style={{
            position: 'absolute',
            zIndex: 999,
            minWidth: '100%',
            width: 'max-content',
            backgroundColor: 'var(--sq-surface)',
            color: 'var(--sq-fg)',
            border: '1px solid var(--sq-border)',
            borderRadius: 10,
            boxSizing: 'border-box',
            boxShadow: '0 12px 32px color-mix(in srgb, var(--sq-fg) 18%, transparent)',
            ...positionStyle,
            ...alignStyle,
            animation: reduced ? 'none' : 'sq-fade-in 120ms ease-out',
          }}
        >
          {children}
        </div>
      ) : null}
    </div>
  );
}