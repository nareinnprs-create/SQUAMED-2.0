'use client';

import { useEffect } from 'react';
import type { ReactNode } from 'react';

/**
 * Chunk-00 item 2: i18n v1 (en, hi) + RTL-ready.
 *
 * Design-system UI strings live in catalogs, never inline literals, so a
 * future locale needs one dictionary, not a component sweep. Direction is a
 * data lookup (dirOf): today en/hi are LTR; adding ar/ur later flips layout
 * through logical CSS properties, which the whole library uses.
 */

export type Locale = 'en' | 'hi';

export type Direction = 'ltr' | 'rtl';

/** Locales whose script runs right-to-left; hi (Devanagari) is LTR. */
const RTL_LOCALES = new Set(['ar', 'he', 'fa', 'ur']);

const catalogs: Record<Locale, Record<string, string>> = {
  en: {
    'datagrid.empty': 'No data',
    'datagrid.pageOf': 'Page {page} of {pages}',
    'datagrid.prevPage': 'Previous page',
    'datagrid.nextPage': 'Next page',
    'field.required': 'Required',
    'taskcard.open': 'Open task',
    'taskcard.escalated': 'Escalated x{count}',
  },
  hi: {
    'datagrid.empty': 'कोई डेटा नहीं',
    'datagrid.pageOf': 'पृष्ठ {page} / {pages}',
    'datagrid.prevPage': 'पिछला पृष्ठ',
    'datagrid.nextPage': 'अगला पृष्ठ',
    'field.required': 'आवश्यक',
    'taskcard.open': 'कार्य खोलें',
    'taskcard.escalated': 'एस्कलेटेड x{count}',
  },
};

let currentLocale: Locale = 'en';

export function setLocale(locale: Locale): void {
  currentLocale = locale;
}

export function getLocale(): Locale {
  return currentLocale;
}

/** Translate a catalog key in the current locale; en is the fallback chain root. */
export function t(key: string, params?: Record<string, string | number>): string {
  const template = catalogs[currentLocale][key] ?? catalogs.en[key] ?? key;
  if (!params) return template;
  return Object.entries(params).reduce(
    (text, [name, value]) => text.replaceAll(`{${name}}`, String(value)),
    template,
  );
}

/** Text direction for a BCP-47 locale tag (base language wins for v1). */
export function dirOf(locale: string): Direction {
  return RTL_LOCALES.has(locale.split('-')[0].toLowerCase()) ? 'rtl' : 'ltr';
}

/**
 * Locale provider: syncs the module-level locale (read by non-hook
 * consumers such as DataGrid) and the document language/direction.
 */
export function I18nProvider({ locale, children }: { locale: Locale; children: ReactNode }) {
  useEffect(() => {
    setLocale(locale);
    if (typeof document !== 'undefined') {
      document.documentElement.lang = locale;
      document.documentElement.dir = dirOf(locale);
    }
  }, [locale]);

  return <>{children}</>;
}

/** Read the active locale (module-level; the provider keeps it in sync). */
export function useI18n(): { locale: Locale; dir: Direction; t: typeof t } {
  const locale = getLocale();
  return { locale, dir: dirOf(locale), t };
}
