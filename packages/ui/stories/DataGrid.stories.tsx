import type { Meta, StoryObj } from '@storybook/react';
import { DataGrid, type DataGridColumn } from '../src/primitives/DataGrid';

interface BedRow {
  bed: string;
  patient: string;
  mrn: string;
  status: string;
  since: string;
}

const columns: DataGridColumn<BedRow>[] = [
  { key: 'bed', header: 'Bed', width: 90 },
  { key: 'patient', header: 'Patient' },
  { key: 'mrn', header: 'MRN', width: 140 },
  { key: 'status', header: 'Status', width: 130 },
  { key: 'since', header: 'Since', width: 150 },
];

const rows: BedRow[] = [
  { bed: 'A-101', patient: 'Rohit Verma', mrn: 'SM-100241', status: 'Stable', since: '10 Nov, 14:20' },
  { bed: 'A-102', patient: 'Anita Desai', mrn: 'SM-100242', status: 'Critical', since: '12 Nov, 03:05' },
  { bed: 'B-210', patient: 'Farid Khan', mrn: 'SM-100243', status: 'Stable', since: '12 Nov, 18:45' },
  { bed: 'B-211', patient: 'Meera Nair', mrn: 'SM-100244', status: 'Watch', since: '13 Nov, 08:00' },
  { bed: 'C-311', patient: 'Joseph Dsouza', mrn: 'SM-100245', status: 'Critical', since: '13 Nov, 22:10' },
  { bed: 'C-312', patient: 'Lata Menon', mrn: 'SM-100246', status: 'Discharged', since: '14 Nov, 11:30' },
  { bed: 'D-401', patient: 'Arjun Singh', mrn: 'SM-100247', status: 'Watch', since: '14 Nov, 16:55' },
];

const toneFor = (row: BedRow) =>
  row.status === 'Critical' ? 'critical' : row.status === 'Watch' ? 'warning' : row.status === 'Stable' ? 'success' : null;

const meta: Meta<typeof DataGrid> = {
  title: 'Primitives/DataGrid',
  component: DataGrid,
  parameters: { layout: 'padded' },
};

export default meta;
type Story = StoryObj<typeof DataGrid>;

export const ClinicalDensity: Story = {
  args: {
    columns,
    rows,
    rowTone: toneFor,
  },
};

export const EnterpriseDensity: Story = {
  args: {
    columns,
    rows,
    density: 'enterprise',
    rowTone: toneFor,
  },
};

export const Empty: Story = {
  args: { columns, rows: [] },
};

export const Loading: Story = {
  args: { columns, rows: [], loading: true },
};

export const Paginated: Story = {
  args: { columns, rows, pageSize: 3, rowTone: toneFor },
};
