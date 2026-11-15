// Playwright driver for the 00.15 app shell demo (called by demo-00.15-app-shell.ps1).
// Exercises the real UI on http://localhost:3000 against the live API on 8081:
// login, shell chrome (nav / hospital selector / universal search), patient
// context bar, task inbox, language toggle, sign out. ASCII-only output.
import { chromium } from 'playwright-core';

const api = 'http://localhost:8081';
const web = 'http://localhost:3100';
let pass = 0;
let fail = 0;
const fails = [];

function check(name, ok) {
  if (ok) {
    pass++;
    console.log(`PASS ${name}`);
  } else {
    fail++;
    fails.push(name);
    console.log(`FAIL ${name}`);
  }
}

async function apiCall(method, path, body, token) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const res = await fetch(`${api}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const json = await res.json().catch(() => null);
  return { status: res.status, data: json && json.data };
}

const suffix = Math.random().toString(36).slice(2, 10);

// 1 API bootstrap: admin, fresh hospital, doctor, patient (00.11 pattern).
const adminLogin = await apiCall('POST', '/api/v1/auth/login', {
  username: 'admin@squamed.local',
  password: 'Admin@12345',
});
const admin = adminLogin.data && adminLogin.data.accessToken;
check('admin api login', Boolean(admin));

const hospital = (await apiCall('POST', '/api/v1/hospital-config/hospitals', {
  name: `Shell Demo ${suffix}`,
  code: `SH-${suffix}`,
  country: 'Qatar',
}, admin)).data;
check('hospital created via api', Boolean(hospital && hospital.id));

const email = `shell-demo-${suffix}@test.local`;
await apiCall('POST', '/api/v1/auth/register', {
  email,
  password: 'Passw0rd!123',
  firstName: 'Doc',
  lastName: `Shell${suffix}`,
  roles: ['DOCTOR'],
  hospitalId: hospital.id,
}, undefined);
const doctorLogin = await apiCall('POST', '/api/v1/auth/login', {
  username: email,
  password: 'Passw0rd!123',
});
const doctor = doctorLogin.data && doctorLogin.data.accessToken;
check('doctor api login', Boolean(doctor));

const patientResponse = await apiCall('POST', '/api/v1/patients', {
  firstName: 'Zara',
  lastName: `Shell${suffix}`,
  gender: 'female',
  dateOfBirth: '1991-03-05',
  phone: `55${suffix.slice(0, 6)}`,
  qid: `7${suffix}`,
  nationality: 'Qatar',
  city: 'Doha',
}, admin);
const patientId = patientResponse.data && (patientResponse.data.patient
  ? patientResponse.data.patient.id
  : patientResponse.data.id);
check('patient created via api', Boolean(patientId));

// 2 UI: admin login through the login form.
const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1280, height: 800 } });
await page.goto(`${web}/login`);
await page.getByLabel('Email or phone').fill('admin@squamed.local');
await page.getByLabel('Password').fill('Admin@12345');
await page.getByRole('button', { name: 'Sign in' }).click();
await page.waitForURL('**/dashboard', { timeout: 30000 });
check('ui login lands on dashboard', page.url().includes('/dashboard'));

// 3 shell chrome: nav rail, hospital selector, universal search, user menu.
const hospitalSelect = page.getByRole('combobox', { name: 'Switch active hospital' });
check('hospital selector renders', await hospitalSelect.isVisible());
// The hospitals list loads async; wait for the option to appear.
const hospitalOption = hospitalSelect.locator('option', { hasText: `Shell Demo ${suffix}` }).first();
await hospitalOption.waitFor({ timeout: 15000 }).catch(() => {});
check(
  'hospital selector lists the new hospital',
  (await hospitalSelect.textContent()).includes(`Shell Demo ${suffix}`),
);
const search = page.getByRole('combobox', { name: 'Universal search' });
check('universal search renders', await search.isVisible());
check(
  'notifications bell renders',
  await page.getByRole('button', { name: 'Notifications' }).isVisible(),
);
check(
  'user menu shows Sign out',
  await page.getByRole('button', { name: 'Sign out' }).isVisible(),
);

// 4 universal search: find the patient, navigate, context bar, clear.
await search.fill(`Zara Shell${suffix.slice(0, 4)}`);
const option = page
  .getByRole('option', { name: new RegExp(`Zara Shell${suffix.slice(0, 4)}`) })
  .first();
// Backend indexes the new patient via the domain event; poll up to 40s.
await option.waitFor({ timeout: 40000 }).catch(() => {});
check('search finds the new patient', await option.isVisible().catch(() => false));
await option.click();
await page.waitForURL(`**/patients/${patientId}`, { timeout: 20000 });
check('search result opens the patient record', page.url().includes(`/patients/${patientId}`));
const contextBar = page.getByRole('region', { name: 'Patient context' });
// The bar appears once the patient detail fetch resolves.
await contextBar.waitFor({ timeout: 15000 }).catch(() => {});
check(
  'patient context bar shows the patient',
  (await contextBar.textContent().catch(() => '')).includes(`Zara Shell${suffix.slice(0, 4)}`),
);
await page
  .getByRole('button', { name: 'Clear patient context' })
  .click({ timeout: 10000 })
  .catch(() => {});
await page.waitForTimeout(300);
check(
  'context bar clears',
  !(await contextBar.isVisible().catch(() => false)),
);

// 5 task inbox route renders through the nav.
await page.goto(`${web}/tasks`);
await page.waitForSelector('nav', { timeout: 20000 });
check('task inbox page renders', await page.getByText('Task Inbox', { exact: true }).first().isVisible());

// 6 bound doctor: pinned hospital label (403 on listing degrades to fallback).
const doctorPage = await browser.newPage({ viewport: { width: 1280, height: 800 } });
await doctorPage.goto(`${web}/login`);
await doctorPage.getByLabel('Email or phone').fill(email);
await doctorPage.getByLabel('Password').fill('Passw0rd!123');
await doctorPage.getByRole('button', { name: 'Sign in' }).click();
await doctorPage.waitForURL('**/dashboard', { timeout: 30000 }).catch(() => {});
check(
  'bound doctor sees pinned hospital label with fallback name',
  (await doctorPage.getByText('Hospital: Unknown hospital').isVisible().catch(() => false)) ||
    (await doctorPage
      .getByText(`Hospital: Shell Demo ${suffix}`)
      .isVisible()
      .catch(() => false)),
);
await doctorPage.close();

// 7 language toggle in the user menu (English -> Hindi -> English).
await page
  .getByRole('button', { name: 'Account menu' })
  .click()
  .catch(() => {});
await page
  .getByRole('button', { name: /[\u0900-\u097F]/ })
  .first()
  .click()
  .catch(() => {});
await page.waitForTimeout(500);
check(
  'language switch to Hindi re-renders shell strings',
  (await page.locator('body').textContent()).match(/[\u0900-\u097F]/) !== null,
);
await page
  .getByRole('button', { name: 'Account menu' })
  .click()
  .catch(() => {});
await page
  .getByRole('button', { name: 'English' })
  .first()
  .click()
  .catch(() => {});
await page.waitForTimeout(500);

// 8 sign out returns to the login page.
await page
  .getByRole('button', { name: 'Sign out' })
  .click()
  .catch(() => {});
await page.waitForURL('**/login', { timeout: 20000 }).catch(() => {});
check('sign out returns to login', page.url().includes('/login'));

await browser.close();

console.log(`== ${pass} passed, ${fail} failed ==`);
if (fail > 0) {
  console.log(`failed: ${fails.join(' | ')}`);
  process.exit(1);
}
