// Gate 6 · axe-core scan of the built app (see .gate/GATES.md).
// Starts (or reuses) the production server on the fixed gate port, drives
// Playwright chromium through the key routes with a deterministic fixture API,
// injects axe-core into each page, and FAILS (exit 1) on any serious/critical
// violation. Prints a summary table of everything found.
import { readFile } from 'node:fs/promises';
import { createRequire } from 'node:module';
import { chromium } from '@playwright/test';
import { BASE_URL, ensureChromium, startWebServer } from './lib/scan-server.mjs';
import {
  SCAN_ROUTES,
  installApiFixtures,
  seedScanSession,
  settleForScan,
} from './lib/scan-fixtures.mjs';

const require = createRequire(import.meta.url);

const FAILING_IMPACTS = new Set(['serious', 'critical']);

function printTable(headers, rows) {
  const widths = headers.map((header, column) =>
    Math.max(header.length, ...rows.map((row) => String(row[column]).length)),
  );
  const line = (cells) => cells.map((cell, column) => String(cell).padEnd(widths[column])).join('  ');
  console.log(line(headers));
  console.log(widths.map((width) => '-'.repeat(width)).join('  '));
  for (const row of rows) console.log(line(row));
}

async function scanRoute(context, route) {
  const page = await context.newPage();
  // Belt and braces: every Playwright wait gets a default ceiling if it doesn't set one.
  page.setDefaultTimeout(30000);
  seedScanSession(page);
  await installApiFixtures(page);
  try {
    await page.goto(`${BASE_URL}${route.path}`, { waitUntil: 'domcontentloaded', timeout: 60000 });
    // '/' is a server-side redirect to /login, so it settles as the login page.
    await settleForScan(page, { authenticated: route.path !== '/login' && route.path !== '/' && !route.public });
    await page.addScriptTag({
      path: require.resolve('axe-core/axe.min.js'),
    });
    const results = await Promise.race([
      page.evaluate(() =>
        window.axe.run(document, {
          resultTypes: ['violations'],
          elementRef: false,
        }),
      ),
      new Promise((_, reject) => setTimeout(() => reject(new Error('axe.run did not settle within 90s')), 90_000)),
    ]);
    return results;
  } finally {
    await page.close();
  }
}

function violationRows(results) {
  return results.violations.map((violation) => [
    violation.impact ?? 'unknown',
    violation.id,
    violation.nodes.length,
    violation.help,
    violation.nodes
      .slice(0, 3)
      .map((node) => node.target.join(' '))
      .join(' | '),
  ]);
}

async function main() {
  // Bound the whole gate — evaluate() and addScriptTag() have no timeout, a wedged
  // runner must never hold the CI step open.
  setTimeout(() => {
    console.error('GATE WATCHDOG: axe scan exceeded 8 minutes, terminating (hang guard)');
    process.exit(1);
  }, 8 * 60_000).unref();

  console.log('starting production server');
  ensureChromium();
  const server = await startWebServer();
  const browser = await chromium.launch();
  const context = await browser.newContext({ colorScheme: 'light' });
  const axeHeader = await readFile(require.resolve('axe-core/package.json'), 'utf8');
  const axeVersion = JSON.parse(axeHeader).version;

  try {
    console.log(`axe-core ${axeVersion} — scanning ${SCAN_ROUTES.map((route) => route.path).join(', ')}`);
    const allResults = [];
    for (const route of SCAN_ROUTES) {
      console.log(`scanning ${route.path}`);
      const startedAt = Date.now();
      const results = await scanRoute(context, route);
      console.log(`axe run ${route.path} done in ${Date.now() - startedAt}ms`);
      allResults.push({ route, results });
    }

    const rows = allResults.flatMap(({ route, results }) =>
      violationRows(results).map((row) => [route.path, ...row]),
    );
    console.log('');
    console.log('Axe violations summary');
    console.log('======================');
    if (rows.length === 0) {
      console.log('(none — zero violations on all scanned routes)');
    } else {
      printTable(['route', 'impact', 'rule', 'nodes', 'help', 'selectors'], rows);
    }

    const failures = rows.filter((row) => FAILING_IMPACTS.has(String(row[1])));
    console.log('');
    if (failures.length > 0) {
      console.error(
        `Gate 6 FAILED — ${failures.length} serious/critical violation(s) across the scanned routes.`,
      );
      process.exitCode = 1;
    } else {
      const passing = rows.length === 0 ? 'zero violations' : `${rows.length} non-blocking violation(s)`;
      console.log(`Gate 6 PASSED — zero serious/critical violations (${passing}).`);
    }
  } finally {
    await context.close();
    await browser.close();
    server.stop();
  }
  // 250ms grace lets piped stdout flush, then hard-exits so orphaned
  // `next start` pipes can never keep the step alive.
  setTimeout(() => process.exit(process.exitCode ?? 0), 250).unref();
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
