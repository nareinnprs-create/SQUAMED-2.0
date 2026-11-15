import { test, expect } from '@playwright/test';
import { readFile } from 'node:fs/promises';
import path from 'path';
import {
  SCAN_ROUTES,
  installApiFixtures,
  seedScanSession,
  settleForScan,
} from '../../scripts/lib/scan-fixtures.mjs';

// Gate 7 visual regression: one committed baseline screenshot per scanned
// route, taken from the production build with the deterministic fixture API.
// Refresh baselines with `npm run visual-regression:update` (run from the repo root).
test.describe('visual regression — scanned routes', () => {
  for (const route of SCAN_ROUTES) {
    test(`route ${route.path} matches its committed baseline`, async ({ page }) => {
      seedScanSession(page);
      await installApiFixtures(page);
      await page.goto(route.path, { waitUntil: 'domcontentloaded' });
      // Inject the screenshot-normalizing CSS once up front: the same font
      // parity as the config's toHaveScreenshot stylePath plus an
      // animation/transition kill, so the capture below needs no per-capture
      // page manipulation — that manipulation races the compositor into blank
      // below-fold tiles on pages taller than the screen.
      await page.addStyleTag({
        content: `${await readFile(path.resolve('tests/visual/font-normalize.css'), 'utf8')}\n*, *::before, *::after { animation: none !important; transition: none !important; }`,
      });
      // '/' is a server-side redirect to /login, so it settles as the login page.
      await settleForScan(page, { authenticated: route.path !== '/login' && route.path !== '/' && !route.public });
      const viewport = page.viewportSize() ?? { width: 1280, height: 720 };
      const fullHeight = await page.evaluate(() => document.documentElement.scrollHeight);
      if (fullHeight <= viewport.height) {
        await expect(page).toHaveScreenshot(`${route.label}.png`, { fullPage: true });
        return;
      }
      // Pages taller than the viewport cannot use toHaveScreenshot: its
      // stability loop re-captures in rapid succession and Chromium drops the
      // raster below the original screen height mid-loop, letting two identical
      // blank captures win. Size the viewport to the page and compare one
      // unprepared capture instead.
      await page.setViewportSize({ width: viewport.width, height: fullHeight });
      await page.waitForTimeout(250);
      const shot = await page.screenshot({ fullPage: true, caret: 'initial', animations: 'allow' });
      expect(shot).toMatchSnapshot(`${route.label}.png`, { maxDiffPixelRatio: 0.02 });
    });
  }
});
