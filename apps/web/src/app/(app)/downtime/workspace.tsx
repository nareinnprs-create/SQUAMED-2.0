'use client';

import { useCallback, useEffect, useState } from 'react';
import {
  Badge,
  Button,
  ContentPanel,
  Input,
  PageHeader,
  Stack,
  StatusIndicator,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
  Textarea,
} from '@squamed/ui';

export type DowntimeWindow = {
  id: string;
  reason: string;
  startedAt: string;
  endedAt: string | null;
  open: boolean;
};

export type ReentryToken = {
  queueType: string;
  tokenNumber: string;
  patientId: string;
  patientName?: string;
  priority?: string;
};

/** Re-entry lines are "queueType,tokenNumber,patientId[,patientName][,priority]" — one paper token per line. */
export function parseTokenLines(text: string): ReentryToken[] {
  const lines = text
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean);
  if (lines.length === 0) throw new Error('Add at least one paper token line');
  return lines.map((line) => {
    const parts = line.split(',').map((part) => part.trim());
    if (parts.length < 3 || parts.some((part, idx) => idx < 3 && !part)) {
      throw new Error('Each line needs: queueType,tokenNumber,patientId[,patientName][,priority]');
    }
    return {
      queueType: parts[0],
      tokenNumber: parts[1],
      patientId: parts[2],
      patientName: parts[3] || undefined,
      priority: parts[4] || undefined,
    };
  });
}

export function parseVisitIds(text: string): string[] {
  return text
    .split(',')
    .map((part) => part.trim())
    .filter(Boolean);
}

/** 58mm-style A4 print target: on print, only the pack is visible. */
export const DOWNTIME_PRINT_CSS = `
@media print {
  body * { visibility: hidden; }
  #downtime-pack, #downtime-pack * { visibility: visible; }
  #downtime-pack {
    position: absolute;
    inset-inline-start: 0;
    top: 0;
    width: 190mm;
    padding: 10mm;
    color: #000;
    background: #fff;
  }
  #downtime-pack table { width: 100%; border-collapse: collapse; }
  #downtime-pack th, #downtime-pack td { border: 1px solid #000; padding: 4px 6px; font-size: 12px; text-align: left; }
  #downtime-pack .paper-form { border: 1px dashed #000; padding: 8px; margin-bottom: 8mm; page-break-inside: avoid; }
  #downtime-pack .paper-form div { min-height: 14px; }
}
`;

type PackAppointment = {
  id: string;
  patientId: string | null;
  scheduledStart: string | null;
  status: string | null;
  type: string | null;
};

type PackData = {
  window: DowntimeWindow | null;
  appointments: Array<{ time: string; patient: string; status: string }>;
  board: Array<{ tokenNumber: string; patientName: string | null; priority: string; status: string }>;
};

const BLANK_FORMS = 3;

export function DowntimeWorkspace({
  apiFetch,
  hospitalId,
}: {
  apiFetch: <T>(path: string, options?: RequestInit) => Promise<T>;
  hospitalId: string | null;
}) {
  const [tab, setTab] = useState('log');
  const [windows, setWindows] = useState<DowntimeWindow[] | null>(null);
  const [reason, setReason] = useState('');
  const [tokenText, setTokenText] = useState('');
  const [visitText, setVisitText] = useState('');
  const [pack, setPack] = useState<PackData | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const loadWindows = useCallback(async () => {
    try {
      const list = await apiFetch<DowntimeWindow[]>('/downtime/windows');
      setWindows(Array.isArray(list) ? list : []);
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load downtime windows');
    }
  }, [apiFetch]);

  useEffect(() => {
    if (tab === 'log' && !windows) void loadWindows();
  }, [tab, windows, loadWindows]);

  async function openWindow() {
    if (!reason.trim()) {
      setError('Downtime reason required');
      return;
    }
    setBusy(true);
    try {
      await apiFetch('/downtime/windows', { method: 'POST', body: JSON.stringify({ reason: reason.trim(), hospitalId }) });
      setReason('');
      setMessage('Downtime window opened — switch to paper mode and print the pack.');
      await loadWindows();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to open window');
    } finally {
      setBusy(false);
    }
  }

  async function closeWindow(id: string) {
    setBusy(true);
    try {
      await apiFetch(`/downtime/windows/${id}/close`, { method: 'PATCH', body: '{}' });
      setMessage('Downtime window closed — run the re-entry tool before the next surge.');
      await loadWindows();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to close window');
    } finally {
      setBusy(false);
    }
  }

  async function submitReentry() {
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      const tokens = parseTokenLines(tokenText);
      const visitIds = parseVisitIds(visitText);
      const result = await apiFetch<{ tokens: Array<{ tokenNumber: string }>; visits: unknown[] }>('/downtime/reentries', {
        method: 'POST',
        body: JSON.stringify({ tokens, visitIds, hospitalId }),
      });
      const tokenList = (result.tokens ?? []).map((t) => t.tokenNumber).join(', ');
      setMessage(`Re-entry complete without loss: ${(result.tokens ?? []).length} tokens (${tokenList}), ${(result.visits ?? []).length} check-ins.`);
      setTokenText('');
      setVisitText('');
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Re-entry failed — nothing was written');
    } finally {
      setBusy(false);
    }
  }

  async function loadPack() {
    setBusy(true);
    setError(null);
    try {
      const today = new Date().toISOString().slice(0, 10);
      const list = await apiFetch<PackAppointment[]>('/appointments?limit=200');
      const todays = (Array.isArray(list) ? list : []).filter((a) => (a.scheduledStart ?? '').slice(0, 10) === today);
      const [windowList, board] = await Promise.all([
        apiFetch<DowntimeWindow[]>('/downtime/windows'),
        apiFetch<PackData['board']>('/queue/board?queueType=consult'),
      ]);
      const open = (Array.isArray(windowList) ? windowList : []).find((w) => w.open) ?? null;
      const appointments = await Promise.all(
        todays.map(async (a) => {
          let patient = '—';
          if (a.patientId) {
            try {
              const p = await apiFetch<{ firstName?: string | null; lastName?: string | null }>(`/patients/${a.patientId}`);
              patient = [p?.firstName, p?.lastName].filter(Boolean).join(' ') || '—';
            } catch {
              patient = a.patientId.slice(0, 8);
            }
          }
          const time = (a.scheduledStart ?? '').slice(11, 16);
          return { time: time || '—', patient, status: a.status ?? '—' };
        }),
      );
      setPack({
        window: open,
        appointments,
        board: (Array.isArray(board) ? board : []).map((b) => ({
          tokenNumber: b.tokenNumber,
          patientName: b.patientName,
          priority: b.priority,
          status: b.status,
        })),
      });
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load pack data');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div style={{ padding: 24 }}>
      <style>{DOWNTIME_PRINT_CSS}</style>
      <PageHeader
        title="Downtime pack"
        description="Paper-mode fallback for reception and queue outages: open a window, print the pack, then re-enter paper tokens and check-ins without loss."
      />

      {message ? <StatusIndicator tone="success" label={message} /> : null}
      {error ? <StatusIndicator tone="critical" label={error} /> : null}

      <div style={{ height: 16 }} />

      <Tabs value={tab} onValueChange={setTab}>
        <TabsList>
          <TabsTrigger value="log">Downtime log</TabsTrigger>
          <TabsTrigger value="reentry">Re-entry tool</TabsTrigger>
          <TabsTrigger value="pack">Print pack</TabsTrigger>
        </TabsList>

        <TabsContent value="log">
          <ContentPanel>
            <Stack direction="row" gap={12} wrap>
              <div style={{ flex: 1, minWidth: 240 }}>
                <Input
                  label="Downtime reason"
                  placeholder="e.g. PG outage drill"
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                />
              </div>
              <Button loading={busy} onClick={() => void openWindow()}>
                Open downtime window
              </Button>
            </Stack>
            <div style={{ height: 12 }} />
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Reason</TableHead>
                  <TableHead>Started</TableHead>
                  <TableHead>Ended</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {(windows ?? []).length === 0 ? (
                  <TableRow>
                    <TableCell>No downtime windows yet.</TableCell>
                  </TableRow>
                ) : (
                  (windows ?? []).map((w) => (
                    <TableRow key={w.id}>
                      <TableCell>{w.reason}</TableCell>
                      <TableCell>{w.startedAt?.slice(0, 19).replace('T', ' ')}</TableCell>
                      <TableCell>{w.endedAt ? w.endedAt.slice(0, 19).replace('T', ' ') : '—'}</TableCell>
                      <TableCell>
                        <Badge variant={w.open ? 'critical' : 'neutral'}>{w.open ? 'open' : 'closed'}</Badge>
                      </TableCell>
                      <TableCell>
                        {w.open ? (
                          <Button size="sm" variant="outline" loading={busy} onClick={() => void closeWindow(w.id)}>
                            Close window
                          </Button>
                        ) : (
                          '—'
                        )}
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </ContentPanel>
        </TabsContent>

        <TabsContent value="reentry">
          <ContentPanel>
            <Textarea
              label="Paper tokens (one per line: queueType,tokenNumber,patientId[,patientName][,priority])"
              rows={5}
              placeholder={'consult,DT-01,11111111-1111-1111-1111-111111111111,Devika Paper\nconsult,DT-02,22222222-2222-2222-2222-222222222222,Eshan Paper,urgent'}
              value={tokenText}
              onChange={(e) => setTokenText(e.target.value)}
            />
            <div style={{ height: 12 }} />
            <Input
              label="Paper check-ins (visit ids, comma-separated)"
              placeholder="33333333-3333-3333-3333-333333333333,44444444-4444-4444-4444-444444444444"
              value={visitText}
              onChange={(e) => setVisitText(e.target.value)}
            />
            <div style={{ height: 12 }} />
            <Button loading={busy} onClick={() => void submitReentry()}>
              Re-enter paper records
            </Button>
          </ContentPanel>
        </TabsContent>

        <TabsContent value="pack">
          <ContentPanel>
            <Stack direction="row" gap={12}>
              <Button loading={busy} onClick={() => void loadPack()}>
                Load pack data
              </Button>
              <Button variant="outline" disabled={!pack} onClick={() => window.print()}>
                Print
              </Button>
            </Stack>
            <div style={{ height: 12 }} />
            <p style={{ color: 'var(--sq-fg-muted)', fontSize: 13 }}>
              The pack prints today&apos;s appointment sheet, the live queue snapshot and blank paper forms for tokens and
              check-ins.
            </p>
          </ContentPanel>
          {pack ? (
            <div id="downtime-pack" style={{ marginTop: 16, border: '1px solid var(--sq-border)', padding: 16 }}>
              <h2 style={{ fontSize: 18, margin: '0 0 4px' }}>Downtime pack — reception &amp; queue</h2>
              <p style={{ fontSize: 12, margin: '0 0 12px' }}>
                Printed {new Date().toISOString().slice(0, 19).replace('T', ' ')} · Hospital {hospitalId?.slice(0, 8) ?? '—'}
                {pack.window ? ` · Downtime since ${pack.window.startedAt.slice(0, 19).replace('T', ' ')}: ${pack.window.reason}` : ' · No open downtime window'}
              </p>
              <h3 style={{ fontSize: 14, margin: '0 0 4px' }}>A. Today&apos;s appointment sheet</h3>
              <table>
                <thead>
                  <tr>
                    <th>Time</th>
                    <th>Patient</th>
                    <th>Status</th>
                    <th>Seen (paper)</th>
                  </tr>
                </thead>
                <tbody>
                  {pack.appointments.length === 0 ? (
                    <tr>
                      <td colSpan={4}>No appointments today.</td>
                    </tr>
                  ) : (
                    pack.appointments.map((a, i) => (
                      <tr key={i}>
                        <td>{a.time}</td>
                        <td>{a.patient}</td>
                        <td>{a.status}</td>
                        <td />
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
              <h3 style={{ fontSize: 14, margin: '12px 0 4px' }}>B. Live queue snapshot at print time</h3>
              <table>
                <thead>
                  <tr>
                    <th>Token</th>
                    <th>Patient</th>
                    <th>Priority</th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {pack.board.length === 0 ? (
                    <tr>
                      <td colSpan={4}>Queue empty.</td>
                    </tr>
                  ) : (
                    pack.board.map((b) => (
                      <tr key={b.tokenNumber}>
                        <td>{b.tokenNumber}</td>
                        <td>{b.patientName ?? '—'}</td>
                        <td>{b.priority}</td>
                        <td>{b.status}</td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
              <h3 style={{ fontSize: 14, margin: '12px 0 4px' }}>C. Paper token forms (transcribe to the re-entry tool)</h3>
              {Array.from({ length: BLANK_FORMS }, (_, i) => (
                <div key={i} className="paper-form">
                  <div>Paper token #: ____________</div>
                  <div>Patient name: ______________________________</div>
                  <div>Phone: ______________ Department: ______________</div>
                  <div>Time: ________ Priority (urgent?): ________</div>
                </div>
              ))}
            </div>
          ) : null}
        </TabsContent>
      </Tabs>
    </div>
  );
}
