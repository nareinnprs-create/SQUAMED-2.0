// Long-running server process used as the Playwright webServer command for the
// visual regression config: serves the PRODUCTION build on the fixed gate port.
// Readiness is checked by Playwright (it polls the webServer url), so this
// process only needs to start the server and stay alive.
import path from 'node:path';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { ensureChromium, requireProductionBuild, WEB_PORT } from './lib/scan-server.mjs';

const webRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

ensureChromium();
requireProductionBuild();
const child = spawn('npx', ['next', 'start', '-p', String(WEB_PORT)], {
  cwd: webRoot,
  stdio: 'inherit',
  shell: true,
});
child.on('exit', (code) => process.exit(code ?? 1));
