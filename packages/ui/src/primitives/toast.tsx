'use client';

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import type { CSSProperties, ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { ensureKeyframes, usePortalMounted, usePrefersReducedMotion } from './internal';

export type ToastVariant = 'success' | 'error' | 'info' | 'warning';

export interface ToastOptions {
  title?: ReactNode;
  message: ReactNode;
}

export type ToastInput = string | ToastOptions;

interface ToastItem {
  id: string;
  variant: ToastVariant;
  title?: ReactNode;
  message: ReactNode;
}

interface ToastApi {
  success: (input: ToastInput) => void;
  error: (input: ToastInput) => void;
  info: (input: ToastInput) => void;
  warning: (input: ToastInput) => void;
}

interface ToastContextValue {
  toast: ToastApi;
}

const variantColors: Record<ToastVariant, string> = {
  success: 'var(--sq-success)',
  error: 'var(--sq-critical)',
  info: 'var(--sq-information)',
  warning: 'var(--sq-warning)',
};

const icons: Record<ToastVariant, ReactNode> = {
  success: (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
      <path d="M3 8.5 L6.5 12 L13 4.5" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  ),
  error: (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
      <path d="M4 4 L12 12 M12 4 L4 12" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
    </svg>
  ),
  info: (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
      <circle cx="8" cy="8" r="6" stroke="currentColor" strokeWidth="1.6" />
      <path d="M8 7.5 L8 11 M8 5 L8 5.25" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
    </svg>
  ),
  warning: (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
      <path d="M8 2.5 L14.5 13.5 L1.5 13.5 Z" stroke="currentColor" strokeWidth="1.6" strokeLinejoin="round" />
      <path d="M8 6.5 L8 9.75 M8 11.5 L8 11.75" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
    </svg>
  ),
};

const closeButtonStyle: CSSProperties = {
  display: 'inline-flex',
  alignItems: 'center',
  justifyContent: 'center',
  width: 24,
  height: 24,
  flexShrink: 0,
  padding: 0,
  border: 'none',
  borderRadius: 6,
  backgroundColor: 'transparent',
  color: 'var(--sq-fg-muted)',
  cursor: 'pointer',
};

const TOAST_DURATION = 5000;
const MAX_TOASTS = 4;

const ToastContext = createContext<ToastContextValue | undefined>(undefined);

ensureKeyframes();

interface ToastViewportProps {
  toasts: ToastItem[];
  onDismiss: (id: string) => void;
}

function ToastViewport({ toasts, onDismiss }: ToastViewportProps) {
  const mounted = usePortalMounted();
  const reduced = usePrefersReducedMotion();

  if (!mounted) return null;

  return createPortal(
    <div
      role="region"
      aria-label="Notifications"
      style={{
        position: 'fixed',
        right: 16,
        bottom: 16,
        zIndex: 9999,
        display: 'flex',
        flexDirection: 'column',
        gap: 10,
        width: 'min(420px, calc(100vw - 32px))',
        pointerEvents: 'none',
      }}
    >
      {toasts.map((toast) => (
        <div
          key={toast.id}
          role="region"
          aria-label={toast.variant}
          aria-live={toast.variant === 'error' ? 'assertive' : 'polite'}
          style={{
            display: 'flex',
            alignItems: 'flex-start',
            gap: 12,
            pointerEvents: 'auto',
            padding: '12px 14px',
            borderRadius: 10,
            backgroundColor: 'var(--sq-surface)',
            color: 'var(--sq-fg)',
            border: '1px solid',
            borderColor: 'var(--sq-border)',
            borderLeft: `3px solid ${variantColors[toast.variant]}`,
            boxShadow: '0 8px 24px color-mix(in srgb, var(--sq-fg) 16%, transparent)',
            animation: reduced ? 'none' : 'sq-toast-in 180ms ease-out',
          }}
        >
          <span aria-hidden="true" style={{ marginTop: 2, color: variantColors[toast.variant], flexShrink: 0 }}>
            {icons[toast.variant]}
          </span>
          <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 2, fontSize: 13 }}>
            {toast.title ? <strong style={{ fontSize: 14, fontWeight: 600 }}>{toast.title}</strong> : null}
            <span>{toast.message}</span>
          </div>
          <button type="button" aria-label="Dismiss notification" onClick={() => onDismiss(toast.id)} style={closeButtonStyle}>
            <svg width="12" height="12" viewBox="0 0 12 12" fill="none" aria-hidden="true">
              <path d="M3 3 L9 9 M9 3 L3 9" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
            </svg>
          </button>
        </div>
      ))}
    </div>,
    document.body,
  );
}

export interface ToastProviderProps {
  children: ReactNode;
}

export function ToastProvider({ children }: ToastProviderProps) {
  const [toasts, setToasts] = useState<ToastItem[]>([]);
  const counterRef = useRef(0);
  const mountedRef = useRef(true);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

  const dismiss = useCallback((id: string) => {
    setToasts((prev) => prev.filter((toast) => toast.id !== id));
  }, []);

  const push = useCallback(
    (variant: ToastVariant, input: ToastInput) => {
      const id = `sq-toast-${performance.now()}-${++counterRef.current}`;
      const item: ToastItem =
        typeof input === 'string'
          ? { id, variant, message: input }
          : { id, variant, title: input.title, message: input.message };
      setToasts((prev) => [...prev.slice(-(MAX_TOASTS - 1)), item]);
      window.setTimeout(() => {
        if (mountedRef.current) dismiss(id);
      }, TOAST_DURATION);
    },
    [dismiss],
  );

  const api = useMemo<ToastContextValue>(
    () => ({
      toast: {
        success: (input) => push('success', input),
        error: (input) => push('error', input),
        info: (input) => push('info', input),
        warning: (input) => push('warning', input),
      },
    }),
    [push],
  );

  return (
    <ToastContext.Provider value={api}>
      {children}
      <ToastViewport toasts={toasts} onDismiss={dismiss} />
    </ToastContext.Provider>
  );
}

export function useToast(): ToastContextValue {
  const context = useContext(ToastContext);
  if (!context) throw new Error('useToast must be used within a ToastProvider');
  return context;
}