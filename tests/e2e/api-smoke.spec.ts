import { test, expect } from '@playwright/test';

const API = 'http://localhost:8080';

test.describe('API surface smoke', () => {
  test('authenticated CRUD journey works end to end', async ({ request }) => {
    const suffix = Date.now().toString().slice(-8);
    const email = `api-${suffix}@test.local`;

    const register = await request.post(`${API}/api/v1/auth/register`, {
      data: { email, password: 'Passw0rd!123', firstName: 'API', lastName: 'Smoke' },
    });
    expect(register.status()).toBe(201);

    const login = await request.post(`${API}/api/v1/auth/login`, {
      data: { username: email, password: 'Passw0rd!123' },
    });
    expect(login.status()).toBe(200);
    const loginBody = await login.json();
    expect(loginBody.data).toHaveProperty('accessToken');
    const token = loginBody.data.accessToken as string;

    const headers = { Authorization: `Bearer ${token}` };

    const patient = await request.post(`${API}/api/v1/patients`, {
      headers,
      data: { firstName: 'Sam', lastName: 'Squame', dob: '1985-06-15', gender: 'M', contactPhone: '+15551234567' },
    });
    expect(patient.status()).toBe(201);
    const patientBody = await patient.json();
    expect(patientBody.data).toHaveProperty('id');

    const patients = await request.get(`${API}/api/v1/patients`, { headers });
    expect(patients.status()).toBe(200);
    expect(await patients.json()).toHaveProperty('data');

    const audit = await request.get(`${API}/api/v1/audit/logs`, { headers });
    expect(audit.status()).toBe(200);
    const auditBody = await audit.json();
    expect(Array.isArray(auditBody.data)).toBe(true);
  });

  test('unauthenticated calls are rejected', async ({ request }) => {
    const response = await request.get(`${API}/api/v1/patients`);
    expect(response.status()).toBe(401);
  });
});