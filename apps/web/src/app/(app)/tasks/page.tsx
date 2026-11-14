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

interface TaskLink {
  subjectType?: string | null;
  subjectId?: string | null;
}

interface TaskView {
  id: string;
  title: string;
  description?: string | null;
  status: string;
  priority: string;
  dueAt?: string | null;
  escalationCount: number;
  escalatedAt?: string | null;
  links: TaskLink[];
  completedAt?: string | null;
}

const statusOptions = ['all', 'pending', 'in_progress', 'escalated', 'completed'];

function statusTone(status: string): 'success' | 'critical' | 'warning' | 'information' {
  if (status === 'completed') return 'success';
  if (status === 'escalated') return 'critical';
  if (status === 'in_progress') return 'warning';
  return 'information';
}

export default function TasksPage() {
  const { apiFetch, isReady } = useApi();
  const [me, setMe] = useState<string | null>(null);
  const [tasks, setTasks] = useState<TaskView[]>([]);
  const [statusFilter, setStatusFilter] = useState('all');
  const [overdueOnly, setOverdueOnly] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!isReady) return;
    void (async () => {
      try {
        const data = await apiFetch<{ userId: string }>('/auth/me');
        setMe(data?.userId ?? null);
      } catch {
        setMe(null);
      }
    })();
  }, [apiFetch, isReady]);

  const load = useCallback(async () => {
    if (!me) return;
    try {
      const path =
        overdueOnly ? '/tasks/overdue' : `/tasks?assigneeId=${me}${statusFilter !== 'all' ? `&status=${statusFilter}` : ''}`;
      const data = await apiFetch<TaskView[]>(path);
      setTasks(data ?? []);
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load task inbox');
    }
  }, [apiFetch, me, statusFilter, overdueOnly]);

  useEffect(() => {
    if (isReady) void load();
  }, [isReady, load]);

  async function complete(taskId: string) {
    setBusyId(taskId);
    try {
      await apiFetch(`/tasks/${taskId}/status`, { method: 'PATCH', body: JSON.stringify({ status: 'completed' }) });
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to complete task');
    } finally {
      setBusyId(null);
    }
  }

  async function escalate(taskId: string) {
    setBusyId(taskId);
    try {
      await apiFetch(`/tasks/${taskId}/escalate`, { method: 'POST', body: JSON.stringify({ reason: 'Manual escalation from inbox' }) });
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to escalate task');
    } finally {
      setBusyId(null);
    }
  }

  return (
    <div style={{ padding: 24 }}>
      <PageHeader
        title="Task Inbox"
        description="Create, assign, execute, monitor, escalate, complete - tasks with SLA rules from hospital config (chunk-00 item 9)."
        actions={
          <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
            <div style={{ width: 150 }}>
              <Select value={statusFilter} onChange={(e) => { setStatusFilter(e.target.value); setOverdueOnly(false); }}>
                {statusOptions.map((s) => (
                  <option key={s} value={s}>
                    {s === 'all' ? 'All statuses' : s}
                  </option>
                ))}
              </Select>
            </div>
            <Button variant={overdueOnly ? 'primary' : 'outline'} size="sm" onClick={() => setOverdueOnly(!overdueOnly)}>
              Overdue only
            </Button>
            <Button variant="outline" size="sm" onClick={() => void load()}>
              Refresh
            </Button>
          </div>
        }
      />

      {error ? <StatusIndicator tone="critical" label={error} /> : null}

      <Stack gap={16}>
        <ContentPanel>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Task</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Priority</TableHead>
                <TableHead>Due</TableHead>
                <TableHead>Escalations</TableHead>
                <TableHead>Subjects</TableHead>
                <TableHead>Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {tasks.length === 0 ? (
                <TableRow>
                  <TableCell>No tasks in your inbox.</TableCell>
                </TableRow>
              ) : (
                tasks.map((task) => (
                  <TableRow key={task.id}>
                    <TableCell>
                      <div style={{ fontWeight: 600 }}>{task.title}</div>
                      {task.description ? <div style={{ fontSize: 12, color: 'var(--sq-fg-muted)' }}>{task.description}</div> : null}
                    </TableCell>
                    <TableCell>
                      <Badge variant={statusTone(task.status)}>{task.status}</Badge>
                    </TableCell>
                    <TableCell>{task.priority}</TableCell>
                    <TableCell style={{ fontSize: 12 }}>{task.dueAt ? new Date(task.dueAt).toLocaleString() : '-'}</TableCell>
                    <TableCell>
                      {task.escalationCount > 0 ? (
                        <Badge variant="critical">
                          {task.escalationCount}x
                        </Badge>
                      ) : (
                        '-'
                      )}
                    </TableCell>
                    <TableCell style={{ fontSize: 12 }}>
                      {task.links.length === 0 ? '-' : task.links.map((l) => l.subjectType).join(', ')}
                    </TableCell>
                    <TableCell>
                      <div style={{ display: 'flex', gap: 6 }}>
                        {task.status !== 'completed' && task.status !== 'cancelled' ? (
                          <>
                            <Button size="sm" disabled={busyId === task.id} onClick={() => void complete(task.id)}>
                              Complete
                            </Button>
                            {task.status !== 'escalated' ? (
                              <Button variant="outline" size="sm" disabled={busyId === task.id} onClick={() => void escalate(task.id)}>
                                Escalate
                              </Button>
                            ) : null}
                          </>
                        ) : (
                          <span style={{ fontSize: 12, color: 'var(--sq-fg-muted)' }}>closed</span>
                        )}
                      </div>
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
