import { z } from 'zod';
import { uuidSchema, genderSchema, bloodGroupSchema } from './common';

export const createPatientSchema = z.object({
  firstName: z.string().min(1, 'First name is required').max(100),
  middleName: z.string().max(100).optional(),
  lastName: z.string().min(1, 'Last name is required').max(100),
  dateOfBirth: z.string().datetime(),
  gender: genderSchema,
  bloodGroup: bloodGroupSchema.optional(),
  maritalStatus: z.string().max(50).optional(),
  nationality: z.string().max(100).optional(),
  languagePreference: z.string().max(50).optional(),
  phone: z.string().max(20).optional(),
  email: z.string().email().optional(),
  addressLine1: z.string().max(200).optional(),
  city: z.string().max(100).optional(),
  state: z.string().max(100).optional(),
  country: z.string().max(100).optional(),
  postalCode: z.string().max(20).optional(),
  isVip: z.boolean().default(false),
});

export const updatePatientSchema = createPatientSchema.partial();

export const patientIdSchema = z.object({
  type: z.enum(['national_id', 'passport', 'driving_license', 'insurance_id', 'social_security']),
  value: z.string().min(1).max(100),
  country: z.string().max(100).optional(),
  isPrimary: z.boolean().default(false),
});

export const patientContactSchema = z.object({
  type: z.enum(['phone', 'mobile', 'email', 'fax']),
  value: z.string().min(1).max(200),
  isPrimary: z.boolean().default(false),
  isEmergency: z.boolean().default(false),
});

export const patientAllergySchema = z.object({
  allergen: z.string().min(1).max(200),
  type: z.enum(['drug', 'food', 'environmental', 'other']),
  severity: z.enum(['mild', 'moderate', 'severe', 'life_threatening']),
  reaction: z.string().max(500).optional(),
  onsetDate: z.string().datetime().optional(),
});

export const mergePatientsSchema = z.object({
  sourcePatientIds: z.array(uuidSchema).min(1),
  targetPatientId: uuidSchema,
  reason: z.string().min(1).max(500),
});

export type CreatePatientInput = z.infer<typeof createPatientSchema>;
export type UpdatePatientInput = z.infer<typeof updatePatientSchema>;
export type MergePatientsInput = z.infer<typeof mergePatientsSchema>;