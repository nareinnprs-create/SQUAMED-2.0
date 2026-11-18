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

export type FastPathEntry = {
  queueType: string;
  tokenNumber: string;
  patientName: string | null;
  status: string;
  priority: string;
  estimatedWaitMinutes: number | null;
  calledAt: string | null;
};

/** 01.09 triage-lite: walk-in tag -> the queue priority it boosts to (green leaves routine as-is). */
export const TRIAGE_TAG_PRIORITY: Record<string, string> = {
  red: 'emergency',
  yellow: 'urgent',
  green: 'routine',
};

export const QUEUE_TYPES = ['consult', 'registration', 'pharmacy', 'billing'];

const statusVariants: Record<string, BadgeVariant> = {
  waiting: 'warning',
  called: 'information',
  'in-progress': 'success',
  completed: 'neutral',
  skipped: 'critical',
  postponed: 'neutral',
};

export type FastPathMessage = { tone: StatusTone; label: string };

export function tagMessage(
  tokenNumber: string,
  tag: string,
  boostedTo: string,
): FastPathMessage {
  if (tag === 'green') return { tone: 'information', label: `${tokenNumber} stays ${boostedTo} (green tag)` };
  return { tone: 'success', label: `${tokenNumber} boosted to ${boostedTo} (${tag} tag)` };
}

export function EmergencyWorkspace({
  apiFetch,
}: {
  apiFetch: <T>(path: string, options?: RequestInit) => Promise<T>;
}) {
  const [queueType, setQueueType] = useState('consult');
  const [entries, setEntries] = useState<FastPathEntry[]>([]);
  const [message, setMessage] = useState<FastPathMessage | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);

  const loadBoard = useCallback(async () => {
    try {
      const board = await apiFetch<FastPathEntry[]>(`/queue/board?queueType=${encodeURIComponent(queueType)}`);
      setEntries(Array.isArray(board) ? board : []);
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load queue board');
    }
  }, [apiFetch, queueType]);

  useEffect(() => {
    const timer = setInterval(() => void loadBoard(), 5000);
    void loadBoard();
    return () => clearInterval(timer);
  }, [loadBoard]);

  async function applyTag(tokenNumber: string, tag: string) {
    const key = `${tag}:${tokenNumber}`;
    setBusy(key);
    try {
      const entry = await apiFetch<FastPathEntry>(`/queue/entries/${queueType}/${tokenNumber}/triage-lite`, {
        method: 'POST',
        body: JSON.stringify({ tag }),
      });
      setMessage(tagMessage(tokenNumber, tag, entry.priority));
      await loadBoard();
    } catch (e) {
      setMessage({ tone: 'critical', label: e instanceof Error ? e.message : `Failed to tag ${tokenNumber}` });
    } finally {
      setBusy(null);
    }
  }

  return (
    <div style={{ padding: 24 }}>
      <PageHeader
        title="Emergency fast-path"
        description="Tag walk-ins red, yellow or green at triage — red boosts the token to emergency priority on every display."
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
          </>
        }
      />

      {message ? <StatusIndicator tone={message.tone} label={message.label} /> : null}
      {error ? <StatusIndicator tone="critical" label={error} /> : null}

      <div style={{ height: 16 }} />

      <ContentPanel>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Token</TableHead>
              <TableHead>Patient name</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Priority</TableHead>
              <TableHead>Triage-lite tag</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {entries.length === 0 ? (
              <TableRow>
                <TableCell>No walk-ins on the board.</TableCell>
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
                    {entry.status === 'waiting' ? (
                      <Stack direction="row" gap={6}>
                        <Button
                          size="sm"
                          variant="danger"
                          loading={busy === `red:${entry.tokenNumber}`}
                          onClick={() => void applyTag(entry.tokenNumber, 'red')}
                        >
                          Red
                        </Button>
                        <Button
                          size="sm"
                          variant="outline"
                          loading={busy === `yellow:${entry.tokenNumber}`}
                          onClick={() => void applyTag(entry.tokenNumber, 'yellow')}
                        >
                          Yellow
                        </Button>
                        <Button
                          size="sm"
                          variant="success"
                          loading={busy === `green:${entry.tokenNumber}`}
                          onClick={() => void applyTag(entry.tokenNumber, 'green')}
                        >
                          Green
                        </Button>
                      </Stack>
                    ) : (
                      <span style={{ color: 'var(--sq-fg-muted)' }}>—</span>
                    )}
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
