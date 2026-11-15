import { render, screen } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import { describe, expect, it } from 'vitest';
import { DataGrid, type DataGridColumn } from './DataGrid';
import { setLocale } from '../i18n';

interface Row {
  bed: string;
  state: string;
}

const columns: DataGridColumn<Row>[] = [
  { key: 'bed', header: 'Bed' },
  { key: 'state', header: 'State' },
];

const rows: Row[] = [
  { bed: 'A-101', state: 'stable' },
  { bed: 'A-102', state: 'critical' },
  { bed: 'B-210', state: 'stable' },
  { bed: 'B-211', state: 'stable' },
  { bed: 'C-311', state: 'critical' },
];

describe('DataGrid density modes (02-DESIGN-SYSTEM §4)', () => {
  it('defaults to clinical density: 12px block padding (44px rows)', () => {
    const { container } = render(<DataGrid columns={columns} rows={rows} />);
    const bodyCell = container.querySelector('tbody td') as HTMLElement;
    expect(bodyCell.style.padding).toBe('12px 14px');
  });

  it('enterprise density tightens cells to 8px block padding (36px rows)', () => {
    const { container } = render(<DataGrid columns={columns} rows={rows} density="enterprise" />);
    const bodyCell = container.querySelector('tbody td') as HTMLElement;
    const headCell = container.querySelector('thead th') as HTMLElement;
    expect(bodyCell.style.padding).toBe('8px 10px');
    expect(headCell.style.padding).toBe('6px 10px');
  });
});

describe('DataGrid row-status strip', () => {
  it('paints a 3px inline-start bar in the row tone on the first cell', () => {
    const { container } = render(
      <DataGrid
        columns={columns}
        rows={rows}
        rowTone={(row) => (row.state === 'critical' ? 'critical' : 'success')}
      />,
    );
    const firstRowFirstCell = container.querySelector('tbody tr td') as HTMLElement;
    expect(firstRowFirstCell.style.borderInlineStart).toBe('3px solid var(--sq-success)');
    const secondRow = container.querySelectorAll('tbody tr')[1] as HTMLElement;
    const secondRowFirstCell = secondRow.querySelector('td') as HTMLElement;
    expect(secondRowFirstCell.style.borderInlineStart).toBe('3px solid var(--sq-critical)');
  });

  it('leaves rows without a tone unstriped', () => {
    const { container } = render(
      <DataGrid columns={columns} rows={rows} rowTone={() => null} />,
    );
    const firstCell = container.querySelector('tbody td') as HTMLElement;
    expect(firstCell.style.borderInlineStart).toBe('');
  });
});

describe('DataGrid localization', () => {
  it('shows the catalog empty message in the active locale', () => {
    const { container, unmount } = render(<DataGrid columns={columns} rows={[]} />);
    expect(container.textContent).toContain('No data');
    unmount();
    setLocale('hi');
    const hindi = render(<DataGrid columns={columns} rows={[]} />);
    expect(hindi.container.textContent).toContain('कोई डेटा नहीं');
    hindi.unmount();
    setLocale('en');
  });

  it('localizes pagination text and button labels', () => {
    const { unmount } = render(<DataGrid columns={columns} rows={rows} pageSize={2} />);
    expect(screen.getByText('Page 1 of 3')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Previous page' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Next page' })).toBeInTheDocument();
    unmount();
    setLocale('hi');
    const hindi = render(<DataGrid columns={columns} rows={rows} pageSize={2} />);
    expect(hindi.getByText('पृष्ठ 1 / 3')).toBeInTheDocument();
    expect(hindi.getByRole('button', { name: 'पिछला पृष्ठ' })).toBeInTheDocument();
    expect(hindi.getByRole('button', { name: 'अगला पृष्ठ' })).toBeInTheDocument();
    hindi.unmount();
    setLocale('en');
  });
});
