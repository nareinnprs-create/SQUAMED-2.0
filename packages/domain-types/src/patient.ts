import type { HospitalEntity, Gender, BloodGroup, PatientStatus, UUID, ISO8601 } from './common';

export interface Patient extends HospitalEntity {
  mrn: string;
  empiId: UUID;
  firstName: string;
  middleName?: string;
  lastName: string;
  displayName: string;
  dateOfBirth: ISO8601;
  gender: Gender;
  bloodGroup: BloodGroup;
  maritalStatus?: string;
  nationality?: string;
  languagePreference?: string;
  religion?: string;
  occupation?: string;
  photoUrl?: string;
  status: PatientStatus;
  isEmergency: boolean;
  isUnknownPatient: boolean;
  isVip: boolean;
  tenantId: UUID;
  hospitalId: UUID;
}

export interface PatientIdentifier {
  id: UUID;
  patientId: UUID;
  type: 'national_id' | 'passport' | 'driving_license' | 'insurance_id' | 'social_security';
  value: string;
  country?: string;
  isPrimary: boolean;
  verified: boolean;
  verifiedAt?: ISO8601;
  verifiedBy?: UUID;
}

export interface PatientContact {
  id: UUID;
  patientId: UUID;
  type: 'phone' | 'mobile' | 'email' | 'fax';
  value: string;
  isPrimary: boolean;
  isEmergency: boolean;
  isVerified: boolean;
}

export interface PatientAddress {
  id: UUID;
  patientId: UUID;
  type: 'home' | 'work' | 'temporary';
  line1?: string;
  line2?: string;
  city?: string;
  state?: string;
  country?: string;
  postalCode?: string;
  isPrimary: boolean;
  latitude?: number;
  longitude?: number;
}

export interface EmergencyContact {
  id: UUID;
  patientId: UUID;
  name: string;
  relationship: string;
  phone: string;
  email?: string;
  address?: string;
}

export interface PatientAllergy {
  id: UUID;
  patientId: UUID;
  allergen: string;
  type: 'drug' | 'food' | 'environmental' | 'other';
  severity: 'mild' | 'moderate' | 'severe' | 'life_threatening';
  reaction?: string;
  onsetDate?: ISO8601;
  status: 'active' | 'inactive' | 'resolved';
  source: 'patient_stated' | 'clinician_documented';
  recordedBy: UUID;
  recordedAt: ISO8601;
}

export interface InsurancePolicy {
  id: UUID;
  patientId: UUID;
  insuranceTpaId: UUID;
  policyNumber: string;
  policyHolderName?: string;
  relationshipToPatient?: string;
  coverageType: 'individual' | 'family' | 'group';
  sumInsured?: number;
  validFrom: ISO8601;
  validUntil: ISO8601;
  isPrimary: boolean;
  status: 'active' | 'expired' | 'cancelled';
}

export interface PatientDocument {
  id: UUID;
  patientId: UUID;
  type:
    | 'consent'
    | 'id_proof'
    | 'insurance_card'
    | 'referral_letter'
    | 'discharge_summary'
    | 'external_report'
    | 'clinical_document';
  name: string;
  fileUrl: string;
  mimeType: string;
  fileSize: number;
  uploadedBy: UUID;
  uploadedAt: ISO8601;
}
