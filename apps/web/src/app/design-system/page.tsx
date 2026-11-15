'use client';

import { useState } from 'react';
import { colors } from '@squamed/design-system';
import {
  Badge,
  Button,
  Card,
  CardContent,
  DataGrid,
  type DataGridColumn,
  Field,
  Input,
  I18nProvider,
  PriorityIndicator,
  Select,
  StatusIndicator,
  TaskCard,
  type Locale,
  type PriorityLevel,
  type StatusTone,
} from '@squamed/ui';

// Chunk-00 item 2 showcase: the living inventory of the design system (02-DESIGN-SYSTEM §6).
// Public route — no auth — so gate scans and visual regression can settle it unauthenticated.
export default function DesignSystemPage() {
  const [locale, setLocale] = useState<Locale>('en');

  return (
    <I18nProvider locale={locale}>
      {/* Route-scoped: the global html/body 100% height lock (AppShell internal
          scrolling) blanks full-page paint capture below the fold. */}
      <style>{'html, body { height: auto; }'}</style>
      <main style={{ padding: '32px 40px 64px', display: 'flex', flexDirection: 'column', gap: 28 }}>
        <header style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 16 }}>
          <div>
            <h1 style={{ margin: 0, fontSize: 24, fontWeight: 700, color: 'var(--sq-fg)' }}>SQUAMED Design System</h1>
            <p style={{ margin: '6px 0 0', fontSize: 13, color: 'var(--sq-fg-muted)' }}>
              Tokens, primitives and domain components in light clinical mode · i18n (en, hi) · RTL-ready · WCAG 2.2 AA
            </p>
          </div>
          <div style={{ display: 'flex', gap: 8 }}>
            <Button size="sm" variant={locale === 'en' ? 'primary' : 'secondary'} onClick={() => setLocale('en')}>
              English
            </Button>
            <Button size="sm" variant={locale === 'hi' ? 'primary' : 'secondary'} onClick={() => setLocale('hi')}>
              हिन्दी
            </Button>
          </div>
        </header>

        <Section title="Tokens">
          <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
            {(Object.keys(colors) as Array<keyof typeof colors>).map((name) => (
              <div key={name} style={{ width: 128 }}>
                <div
                  aria-hidden="true"
                  style={{ height: 44, borderRadius: 8, border: '1px solid var(--sq-border)', backgroundColor: colors[name] }}
                />
                <div style={{ marginTop: 6, fontSize: 12, fontWeight: 600, color: 'var(--sq-fg)' }}>{name}</div>
                <div style={{ fontSize: 11, color: 'var(--sq-fg-muted)', fontFamily: 'monospace' }}>{colors[name]}</div>
              </div>
            ))}
          </div>
        </Section>

        <Section title="Buttons">
          <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', alignItems: 'center' }}>
            <Button variant="primary">Admit patient</Button>
            <Button variant="secondary">Save draft</Button>
            <Button variant="outline">Print chart</Button>
            <Button variant="ghost">Dismiss</Button>
            <Button variant="danger">Cancel order</Button>
            <Button variant="success">Verify vitals</Button>
            <Button variant="primary" size="sm">Compact</Button>
            <Button variant="primary" size="lg">Primary action</Button>
            <Button variant="primary" loading>Submitting</Button>
          </div>
        </Section>

        <Section title="Form fields">
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, minmax(200px, 1fr))', gap: 16, maxWidth: 900 }}>
            <Input label="Patient name" hint="As printed on the ID document" />
            <Input label="Mobile number" error="Enter a 10-digit number" defaultValue="98765" />
            <Field label="Ward" required>
              <Select>
                <option value="">Choose…</option>
                <option value="general">General medicine</option>
                <option value="icu">Intensive care</option>
              </Select>
            </Field>
          </div>
        </Section>

        <Section title="Status">
          <div style={{ display: 'flex', gap: 18, flexWrap: 'wrap', alignItems: 'center' }}>
            <StatusIndicator tone="success" label="Stable" />
            <StatusIndicator tone="warning" label="Watch" />
            <StatusIndicator tone="critical" label="Deteriorating" />
            <StatusIndicator tone="information" label="In transit" />
            <StatusIndicator tone="neutral" label="Unknown" />
            <Badge variant="primary">SQUAMED</Badge>
            <Badge variant="success">Paid</Badge>
            <Badge variant="critical">Overdue</Badge>
            <PriorityIndicator level="routine" />
            <PriorityIndicator level="urgent" />
            <PriorityIndicator level="emergency" />
          </div>
        </Section>

        <Section title="Data table — Clinical density (44px rows)">
          <DataGrid columns={bedColumns} rows={bedRows} rowTone={toneFor} />
        </Section>

        <Section title="Data table — Enterprise density (36px rows)">
          <DataGrid columns={bedColumns} rows={bedRows} density="enterprise" rowTone={toneFor} />
        </Section>

        <Section title="Task cards">
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10, maxWidth: 560 }}>
            {taskSamples.map((task) => (
              <TaskCard key={task.id} task={task} onOpen={() => undefined} />
            ))}
          </div>
        </Section>
      </main>
    </I18nProvider>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <Card>
      <CardContent style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
        <h2 style={{ margin: 0, fontSize: 13, fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.04em', color: 'var(--sq-fg-muted)' }}>
          {title}
        </h2>
        {children}
      </CardContent>
    </Card>
  );
}

interface BedRow {
  bed: string;
  patient: string;
  mrn: string;
  status: string;
  since: string;
}

const bedColumns: DataGridColumn<BedRow>[] = [
  { key: 'bed', header: 'Bed', width: 90 },
  { key: 'patient', header: 'Patient' },
  { key: 'mrn', header: 'MRN', width: 140 },
  { key: 'status', header: 'Status', width: 130 },
  { key: 'since', header: 'Since', width: 150 },
];

const bedRows: BedRow[] = [
  { bed: 'A-101', patient: 'Rohit Verma', mrn: 'SM-100241', status: 'Stable', since: '10 Nov, 14:20' },
  { bed: 'A-102', patient: 'Anita Desai', mrn: 'SM-100242', status: 'Critical', since: '12 Nov, 03:05' },
  { bed: 'B-210', patient: 'Farid Khan', mrn: 'SM-100243', status: 'Watch', since: '12 Nov, 18:45' },
  { bed: 'B-211', patient: 'Meera Nair', mrn: 'SM-100244', status: 'Discharged', since: '13 Nov, 08:00' },
  { bed: 'C-311', patient: 'Joseph Dsouza', mrn: 'SM-100245', status: 'Critical', since: '13 Nov, 22:10' },
];

const toneFor = (row: BedRow): StatusTone | null =>
  row.status === 'Critical' ? 'critical' : row.status === 'Watch' ? 'warning' : row.status === 'Stable' ? 'success' : null;

const taskSamples = [
  { id: 't-1', title: 'Administer morning insulin', status: 'pending' as const, priority: 'urgent' as PriorityLevel, dueAt: new Date(2026, 10, 15, 9, 30), assigneeName: 'R. Sharma' },
  { id: 't-2', title: 'Recheck vitals after transfusion', status: 'in_progress' as const, priority: 'high' as PriorityLevel, dueAt: new Date(2026, 10, 15, 11, 0), assigneeName: 'P. Iyer' },
  { id: 't-3', title: 'Call family about discharge plan', status: 'escalated' as const, priority: 'emergency' as PriorityLevel, dueAt: new Date(2026, 10, 15, 12, 15), assigneeName: 'S. Gupta', escalationCount: 3 },
  { id: 't-4', title: 'Sign discharge summary', status: 'completed' as const, priority: 'routine' as PriorityLevel, dueAt: new Date(2026, 10, 14, 17, 45), assigneeName: 'Dr. K. Rao' },
];
