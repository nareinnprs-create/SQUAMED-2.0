'use client';

import { createContext, useCallback, useContext, useId, useMemo } from 'react';
import type { ButtonHTMLAttributes, CSSProperties, ReactNode } from 'react';
import { useControllableState, useFocusVisible, usePrefersReducedMotion } from './internal';

export type AccordionType = 'single' | 'multiple';

function toValues(value: string | string[] | undefined): string[] {
  if (value === undefined) return [];
  return Array.isArray(value) ? value : [value];
}

interface AccordionContextValue {
  values: string[];
  toggle: (value: string) => void;
  baseId: string;
}

const AccordionContext = createContext<AccordionContextValue | undefined>(undefined);

interface AccordionItemContextValue {
  itemValue: string;
  isOpen: boolean;
}

const AccordionItemContext = createContext<AccordionItemContextValue | undefined>(undefined);

export interface AccordionProps {
  type?: AccordionType;
  value?: string | string[];
  defaultValue?: string | string[];
  onValueChange?: (value: string | string[]) => void;
  children: ReactNode;
}

export function Accordion({ type = 'single', value, defaultValue, onValueChange, children }: AccordionProps) {
  const baseId = useId();
  const controlledValue = value === undefined ? undefined : toValues(value);
  const [values, setValues] = useControllableState<string[]>(
    controlledValue,
    toValues(defaultValue),
    (next) => onValueChange?.(next),
  );

  const toggle = useCallback(
    (itemValue: string) => {
      setValues((prev) => {
        const isOpen = prev.includes(itemValue);
        if (type === 'single') return isOpen ? [] : [itemValue];
        return isOpen ? prev.filter((item) => item !== itemValue) : [...prev, itemValue];
      });
    },
    [setValues, type],
  );

  const contextValue = useMemo<AccordionContextValue>(
    () => ({ values, toggle, baseId }),
    [values, toggle, baseId],
  );

  return <AccordionContext.Provider value={contextValue}>{children}</AccordionContext.Provider>;
}

export interface AccordionItemProps {
  value: string;
  children: ReactNode;
  className?: string;
  style?: CSSProperties;
}

export function AccordionItem({ value, children, className, style }: AccordionItemProps) {
  const accordion = useContext(AccordionContext);
  if (!accordion) throw new Error('AccordionItem must be used within Accordion');
  const isOpen = accordion.values.includes(value);
  const contextValue = useMemo<AccordionItemContextValue>(
    () => ({ itemValue: value, isOpen }),
    [value, isOpen],
  );

  return (
    <div className={className} style={{ borderBottom: '1px solid var(--sq-border)', ...style }}>
      <AccordionItemContext.Provider value={contextValue}>{children}</AccordionItemContext.Provider>
    </div>
  );
}

export interface AccordionTriggerProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  children: ReactNode;
}

export function AccordionTrigger({ children, onClick, className, style, ...rest }: AccordionTriggerProps) {
  const accordion = useContext(AccordionContext);
  const item = useContext(AccordionItemContext);
  if (!accordion || !item) throw new Error('AccordionTrigger must be used within AccordionItem');
  const headerId = `${accordion.baseId}-header-${item.itemValue}`;
  const panelId = `${accordion.baseId}-panel-${item.itemValue}`;
  const reduced = usePrefersReducedMotion();
  const { focusVisible, onPointerDown, onFocus, onBlur } = useFocusVisible();

  return (
    <div className={className} style={style}>
      <button
        {...rest}
        type="button"
        id={headerId}
        aria-expanded={item.isOpen}
        aria-controls={panelId}
        onClick={(event) => {
          accordion.toggle(item.itemValue);
          onClick?.(event);
        }}
        onPointerDown={onPointerDown}
        onFocus={onFocus}
        onBlur={onBlur}
        style={{
          width: '100%',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 12,
          padding: '14px 4px',
          border: 'none',
          background: 'none',
          fontFamily: 'inherit',
          fontSize: 14,
          fontWeight: 600,
          color: 'var(--sq-fg)',
          textAlign: 'start',
          cursor: 'pointer',
          ...(focusVisible ? { outline: '2px solid var(--sq-focus-ring)', outlineOffset: 2 } : null),
        }}
      >
        <span>{children}</span>
        <svg
          width="16"
          height="16"
          viewBox="0 0 16 16"
          fill="none"
          aria-hidden="true"
          style={{
            flexShrink: 0,
            color: 'var(--sq-fg-muted)',
            transform: item.isOpen ? 'rotate(180deg)' : 'rotate(0deg)',
            transition: reduced ? 'none' : 'transform 180ms ease',
          }}
        >
          <path d="M4 6 L8 10 L12 6" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      </button>
    </div>
  );
}

export interface AccordionContentProps {
  children: ReactNode;
}

export function AccordionContent({ children }: AccordionContentProps) {
  const accordion = useContext(AccordionContext);
  const item = useContext(AccordionItemContext);
  if (!accordion || !item) throw new Error('AccordionContent must be used within AccordionItem');
  const headerId = `${accordion.baseId}-header-${item.itemValue}`;
  const panelId = `${accordion.baseId}-panel-${item.itemValue}`;

  return (
    <div id={panelId} role="region" aria-labelledby={headerId} hidden={!item.isOpen}>
      <div style={{ padding: '2px 4px 14px' }}>{item.isOpen ? children : null}</div>
    </div>
  );
}