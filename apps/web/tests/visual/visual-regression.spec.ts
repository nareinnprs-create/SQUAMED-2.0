import { test, expect } from '@playwright/test';
import {
  SCAN_ROUTES,
  installApiFixtures,
  seedScanSession,
  settleForScan,
} from '../../scripts/lib/scan-fixtures.mjs';

// Gate 7 visual regression: one committed baseline screenshot per scanned
// route, taken from the production build with the deterministic fixture API.
// Refresh baselines with `npm run visual-regression:update` (run from apps/web).
test.describe('visual regression — scanned routes', () => {
  for (const route of SCAN_ROUTES) {
    test(`route ${route.path} matches its committed baseline`, async ({ page }) => {
      seedScanSession(page);
      await installApiFixtures(page);
      await page.goto(route.path, { waitUntil: 'domcontentloaded' });
      // '/' is a server-side redirect to /login, so it settles as the login page.
      await settleForScan(page, { authenticated: route.path !== '/login' && route.path !== '/' });
      await expect(page).toHaveScreenshot(`${route.label}.png`, { fullPage: true });
    });
  }
});
