/**
 * Chunk-00 item 13: write-whitelist framework for offline writes (empty v1).
 *
 * A write may be queued for store-and-forward replay only after a future
 * section registers an explicit rule here. Nothing is registered in v1: the
 * outbox accepts nothing until the owning feature opts in, which keeps the
 * offline replay surface auditable (each rule = one replayed-write contract
 * backed by the server IdempotencyFilter).
 */

export type OfflineWriteMethod = 'POST' | 'PUT' | 'PATCH' | 'DELETE';

export interface OfflineWriteRule {
  method: OfflineWriteMethod;
  /** Matched against the API path (no base URL), e.g. /^\/tasks\/[^/]+\/status$/. */
  pathPattern: RegExp;
}

const rules: OfflineWriteRule[] = [];

export function registerOfflineWriteRule(rule: OfflineWriteRule): void {
  rules.push(rule);
}

export function isWriteAllowed(method: string, path: string): boolean {
  return rules.some((rule) => rule.method === method && rule.pathPattern.test(path));
}

/** Test-fixture helper: restores the empty v1 rule set between tests. */
export function resetOfflineWriteRules(): void {
  rules.length = 0;
}
