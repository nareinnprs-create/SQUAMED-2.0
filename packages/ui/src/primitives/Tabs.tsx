'use client';

import { createContext, useContext, useId, useMemo } from 'react';
import type { ButtonHTMLAttributes, CSSProperties, KeyboardEvent, ReactNode } from 'react';
import { useControllableState, useFocusVisible, usePrefersReducedMotion } from './internal';

interface TabsContextValue {
  baseId: string;
  value: string;
  setValue: (value: string) => void;
}

const TabsContext = createContext<TabsContextValue | undefined>(undefined);

export interface TabsProps {
  value?: string;
  defaultValue?: string;
  onValueChange?: (value: string) => void;
  children: ReactNode;
  className?: string;
  style?: CSSProperties;
}

export function Tabs({ value, defaultValue, onValueChange, children, className, style }: TabsProps) {
  const baseId = useId();
  const [currentValue, setCurrentValue] = useControllableState(value, defaultValue ?? '', onValueChange);
  const contextValue = useMemo<TabsContextValue>(
    () => ({ baseId, value: currentValue, setValue: setCurrentValue }),
    [baseId, currentValue, setCurrentValue],
  );

  return (
    <TabsContext.Provider value={contextValue}>
      <div className={className} style={style}>
        {children}
      </div>
    </TabsContext.Provider>
  );
}

export interface TabsListProps {
  children: ReactNode;
  className?: string;
  style?: CSSProperties;
}

export function TabsList({ children, className, style }: TabsListProps) {
  const context = useContext(TabsContext);
  if (!context) throw new Error('TabsList must be used within Tabs');

  const handleKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    const list = event.currentTarget;
    const tabs = Array.from(list.querySelectorAll<HTMLButtonElement>('button[role="tab"]'));
    if (tabs.length === 0) return;
    const currentIndex = tabs.findIndex((tab) => tab === document.activeElement);
    const current = currentIndex === -1 ? 0 : currentIndex;
    let nextIndex = current;
    if (event.key === 'ArrowRight' || event.key === 'ArrowDown') {
      nextIndex = (current + 1) % tabs.length;
    } else if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') {
      nextIndex = (current - 1 + tabs.length) % tabs.length;
    } else if (event.key === 'Home') {
      nextIndex = 0;
    } else if (event.key === 'End') {
      nextIndex = tabs.length - 1;
    } else {
      return;
    }
    event.preventDefault();
    const nextTab = tabs[nextIndex];
    if (nextTab) {
      nextTab.focus();
      const nextValue = nextTab.getAttribute('data-value');
      if (nextValue) context.setValue(nextValue);
    }
  };

  return (
    <div
      role="tablist"
      className={className}
      onKeyDown={handleKeyDown}
      style={{
        display: 'inline-flex',
        gap: 4,
        padding: 4,
        borderRadius: 10,
        backgroundColor: 'var(--sq-surface)',
        border: '1px solid var(--sq-border)',
        ...style,
      }}
    >
      {children}
    </div>
  );
}

export interface TabsTriggerProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'value'> {
  value: string;
  children: ReactNode;
}

export function TabsTrigger({ value, children, className, style, onClick, ...rest }: TabsTriggerProps) {
  const context = useContext(TabsContext);
  if (!context) throw new Error('TabsTrigger must be used within Tabs');
  const selected = context.value === value;
  const tabId = `${context.baseId}-tab-${value}`;
  const panelId = `${context.baseId}-panel-${value}`;
  const reduced = usePrefersReducedMotion();
  const { focusVisible, onPointerDown, onFocus, onBlur } = useFocusVisible();

  return (
    <button
      {...rest}
      type="button"
      role="tab"
      id={tabId}
      data-value={value}
      aria-selected={selected}
      aria-controls={panelId}
      tabIndex={selected ? 0 : -1}
      onClick={(event) => {
        context.setValue(value);
        onClick?.(event);
      }}
      onPointerDown={onPointerDown}
      onFocus={onFocus}
      onBlur={onBlur}
      className={className}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        height: 32,
        padding: '0 14px',
        borderRadius: 7,
        border: 'none',
        fontFamily: 'inherit',
        fontSize: 13,
        fontWeight: 600,
        whiteSpace: 'nowrap',
        color: selected ? 'var(--sq-primary)' : 'var(--sq-fg-muted)',
        backgroundColor: selected ? 'var(--sq-primary-soft)' : 'transparent',
        cursor: 'pointer',
        transition: reduced ? 'none' : 'background-color 150ms ease, color 150ms ease',
        ...(focusVisible ? { outline: '2px solid var(--sq-focus-ring)', outlineOffset: 2 } : null),
        ...style,
      }}
    >
      {children}
    </button>
  );
}

export interface TabsContentProps {
  value: string;
  children: ReactNode;
  className?: string;
  style?: CSSProperties;
}

export function TabsContent({ value, children, className, style }: TabsContentProps) {
  const context = useContext(TabsContext);
  if (!context) throw new Error('TabsContent must be used within Tabs');
  const selected = context.value === value;
  const tabId = `${context.baseId}-tab-${value}`;
  const panelId = `${context.baseId}-panel-${value}`;

  return (
    <div
      id={panelId}
      role="tabpanel"
      aria-labelledby={tabId}
      tabIndex={0}
      hidden={!selected}
      className={className}
      style={{ outline: 'none', ...style }}
    >
      {selected ? children : null}
    </div>
  );
}