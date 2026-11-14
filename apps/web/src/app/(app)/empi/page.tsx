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

interface EmpiReview {
  id: string;
  targetPatientId: string;
  duplicatePatientId: string;
  score: number;
  matchedOn: string[];
  status: string;
  decidedAt?: string | null;
  mergeId?: string | null;
  createdAt?: string | null;
}

interface MergeLog {
  id: string;
  targetPatientId: string;
  duplicatePatientId: string;
  mergeVersion: number;
  status: string;
  mergedAt?: string | null;
  unmergedAt?: string | null;
  moved: Record<string, number>;
}

const statusOptions = ['PENDING', 'APPROVED', 'REJECTED'];

export default function EmpiPage() {
  const { apiFetch, isReady } = useApi();
  const [statusFilter, setStatusFilter] = useState('PENDING');
  const [reviews, setReviews] = useState<EmpiReview[]>([]);
  const [merges, setMerges] = useState<MergeLog[]>([]);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const suffix = statusFilter ? `&status=${statusFilter}` : '';
      const [reviewData, mergeData] = await Promise.all([
        apiFetch<EmpiReview[]>(`/patients/empi/reviews?${suffix}`),
        apiFetch<MergeLog[]>('/patients/merges'),
      ]);
      setReviews(reviewData ?? []);
      setMerges(mergeData ?? []);
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load EMPI queue');
    }
  }, [apiFetch, statusFilter]);

  useEffect(() => {
    if (isReady) void load();
  }, [isReady, load]);

  async function decide(reviewId: string, action: 'approve' | 'reject') {
    setBusyId(reviewId);
    try {
      await apiFetch(`/patients/empi/reviews/${reviewId}/${action}`, { method: 'POST' });
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : `Failed to ${action} review`);
    } finally {
      setBusyId(null);
    }
  }

  async function unmerge(mergeId: string) {
    setBusyId(mergeId);
    try {
      await apiFetch(`/patients/merges/${mergeId}/unmerge`, { method: 'POST' });
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to unmerge');
    } finally {
      setBusyId(null);
    }
  }

  return (
    <div style={{ padding: 24 }}>
      <PageHeader
        title="EMPI Review"
        description="Approve or reject duplicate-patient match candidates. Approval requires a second person holding the merge-cosign permission."
        actions={
          <div style={{ width: 200 }}>
            <Select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
              <option value="">All statuses</option>
              {statusOptions.map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </Select>
          </div>
        }
      />

      {error ? <StatusIndicator tone="critical" label={error} /> : null}

      <Stack gap={16}>
        <ContentPanel>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Target</TableHead>
                <TableHead>Duplicate</TableHead>
                <TableHead>Score</TableHead>
                <TableHead>Matched on</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {reviews.length === 0 ? (
                <TableRow>
                  <TableCell>No reviews found.</TableCell>
                </TableRow>
              ) : (
                reviews.map((review) => (
                  <TableRow key={review.id}>
                    <TableCell style={{ fontFamily: 'monospace', fontSize: 12 }}>{review.targetPatientId.slice(0, 8)}</TableCell>
                    <TableCell style={{ fontFamily: 'monospace', fontSize: 12 }}>{review.duplicatePatientId.slice(0, 8)}</TableCell>
                    <TableCell>
                      <Badge variant={review.score >= 96 ? 'success' : 'warning'}>{review.score}</Badge>
                    </TableCell>
                    <TableCell>{review.matchedOn.join(', ') || '-'}</TableCell>
                    <TableCell>
                      <Badge variant={review.status === 'PENDING' ? 'information' : review.status === 'APPROVED' ? 'success' : 'critical'}>
                        {review.status}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      {review.status === 'PENDING' ? (
                        <Stack direction="row" gap={8}>
                          <Button size="sm" onClick={() => void decide(review.id, 'approve')} loading={busyId === review.id}>
                            Approve
                          </Button>
                          <Button variant="outline" size="sm" onClick={() => void decide(review.id, 'reject')} loading={busyId === review.id}>
                            Reject
                          </Button>
                        </Stack>
                      ) : review.mergeId ? (
                        <span style={{ fontSize: 12, color: 'var(--sq-fg-muted)' }}>merge {review.mergeId.slice(0, 8)}</span>
                      ) : (
                        '-'
                      )}
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </ContentPanel>

        <ContentPanel>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Merge</TableHead>
                <TableHead>Target</TableHead>
                <TableHead>Duplicate</TableHead>
                <TableHead>Version</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Moved</TableHead>
                <TableHead>Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {merges.length === 0 ? (
                <TableRow>
                  <TableCell>No merges recorded.</TableCell>
                </TableRow>
              ) : (
                merges.map((log) => (
                  <TableRow key={log.id}>
                    <TableCell style={{ fontFamily: 'monospace', fontSize: 12 }}>{log.id.slice(0, 8)}</TableCell>
                    <TableCell style={{ fontFamily: 'monospace', fontSize: 12 }}>{log.targetPatientId.slice(0, 8)}</TableCell>
                    <TableCell style={{ fontFamily: 'monospace', fontSize: 12 }}>{log.duplicatePatientId.slice(0, 8)}</TableCell>
                    <TableCell>v{log.mergeVersion}</TableCell>
                    <TableCell>
                      <Badge variant={log.status === 'MERGED' ? 'success' : 'warning'}>{log.status}</Badge>
                    </TableCell>
                    <TableCell>
                      {Object.entries(log.moved)
                        .filter(([, n]) => n > 0)
                        .map(([k, n]) => `${k}:${n}`)
                        .join(' ') || '-'}
                    </TableCell>
                    <TableCell>
                      {log.status === 'MERGED' ? (
                        <Button variant="outline" size="sm" onClick={() => void unmerge(log.id)} loading={busyId === log.id}>
                          Unmerge
                        </Button>
                      ) : (
                        <span style={{ fontSize: 12, color: 'var(--sq-fg-muted)' }}>reversed</span>
                      )}
                    </TableCell>
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
