import { useState, type ReactNode } from 'react';
import { cn } from '../lib/cn';
import { Topbar } from '../primitives/Topbar';
import { Sidebar, type SidebarItem } from '../primitives/Sidebar';

export interface AppShellProps {
  children: ReactNode;
  navItems?: SidebarItem[];
  activeNavId?: string;
  onNavChange?: (id: string) => void;
  topbar?: {
    title?: ReactNode;
    leftSlot?: ReactNode;
    centerSlot?: ReactNode;
    rightSlot?: ReactNode;
    onMenuClick?: () => void;
  };
  sidebarContent?: ReactNode;
  rightPanel?: ReactNode;
  sidebarCollapsed?: boolean;
  defaultSidebarCollapsed?: boolean;
  onSidebarCollapsedChange?: (collapsed: boolean) => void;
  className?: string;
}

export function AppShell({
  children,
  navItems,
  activeNavId,
  onNavChange,
  topbar,
  sidebarContent,
  rightPanel,
  sidebarCollapsed,
  defaultSidebarCollapsed = false,
  onSidebarCollapsedChange,
  className,
}: AppShellProps) {
  const [internalCollapsed, setInternalCollapsed] = useState(defaultSidebarCollapsed);
  const collapsed = sidebarCollapsed ?? internalCollapsed;
  const setCollapsed = (next: boolean) => {
    setInternalCollapsed(next);
    onSidebarCollapsedChange?.(next);
  };

  return (
    <div
      className={cn('sq-app-shell', className)}
      style={{ display: 'flex', flexDirection: 'column', height: '100vh', background: 'var(--sq-bg)', color: 'var(--sq-fg)' }}
    >
      <Topbar
        title={topbar?.title}
        leftSlot={topbar?.leftSlot}
        centerSlot={topbar?.centerSlot}
        rightSlot={topbar?.rightSlot}
        onMenuClick={topbar?.onMenuClick ?? (() => setCollapsed(!collapsed))}
      />
      <div style={{ display: 'flex', flex: 1, minHeight: 0 }}>
        <div
          style={{
            width: collapsed ? 56 : 240,
            transition: 'width 200ms cubic-bezier(0.4, 0, 0.2, 1)',
            flexShrink: 0,
            background: 'var(--sq-surface)',
            borderRight: '1px solid var(--sq-border)',
            overflow: 'hidden',
          }}
        >
          {sidebarContent ?? (
            <Sidebar
              items={navItems ?? []}
              activeId={activeNavId}
              onActiveChange={(id) => onNavChange?.(id)}
              collapsed={collapsed}
              onCollapsedChange={setCollapsed}
            />
          )}
        </div>
        <main style={{ flex: 1, minWidth: 0, overflow: 'auto' }}>{children}</main>
        {rightPanel ? (
          <div style={{ width: 320, flexShrink: 0, overflow: 'auto' }}>{rightPanel}</div>
        ) : null}
      </div>
    </div>
  );
}