'use client';

import { useMemo, useState } from 'react';
import type { CSSProperties, ReactNode } from 'react';
import { cn } from '../lib/cn';
import { Skeleton } from './Skeleton';

export interface DataGridColumn<T> {
  key: keyof T;
  header: ReactNode;
  render?: (row: T, index: number) => ReactNode;
  width?: number | string;
}

export interface DataGridSort<T> {
  key: keyof T;
  direction: 'asc' | 'desc';
}

export interface DataGridProps<T> {
  columns: DataGridColumn<T>[];
  rows: T[];
  loading?: boolean;
  emptyMessage?: string;
  onRowClick?: (row: T, index: number) => void;
  pageSize?: number;
  initialSort?: DataGridSort<T>;
  className?: string;
  style?: CSSProperties;
}

let dataGridStylesInjected = false;

function injectDataGridStyles(): void {
  if (dataGridStylesInjected || typeof document === 'undefined') return;
  dataGridStylesInjected = true;
  const styleEl = document.createElement('style');
  styleEl.textContent = [
    '.sq-datagrid thead th{position:sticky;top:0;background:var(--sq-surface);z-index:1}',
    '.sq-datagrid-interactive tbody tr:hover{background-color:color-mix(in srgb, var(--sq-fg) 6%, transparent)}',
  ].join('\n');
  document.head.appendChild(styleEl);
}

injectDataGridStyles();

function compareValues(a: unknown, b: unknown): number {
  if (a === b) return 0;
  if (a == null) return 1;
  if (b == null) return -1;
  if (typeof a === 'number' && typeof b === 'number') return a - b;
  if (a instanceof Date && b instanceof Date) return a.getTime() - b.getTime();
  return String(a).localeCompare(String(b));
}

function renderCellValue<T>(row: T, column: DataGridColumn<T>, index: number): ReactNode {
  if (column.render) return column.render(row, index);
  const raw = row[column.key];
  if (raw == null) return '';
  return String(raw);
}

export function DataGrid<T>({
  columns,
  rows,
  loading = false,
  emptyMessage = 'No data',
  onRowClick,
  pageSize,
  initialSort,
  className,
  style,
}: DataGridProps<T>) {
  const [pageIndex, setPageIndex] = useState(0);

  const sortedRows = useMemo(() => {
    if (!initialSort) return rows;
    const { key, direction } = initialSort;
    return [...rows].sort((left, right) => {
      const comparison = compareValues(left[key], right[key]);
      return direction === 'asc' ? comparison : -comparison;
    });
  }, [rows, initialSort]);

  const pageCount = pageSize && pageSize > 0 ? Math.max(1, Math.ceil(sortedRows.length / pageSize)) : 1;
  const safePage = Math.min(pageIndex, pageCount - 1);
  const visibleRows =
    pageSize && pageSize > 0
      ? sortedRows.slice(safePage * pageSize, safePage * pageSize + pageSize)
      : sortedRows;

  const interactive = Boolean(onRowClick);

  const renderHeader = () => (
    <thead>
      <tr>
        {columns.map((column) => (
          <th
            key={String(column.key)}
            scope="col"
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
              width: column.width,
              whiteSpace: 'nowrap',
            }}
          >
            {column.header}
          </th>
        ))}
      </tr>
    </thead>
  );

  const renderLoading = () => (
    <tbody>
      {Array.from({ length: 5 }, (_, rowIndex) => (
        <tr key={`loading-${rowIndex}`}>
          {columns.map((column) => (
            <td
              key={String(column.key)}
              style={{ padding: '12px 14px', borderBottom: '1px solid var(--sq-border)', boxSizing: 'border-box' }}
            >
              <Skeleton height={12} width="70%" radius={4} />
            </td>
          ))}
        </tr>
      ))}
    </tbody>
  );

  const renderEmpty = () => (
    <tbody>
      <tr>
        <td
          colSpan={columns.length}
          style={{
            padding: '32px 14px',
            textAlign: 'center',
            fontSize: 13,
            color: 'var(--sq-fg-muted)',
          }}
        >
          {emptyMessage}
        </td>
      </tr>
    </tbody>
  );

  const renderRows = () => (
    <tbody>
      {visibleRows.map((row, index) => {
        const globalIndex = pageSize && pageSize > 0 ? safePage * pageSize + index : index;
        return (
          <tr
            key={globalIndex}
            onClick={onRowClick ? () => onRowClick(row, globalIndex) : undefined}
            style={{
              cursor: interactive ? 'pointer' : undefined,
              transition: 'background-color 120ms ease',
            }}
          >
            {columns.map((column) => (
              <td
                key={String(column.key)}
                style={{
                  padding: '12px 14px',
                  borderBottom: '1px solid var(--sq-border)',
                  color: 'var(--sq-fg)',
                  boxSizing: 'border-box',
                  verticalAlign: 'middle',
                }}
              >
                {renderCellValue(row, column, globalIndex)}
              </td>
            ))}
          </tr>
        );
      })}
    </tbody>
  );

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12, width: '100%', boxSizing: 'border-box' }}>
      <div
        className={cn('sq-datagrid', interactive ? 'sq-datagrid-interactive' : undefined, className)}
        style={{
          overflow: 'auto',
          boxSizing: 'border-box',
          border: '1px solid var(--sq-border)',
          borderRadius: 8,
          backgroundColor: 'var(--sq-surface)',
          ...style,
        }}
      >
        <table style={{ width: '100%', borderCollapse: 'collapse', fontFamily: 'inherit', fontSize: 14 }}>
          {renderHeader()}
          {loading ? renderLoading() : visibleRows.length > 0 ? renderRows() : renderEmpty()}
        </table>
      </div>
      {pageSize && pageSize > 0 && pageCount > 1 ? (
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'flex-end',
            gap: 10,
            fontSize: 13,
            color: 'var(--sq-fg-muted)',
          }}
        >
          <span>
            Page {safePage + 1} of {pageCount}
          </span>
          <button
            type="button"
            aria-label="Previous page"
            disabled={safePage === 0}
            onClick={() => setPageIndex((index) => Math.max(0, index - 1))}
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              width: 30,
              height: 30,
              padding: 0,
              border: '1px solid var(--sq-border)',
              borderRadius: 6,
              backgroundColor: 'var(--sq-surface)',
              color: 'var(--sq-fg)',
              cursor: safePage === 0 ? 'not-allowed' : 'pointer',
              opacity: safePage === 0 ? 0.5 : undefined,
            }}
          >
            <svg width="14" height="14" viewBox="0 0 16 16" fill="none" aria-hidden="true">
              <path d="M10 3 L5.5 8 L10 13" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </button>
          <button
            type="button"
            aria-label="Next page"
            disabled={safePage >= pageCount - 1}
            onClick={() => setPageIndex((index) => Math.min(pageCount - 1, index + 1))}
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              width: 30,
              height: 30,
              padding: 0,
              border: '1px solid var(--sq-border)',
              borderRadius: 6,
              backgroundColor: 'var(--sq-surface)',
              color: 'var(--sq-fg)',
              cursor: safePage >= pageCount - 1 ? 'not-allowed' : 'pointer',
              opacity: safePage >= pageCount - 1 ? 0.5 : undefined,
            }}
          >
            <svg width="14" height="14" viewBox="0 0 16 16" fill="none" aria-hidden="true">
              <path d="M6 3 L10.5 8 L6 13" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </button>
        </div>
      ) : null}
    </div>
  );
}