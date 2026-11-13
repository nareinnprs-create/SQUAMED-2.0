import { describe, expect, it } from 'vitest';
import { createPatientSchema, updatePatientSchema } from './patient';

describe('createPatientSchema', () => {
  it('accepts a valid patient payload', () => {
    const result = createPatientSchema.safeParse({
      firstName: 'Maryam',
      lastName: 'Al-Sulaiti',
      dateOfBirth: '1990-05-12T00:00:00Z',
      gender: 'female',
      email: 'maryam@example.com',
    });
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.isVip).toBe(false);
    }
  });

  it('rejects missing first name', () => {
    const result = createPatientSchema.safeParse({
      lastName: 'Al-Sulaiti',
      dateOfBirth: '1990-05-12T00:00:00Z',
      gender: 'male',
    });
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues[0].path).toContain('firstName');
    }
  });

  it('rejects invalid datetime for dateOfBirth', () => {
    const result = createPatientSchema.safeParse({
      firstName: 'Maryam',
      lastName: 'Al-Sulaiti',
      dateOfBirth: 'not-a-date',
      gender: 'female',
    });
    expect(result.success).toBe(false);
  });

  it('rejects an invalid gender', () => {
    const result = createPatientSchema.safeParse({
      firstName: 'Maryam',
      lastName: 'Al-Sulaiti',
      dateOfBirth: '1990-05-12T00:00:00Z',
      gender: 'Z',
    });
    expect(result.success).toBe(false);
  });
});

describe('updatePatientSchema', () => {
  it('accepts partial updates', () => {
    expect(updatePatientSchema.safeParse({ phone: '+974 5555 0000' }).success).toBe(true);
  });
});