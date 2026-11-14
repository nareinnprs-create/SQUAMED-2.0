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
import { useApi } from '../../../lib/use-api';

interface AuditLogView {
  id: string;
  actorUserId?: string | null;
  actorEmail?: string | null;
  action: string;
  entityType: string;
  entityId?: string | null;
  method?: string | null;
  path?: string | null;
  responseStatus?: number | null;
  occurredAt?: string | null;
  ipAddress?: string | null;
  chainSeq?: number | null;
  previousHash?: string | null;
  rowHash?: string | null;
  deviceId?: string | null;
  beforeState?: string | null;
  afterState?: string | null;
}

interface AuditVerifyView {
  ok: boolean;
  checked: number;
  breachedTenants: number;
  legacyRows: number;
  breaches: { hospitalId: string; seq?: number | null; reason: string }[];
  verifiedAt: string;
}

export default function AuditPage() {
  const { apiFetch, isReady } = useApi();
  const [logs, setLogs] = useState<AuditLogView[]>([]);
  const [verify, setVerify] = useState<AuditVerifyView | null>(null);
  const [limit, setLimit] = useState('50');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const data = await apiFetch<AuditLogView[]>(`/audit/logs?limit=${limit}`);
      setLogs(data ?? []);
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load audit log');
    }
  }, [apiFetch, limit]);

  useEffect(() => {
    if (isReady) void load();
  }, [isReady, load]);

  async function runVerify() {
    setBusy(true);
    try {
      const data = await apiFetch<AuditVerifyView>('/audit/verify', { method: 'POST' });
      setVerify(data ?? null);
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Chain verification failed');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div style={{ padding: 24 }}>
      <PageHeader
        title="Audit Viewer"
        description="Append-only audit trail, hash-chained per tenant (ADR-9). Verify integrity and review who did what, from which device."
        actions={
          <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
            <div style={{ width: 140 }}>
              <Select value={limit} onChange={(e) => setLimit(e.target.value)}>
                {['25', '50', '100'].map((n) => (
                  <option key={n} value={n}>
                    {n} rows
                  </option>
                ))}
              </Select>
            </div>
            <Button size="sm" onClick={() => void runVerify()} loading={busy}>
              Verify chain
            </Button>
            <Button variant="outline" size="sm" onClick={() => void load()}>
              Refresh
            </Button>
          </div>
        }
      />

      {error ? <StatusIndicator tone="critical" label={error} /> : null}

      <Stack gap={16}>
        {verify ? (
          <ContentPanel>
            <Stack gap={8}>
              <Badge variant={verify.ok ? 'success' : 'critical'}>
                {verify.ok ? 'Chain intact' : 'Chain broken'}
              </Badge>
              <span style={{ fontSize: 13 }}>
                {verify.checked} rows verified · {verify.breachedTenants} breached tenant(s) · {verify.legacyRows} legacy (pre-chain) rows
              </span>
              {verify.breaches.length > 0 ? (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Tenant</TableHead>
                      <TableHead>Seq</TableHead>
                      <TableHead>Reason</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {verify.breaches.map((b, i) => (
                      <TableRow key={`${b.hospitalId}-${i}`}>
                        <TableCell style={{ fontFamily: 'monospace', fontSize: 12 }}>{b.hospitalId.slice(0, 8)}</TableCell>
                        <TableCell>{b.seq ?? '-'}</TableCell>
                        <TableCell style={{ fontFamily: 'monospace', fontSize: 12 }}>{b.reason}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              ) : null}
            </Stack>
          </ContentPanel>
        ) : null}

        <ContentPanel>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>When</TableHead>
                <TableHead>Actor</TableHead>
                <TableHead>Action</TableHead>
                <TableHead>Entity</TableHead>
                <TableHead>Request</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Chain</TableHead>
                <TableHead>Device</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {logs.length === 0 ? (
                <TableRow>
                  <TableCell>No audit rows found.</TableCell>
                </TableRow>
              ) : (
                logs.map((log) => (
                  <TableRow key={log.id}>
                    <TableCell style={{ fontSize: 12 }}>{log.occurredAt ? new Date(log.occurredAt).toLocaleString() : '-'}</TableCell>
                    <TableCell style={{ fontSize: 12 }}>{log.actorEmail ?? log.actorUserId?.slice(0, 8) ?? 'system'}</TableCell>
                    <TableCell>
                      <Badge variant={log.responseStatus && log.responseStatus >= 400 ? 'critical' : 'information'}>{log.action}</Badge>
                    </TableCell>
                    <TableCell style={{ fontSize: 12 }}>
                      {log.entityType}
                      {log.entityId ? ` · ${log.entityId.slice(0, 8)}` : ''}
                    </TableCell>
                    <TableCell style={{ fontFamily: 'monospace', fontSize: 12 }}>
                      {log.method ?? ''} {log.path ?? ''}
                    </TableCell>
                    <TableCell>{log.responseStatus ?? '-'}</TableCell>
                    <TableCell style={{ fontFamily: 'monospace', fontSize: 12 }}>
                      {log.chainSeq != null ? `#${log.chainSeq} ${log.rowHash?.slice(0, 8) ?? ''}` : 'legacy'}
                    </TableCell>
                    <TableCell style={{ fontFamily: 'monospace', fontSize: 12 }}>{log.deviceId?.slice(0, 8) ?? '-'}</TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </ContentPanel>
      </Stack>
    </div>
  );
}
