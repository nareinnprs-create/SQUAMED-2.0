import type { CSSProperties } from 'react';
import { cn } from '../lib/cn';

export type AvatarSize = 'xs' | 'sm' | 'md' | 'lg' | 'xl';
export type AvatarStatus = 'online' | 'offline' | 'busy' | 'away';

export interface AvatarProps {
  name: string;
  src?: string;
  alt?: string;
  size?: AvatarSize;
  status?: AvatarStatus;
  className?: string;
  style?: CSSProperties;
}

const sizeMap: Record<AvatarSize, { size: number; fontSize: number }> = {
  xs: { size: 24, fontSize: 9 },
  sm: { size: 32, fontSize: 12 },
  md: { size: 40, fontSize: 14 },
  lg: { size: 56, fontSize: 20 },
  xl: { size: 72, fontSize: 26 },
};

const statusColors: Record<AvatarStatus, string> = {
  online: 'var(--sq-success)',
  offline: 'var(--sq-fg-muted)',
  busy: 'var(--sq-critical)',
  away: 'var(--sq-warning)',
};

function getInitials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  const first = parts[0]?.charAt(0) ?? '';
  const second = parts[1]?.charAt(0) ?? '';
  return `${first}${second}`.toUpperCase();
}

export function Avatar({ name, src, alt, size = 'md', status, className, style }: AvatarProps) {
  const { size: px, fontSize } = sizeMap[size];
  const dotSize = Math.max(8, Math.round(px / 4));
  const initials = getInitials(name);
  const altText = alt ?? name;

  return (
    <div
      className={cn(className)}
      style={{
        position: 'relative',
        flexShrink: 0,
        width: px,
        height: px,
        borderRadius: '50%',
        backgroundColor: src ? 'transparent' : 'var(--sq-secondary)',
        ...style,
      }}
    >
      {src ? (
        <img
          src={src}
          alt={altText}
          style={{
            display: 'block',
            width: '100%',
            height: '100%',
            borderRadius: '50%',
            objectFit: 'cover',
          }}
        />
      ) : (
        <span
          aria-hidden="true"
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            width: '100%',
            height: '100%',
            borderRadius: '50%',
            fontSize,
            fontWeight: 700,
            letterSpacing: '0.03em',
            color: 'var(--sq-fg)',
            userSelect: 'none',
          }}
        >
          {initials}
        </span>
      )}
      {status ? (
        <span
          role="img"
          aria-label={status}
          style={{
            position: 'absolute',
            right: 0,
            bottom: 0,
            width: dotSize,
            height: dotSize,
            borderRadius: '50%',
            backgroundColor: statusColors[status],
            border: '2px solid var(--sq-surface)',
            boxSizing: 'content-box',
          }}
        />
      ) : null}
    </div>
  );
}