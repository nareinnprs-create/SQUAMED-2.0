// Shared server lifecycle for the a11y/perf/visual gate scripts.
// The gate scripts run against the PRODUCTION build (next start), never the dev server.
import { spawn, execSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export const WEB_PORT = 3100;
export const BASE_URL = `http://127.0.0.1:${WEB_PORT}`;

const webRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');

async function probe(url) {
  try {
    const response = await fetch(url, { signal: AbortSignal.timeout(2000) });
    return response.status > 0;
  } catch {
    return false;
  }
}

export async function isWebServerUp() {
  return probe(`${BASE_URL}/login`);
}

function stopProcessTree(child) {
  if (!child || child.exitCode !== null || child.killed) return;
  if (process.platform === 'win32') {
    try {
      execSync(`taskkill /pid ${child.pid} /T /F`, { stdio: 'ignore' });
    } catch {
      child.kill();
    }
  } else {
    child.kill('SIGTERM');
  }
}

// Ensures the Playwright chromium binary exists so the gate scripts self-provision in CI.
// System libs (fonts etc.) come from the workflow's `npx playwright install --with-deps chromium`
// step; this covers the browser binary itself and is a no-op when already installed.
export function ensureChromium() {
  execSync('npx playwright install chromium', { cwd: webRoot, stdio: 'inherit', shell: true });
}

export function requireProductionBuild() {
  const buildId = path.join(webRoot, '.next', 'BUILD_ID');
  if (!existsSync(buildId)) {
    throw new Error(
      `No production build found at ${buildId}. Run "npx turbo build --filter=@squamed/web" before the gate scripts.`,
    );
  }
}

// Starts `next start` on WEB_PORT, or reuses an already-running instance.
// Readiness is a poll loop (never a fixed sleep): probe every 250 ms until the
// server responds or the budget is exhausted, then kill the child on failure.
export async function startWebServer({ startTimeoutMs = 180000 } = {}) {
  if (await isWebServerUp()) {
    return { started: false, stop() {} };
  }
  requireProductionBuild();
  const child = spawn('npx', ['next', 'start', '-p', String(WEB_PORT)], {
    cwd: webRoot,
    stdio: ['ignore', 'pipe', 'pipe'],
    shell: true,
  });
  let serverLog = '';
  child.stdout.on('data', (chunk) => {
    serverLog += chunk;
  });
  child.stderr.on('data', (chunk) => {
    serverLog += chunk;
  });
  const deadline = Date.now() + startTimeoutMs;
  for (;;) {
    if (await isWebServerUp()) break;
    if (child.exitCode !== null) {
      throw new Error(`next start exited with code ${child.exitCode}\n${serverLog}`);
    }
    if (Date.now() > deadline) {
      stopProcessTree(child);
      throw new Error(`next start did not become ready within ${startTimeoutMs} ms\n${serverLog}`);
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  return {
    started: true,
    stop() {
      stopProcessTree(child);
    },
  };
}
