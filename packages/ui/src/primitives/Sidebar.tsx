'use client';

import type { CSSProperties, ReactNode } from 'react';
import { cn } from '../lib/cn';
import { useFocusVisible } from './internal';

export interface SidebarItem {
  id: string;
  label: string;
  icon?: ReactNode;
  href?: string;
  section?: string;
  badge?: ReactNode;
  onClick?: () => void;
}

export interface SidebarProps {
  items: SidebarItem[];
  activeId?: string;
  onActiveChange?: (id: string) => void;
  collapsed?: boolean;
  onCollapsedChange?: (collapsed: boolean) => void;
  className?: string;
  style?: CSSProperties;
}

interface SidebarItemRowProps {
  item: SidebarItem;
  active: boolean;
  collapsed: boolean;
  onActivate: (id: string) => void;
}

function SidebarItemRow({ item, active, collapsed, onActivate }: SidebarItemRowProps) {
  const focus = useFocusVisible();

  const handleClick = () => {
    onActivate(item.id);
    item.onClick?.();
  };

  const commonStyle: CSSProperties = {
    display: 'flex',
    alignItems: 'center',
    justifyContent: collapsed ? 'center' : 'flex-start',
    gap: 10,
    width: '100%',
    height: 38,
    boxSizing: 'border-box',
    padding: collapsed ? '0' : '0 12px',
    border: 'none',
    borderRadius: 8,
    backgroundColor: active ? 'var(--sq-primary-soft)' : 'transparent',
    color: active ? 'var(--sq-primary)' : 'var(--sq-fg)',
    fontFamily: 'inherit',
    fontSize: 14,
    fontWeight: active ? 600 : 500,
    textDecoration: 'none',
    cursor: 'pointer',
    whiteSpace: 'nowrap',
    ...(focus.focusVisible ? { outline: '2px solid var(--sq-focus-ring)', outlineOffset: 2 } : null),
  };

  const content = (
    <>
      {item.icon ? <span style={{ display: 'inline-flex', flexShrink: 0 }}>{item.icon}</span> : null}
      {!collapsed ? (
        <span style={{ flex: 1, textAlign: 'left', overflow: 'hidden', textOverflow: 'ellipsis' }}>
          {item.label}
        </span>
      ) : null}
      {!collapsed && item.badge ? (
        <span
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            minWidth: 18,
            height: 18,
            padding: '0 5px',
            borderRadius: 999,
            backgroundColor: 'var(--sq-secondary)',
            color: 'var(--sq-fg-muted)',
            fontSize: 11,
            fontWeight: 600,
            lineHeight: 1,
          }}
        >
          {item.badge}
        </span>
      ) : null}
    </>
  );

  if (item.href) {
    return (
      <a
        href={item.href}
        title={collapsed ? item.label : undefined}
        onClick={() => {
          handleClick();
        }}
        onPointerDown={focus.onPointerDown}
        onFocus={focus.onFocus}
        onBlur={focus.onBlur}
        style={commonStyle}
      >
        {content}
      </a>
    );
  }

  return (
    <button
      type="button"
      title={collapsed ? item.label : undefined}
      onClick={() => {
        handleClick();
      }}
      onPointerDown={focus.onPointerDown}
      onFocus={focus.onFocus}
      onBlur={focus.onBlur}
      style={commonStyle}
    >
      {content}
    </button>
  );
}

export function Sidebar({
  items,
  activeId,
  onActiveChange,
  collapsed = false,
  onCollapsedChange,
  className,
  style,
}: SidebarProps) {
  const collapseFocus = useFocusVisible();

  const groups = buildGroups(items);

  return (
    <nav
      aria-label="Sidebar navigation"
      className={cn(className)}
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 4,
        width: collapsed ? 64 : 240,
        height: '100%',
        boxSizing: 'border-box',
        padding: '12px 8px',
        backgroundColor: 'var(--sq-surface)',
        borderRight: '1px solid var(--sq-border)',
        transition: 'width 180ms ease',
        flexShrink: 0,
        ...style,
      }}
    >
      <div style={{ display: 'flex', justifyContent: collapsed ? 'center' : 'flex-end', paddingBottom: 8 }}>
        <button
          type="button"
          aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
          onClick={() => onCollapsedChange?.(!collapsed)}
          onPointerDown={collapseFocus.onPointerDown}
          onFocus={collapseFocus.onFocus}
          onBlur={collapseFocus.onBlur}
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            width: 30,
            height: 30,
            padding: 0,
            border: 'none',
            borderRadius: 8,
            backgroundColor: 'transparent',
            color: 'var(--sq-fg-muted)',
            cursor: 'pointer',
            ...(collapseFocus.focusVisible ? { outline: '2px solid var(--sq-focus-ring)', outlineOffset: 2 } : null),
          }}
        >
          <svg
            width="16"
            height="16"
            viewBox="0 0 16 16"
            fill="none"
            aria-hidden="true"
            style={{ transform: collapsed ? 'rotate(180deg)' : undefined, transition: 'transform 180ms ease' }}
          >
            <path d="M10 3 L5.5 8 L10 13" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        </button>
      </div>
      <div style={{ flex: 1, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 4 }}>
        {groups.map((group) => (
          <div key={group.section || '__root__'} style={{ display: 'flex', flexDirection: 'column', gap: 2, marginTop: group.section ? 10 : 0 }}>
            {group.section && !collapsed ? (
              <div
                style={{
                  padding: '4px 12px 6px',
                  fontSize: 11,
                  fontWeight: 700,
                  textTransform: 'uppercase',
                  letterSpacing: '0.06em',
                  color: 'var(--sq-fg-muted)',
                }}
              >
                {group.section}
              </div>
            ) : null}
            {group.items.map((item) => (
              <SidebarItemRow
                key={item.id}
                item={item}
                active={item.id === activeId}
                collapsed={collapsed}
                onActivate={(id) => onActiveChange?.(id)}
              />
            ))}
          </div>
        ))}
      </div>
    </nav>
  );
}

function buildGroups(items: SidebarItem[]): { section: string; items: SidebarItem[] }[] {
  const groups: { section: string; items: SidebarItem[] }[] = [];
  const indexBySection = new Map<string, number>();
  for (const item of items) {
    const section = item.section ?? '';
    const groupIndex = indexBySection.get(section);
    if (groupIndex === undefined) {
      indexBySection.set(section, groups.length);
      groups.push({ section, items: [item] });
    } else {
      groups[groupIndex].items.push(item);
    }
  }
  return groups;
}