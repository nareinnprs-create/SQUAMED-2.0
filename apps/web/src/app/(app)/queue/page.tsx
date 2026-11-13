'use client';

import { useCallback, useEffect, useState } from 'react';
import {
  Badge,
  Button,
  ContentPanel,
  PageHeader,
  Select,
  Stack,
  StatusIndicator,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@squamed/ui';
import type { BadgeVariant, StatusTone } from '@squamed/ui';
import { useApi } from '../../../lib/use-api';

const QUEUE_TYPES = ['registration', 'pharmacy', 'billing', 'consult'];

const statusVariants: Record<string, BadgeVariant> = {
  waiting: 'warning',
  called: 'information',
  'in-progress': 'success',
  completed: 'neutral',
  skipped: 'critical',
  postponed: 'neutral',
};

interface BoardEntry {
  queueType: string;
  tokenNumber: string;
  patientName: string | null;
  status: string;
  priority: string;
  estimatedWaitMinutes: number | null;
  calledAt: string | null;
}

interface QueueStats {
  queueType: string;
  waiting: number;
  inService: number;
  completed: number;
  avgWaitMinutes: number;
}

interface BoardMessage {
  tone: StatusTone;
  label: string;
}

type RowAction = 'call' | 'start' | 'complete' | 'skip';

export default function QueueBoardPage() {
  const { apiFetch, isReady } = useApi();
  const [queueType, setQueueType] = useState('consult');
  const [entries, setEntries] = useState<BoardEntry[]>([]);
  const [stats, setStats] = useState<QueueStats | null>(null);
  const [message, setMessage] = useState<BoardMessage | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [callingNext, setCallingNext] = useState(false);

  const loadBoard = useCallback(async () => {
    if (!queueType) return;
    try {
      const [board, statistics] = await Promise.all([
        apiFetch<BoardEntry[]>(`/queue/board?queueType=${encodeURIComponent(queueType)}`),
        apiFetch<QueueStats>(`/queue/statistics?queueType=${encodeURIComponent(queueType)}`),
      ]);
      setEntries(board ?? []);
      setStats(statistics ?? null);
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load queue board');
    }
  }, [apiFetch, queueType]);

  useEffect(() => {
    if (!isReady) return;
    void loadBoard();
    const timer = setInterval(() => void loadBoard(), 5000);
    return () => clearInterval(timer);
  }, [isReady, loadBoard]);

  async function applyAction(tokenNumber: string, action: RowAction) {
    const key = `${action}:${tokenNumber}`;
    setBusy(key);
    try {
      await apiFetch(`/queue/entries/${queueType}/${tokenNumber}/${action}`, {
        method: 'PATCH',
        body: '{}',
      });
      setMessage({ tone: 'success', label: `${action[0].toUpperCase()}${action.slice(1)} ${tokenNumber}` });
      await loadBoard();
    } catch (e) {
      setMessage({ tone: 'critical', label: e instanceof Error ? e.message : `Failed to ${action} ${tokenNumber}` });
    } finally {
      setBusy(null);
    }
  }

  async function callNext() {
    setCallingNext(true);
    try {
      const entry = await apiFetch<BoardEntry>('/queue/entries/call-next', {
        method: 'POST',
        body: JSON.stringify({ queueType }),
      });
      setMessage({ tone: 'success', label: `Called ${entry.tokenNumber} — ${entry.patientName ?? ''}` });
      await loadBoard();
    } catch (e) {
      const text = e instanceof Error ? e.message : 'Failed to call next';
      setMessage({
        tone: text.includes('QUEUE_EMPTY') ? 'information' : 'critical',
        label: text.includes('QUEUE_EMPTY') ? 'Queue empty' : text,
      });
    } finally {
      setCallingNext(false);
    }
  }

  function statCard(label: string, value: number | null | undefined) {
    return (
      <ContentPanel style={{ flex: 1, minWidth: 140 }}>
        <div style={{ fontSize: 13, color: 'var(--sq-fg-muted)' }}>{label}</div>
        <div style={{ fontSize: 28, fontWeight: 700, lineHeight: 1.2, marginTop: 4 }}>
          {value !== null && value !== undefined ? value : '—'}
        </div>
      </ContentPanel>
    );
  }

  return (
    <div style={{ padding: 24 }}>
      <PageHeader
        title="Queue Manager"
        description="Monitor patient flow and manage each service queue."
        actions={
          <>
            <div style={{ width: 200 }}>
              <Select label="Queue type" value={queueType} onChange={(e) => setQueueType(e.target.value)}>
                {QUEUE_TYPES.map((type) => (
                  <option key={type} value={type}>
                    {type}
                  </option>
                ))}
              </Select>
            </div>
            <Button variant="outline" onClick={() => void loadBoard()}>
              Refresh
            </Button>
            <Button loading={callingNext} onClick={() => void callNext()}>
              Call next
            </Button>
          </>
        }
      />

      {message ? <StatusIndicator tone={message.tone} label={message.label} /> : null}
      {error ? <StatusIndicator tone="critical" label={error} /> : null}

      <div style={{ height: 16 }} />

      <Stack direction="row" gap={12} wrap>
        {statCard('Waiting', stats?.waiting)}
        {statCard('In service', stats?.inService)}
        {statCard('Completed', stats?.completed)}
      </Stack>

      <div style={{ height: 16 }} />

      <ContentPanel>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Token</TableHead>
              <TableHead>Patient name</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Priority</TableHead>
              <TableHead>Estimated wait</TableHead>
              <TableHead>Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {entries.length === 0 ? (
              <TableRow>
                <TableCell>No patients in queue.</TableCell>
              </TableRow>
            ) : (
              entries.map((entry) => (
                <TableRow key={`${entry.queueType}-${entry.tokenNumber}`}>
                  <TableCell>
                    <strong>{entry.tokenNumber}</strong>
                  </TableCell>
                  <TableCell>{entry.patientName ?? '—'}</TableCell>
                  <TableCell>
                    <Badge variant={statusVariants[entry.status] ?? 'neutral'}>{entry.status}</Badge>
                  </TableCell>
                  <TableCell>
                    <Badge variant="information" size="sm">
                      {entry.priority}
                    </Badge>
                  </TableCell>
                  <TableCell>
                    {entry.estimatedWaitMinutes !== null && entry.estimatedWaitMinutes !== undefined
                      ? `${entry.estimatedWaitMinutes} min`
                      : '—'}
                  </TableCell>
                  <TableCell>
                    <Stack direction="row" gap={6}>
                      {entry.status === 'waiting' ? (
                        <>
                          <Button
                            size="sm"
                            loading={busy === `call:${entry.tokenNumber}`}
                            onClick={() => void applyAction(entry.tokenNumber, 'call')}
                          >
                            Call
                          </Button>
                          <Button
                            size="sm"
                            variant="outline"
                            loading={busy === `skip:${entry.tokenNumber}`}
                            onClick={() => void applyAction(entry.tokenNumber, 'skip')}
                          >
                            Skip
                          </Button>
                        </>
                      ) : null}
                      {entry.status === 'called' ? (
                        <>
                          <Button
                            size="sm"
                            loading={busy === `start:${entry.tokenNumber}`}
                            onClick={() => void applyAction(entry.tokenNumber, 'start')}
                          >
                            Start
                          </Button>
                          <Button
                            size="sm"
                            variant="outline"
                            loading={busy === `skip:${entry.tokenNumber}`}
                            onClick={() => void applyAction(entry.tokenNumber, 'skip')}
                          >
                            Skip
                          </Button>
                        </>
                      ) : null}
                      {entry.status === 'in-progress' ? (
                        <Button
                          size="sm"
                          variant="success"
                          loading={busy === `complete:${entry.tokenNumber}`}
                          onClick={() => void applyAction(entry.tokenNumber, 'complete')}
                        >
                          Complete
                        </Button>
                      ) : null}
                    </Stack>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </ContentPanel>
    </div>
  );
}