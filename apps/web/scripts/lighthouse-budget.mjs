// Gate 7 · Lighthouse budget check against the built app (see .gate/GATES.md).
// Runs Lighthouse programmatically (mobile emulation) through chrome-launcher on
// the fixed gate port and asserts the budgets in lighthouse-budgets.json:
// category scores plus request-count and transfer-size resource budgets.
// Exits non-zero on any breach.
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { launch as launchChrome } from 'chrome-launcher';
import lighthouse from 'lighthouse';
import { BASE_URL, ensureChromium, startWebServer } from './lib/scan-server.mjs';

const budgetsPath = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  '..',
  'lighthouse-budgets.json',
);

const RESOURCE_TYPE_LABELS = {
  Document: 'document',
  Script: 'script',
  Stylesheet: 'stylesheet',
  Image: 'image',
  Font: 'font',
  Other: 'other',
};

function printTable(headers, rows) {
  const widths = headers.map((header, column) =>
    Math.max(header.length, ...rows.map((row) => String(row[column]).length)),
  );
  const line = (cells) => cells.map((cell, column) => String(cell).padEnd(widths[column])).join('  ');
  console.log(line(headers));
  console.log(widths.map((width) => '-'.repeat(width)).join('  '));
  for (const row of rows) console.log(line(row));
}

async function main() {
  // Bound the whole gate — Lighthouse internals have no timeout, a wedged
  // runner must never hold the CI step open.
  setTimeout(() => {
    console.error('GATE WATCHDOG: lighthouse budget check exceeded 8 minutes, terminating (hang guard)');
    process.exit(1);
  }, 8 * 60_000).unref();

  ensureChromium();
  const budgets = JSON.parse(await readFile(budgetsPath, 'utf8'));
  const target = `${BASE_URL}${budgets.url}`;
  const server = await startWebServer();
  let chrome;
  try {
    chrome = await launchChrome({ chromeFlags: ['--headless=new', '--no-first-run'] });
    const runnerResult = await lighthouse(
      target,
      {
        port: chrome.port,
        output: 'json',
        logLevel: 'error',
        onlyCategories: ['performance', 'accessibility'],
        formFactor: 'mobile',
        screenEmulation: { mobile: true, width: 412, height: 823, deviceScaleFactor: 2.625, disabled: false },
        throttling: budgets.throttling ?? undefined,
      },
    );
    const lhr = runnerResult.lhr;

    console.log(`Lighthouse ${lhr.lighthouseVersion} — ${target}`);

    const rows = [];
    const breaches = [];
    for (const [category, minimum] of Object.entries(budgets.categories)) {
      const score = lhr.categories[category]?.score ?? null;
      const met = score !== null && score >= minimum;
      if (!met) breaches.push(`category ${category}: ${score} < ${minimum}`);
      rows.push([category, score === null ? 'n/a' : score, minimum, met ? 'pass' : 'BREACH']);
    }
    printTable(['category', 'score', 'budget', 'result'], rows);
    console.log('');

    const networkItems = lhr.audits['network-requests']?.details?.items ?? [];
    const requestCount = networkItems.length;
    const byteItems = lhr.audits['total-byte-weight']?.details?.items ?? [];
    const bytesByType = {};
    for (const item of networkItems) {
      const label = RESOURCE_TYPE_LABELS[item.resourceType] ?? 'other';
      bytesByType[label] = (bytesByType[label] ?? 0) + (item.transferSize ?? 0);
    }

    const resourceRows = [];
    const countBudget = budgets.resourceCounts.requests;
    if (requestCount > countBudget) {
      breaches.push(`requests: ${requestCount} > ${countBudget}`);
    }
    resourceRows.push(['requests', requestCount, countBudget, requestCount > countBudget ? 'BREACH' : 'pass']);

    const sizeBudgets = budgets.resourceSizesKb;
    const totalKb = byteItems.reduce((sum, item) => sum + (item.transferSize ?? 0), 0) / 1024;
    for (const [label, budgetKb] of Object.entries(sizeBudgets)) {
      const actualKb = label === 'total' ? totalKb : (bytesByType[label] ?? 0) / 1024;
      const actualRounded = Math.round(actualKb * 10) / 10;
      if (actualKb > budgetKb) {
        breaches.push(`${label} transfer size: ${actualRounded} KB > ${budgetKb} KB`);
      }
      resourceRows.push([`${label} KB`, actualRounded, budgetKb, actualKb > budgetKb ? 'BREACH' : 'pass']);
    }
    printTable(['resource', 'actual', 'budget', 'result'], resourceRows);

    console.log('');
    if (breaches.length > 0) {
      console.error(`Gate 7 FAILED — ${breaches.length} budget breach(es):`);
      for (const breach of breaches) console.error(`  - ${breach}`);
      process.exitCode = 1;
    } else {
      console.log('Gate 7 PASSED — all Lighthouse budgets met.');
    }
  } finally {
    if (chrome) await chrome.kill();
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
