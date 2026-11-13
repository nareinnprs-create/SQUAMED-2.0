'use client';

import { useCallback, useEffect, useRef, useState, type CSSProperties, type RefObject } from 'react';

export type FieldSize = 'sm' | 'md' | 'lg';

export const fieldBase: CSSProperties = {
  width: '100%',
  boxSizing: 'border-box',
  backgroundColor: 'var(--sq-surface)',
  color: 'var(--sq-fg)',
  fontFamily: 'inherit',
  border: '1px solid',
};

export const fieldSizes: Record<FieldSize, CSSProperties> = {
  sm: { height: 32, fontSize: 13, paddingLeft: 12, paddingRight: 12, borderRadius: 6 },
  md: { height: 38, fontSize: 14, paddingLeft: 14, paddingRight: 14, borderRadius: 8 },
  lg: { height: 46, fontSize: 16, paddingLeft: 16, paddingRight: 16, borderRadius: 10 },
};

export const FOCUSABLE_SELECTOR =
  'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

let keyframesInjected = false;

export function ensureKeyframes(): void {
  if (keyframesInjected || typeof document === 'undefined') return;
  keyframesInjected = true;
  const styleEl = document.createElement('style');
  styleEl.textContent = [
    '@keyframes sq-spin{to{transform:rotate(360deg)}}',
    '@keyframes sq-fade-in{from{opacity:0}to{opacity:1}}',
    '@keyframes sq-slide-up{from{opacity:0;transform:translateY(16px)}to{opacity:1;transform:translateY(0)}}',
    '@keyframes sq-slide-in-left{from{opacity:0;transform:translateX(-24px)}to{opacity:1;transform:translateX(0)}}',
    '@keyframes sq-slide-in-right{from{opacity:0;transform:translateX(24px)}to{opacity:1;transform:translateX(0)}}',
    '@keyframes sq-scale-in{from{opacity:0;transform:scale(.96) translateY(4px)}to{opacity:1;transform:scale(1) translateY(0)}}',
    '@keyframes sq-toast-in{from{opacity:0;transform:translateY(8px) scale(.98)}to{opacity:1;transform:translateY(0) scale(1)}}',
  ].join('\n');
  document.head.appendChild(styleEl);
}

export function usePortalMounted(): boolean {
  const [mounted, setMounted] = useState(false);
  useEffect(() => {
    setMounted(true);
  }, []);
  return mounted;
}

export function usePrefersReducedMotion(): boolean {
  const [reduced, setReduced] = useState(false);
  useEffect(() => {
    const mq = window.matchMedia('(prefers-reduced-motion: reduce)');
    setReduced(mq.matches);
    const onChange = (event: MediaQueryListEvent) => setReduced(event.matches);
    mq.addEventListener('change', onChange);
    return () => mq.removeEventListener('change', onChange);
  }, []);
  return reduced;
}

export function useFocusVisible() {
  const [focusVisible, setFocusVisible] = useState(false);
  const pointerRef = useRef(false);

  useEffect(() => {
    const resetPointer = () => {
      pointerRef.current = false;
    };
    window.addEventListener('keydown', resetPointer);
    return () => window.removeEventListener('keydown', resetPointer);
  }, []);

  const onPointerDown = useCallback(() => {
    pointerRef.current = true;
    setFocusVisible(false);
  }, []);

  const onFocus = useCallback(() => {
    if (!pointerRef.current) setFocusVisible(true);
  }, []);

  const onBlur = useCallback(() => {
    setFocusVisible(false);
  }, []);

  return { focusVisible, onPointerDown, onFocus, onBlur };
}

export function useFocusTrap(ref: RefObject<HTMLElement | null>, active: boolean): void {
  useEffect(() => {
    if (!active) return;
    const container = ref.current;
    if (!container) return;
    const getFocusables = () => Array.from(container.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR));
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key !== 'Tab') return;
      const items = getFocusables();
      if (items.length === 0) {
        event.preventDefault();
        return;
      }
      const first = items[0];
      const last = items[items.length - 1];
      const activeElement = document.activeElement;
      if (event.shiftKey) {
        if (activeElement === first || !container.contains(activeElement)) {
          event.preventDefault();
          last.focus();
        }
      } else if (activeElement === last || !container.contains(activeElement)) {
        event.preventDefault();
        first.focus();
      }
    };
    container.addEventListener('keydown', onKeyDown);
    return () => container.removeEventListener('keydown', onKeyDown);
  }, [active, ref]);
}

export function useBodyScrollLock(active: boolean): void {
  useEffect(() => {
    if (!active) return;
    const previous = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = previous;
    };
  }, [active]);
}

export function useEscapeKey(active: boolean, onEscape: () => void): void {
  useEffect(() => {
    if (!active) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onEscape();
    };
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [active, onEscape]);
}

export function useFocusRestore(active: boolean, ref: RefObject<HTMLElement | null>): void {
  const previousRef = useRef<HTMLElement | null>(null);

  useEffect(() => {
    if (!active) return;
    const container = ref.current;
    previousRef.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    if (!container) return;
    const rafId = window.requestAnimationFrame(() => {
      const first = container.querySelector<HTMLElement>(FOCUSABLE_SELECTOR);
      const target = first ?? container;
      target.focus();
    });
    return () => {
      window.cancelAnimationFrame(rafId);
      previousRef.current?.focus();
    };
  }, [active, ref]);
}

export function useClickOutside(ref: RefObject<HTMLElement | null>, active: boolean, onOutside: () => void): void {
  useEffect(() => {
    if (!active) return;
    const onPointerDown = (event: PointerEvent) => {
      const el = ref.current;
      if (el && event.target instanceof Node && !el.contains(event.target)) onOutside();
    };
    document.addEventListener('pointerdown', onPointerDown);
    return () => document.removeEventListener('pointerdown', onPointerDown);
  }, [active, ref, onOutside]);
}

export function useControllableState<T>(
  value: T | undefined,
  defaultValue: T,
  onChange?: (next: T) => void,
): [T, (next: T | ((prev: T) => T)) => void] {
  const [internal, setInternal] = useState<T>(defaultValue);
  const isControlled = value !== undefined;

  useEffect(() => {
    if (isControlled) setInternal(value as T);
  }, [isControlled, value]);

  const setValue = useCallback(
    (next: T | ((prev: T) => T)) => {
      const resolved = typeof next === 'function' ? (next as (prev: T) => T)(internal) : next;
      setInternal(resolved);
      onChange?.(resolved);
    },
    [internal, onChange],
  );

  return [isControlled ? (value as T) : internal, setValue];
}