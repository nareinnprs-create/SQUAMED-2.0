// Appends the sha256 of the committed OpenAPI snapshot to the generated types so CI can
// detect codegen drift (see docs/development/contract-codegen.md).
import { createHash } from 'node:crypto';
import { readFileSync, writeFileSync } from 'node:fs';

const snapshotPath = new URL('../../../docs/contracts/openapi-v1.json', import.meta.url);
const generatedPath = new URL('../src/generated/schema.d.ts', import.meta.url);

const snapshot = readFileSync(snapshotPath, 'utf8');
const stamp = createHash('sha256').update(snapshot).digest('hex');

let generated = readFileSync(generatedPath, 'utf8');
// Replace any previous stamp so re-running codegen never accumulates stamps.
generated = generated.replace(/^\/\/ openapi snapshot sha256: [0-9a-f]{64}\n?/m, '');
if (!generated.endsWith('\n')) generated += '\n';
generated += `// openapi snapshot sha256: ${stamp}\n`;

writeFileSync(generatedPath, generated);
console.log(`stamped src/generated/schema.d.ts with snapshot sha256 ${stamp}`);
