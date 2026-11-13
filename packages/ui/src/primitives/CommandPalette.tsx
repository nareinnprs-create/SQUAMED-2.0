'use client';

import { useEffect, useId, useMemo, useRef, useState } from 'react';
import type { CSSProperties, KeyboardEvent as ReactKeyboardEvent, ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { cn } from '../lib/cn';
import { ensureKeyframes, useEscapeKey, usePortalMounted } from './internal';

export interface CommandPaletteItem {
  id: string;
  label: string;
  description?: string;
  keywords?: string;
  icon?: ReactNode;
  onSelect?: (item: CommandPaletteItem) => void;
  section?: string;
}

export interface CommandPaletteProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  items: CommandPaletteItem[];
  className?: string;
  style?: CSSProperties;
}

ensureKeyframes();

interface CommandPaletteGroup {
  section: string;
  items: { item: CommandPaletteItem; index: number }[];
}

export function CommandPalette({ open, onOpenChange, items, className, style }: CommandPaletteProps) {
  const baseId = useId();
  const listboxId = `${baseId}-listbox`;
  const mounted = usePortalMounted();
  const searchRef = useRef<HTMLInputElement>(null);
  const listRef = useRef<HTMLDivElement>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [activeIndex, setActiveIndex] = useState(0);

  const query = searchQuery.trim().toLowerCase();

  const filtered = useMemo(() => {
    if (!query) return items;
    return items.filter((item) => {
      const haystack = `${item.label} ${item.keywords ?? ''}`.toLowerCase();
      return haystack.includes(query);
    });
  }, [items, query]);

  const groups = useMemo<CommandPaletteGroup[]>(() => {
    const result: CommandPaletteGroup[] = [];
    const indexBySection = new Map<string, number>();
    for (let index = 0; index < filtered.length; index++) {
      const item = filtered[index];
      const section = item.section ?? '';
      const groupIndex = indexBySection.get(section);
      if (groupIndex === undefined) {
        indexBySection.set(section, result.length);
        result.push({ section, items: [] });
        result[result.length - 1].items.push({ item, index });
      } else {
        result[groupIndex].items.push({ item, index });
      }
    }
    return result;
  }, [filtered]);

  const previousOpen = useRef(open);
  if (open !== previousOpen.current) {
    previousOpen.current = open;
    if (open) {
      setSearchQuery('');
      setActiveIndex(0);
    }
  }

  useEffect(() => {
    if (!open) return;
    const raf = window.requestAnimationFrame(() => searchRef.current?.focus());
    return () => window.cancelAnimationFrame(raf);
  }, [open]);

  useEffect(() => {
    const el = listRef.current?.querySelector<HTMLElement>(`#${baseId}-option-${activeIndex}`);
    el?.scrollIntoView({ block: 'nearest' });
  }, [activeIndex, baseId]);

  useEscapeKey(open, () => onOpenChange(false));

  if (!mounted || !open) return null;

  const handleSelect = (item: CommandPaletteItem) => {
    item.onSelect?.(item);
    setSearchQuery('');
    onOpenChange(false);
  };

  const handleKeyDown = (event: ReactKeyboardEvent<HTMLDivElement>) => {
    if (filtered.length === 0) return;
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      setActiveIndex((index) => (index + 1) % filtered.length);
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      setActiveIndex((index) => (index - 1 + filtered.length) % filtered.length);
    } else if (event.key === 'Home') {
      event.preventDefault();
      setActiveIndex(0);
    } else if (event.key === 'End') {
      event.preventDefault();
      setActiveIndex(filtered.length - 1);
    } else if (event.key === 'Enter') {
      event.preventDefault();
      const item = filtered[activeIndex];
      if (item) handleSelect(item);
    }
  };

  const activeItem = filtered[activeIndex];

  return createPortal(
    <div
      style={{
        position: 'fixed',
        inset: 0,
        zIndex: 1100,
        display: 'flex',
        alignItems: 'flex-start',
        justifyContent: 'center',
        padding: '12vh 16px 16px',
        boxSizing: 'border-box',
      }}
    >
      <div
        aria-hidden="true"
        onPointerDown={() => onOpenChange(false)}
        style={{
          position: 'absolute',
          inset: 0,
          backgroundColor: 'color-mix(in srgb, var(--sq-fg) 32%, transparent)',
        }}
      />
      <div
        role="dialog"
        aria-modal="true"
        aria-label="Command palette"
        className={cn(className)}
        style={{
          position: 'relative',
          width: 560,
          maxWidth: '100%',
          maxHeight: '70vh',
          display: 'flex',
          flexDirection: 'column',
          boxSizing: 'border-box',
          backgroundColor: 'var(--sq-surface)',
          color: 'var(--sq-fg)',
          border: '1px solid var(--sq-border)',
          borderRadius: 14,
          boxShadow: 'var(--sq-shadow-xl)',
          overflow: 'hidden',
          animation: 'sq-scale-in 150ms ease-out',
          ...style,
        }}
      >
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 10,
            padding: '14px 16px',
            borderBottom: '1px solid var(--sq-border)',
          }}
        >
          <svg
            width="16"
            height="16"
            viewBox="0 0 16 16"
            fill="none"
            aria-hidden="true"
            style={{ color: 'var(--sq-fg-muted)', flexShrink: 0 }}
          >
            <circle cx="7" cy="7" r="4.5" stroke="currentColor" strokeWidth="1.5" />
            <path d="M10.5 10.5 L13.5 13.5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
          </svg>
          <input
            ref={searchRef}
            value={searchQuery}
            onChange={(event) => {
              setSearchQuery(event.target.value);
              setActiveIndex(0);
            }}
            placeholder="Search commands..."
            role="combobox"
            aria-expanded="true"
            aria-controls={listboxId}
            aria-activedescendant={activeItem ? `${baseId}-option-${activeIndex}` : undefined}
            style={{
              flex: 1,
              minWidth: 0,
              border: 'none',
              outline: 'none',
              background: 'transparent',
              color: 'var(--sq-fg)',
              fontFamily: 'inherit',
              fontSize: 15,
            }}
          />
        </div>
        {filtered.length > 0 ? (
          <div
            ref={listRef}
            id={listboxId}
            role="listbox"
            aria-label="Commands"
            onKeyDown={handleKeyDown}
            style={{ overflowY: 'auto', flex: 1, padding: 8, minHeight: 160 }}
          >
            {groups.map((group) => (
              <div key={group.section || '__root__'}>
                {group.section ? (
                  <div
                    style={{
                      padding: '8px 10px 4px',
                      fontSize: 11,
                      fontWeight: 700,
                      textTransform: 'uppercase',
                      letterSpacing: '0.05em',
                      color: 'var(--sq-fg-muted)',
                    }}
                  >
                    {group.section}
                  </div>
                ) : null}
                {group.items.map(({ item, index }) => {
                  const isActive = index === activeIndex;
                  return (
                    <div
                      key={item.id}
                      id={`${baseId}-option-${index}`}
                      role="option"
                      aria-selected={isActive}
                      data-command-palette-option={index}
                      onPointerDown={(event) => {
                        event.preventDefault();
                        handleSelect(item);
                      }}
                      onPointerEnter={() => setActiveIndex(index)}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 10,
                        padding: '9px 10px',
                        borderRadius: 8,
                        cursor: 'pointer',
                        backgroundColor: isActive ? 'var(--sq-primary-soft)' : 'transparent',
                        color: isActive ? 'var(--sq-primary)' : 'var(--sq-fg)',
                      }}
                    >
                      {item.icon ? (
                        <span style={{ display: 'inline-flex', flexShrink: 0, color: 'var(--sq-fg-muted)' }}>
                          {item.icon}
                        </span>
                      ) : null}
                      <span style={{ flex: 1, minWidth: 0 }}>
                        <span style={{ display: 'block', fontSize: 14, fontWeight: 500, lineHeight: 1.4 }}>
                          {item.label}
                        </span>
                        {item.description ? (
                          <span
                            style={{
                              display: 'block',
                              fontSize: 12,
                              color: 'var(--sq-fg-muted)',
                              lineHeight: 1.4,
                            }}
                          >
                            {item.description}
                          </span>
                        ) : null}
                      </span>
                    </div>
                  );
                })}
              </div>
            ))}
          </div>
        ) : (
          <div
            style={{
              padding: 24,
              textAlign: 'center',
              fontSize: 13,
              color: 'var(--sq-fg-muted)',
            }}
          >
            No results found
          </div>
        )}
        <div
          style={{
            display: 'flex',
            gap: 16,
            padding: '10px 16px',
            borderTop: '1px solid var(--sq-border)',
            fontSize: 11,
            color: 'var(--sq-fg-muted)',
          }}
        >
          <span>&uarr;&darr; navigate</span>
          <span>&#8629; select</span>
          <span>esc close</span>
        </div>
      </div>
    </div>,
    document.body,
  );
}

export function useCommandShortcut(onOpen: () => void): void {
  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault();
        onOpen();
      }
    };
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [onOpen]);
}