'use client';

import { useState, type CSSProperties } from 'react';
import { Button } from '../primitives/Button';
import { Input } from '../primitives/Input';
import { cn } from '../lib/cn';
import { PlusIcon, TrashIcon } from './icons';

export interface PrescriptionRow {
  drugName: string;
  strength: string;
  form: string;
  dose: string;
  frequency: string;
  duration: string;
  instructions: string;
}

export interface PrescriptionBuilderProps {
  onChange: (rows: PrescriptionRow[]) => void;
  onSubmit: (rows: PrescriptionRow[]) => void;
  initialRows?: PrescriptionRow[];
  className?: string;
  style?: CSSProperties;
}

interface RowField {
  key: keyof PrescriptionRow;
  label: string;
}

const fields: RowField[] = [
  { key: 'drugName', label: 'Drug name' },
  { key: 'strength', label: 'Strength' },
  { key: 'form', label: 'Form' },
  { key: 'dose', label: 'Dose' },
  { key: 'frequency', label: 'Frequency' },
  { key: 'duration', label: 'Duration' },
  { key: 'instructions', label: 'Instructions' },
];

const GRID_TEMPLATE =
  'minmax(130px, 1.6fr) minmax(90px, 1fr) minmax(90px, 1fr) minmax(90px, 1fr) minmax(110px, 1fr) minmax(90px, 1fr) minmax(150px, 1.6fr) 32px';

function emptyRow(): PrescriptionRow {
  return { drugName: '', strength: '', form: '', dose: '', frequency: '', duration: '', instructions: '' };
}

export function PrescriptionBuilder({ onChange, onSubmit, initialRows, className, style }: PrescriptionBuilderProps) {
  const [rows, setRows] = useState<PrescriptionRow[]>(() =>
    initialRows && initialRows.length > 0 ? initialRows.map((row) => ({ ...row })) : [emptyRow()],
  );

  const updateRow = (index: number, key: keyof PrescriptionRow, value: string) => {
    const nextRows = rows.map((row, i) => (i === index ? { ...row, [key]: value } : row));
    setRows(nextRows);
    onChange(nextRows);
  };

  const addRow = () => {
    const nextRows = [...rows, emptyRow()];
    setRows(nextRows);
    onChange(nextRows);
  };

  const removeRow = (index: number) => {
    const nextRows = rows.filter((_, i) => i !== index);
    setRows(nextRows);
    onChange(nextRows);
  };

  const handleSubmit = () => {
    onSubmit(rows.map((row) => ({ ...row })));
  };

  return (
    <div className={cn(className)} style={{ display: 'flex', flexDirection: 'column', gap: 12, ...style }}>
      <div style={{ display: 'grid', gridTemplateColumns: GRID_TEMPLATE, gap: 8, alignItems: 'end' }}>
        {fields.map((field) => (
          <span
            key={field.key}
            style={{
              fontSize: 12,
              fontWeight: 600,
              color: 'var(--sq-fg-muted)',
              letterSpacing: '0.04em',
              textTransform: 'uppercase',
              whiteSpace: 'nowrap',
            }}
          >
            {field.label}
          </span>
        ))}
        <span aria-hidden="true" />
      </div>
      {rows.map((row, index) => (
        <div
          key={index}
          style={{ display: 'grid', gridTemplateColumns: GRID_TEMPLATE, gap: 8, alignItems: 'center' }}
        >
          {fields.map((field) => (
            <Input
              key={field.key}
              size="sm"
              aria-label={`${field.label}, row ${index + 1}`}
              placeholder={field.label}
              value={row[field.key]}
              onChange={(event) => updateRow(index, field.key, event.target.value)}
            />
          ))}
          <Button
            variant="ghost"
            size="sm"
            aria-label={`Remove row ${index + 1}`}
            disabled={rows.length === 1}
            onClick={() => removeRow(index)}
            style={{ color: 'var(--sq-critical)', padding: 0, height: 32, width: 32 }}
          >
            <TrashIcon size={16} />
          </Button>
        </div>
      ))}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 10 }}>
        <Button variant="outline" size="sm" onClick={addRow}>
          <PlusIcon size={14} />
          Add row
        </Button>
        <Button variant="primary" onClick={handleSubmit}>
          Submit prescription
        </Button>
      </div>
    </div>
  );
}