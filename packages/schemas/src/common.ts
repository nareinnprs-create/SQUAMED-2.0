import { z } from 'zod';

export const uuidSchema = z.string().uuid();

export const genderSchema = z.enum(['male', 'female', 'other', 'unknown']);

export const bloodGroupSchema = z.enum([
  'a_positive',
  'a_negative',
  'b_positive',
  'b_negative',
  'ab_positive',
  'ab_negative',
  'o_positive',
  'o_negative',
  'unknown',
]);

export const patientStatusSchema = z.enum(['active', 'inactive', 'deceased']);