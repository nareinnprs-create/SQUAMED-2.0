/**
 * Chunk-00 item 13: store-and-forward outbox for offline writes.
 *
 * Accepted writes (per the write-whitelist) are persisted to localStorage with
 * a client-generated Idempotency-Key before any send attempt. The key stays
 * attached to the entry until the server answers definitively, so a replayed
 * flush can never execute the write twice — the server-side IdempotencyFilter
 * (00.02) turns the repeated send into a byte-identical stored response.
 */

import { isWriteAllowed } from './write-whitelist';

const STORAGE_KEY = 'squamed.outbox.v1';

export type QueuedWriteMethod = 'POST' | 'PUT' | 'PATCH' | 'DELETE';

export interface OutboxEntry {
  idempotencyKey: string;
  method: QueuedWriteMethod;
  /** API path without the base URL, e.g. /tasks/123/status. */
  path: string;
  /** Serialized request body: flushed byte-for-byte so the server payload hash is stable. */
  body: string | null;
  enqueuedAt: string;
}

export interface EnqueueResult {
  accepted: boolean;
  idempotencyKey?: string;
}

export interface FlushResult {
  /** 2xx replies: the write is confirmed done and removed. */
  sent: number;
  /** Definitive 4xx replies (incl. 409 conflict): the server ruled; keeping the entry would loop forever. */
  rejected: number;
  /** Entries still queued (network error, 408/429/5xx): keys persist, no double-send. */
  remaining: number;
}

export interface FlushOptions {
  /** Defaults to the shared API base URL. */
  baseUrl?: string;
  /** Extra headers, e.g. the Authorization bearer header. */
  headers?: Record<string, string>;
  /** Injectable fetch for tests; defaults to the global. */
  fetchImpl?: typeof fetch;
}

function load(): OutboxEntry[] {
  if (typeof window === 'undefined' || typeof localStorage === 'undefined') return [];
  const raw = localStorage.getItem(STORAGE_KEY);
  if (!raw) return [];
  try {
    const parsed: unknown = JSON.parse(raw);
    return Array.isArray(parsed) ? (parsed as OutboxEntry[]) : [];
  } catch {
    return [];
  }
}

function save(entries: OutboxEntry[]): void {
  if (typeof window === 'undefined' || typeof localStorage === 'undefined') return;
  localStorage.setItem(STORAGE_KEY, JSON.stringify(entries));
}

export function pendingOfflineWrites(): readonly OutboxEntry[] {
  return load();
}

export function enqueueOfflineWrite(
  method: QueuedWriteMethod,
  path: string,
  body?: unknown,
): EnqueueResult {
  if (!isWriteAllowed(method, path)) return { accepted: false };
  const entry: OutboxEntry = {
    // crypto.randomUUID requires a secure context; the PWA targets HTTPS/localhost.
    idempotencyKey: crypto.randomUUID(),
    method,
    path,
    body: body === undefined ? null : JSON.stringify(body),
    enqueuedAt: new Date().toISOString(),
  };
  save([...load(), entry]);
  return { accepted: true, idempotencyKey: entry.idempotencyKey };
}

function removeEntry(idempotencyKey: string): void {
  save(load().filter((entry) => entry.idempotencyKey !== idempotencyKey));
}

/** Retries only while the server answer is not definitive (401 auth-pending/408/429/5xx/network error stop the queue). */
export async function flushOutbox(options: FlushOptions = {}): Promise<FlushResult> {
  const entries = load();
  if (entries.length === 0) return { sent: 0, rejected: 0, remaining: 0 };
  const baseUrl = options.baseUrl ?? '';
  const send = options.fetchImpl ?? fetch;
  let sent = 0;
  let rejected = 0;
  for (const entry of entries) {
    let status = 0;
    try {
      const response = await send(`${baseUrl}${entry.path}`, {
        method: entry.method,
        body: entry.body,
        headers: {
          ...(entry.body === null ? {} : { 'Content-Type': 'application/json' }),
          'Idempotency-Key': entry.idempotencyKey,
          ...options.headers,
        },
      });
      status = response.status;
    } catch {
      // Offline again: keep the entry (and its key) and stop; a later flush replays it once.
      break;
    }
    if (status === 401 || status === 408 || status === 429 || status >= 500) break;
    if (status >= 200 && status < 300) sent += 1;
    else rejected += 1;
    removeEntry(entry.idempotencyKey);
  }
  return { sent, rejected, remaining: entries.length - sent - rejected };
}
