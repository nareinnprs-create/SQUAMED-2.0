import { defineConfig, devices } from '@playwright/test';
import path from 'path';

// Visual regression (gate 7) runs ONLY the production build on the fixed gate
// port, chromium only, serially, with committed baselines — deterministic by
// construction. The shared e2e config in playwright.config.ts stays untouched.
export default defineConfig({
  testDir: 'tests/visual',
  fullyParallel: false,
  workers: 1,
  forbidOnly: true,
  retries: 0,
  reporter: [['list']],
  snapshotPathTemplate: '{testDir}/__screenshots__/{testFileName}/{arg}{ext}',
  use: {
    ...devices['Desktop Chrome'],
    baseURL: 'http://127.0.0.1:3100',
    colorScheme: 'light',
  },
  expect: {
    timeout: 20000,
    toHaveScreenshot: {
      maxDiffPixelRatio: 0.02,
      animations: 'disabled',
      // Metric-compatible font stack keeps Windows-generated baselines
      // comparable with the Liberation Sans fonts installed on CI runners.
      stylePath: path.resolve('tests/visual/font-normalize.css'),
    },
  },
  webServer: {
    command: 'node scripts/next-serve.mjs',
    url: 'http://127.0.0.1:3100/login',
    reuseExistingServer: true,
    timeout: 180000,
  },
});
