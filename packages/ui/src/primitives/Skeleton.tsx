import type { CSSProperties } from 'react';
import { cn } from '../lib/cn';

let skeletonStylesInjected = false;

function injectSkeletonStyles(): void {
  if (skeletonStylesInjected || typeof document === 'undefined') return;
  skeletonStylesInjected = true;
  const styleEl = document.createElement('style');
  styleEl.textContent = '@keyframes sq-skeleton-pulse{0%,100%{opacity:1}50%{opacity:.45}}';
  document.head.appendChild(styleEl);
}

injectSkeletonStyles();

export interface SkeletonProps {
  width?: number | string;
  height?: number | string;
  radius?: number | string;
  className?: string;
  style?: CSSProperties;
}

export function Skeleton({
  width = '100%',
  height = 14,
  radius = 6,
  className,
  style,
}: SkeletonProps) {
  return (
    <div
      aria-hidden="true"
      className={cn(className)}
      style={{
        width,
        height,
        borderRadius: radius,
        backgroundColor: 'var(--sq-secondary)',
        animation: 'sq-skeleton-pulse 1.6s ease-in-out infinite',
        flexShrink: 0,
        ...style,
      }}
    />
  );
}

export interface TextSkeletonProps {
  lines?: number;
  width?: number | string;
  className?: string;
  style?: CSSProperties;
}

export function TextSkeleton({ lines = 1, width, className, style }: TextSkeletonProps) {
  const renderedLines = Array.from({ length: Math.max(1, lines) }, (_, index) => {
    const isLast = index === lines - 1;
    const isShortened = isLast && lines > 1 && width === undefined;
    return (
      <Skeleton
        key={index}
        height={12}
        width={isShortened ? '60%' : (width ?? '100%')}
        radius={4}
      />
    );
  });

  return (
    <div
      aria-hidden="true"
      className={cn(className)}
      style={{ display: 'flex', flexDirection: 'column', gap: 8, width: '100%', ...style }}
    >
      {renderedLines}
    </div>
  );
}

export interface CircleSkeletonProps {
  size?: number | string;
  className?: string;
  style?: CSSProperties;
}

export function CircleSkeleton({ size = 40, className, style }: CircleSkeletonProps) {
  return <Skeleton width={size} height={size} radius="50%" className={className} style={style} />;
}