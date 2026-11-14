import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  enqueueOfflineWrite,
  flushOutbox,
  pendingOfflineWrites,
  type OutboxEntry,
} from './outbox';
import { registerOfflineWriteRule, resetOfflineWriteRules } from './write-whitelist';

const BASE = 'http://api.test/api/v1';

function lastCall(): { url: string; init: RequestInit } {
  const call = vi.mocked(fetch).mock.calls.at(-1);
  if (!call) throw new Error('no fetch call recorded');
  return { url: String(call[0]), init: call[1] ?? {} };
}

function okResponse(status = 200): Response {
  return { ok: status >= 200 && status < 300, status } as Response;
}

describe('outbox (chunk-00 item 13 store-and-forward)', () => {
  beforeEach(() => {
    resetOfflineWriteRules();
    localStorage.clear();
    vi.unstubAllGlobals();
  });

  afterEach(() => {
    resetOfflineWriteRules();
    vi.unstubAllGlobals();
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
    vi.stubGlobal('fetch', vi.fn(async () => okResponse(200)));

    const result = await flushOutbox({ baseUrl: BASE });

    expect(result).toEqual({ sent: 1, rejected: 0, remaining: 0 });
    const { url, init } = lastCall();
    expect(url).toBe(`${BASE}/tasks/123/status`);
    expect(init.method).toBe('PATCH');
    expect(init.body).toBe('{"status":"completed"}');
    expect(new Headers(init.headers).get('Idempotency-Key')).toBe(idempotencyKey);
    expect(pendingOfflineWrites()).toHaveLength(0);
  });

  it('keeps the entry and its key on a network failure; the retry reuses the same key', async () => {
    registerOfflineWriteRule({ method: 'PATCH', pathPattern: /^\/tasks\/[^/]+\/status$/ });
    const { idempotencyKey } = enqueueOfflineWrite('PATCH', '/tasks/1/status', {
      status: 'completed',
    });
    registerOfflineWriteRule({ method: 'POST', pathPattern: /^\/tasks$/ });
    const second = enqueueOfflineWrite('POST', '/tasks', { title: 'second' });
    vi.stubGlobal('fetch', vi.fn().mockRejectedValueOnce(new Error('offline')));

    const result = await flushOutbox({ baseUrl: BASE });

    expect(result).toEqual({ sent: 0, rejected: 0, remaining: 2 });
    expect(vi.mocked(fetch).mock.calls).toHaveLength(1);

    vi.stubGlobal('fetch', vi.fn(async () => okResponse(200)));
    const retry = await flushOutbox({ baseUrl: BASE });
    expect(retry).toEqual({ sent: 2, rejected: 0, remaining: 0 });

    const calls = vi.mocked(fetch).mock.calls;
    // The retried write carries the SAME key: the server replays instead of re-executing.
    expect(new Headers(calls[0]?.[1]?.headers).get('Idempotency-Key')).toBe(idempotencyKey);
    expect(new Headers(calls[1]?.[1]?.headers).get('Idempotency-Key')).toBe(second.idempotencyKey);
    expect(pendingOfflineWrites()).toHaveLength(0);
  });

  it('stops on 5xx without dropping anything, then drains later', async () => {
    registerOfflineWriteRule({ method: 'POST', pathPattern: /^\/tasks$/ });
    enqueueOfflineWrite('POST', '/tasks', { title: 'a' });
    vi.stubGlobal('fetch', vi.fn(async () => okResponse(503)));

    const failed = await flushOutbox({ baseUrl: BASE });
    expect(failed).toEqual({ sent: 0, rejected: 0, remaining: 1 });

    vi.stubGlobal('fetch', vi.fn(async () => okResponse(201)));
    const done = await flushOutbox({ baseUrl: BASE });
    expect(done).toEqual({ sent: 1, rejected: 0, remaining: 0 });
  });

  it('drops a definitive 4xx (the server ruled; replaying cannot help)', async () => {
    registerOfflineWriteRule({ method: 'PATCH', pathPattern: /^\/tasks\/[^/]+\/status$/ });
    enqueueOfflineWrite('PATCH', '/tasks/9/status', { status: 'completed' });
    vi.stubGlobal('fetch', vi.fn(async () => okResponse(400)));

    const result = await flushOutbox({ baseUrl: BASE });

    expect(result).toEqual({ sent: 0, rejected: 1, remaining: 0 });
    expect(pendingOfflineWrites()).toHaveLength(0);
  });

  it('parks the queue on 401 (stale token) instead of dropping the write', async () => {
    registerOfflineWriteRule({ method: 'PATCH', pathPattern: /^\/tasks\/[^/]+\/status$/ });
    enqueueOfflineWrite('PATCH', '/tasks/1/status', { status: 'completed' });
    vi.stubGlobal('fetch', vi.fn(async () => okResponse(401)));

    const result = await flushOutbox({ baseUrl: BASE });

    expect(result).toEqual({ sent: 0, rejected: 0, remaining: 1 });
    expect(pendingOfflineWrites()).toHaveLength(1);
  });

  it('flushing an empty outbox is a no-op', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => okResponse(200)));
    const result = await flushOutbox({ baseUrl: BASE });
    expect(result).toEqual({ sent: 0, rejected: 0, remaining: 0 });
    expect(vi.mocked(fetch).mock.calls).toHaveLength(0);
  });
});
