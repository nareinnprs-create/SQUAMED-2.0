import type { HTMLAttributes, ReactNode } from 'react';
import { cn } from '../lib/cn';

export interface CardProps extends HTMLAttributes<HTMLDivElement> {
  children?: ReactNode;
}

export function Card({ className, style, children, ...rest }: CardProps) {
  return (
    <div
      {...rest}
      className={cn(className)}
      style={{
        boxSizing: 'border-box',
        backgroundColor: 'var(--sq-surface)',
        color: 'var(--sq-fg)',
        border: '1px solid var(--sq-border)',
        borderRadius: 8,
        boxShadow: 'var(--sq-shadow-sm)',
        ...style,
      }}
    >
      {children}
    </div>
  );
}

export interface CardHeaderProps extends HTMLAttributes<HTMLDivElement> {
  children?: ReactNode;
}

export function CardHeader({ className, style, children, ...rest }: CardHeaderProps) {
  return (
    <div
      {...rest}
      className={cn(className)}
      style={{ display: 'flex', flexDirection: 'column', gap: 4, padding: '16px 20px 0', ...style }}
    >
      {children}
    </div>
  );
}

export interface CardTitleProps extends HTMLAttributes<HTMLHeadingElement> {
  children?: ReactNode;
}

export function CardTitle({ className, style, children, ...rest }: CardTitleProps) {
  return (
    <h3
      {...rest}
      className={cn(className)}
      style={{ margin: 0, fontSize: 16, fontWeight: 700, lineHeight: 1.3, color: 'var(--sq-fg)', ...style }}
    >
      {children}
    </h3>
  );
}

export interface CardDescriptionProps extends HTMLAttributes<HTMLParagraphElement> {
  children?: ReactNode;
}

export function CardDescription({ className, style, children, ...rest }: CardDescriptionProps) {
  return (
    <p
      {...rest}
      className={cn(className)}
      style={{ margin: 0, fontSize: 13, lineHeight: 1.5, color: 'var(--sq-fg-muted)', ...style }}
    >
      {children}
    </p>
  );
}

export interface CardContentProps extends HTMLAttributes<HTMLDivElement> {
  children?: ReactNode;
}

export function CardContent({ className, style, children, ...rest }: CardContentProps) {
  return (
    <div
      {...rest}
      className={cn(className)}
      style={{ flex: 1, padding: '16px 20px 20px', minWidth: 0, ...style }}
    >
      {children}
    </div>
  );
}

export interface CardFooterProps extends HTMLAttributes<HTMLDivElement> {
  children?: ReactNode;
}

export function CardFooter({ className, style, children, ...rest }: CardFooterProps) {
  return (
    <div
      {...rest}
      className={cn(className)}
      style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '0 20px 20px', ...style }}
    >
      {children}
    </div>
  );
}