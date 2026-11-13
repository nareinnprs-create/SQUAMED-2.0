'use client';

import { useState, type CSSProperties } from 'react';
import { Button } from '../primitives/Button';
import { Input } from '../primitives/Input';
import { Select } from '../primitives/Select';
import { Textarea } from '../primitives/Textarea';
import { cn } from '../lib/cn';

export type OrderEntryType = 'lab' | 'imaging' | 'procedure' | 'medication' | 'referral';
export type OrderPriority = 'routine' | 'urgent' | 'emergency';

export interface OrderCatalogOption {
  label: string;
  value: string;
}

export interface OrderEntryPayload {
  type: OrderEntryType;
  catalogValue?: string;
  name: string;
  priority: OrderPriority;
  notes: string;
}

export interface OrderEntryFormProps {
  type: OrderEntryType;
  onSubmit: (order: OrderEntryPayload) => void;
  onCancel: () => void;
  catalogs?: OrderCatalogOption[];
  className?: string;
  style?: CSSProperties;
}

const catalogLabels: Record<OrderEntryType, string> = {
  lab: 'Test',
  imaging: 'Imaging study',
  procedure: 'Procedure',
  medication: 'Medication',
  referral: 'Referral',
};

const priorityLabels: Record<OrderPriority, string> = {
  routine: 'Routine',
  urgent: 'Urgent',
  emergency: 'Emergency',
};

export function OrderEntryForm({ type, onSubmit, onCancel, catalogs, className, style }: OrderEntryFormProps) {
  const [catalogValue, setCatalogValue] = useState('');
  const [name, setName] = useState('');
  const [priority, setPriority] = useState<OrderPriority>('routine');
  const [notes, setNotes] = useState('');
  const valid = name.trim().length > 0;

  const handleSubmit = () => {
    if (!valid) return;
    onSubmit({
      type,
      catalogValue: catalogValue || undefined,
      name: name.trim(),
      priority,
      notes: notes.trim(),
    });
  };

  return (
    <div className={cn(className)} style={{ display: 'flex', flexDirection: 'column', gap: 14, ...style }}>
      {catalogs && catalogs.length > 0 ? (
        <Select
          label={catalogLabels[type]}
          value={catalogValue}
          onChange={(event) => setCatalogValue(event.target.value)}
        >
          <option value="">Select {catalogLabels[type].toLowerCase()}…</option>
          {catalogs.map((catalog) => (
            <option key={catalog.value} value={catalog.value}>
              {catalog.label}
            </option>
          ))}
        </Select>
      ) : null}
      <Input
        label="Name / Description"
        placeholder={
          type === 'medication' ? 'Drug name' : `Enter ${catalogLabels[type].toLowerCase()} details`
        }
        value={name}
        onChange={(event) => setName(event.target.value)}
      />
      <Select
        label="Priority"
        value={priority}
        onChange={(event) => setPriority(event.target.value as OrderPriority)}
      >
        {(Object.keys(priorityLabels) as OrderPriority[]).map((key) => (
          <option key={key} value={key}>
            {priorityLabels[key]}
          </option>
        ))}
      </Select>
      <Textarea
        label="Notes"
        placeholder="Clinical notes, indication, special instructions…"
        value={notes}
        onChange={(event) => setNotes(event.target.value)}
        rows={3}
      />
      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10 }}>
        <Button variant="outline" onClick={onCancel}>
          Cancel
        </Button>
        <Button variant="primary" disabled={!valid} onClick={handleSubmit}>
          Submit
        </Button>
      </div>
    </div>
  );
}