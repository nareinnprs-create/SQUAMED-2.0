'use client';

import { useId, useMemo, useRef, useState } from 'react';
import type { CSSProperties, KeyboardEvent as ReactKeyboardEvent, ReactNode } from 'react';
import { cn } from '../lib/cn';
import { useFocusVisible } from './internal';

export interface CommandSuggestion {
  id: string;
  label: string;
  icon?: ReactNode;
  onSelect?: (suggestion: CommandSuggestion) => void;
}

export interface CommandBarProps {
  placeholder?: string;
  value: string;
  onChange: (value: string) => void;
  suggestions?: CommandSuggestion[];
  onSubmit?: (value: string) => void;
  className?: string;
  style?: CSSProperties;
}

export function CommandBar({
  placeholder,
  value,
  onChange,
  suggestions = [],
  onSubmit,
  className,
  style,
}: CommandBarProps) {
  const baseId = useId();
  const listboxId = `${baseId}-listbox`;
  const containerRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const focus = useFocusVisible();
  const [open, setOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(0);

  const query = value.trim().toLowerCase();

  const filtered = useMemo(() => {
    if (!query) return suggestions;
    return suggestions.filter((suggestion) => suggestion.label.toLowerCase().includes(query));
  }, [suggestions, query]);

  const handleSelect = (suggestion: CommandSuggestion) => {
    onChange(suggestion.label);
    suggestion.onSelect?.(suggestion);
    setOpen(false);
    setActiveIndex(0);
    inputRef.current?.focus();
  };

  const handleKeyDown = (event: ReactKeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      setOpen(true);
      setActiveIndex((index) => (index + 1) % filtered.length);
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      setOpen(true);
      setActiveIndex((index) => (index - 1 + filtered.length) % filtered.length);
    } else if (event.key === 'Enter') {
      const suggestion = filtered[activeIndex];
      if (open && suggestion) {
        event.preventDefault();
        handleSelect(suggestion);
      } else {
        onSubmit?.(value);
      }
    } else if (event.key === 'Escape') {
      event.preventDefault();
      setOpen(false);
      setActiveIndex(0);
    }
  };

  const activeSuggestion = open ? filtered[activeIndex] : undefined;

  return (
    <div
      ref={containerRef}
      className={cn(className)}
      style={{ position: 'relative', width: '100%', boxSizing: 'border-box', ...style }}
    >
      <input
        ref={inputRef}
        role="combobox"
        aria-expanded={open}
        aria-controls={listboxId}
        aria-autocomplete="list"
        aria-activedescendant={activeSuggestion ? `${baseId}-option-${activeIndex}` : undefined}
        value={value}
        placeholder={placeholder}
        onChange={(event) => {
          onChange(event.target.value);
          setOpen(true);
          setActiveIndex(0);
        }}
        onKeyDown={handleKeyDown}
        onPointerDown={focus.onPointerDown}
        onFocus={focus.onFocus}
        onBlur={(event) => {
          focus.onBlur();
          if (!containerRef.current?.contains(event.relatedTarget as Node | null)) setOpen(false);
        }}
        style={{
          width: '100%',
          height: 38,
          boxSizing: 'border-box',
          padding: '0 14px',
          borderRadius: 8,
          backgroundColor: 'var(--sq-surface)',
          color: 'var(--sq-fg)',
          fontFamily: 'inherit',
          fontSize: 14,
          border: '1px solid',
          borderColor: focus.focusVisible ? 'var(--sq-primary)' : 'var(--sq-border)',
          boxShadow: focus.focusVisible ? '0 0 0 3px var(--sq-focus-ring)' : undefined,
          outline: 'none',
          transition: 'border-color 150ms ease, box-shadow 150ms ease',
        }}
      />
      {open && filtered.length > 0 ? (
        <ul
          id={listboxId}
          role="listbox"
          aria-label="Suggestions"
          style={{
            position: 'absolute',
            top: 'calc(100% + 6px)',
            left: 0,
            right: 0,
            margin: 0,
            padding: 6,
            listStyle: 'none',
            boxSizing: 'border-box',
            maxHeight: 240,
            overflowY: 'auto',
            backgroundColor: 'var(--sq-surface)',
            border: '1px solid var(--sq-border)',
            borderRadius: 8,
            boxShadow: 'var(--sq-shadow-md)',
            zIndex: 50,
          }}
        >
          {filtered.map((suggestion, index) => {
            const isActive = index === activeIndex;
            return (
              <li
                key={suggestion.id}
                id={`${baseId}-option-${index}`}
                role="option"
                aria-selected={isActive}
                onPointerDown={(event) => {
                  event.preventDefault();
                  handleSelect(suggestion);
                }}
                onPointerEnter={() => setActiveIndex(index)}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 8,
                  padding: '8px 10px',
                  borderRadius: 6,
                  cursor: 'pointer',
                  fontSize: 13,
                  color: isActive ? 'var(--sq-primary)' : 'var(--sq-fg)',
                  backgroundColor: isActive ? 'var(--sq-primary-soft)' : 'transparent',
                }}
              >
                {suggestion.icon ? (
                  <span style={{ display: 'inline-flex', flexShrink: 0, color: 'var(--sq-fg-muted)' }}>
                    {suggestion.icon}
                  </span>
                ) : null}
                {suggestion.label}
              </li>
            );
          })}
        </ul>
      ) : null}
    </div>
  );
}