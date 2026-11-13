'use client';

import type { HTMLAttributes, ReactNode, TdHTMLAttributes, ThHTMLAttributes } from 'react';
import { cn } from '../lib/cn';
import { useFocusVisible } from './internal';

export type SortDirection = 'asc' | 'desc' | 'none';

export interface TableProps extends HTMLAttributes<HTMLTableElement> {
  children: ReactNode;
  striped?: boolean;
}

export interface TableHeaderProps extends HTMLAttributes<HTMLTableSectionElement> {
  children?: ReactNode;
}

export interface TableBodyProps extends HTMLAttributes<HTMLTableSectionElement> {
  children?: ReactNode;
}

export interface TableRowProps extends HTMLAttributes<HTMLTableRowElement> {
  children?: ReactNode;
}

export interface TableHeadProps extends ThHTMLAttributes<HTMLTableCellElement> {
  children?: ReactNode;
  sortable?: boolean;
  onSort?: () => void;
  sortDirection?: SortDirection;
}

export interface TableCellProps extends TdHTMLAttributes<HTMLTableCellElement> {
  children?: ReactNode;
}

let tableStylesInjected = false;

function injectTableStyles(): void {
  if (tableStylesInjected || typeof document === 'undefined') return;
  tableStylesInjected = true;
  const styleEl = document.createElement('style');
  styleEl.textContent =
    '.sq-table-striped tbody tr:nth-of-type(even){background-color:color-mix(in srgb, var(--sq-fg) 4%, transparent)}';
  document.head.appendChild(styleEl);
}

injectTableStyles();

interface SortButtonProps {
  onClick?: () => void;
  direction?: SortDirection;
  children?: ReactNode;
}

function SortButton({ onClick, direction = 'none', children }: SortButtonProps) {
  const focus = useFocusVisible();

  return (
    <button
      type="button"
      onClick={onClick}
      onPointerDown={focus.onPointerDown}
      onFocus={focus.onFocus}
      onBlur={focus.onBlur}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 6,
        padding: 0,
        border: 'none',
        backgroundColor: 'transparent',
        color: 'inherit',
        fontFamily: 'inherit',
        fontSize: 'inherit',
        fontWeight: 'inherit',
        letterSpacing: 'inherit',
        textTransform: 'inherit',
        cursor: onClick ? 'pointer' : 'default',
        whiteSpace: 'nowrap',
        ...(focus.focusVisible ? { outline: '2px solid var(--sq-focus-ring)', outlineOffset: 2 } : null),
      }}
    >
      {children}
      {direction !== 'none' ? (
        <svg
          width="10"
          height="10"
          viewBox="0 0 10 10"
          fill="none"
          aria-hidden="true"
          style={{
            transform: direction === 'asc' ? 'rotate(180deg)' : undefined,
            transition: 'transform 150ms ease',
            opacity: 0.7,
            flexShrink: 0,
          }}
        >
          <path
            d="M2 3.5 L5 6.5 L8 3.5"
            stroke="currentColor"
            strokeWidth="1.5"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      ) : null}
    </button>
  );
}

export function Table({ children, striped = false, className, style, ...rest }: TableProps) {
  return (
    <div
      className={cn(striped ? 'sq-table-striped' : undefined, className)}
      style={{
        overflow: 'auto',
        boxSizing: 'border-box',
        border: '1px solid var(--sq-border)',
        borderRadius: 8,
        ...style,
      }}
    >
      <table
        {...rest}
        style={{
          width: '100%',
          borderCollapse: 'collapse',
          boxSizing: 'border-box',
          fontFamily: 'inherit',
          fontSize: 14,
          color: 'var(--sq-fg)',
        }}
      >
        {children}
      </table>
    </div>
  );
}

export function TableHeader({ children, className, style, ...rest }: TableHeaderProps) {
  return (
    <thead {...rest} className={cn(className)} style={style}>
      {children}
    </thead>
  );
}

export function TableBody({ children, className, style, ...rest }: TableBodyProps) {
  return (
    <tbody {...rest} className={cn(className)} style={style}>
      {children}
    </tbody>
  );
}

export function TableRow({ children, className, style, ...rest }: TableRowProps) {
  return (
    <tr {...rest} className={cn(className)} style={style}>
      {children}
    </tr>
  );
}

export function TableHead({
  children,
  sortable = false,
  onSort,
  sortDirection = 'none',
  className,
  style,
  ...rest
}: TableHeadProps) {
  return (
    <th
      {...rest}
      aria-sort={
        sortable
          ? sortDirection === 'asc'
            ? 'ascending'
            : sortDirection === 'desc'
              ? 'descending'
              : 'none'
          : undefined
      }
      className={cn(className)}
      style={{
        padding: '10px 14px',
        textAlign: 'left',
        fontWeight: 600,
        fontSize: 12,
        textTransform: 'uppercase',
        letterSpacing: '0.04em',
        color: 'var(--sq-fg-muted)',
        borderBottom: '1px solid var(--sq-border)',
        boxSizing: 'border-box',
        ...style,
      }}
    >
      {sortable ? (
        <SortButton onClick={onSort} direction={sortDirection}>
          {children}
        </SortButton>
      ) : (
        children
      )}
    </th>
  );
}

export function TableCell({ children, className, style, ...rest }: TableCellProps) {
  return (
    <td
      {...rest}
      className={cn(className)}
      style={{
        padding: '12px 14px',
        borderBottom: '1px solid var(--sq-border)',
        color: 'var(--sq-fg)',
        boxSizing: 'border-box',
        verticalAlign: 'middle',
        ...style,
      }}
    >
      {children}
    </td>
  );
}