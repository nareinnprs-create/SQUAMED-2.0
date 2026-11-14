// Deterministic API fixtures for the gate scripts (axe scan, visual regression).
// The a11y/perf job has no backend, so the scan drives the real UI against a
// fixed, empty-but-shape-correct API surface: one seeded session plus generic
// list payloads, so every page renders its true components with stable content.
export const SCAN_ROUTES = [
  { path: '/', label: 'root' },
  { path: '/login', label: 'login' },
  { path: '/appointments', label: 'appointments' },
  { path: '/patients', label: 'patients' },
  { path: '/dashboard', label: 'dashboard' },
];

const SCAN_TOKENS = { access: 'scan-access-token', refresh: 'scan-refresh-token' };

const SCAN_USER = {
  userId: 'scan-user-0001',
  email: 'scan@squamed.local',
  displayName: 'Scan Operator',
  roles: ['ROLE_ADMIN'],
  permissions: ['*:*:*'],
  themePreference: 'system',
};

const ANALYTICS = {
  overview: {
    totalPatients: 4,
    patientsToday: 1,
    activeEncounters: 1,
    encountersToday: 2,
    completedEncountersToday: 1,
    appointmentsToday: 3,
    pendingAppointments: 1,
    queueWaiting: 2,
    avgWaitMinutes: 7,
    labOrdersToday: 2,
    labPending: 1,
    invoicesToday: 2,
    revenueTodayQar: 450,
    amountDueQar: 1200,
    dispensationsToday: 5,
  },
  patients: {
    total: 4,
    byGender: [],
    byBloodGroup: [],
    registrationsByMonth: [],
    byNationality: [],
  },
  encounters: { total: 2, today: 2, byStatus: [], byType: [], avgDurationMinutes: 18 },
  appointments: { total: 3, today: 3, byStatus: [], byType: [] },
  queue: { waiting: 2, calledToday: 4, completedToday: 2, avgWaitMinutes: 7, byServicePoint: [], byQueueType: [] },
  lab: { total: 2, today: 2, byStatus: [], byResultStatus: [], releasedToday: 1 },
  billing: {
    revenueTodayQar: 450,
    revenueThisWeekQar: 2600,
    revenueThisMonthQar: 9400,
    outstandingQar: 1200,
    byStatus: [],
    chargesPostedToday: 3,
    chargesByCategory: [],
  },
};

// localStorage seeding must happen in an init script so the first render is
// already authenticated (no redirect flash between page load and settle).
export function seedScanSession(page) {
  page.addInitScript(
    ([accessKey, refreshKey, userKey, access, refresh, user]) => {
      window.localStorage.setItem(accessKey, access);
      window.localStorage.setItem(refreshKey, refresh);
      window.localStorage.setItem(userKey, user);
    },
    [
      'squamed.auth.access-token',
      'squamed.auth.refresh-token',
      'squamed.auth.user',
      SCAN_TOKENS.access,
      SCAN_TOKENS.refresh,
      JSON.stringify(SCAN_USER),
    ],
  );
}

function jsonOk(data) {
  return {
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ success: true, data }),
  };
}

// Route order matters: Playwright gives the last registered handler priority,
// so the catch-all is registered first and specific payloads after it.
export async function installApiFixtures(page) {
  await page.route('**/api/v1/**', (route) => route.fulfill(jsonOk([])));
  await page.route('**/api/v1/analytics/**', (route) => {
    const key = route.request().url().split('/analytics/')[1]?.split('?')[0];
    return route.fulfill(jsonOk(ANALYTICS[key] ?? ANALYTICS.overview));
  });
  await page.route('**/api/v1/auth/refresh', (route) =>
    route.fulfill(
      jsonOk({
        status: 'SUCCESS',
        accessToken: SCAN_TOKENS.access,
        refreshToken: SCAN_TOKENS.refresh,
        mfaToken: null,
        user: SCAN_USER,
      }),
    ),
  );
}

// Waits until the route has fully rendered: network quiet, document complete,
// and — for authenticated shell routes — the AppShell topbar present.
export async function settleForScan(page, { authenticated = true } = {}) {
  await page.waitForLoadState('networkidle');
  await page.waitForFunction(() => document.readyState === 'complete');
  if (authenticated) {
    await page.getByRole('button', { name: 'Sign out' }).waitFor({ state: 'visible', timeout: 20000 });
  } else {
    await page.getByRole('heading', { name: 'SQUAMED' }).waitFor({ state: 'visible', timeout: 20000 });
  }
  // Bound fonts.ready — evaluate has no timeout, a stuck font request must never hang the gate.
  await page.evaluate(() => Promise.race([document.fonts.ready, new Promise((r) => setTimeout(r, 10000))]));
}
