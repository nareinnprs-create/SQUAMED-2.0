'use client';

import { useId, useRef } from 'react';
import type { CSSProperties, PointerEvent, ReactNode } from 'react';
import { createPortal } from 'react-dom';
import {
  ensureKeyframes,
  useBodyScrollLock,
  useEscapeKey,
  useFocusRestore,
  useFocusTrap,
  usePortalMounted,
  usePrefersReducedMotion,
} from './internal';

export type DrawerSize = 'sm' | 'md' | 'lg';
export type DrawerSide = 'left' | 'right';

export interface DrawerProps {
  open: boolean;
  onClose: () => void;
  title?: ReactNode;
  description?: ReactNode;
  footer?: ReactNode;
  children?: ReactNode;
  side?: DrawerSide;
  size?: DrawerSize;
  className?: string;
  style?: CSSProperties;
}

const sizeWidths: Record<DrawerSize, number> = { sm: 320, md: 400, lg: 480 };

const closeButtonStyle: CSSProperties = {
  display: 'inline-flex',
  alignItems: 'center',
  justifyContent: 'center',
  width: 32,
  height: 32,
  flexShrink: 0,
  padding: 0,
  border: 'none',
  borderRadius: 8,
  backgroundColor: 'transparent',
  color: 'var(--sq-fg-muted)',
  cursor: 'pointer',
};

ensureKeyframes();

export function Drawer({
  open,
  onClose,
  title,
  description,
  footer,
  children,
  side = 'right',
  size = 'md',
  className,
  style,
}: DrawerProps) {
  const mounted = usePortalMounted();
  const reduced = usePrefersReducedMotion();
  const panelRef = useRef<HTMLDivElement>(null);
  const titleId = useId();
  const descriptionId = useId();
  const isLeft = side === 'left';

  useBodyScrollLock(open);
  useFocusTrap(panelRef, open);
  useFocusRestore(open, panelRef);
  useEscapeKey(open, onClose);

  if (!mounted) return null;

  return createPortal(
    <div
      className={className}
      style={{
        position: 'fixed',
        inset: 0,
        zIndex: 1000,
        display: open ? 'flex' : 'none',
        justifyContent: isLeft ? 'flex-start' : 'flex-end',
        boxSizing: 'border-box',
      }}
    >
      <div
        aria-hidden="true"
        onPointerDown={(event: PointerEvent<HTMLDivElement>) => {
          if (event.target === event.currentTarget) onClose();
        }}
        style={{
          position: 'absolute',
          inset: 0,
          backgroundColor: 'var(--sq-disabled)',
          opacity: 0.6,
          animation: reduced ? 'none' : 'sq-fade-in 150ms ease-out',
        }}
      />
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby={title ? titleId : undefined}
        aria-describedby={description ? descriptionId : undefined}
        ref={panelRef}
        tabIndex={-1}
        style={{
          position: 'relative',
          width: sizeWidths[size],
          maxWidth: '100%',
          height: '100%',
          display: 'flex',
          flexDirection: 'column',
          backgroundColor: 'var(--sq-surface)',
          color: 'var(--sq-fg)',
          borderInlineStart: isLeft ? '1px solid var(--sq-border)' : undefined,
          borderInlineEnd: isLeft ? undefined : '1px solid var(--sq-border)',
          boxShadow: isLeft
            ? '8px 0 40px color-mix(in srgb, var(--sq-fg) 18%, transparent)'
            : '-8px 0 40px color-mix(in srgb, var(--sq-fg) 18%, transparent)',
          overflow: 'hidden',
          animation: reduced
            ? 'none'
            : isLeft
              ? 'sq-slide-in-left 220ms ease-out'
              : 'sq-slide-in-right 220ms ease-out',
          ...style,
        }}
      >
        <header
          style={{
            padding: '16px 16px 0 24px',
            display: 'flex',
            alignItems: 'flex-start',
            justifyContent: 'space-between',
            gap: 16,
          }}
        >
          {title ? (
            <h2 id={titleId} style={{ margin: 0, fontSize: 18, fontWeight: 700 }}>
              {title}
            </h2>
          ) : (
            <span aria-hidden="true" />
          )}
          <button type="button" aria-label="Close panel" onClick={onClose} style={closeButtonStyle}>
            <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
              <path d="M4 4 L12 12 M12 4 L4 12" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
            </svg>
          </button>
        </header>
        {description ? (
          <p id={descriptionId} style={{ margin: 0, padding: '6px 24px 0', fontSize: 14, color: 'var(--sq-fg-muted)' }}>
            {description}
          </p>
        ) : null}
        {children ? (
          <div style={{ padding: '16px 24px 24px', overflowY: 'auto', flex: 1 }}>{children}</div>
        ) : null}
        {footer ? (
          <footer style={{ padding: '0 24px 20px', display: 'flex', justifyContent: 'flex-end', gap: 10 }}>
            {footer}
          </footer>
        ) : null}
      </div>
    </div>,
    document.body,
  );
}