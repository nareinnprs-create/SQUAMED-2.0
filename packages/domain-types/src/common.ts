export type UUID = string;
export type ISO8601 = string;

export type HospitalScope = {
  id: UUID;
};

export type BaseEntity = {
  id: UUID;
  createdAt: ISO8601;
  updatedAt: ISO8601;
  createdBy?: UUID;
  updatedBy?: UUID;
  version: number;
};

export type SoftDeletable = BaseEntity & {
  deletedAt: ISO8601 | null;
  deletedBy?: UUID;
};

export type TenantEntity = BaseEntity & {
  tenantId: UUID;
};

export type HospitalEntity = TenantEntity & {
  hospitalId: UUID;
};

export type Paginated<T> = {
  data: T[];
  meta: {
    page: number;
    size: number;
    total: number;
    pages: number;
  };
};

export type ApiError = {
  error: {
    code: string;
    message: string;
    details?: unknown[];
  };
};

export type ApiResponse<T> = {
  data: T;
  meta?: Record<string, unknown>;
};

export type Gender = 'male' | 'female' | 'other' | 'unknown';
export type BloodGroup =
  | 'a_positive'
  | 'a_negative'
  | 'b_positive'
  | 'b_negative'
  | 'ab_positive'
  | 'ab_negative'
  | 'o_positive'
  | 'o_negative'
  | 'unknown';

export type PatientStatus = 'active' | 'inactive' | 'deceased';
