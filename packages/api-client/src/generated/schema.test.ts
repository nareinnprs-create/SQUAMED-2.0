import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

describe('generated OpenAPI types', () => {
  it('carry the sha256 stamp of the committed contract snapshot', () => {
    const snapshot = readFileSync(
      new URL('../../../../docs/contracts/openapi-v1.json', import.meta.url),
      'utf8',
    );
    const generated = readFileSync(new URL('./schema.d.ts', import.meta.url), 'utf8');
    const stamp = createHash('sha256').update(snapshot).digest('hex');
    expect(generated).toContain(`// openapi snapshot sha256: ${stamp}`);
  });

  it('declare the contract foundation surfaces', () => {
    const generated = readFileSync(new URL('./schema.d.ts', import.meta.url), 'utf8');
    expect(generated).toContain('/api/v1/patients');
    expect(generated).toContain('/api/v1/master-data/drugs');
  });
});
