'use client';

import type { CSSProperties, ReactNode } from 'react';
import { cn } from '../lib/cn';
import { useFocusVisible } from './internal';

export interface PaginationProps {
  page: number;
  pageCount: number;
  onPageChange: (page: number) => void;
  siblingCount?: number;
  compact?: boolean;
  className?: string;
  style?: CSSProperties;
}

function getPageItems(current: number, total: number, sibling: number): (number | 'ellipsis')[] {
  if (total <= 1) return [1];
  const pages = new Set<number>([1, total]);
  for (let index = Math.max(2, current - sibling); index <= Math.min(total - 1, current + sibling); index++) {
    pages.add(index);
  }
  const sorted = Array.from(pages).sort((a, b) => a - b);
  const items: (number | 'ellipsis')[] = [];
  let previous = 0;
  for (const pageNumber of sorted) {
    if (pageNumber - previous > 1) items.push('ellipsis');
    items.push(pageNumber);
    previous = pageNumber;
  }
  return items;
}

interface PageButtonProps {
  onClick?: () => void;
  disabled?: boolean;
  active?: boolean;
  ariaLabel?: string;
  children?: ReactNode;
}

function PageButton({ onClick, disabled = false, active = false, ariaLabel, children }: PageButtonProps) {
  const focus = useFocusVisible();

  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      aria-label={ariaLabel}
      aria-current={active ? 'page' : undefined}
      onPointerDown={focus.onPointerDown}
      onFocus={focus.onFocus}
      onBlur={focus.onBlur}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        boxSizing: 'border-box',
        minWidth: 34,
        height: 34,
        padding: '0 8px',
        border: '1px solid',
        borderRadius: 8,
        fontFamily: 'inherit',
        fontSize: 13,
        fontWeight: active ? 600 : 500,
        color: active ? 'var(--sq-fg)' : 'var(--sq-fg-muted)',
        backgroundColor: active ? 'var(--sq-primary)' : 'var(--sq-surface)',
        borderColor: active ? 'var(--sq-primary)' : 'var(--sq-border)',
        cursor: disabled ? 'not-allowed' : 'pointer',
        opacity: disabled ? 0.5 : undefined,
        ...(focus.focusVisible ? { outline: '2px solid var(--sq-focus-ring)', outlineOffset: 2 } : null),
      }}
    >
      {children}
    </button>
  );
}

const chevronLeft = (
  <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
    <path d="M10 3 L5.5 8 L10 13" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
  </svg>
);

const chevronRight = (
  <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
    <path d="M6 3 L10.5 8 L6 13" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
  </svg>
);

export function Pagination({
  page,
  pageCount,
  onPageChange,
  siblingCount = 1,
  compact = false,
  className,
  style,
}: PaginationProps) {
  const safePage = Math.max(1, Math.min(page, Math.max(1, pageCount)));
  const pageItems = compact ? [] : getPageItems(safePage, Math.max(1, pageCount), siblingCount);

  return (
    <nav
      aria-label="Pagination"
      className={cn(className)}
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 6,
        boxSizing: 'border-box',
        ...style,
      }}
    >
      <PageButton
        ariaLabel="Previous page"
        disabled={safePage <= 1}
        onClick={() => onPageChange(safePage - 1)}
      >
        {chevronLeft}
      </PageButton>
      {compact ? (
        <span
          style={{
            padding: '0 10px',
            fontSize: 13,
            fontWeight: 500,
            color: 'var(--sq-fg-muted)',
            whiteSpace: 'nowrap',
          }}
        >
          Page {safePage} of {Math.max(1, pageCount)}
        </span>
      ) : (
        <>
          {pageItems.map((item, index) => {
            if (item === 'ellipsis') {
              return (
                <span
                  key={`ellipsis-${index}`}
                  aria-hidden="true"
                  style={{ padding: '0 4px', color: 'var(--sq-fg-muted)', fontSize: 13, userSelect: 'none' }}
                >
                  &hellip;
                </span>
              );
            }
            return (
              <PageButton
                key={item}
                ariaLabel={`Page ${item}`}
                active={item === safePage}
                onClick={() => onPageChange(item)}
              >
                {item}
              </PageButton>
            );
          })}
        </>
      )}
      <PageButton
        ariaLabel="Next page"
        disabled={safePage >= Math.max(1, pageCount)}
        onClick={() => onPageChange(safePage + 1)}
      >
        {chevronRight}
      </PageButton>
    </nav>
  );
}