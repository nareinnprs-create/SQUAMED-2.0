'use client';

import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';
import type { Locale } from '@squamed/ui';

/**
 * Section 00.15 shell state: locale and active hospital survive reloads
 (persisted), the patient context is session-scoped and cleared on navigation.
 */
export interface ActivePatientContext {
  id: string;
  name: string;
  mrn: string;
  dateOfBirth?: string;
  gender?: string;
  age?: number;
  bloodGroup?: string;
  allergySummary?: string;
  isEmergency?: boolean;
}

interface ShellState {
  locale: Locale;
  hospitalId: string | null;
  activePatient: ActivePatientContext | null;
  setLocale: (locale: Locale) => void;
  setHospitalId: (hospitalId: string | null) => void;
  setPatient: (patient: ActivePatientContext | null) => void;
}

export const useShellStore = create<ShellState>()(
  persist(
    (set) => ({
      locale: 'en',
      hospitalId: null,
      activePatient: null,
      setLocale: (locale) => set({ locale }),
      setHospitalId: (hospitalId) => set({ hospitalId }),
      setPatient: (patient) => set({ activePatient: patient }),
    }),
    {
      name: 'squamed.shell',
      storage: createJSONStorage(() => localStorage),
      // activePatient is session-scoped by design; only prefs persist.
      partialize: (state) => ({ locale: state.locale, hospitalId: state.hospitalId }),
    },
  ),
);
