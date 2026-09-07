import { test, expect } from '@playwright/test';

const API = 'http://localhost:8080';

test.describe('login flow', () => {
  test('a fresh user can sign in through the web UI', async ({ page, request }) => {
    const suffix = Date.now().toString().slice(-8);
    const email = `e2e-${suffix}@test.local`;
    const password = 'Passw0rd!123';

    const register = await request.post(`${API}/api/v1/auth/register`, {
      data: { email, password, firstName: 'E2E', lastName: 'Login' },
    });
    expect(register.status()).toBe(201);
    expect((await register.json()).data).toHaveProperty('userId');

    await page.goto('/auth/login');
    await page.getByLabel('Username or email').fill(email);
    await page.getByLabel('Password').fill(password);
    await page.getByRole('button', { name: 'Sign in' }).click();

    await expect(page).toHaveURL(/\/dashboard/, { timeout: 15000 });
    await expect(page.getByText('dashboard', { exact: false }).first()).toBeVisible({ timeout: 15000 });
  });

  test('invalid credentials show an inline error', async ({ page }) => {
    await page.goto('/auth/login');
    await page.getByLabel('Username or email').fill('nobody@test.local');
    await page.getByLabel('Password').fill('wrong-password');
    await page.getByRole('button', { name: 'Sign in' }).click();

    await expect(page.getByText(/invalid credentials/i)).toBeVisible({ timeout: 15000 });
  });
});