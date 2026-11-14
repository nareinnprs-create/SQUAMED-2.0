import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import {
  isWriteAllowed,
  registerOfflineWriteRule,
  resetOfflineWriteRules,
} from './write-whitelist';

describe('write-whitelist (chunk-00 item 13)', () => {
  beforeEach(() => {
    resetOfflineWriteRules();
  });

  afterEach(() => {
    resetOfflineWriteRules();
  });

  it('allows nothing in the empty v1 set', () => {
    expect(isWriteAllowed('POST', '/tasks/123/status')).toBe(false);
    expect(isWriteAllowed('PUT', '/patients/123')).toBe(false);
    expect(isWriteAllowed('DELETE', '/tasks/123')).toBe(false);
  });

  it('accepts a write only after its rule is registered', () => {
    registerOfflineWriteRule({ method: 'PATCH', pathPattern: /^\/tasks\/[^/]+\/status$/ });
    expect(isWriteAllowed('PATCH', '/tasks/123/status')).toBe(true);
    expect(isWriteAllowed('PATCH', '/tasks/123')).toBe(false);
    expect(isWriteAllowed('POST', '/tasks/123/status')).toBe(false);
  });

  it('matches the pattern, not just the prefix', () => {
    registerOfflineWriteRule({ method: 'POST', pathPattern: /^\/consents$/ });
    expect(isWriteAllowed('POST', '/consents')).toBe(true);
    expect(isWriteAllowed('POST', '/consents/123/withdraw')).toBe(false);
  });
});
