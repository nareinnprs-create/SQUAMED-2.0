import { describe, expect, it } from 'vitest';
import { visibleNavItems } from './nav-entitlements';

const items = [{ id: 'dashboard' }, { id: 'ai' }, { id: 'scribe' }, { id: 'patients' }];

describe('visibleNavItems', () => {
  it('shows everything when the hospital is unmanaged or the entitlement call failed', () => {
    expect(visibleNavItems(items, null).map((i) => i.id)).toEqual(['dashboard', 'ai', 'scribe', 'patients']);
  });

  it('hides the AI and scribe modules on a CORE plan', () => {
    expect(visibleNavItems(items, ['patients', 'billing']).map((i) => i.id)).toEqual(['dashboard', 'patients']);
  });

  it('keeps every item on a FULL plan', () => {
    expect(visibleNavItems(items, ['patients', 'ai', 'scribe']).map((i) => i.id)).toEqual([
      'dashboard',
      'ai',
      'scribe',
      'patients',
    ]);
  });

  it('never hides an item that has no feature mapping', () => {
    expect(visibleNavItems([{ id: 'unknown-module' }], []).map((i) => i.id)).toEqual(['unknown-module']);
  });
});
