import type { CSSProperties, ReactNode } from 'react';
import { cn } from '../lib/cn';

type PageHeaderProps = {
  title: ReactNode;
  description?: ReactNode;
  actions?: ReactNode;
  breadcrumb?: ReactNode;
  className?: string;
  style?: CSSProperties;
};

export function PageHeader({ title, description, actions, breadcrumb, className, style }: PageHeaderProps) {
  return (
    <div
      className={cn('sq-page-header', className)}
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: '8px',
        paddingBottom: '16px',
        marginBottom: '0',
        ...style,
      }}
    >
      {breadcrumb ? <div>{breadcrumb}</div> : null}
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: '16px' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '4px', minWidth: 0 }}>
          <h1 style={{ margin: 0, fontSize: '20px', lineHeight: '28px', fontWeight: 600, color: 'var(--sq-fg)' }}>
            {title}
          </h1>
          {description ? (
            <p style={{ margin: 0, fontSize: '13px', lineHeight: '20px', color: 'var(--sq-fg-muted)' }}>
              {description}
            </p>
          ) : null}
        </div>
        {actions ? <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexShrink: 0 }}>{actions}</div> : null}
      </div>
    </div>
  );
}