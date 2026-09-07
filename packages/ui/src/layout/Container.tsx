import type { CSSProperties, ReactNode } from 'react';
import { cn } from '../lib/cn';

type ContainerProps = {
  children: ReactNode;
  size?: 'sm' | 'md' | 'lg' | 'xl' | 'fluid';
  className?: string;
  style?: CSSProperties;
};

const maxWidths: Record<NonNullable<ContainerProps['size']>, string> = {
  sm: '640px',
  md: '768px',
  lg: '1024px',
  xl: '1280px',
  fluid: 'none',
};

export function Container({ children, size = 'xl', className, style }: ContainerProps) {
  return (
    <div
      className={cn('sq-container', className)}
      style={{
        maxWidth: maxWidths[size],
        marginInline: 'auto',
        paddingInline: '24px',
        width: '100%',
        ...style,
      }}
    >
      {children}
    </div>
  );
}