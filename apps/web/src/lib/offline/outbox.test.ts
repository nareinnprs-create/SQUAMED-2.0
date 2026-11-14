import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import {
  enqueueOfflineWrite,
  flushOutbox,
  pendingOfflineWrites,
  type OutboxEntry,
} from './outbox';
import { registerOfflineWriteRule, resetOfflineWriteRules } from './write-whitelist';

const BASE = 'http://api.test/api/v1';

interface FetchCall {
  url: string;
  init: RequestInit;
}

let calls: FetchCall[] = [];

function scriptedFetch(status: number | 'network-error'): typeof fetch {
  return async (input, init) => {
    calls.push({ url: String(input), init: init ?? {} });
    if (status === 'network-error') throw new Error('offline');
    return new Response(null, { status });
  };
}

function header(call: FetchCall | undefined, name: string): string | null {
  return new Headers(call?.init.headers).get(name);
}

describe('outbox (chunk-00 item 13 store-and-forward)', () => {
  beforeEach(() => {
    resetOfflineWriteRules();
    localStorage.clear();
    calls = [];
  });

  afterEach(() => {
    resetOfflineWriteRules();
  });

  it('rejects writes that are not whitelisted and stores nothing', () => {
    const result = enqueueOfflineWrite('PATCH', '/tasks/123/status', { status: 'completed' });
    expect(result.accepted).toBe(false);
    expect(result.idempotencyKey).toBeUndefined();
    expect(pendingOfflineWrites()).toHaveLength(0);
  });

  it('persists an accepted write with a client-generated idempotency key', () => {
    registerOfflineWriteRule({ method: 'PATCH', pathPattern: /^\/tasks\/[^/]+\/status$/ });
    const result = enqueueOfflineWrite('PATCH', '/tasks/123/status', { status: 'completed' });
    expect(result.accepted).toBe(true);
    expect(result.idempotencyKey).toMatch(/^[0-9a-f-]{36}$/);

    const entries = pendingOfflineWrites();
    expect(entries).toHaveLength(1);
    const entry: OutboxEntry = entries[0];
    expect(entry.idempotencyKey).toBe(result.idempotencyKey);
    expect(entry.method).toBe('PATCH');
    expect(entry.path).toBe('/tasks/123/status');
    expect(entry.body).toBe('{"status":"completed"}');
  });

  it('flushes with the stored key and removes the entry on a 2xx reply', async () => {
    registerOfflineWriteRule({ method: 'PATCH', pathPattern: /^\/tasks\/[^/]+\/status$/ });
    const { idempotencyKey } = enqueueOfflineWrite('PATCH', '/tasks/123/status', {
      status: 'completed',
    });

    const result = await flushOutbox({ baseUrl: BASE, fetchImpl: scriptedFetch(200) });

    expect(result).toEqual({ sent: 1, rejected: 0, remaining: 0 });
    expect(calls).toHaveLength(1);
    expect(calls[0]?.url).toBe(`${BASE}/tasks/123/status`);
    expect(calls[0]?.init.method).toBe('PATCH');
    expect(calls[0]?.init.body).toBe('{"status":"completed"}');
    expect(header(calls[0], 'Idempotency-Key')).toBe(idempotencyKey);
    expect(pendingOfflineWrites()).toHaveLength(0);
  });

  it('keeps the entry and its key on a network failure; the retry reuses the same key', async () => {
    registerOfflineWriteRule({ method: 'PATCH', pathPattern: /^\/tasks\/[^/]+\/status$/ });
    const { idempotencyKey } = enqueueOfflineWrite('PATCH', '/tasks/1/status', {
      status: 'completed',
    });
    registerOfflineWriteRule({ method: 'POST', pathPattern: /^\/tasks$/ });
    const second = enqueueOfflineWrite('POST', '/tasks', { title: 'second' });

    const failed = await flushOutbox({ baseUrl: BASE, fetchImpl: scriptedFetch('network-error') });
    expect(failed).toEqual({ sent: 0, rejected: 0, remaining: 2 });
    expect(calls).toHaveLength(1);

    const retry = await flushOutbox({ baseUrl: BASE, fetchImpl: scriptedFetch(200) });
    expect(retry).toEqual({ sent: 2, rejected: 0, remaining: 0 });

    // The retried write carries the SAME key: the server replays instead of re-executing.
    expect(header(calls[1], 'Idempotency-Key')).toBe(idempotencyKey);
    expect(header(calls[2], 'Idempotency-Key')).toBe(second.idempotencyKey);
    expect(pendingOfflineWrites()).toHaveLength(0);
  });

  it('stops on 5xx without dropping anything, then drains later', async () => {
    registerOfflineWriteRule({ method: 'POST', pathPattern: /^\/tasks$/ });
    enqueueOfflineWrite('POST', '/tasks', { title: 'a' });

    const failed = await flushOutbox({ baseUrl: BASE, fetchImpl: scriptedFetch(503) });
    expect(failed).toEqual({ sent: 0, rejected: 0, remaining: 1 });

    const done = await flushOutbox({ baseUrl: BASE, fetchImpl: scriptedFetch(201) });
    expect(done).toEqual({ sent: 1, rejected: 0, remaining: 0 });
  });

  it('drops a definitive 4xx (the server ruled; replaying cannot help)', async () => {
    registerOfflineWriteRule({ method: 'PATCH', pathPattern: /^\/tasks\/[^/]+\/status$/ });
    enqueueOfflineWrite('PATCH', '/tasks/9/status', { status: 'completed' });

    const result = await flushOutbox({ baseUrl: BASE, fetchImpl: scriptedFetch(400) });

    expect(result).toEqual({ sent: 0, rejected: 1, remaining: 0 });
    expect(pendingOfflineWrites()).toHaveLength(0);
  });

  it('parks the queue on 401 (stale token) instead of dropping the write', async () => {
    registerOfflineWriteRule({ method: 'PATCH', pathPattern: /^\/tasks\/[^/]+\/status$/ });
    enqueueOfflineWrite('PATCH', '/tasks/1/status', { status: 'completed' });

    const result = await flushOutbox({ baseUrl: BASE, fetchImpl: scriptedFetch(401) });

    expect(result).toEqual({ sent: 0, rejected: 0, remaining: 1 });
    expect(pendingOfflineWrites()).toHaveLength(1);
  });

  it('flushing an empty outbox is a no-op', async () => {
    const result = await flushOutbox({ baseUrl: BASE, fetchImpl: scriptedFetch(200) });
    expect(result).toEqual({ sent: 0, rejected: 0, remaining: 0 });
    expect(calls).toHaveLength(0);
  });
});
