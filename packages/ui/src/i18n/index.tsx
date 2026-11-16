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
    'shell.loading': 'Loading...',
    'shell.workspace': 'Clinical Workspace',
    'shell.hospital.label': 'Hospital',
    'shell.hospital.all': 'All hospitals',
    'shell.hospital.unknown': 'Unknown hospital',
    'shell.hospital.switchTitle': 'Switch active hospital',
    'shell.search.title': 'Universal search',
    'shell.search.placeholder': 'Search patients, encounters...',
    'shell.search.empty': 'No matches',
    'shell.search.error': 'Search unavailable',
    'shell.user.menu': 'Account menu',
    'shell.user.signOut': 'Sign out',
    'shell.user.roles': 'Roles',
    'shell.user.permissions': 'Permissions',
    'shell.user.language': 'Language',
    'shell.patient.context': 'Patient context',
    'shell.patient.clear': 'Clear patient context',
    'shell.notifications.title': 'Notifications',
    'shell.notifications.markAll': 'Mark all read',
    'shell.notifications.empty': 'No notifications',
    'shell.notifications.connected': 'Realtime connected',
    'shell.notifications.disconnected': 'Realtime disconnected',
    'shell.notifications.count':
      '{count, plural, =0 {No new notifications} =1 {1 new notification} other {# new notifications}}',
    'contextspine.allergy': 'Allergies: {summary}',
    'vitalchip.trendUp': 'trending up',
    'vitalchip.trendDown': 'trending down',
    'vitalchip.trendFlat': 'stable',
    'vitalchip.outOfRange': 'out of range',
    'labresult.normal': 'Normal',
    'labresult.high': 'High',
    'labresult.low': 'Low',
    'medorderline.highAlert': 'High alert',
    'medorderline.verify': 'Verify',
    'medorderline.verifiedBy': 'Verified by {name}',
    'dualconfirm.title': 'Confirm patient identity',
    'dualconfirm.instruction': 'Match both identifiers against the patient wristband before proceeding.',
    'dualconfirm.verified': 'I verified the patient name band and MRN against the patient in front of me',
    'dualconfirm.confirm': 'Confirm',
    'offlinebanner.title': 'You are offline',
    'offlinebanner.description': 'Showing the last synced data. Changes will queue until the connection returns.',
    'offlinebanner.retry': 'Retry',
    'errorstate.retry': 'Try again',
  },
  hi: {
    'datagrid.empty': 'कोई डेटा नहीं',
    'datagrid.pageOf': 'पृष्ठ {page} / {pages}',
    'datagrid.prevPage': 'पिछला पृष्ठ',
    'datagrid.nextPage': 'अगला पृष्ठ',
    'field.required': 'आवश्यक',
    'taskcard.open': 'कार्य खोलें',
    'taskcard.escalated': 'एस्कलेटेड x{count}',
    'shell.loading': 'लोड हो रहा है...',
    'shell.workspace': 'क्लिनिकल वर्कस्पेस',
    'shell.hospital.label': 'अस्पताल',
    'shell.hospital.all': 'सभी अस्पताल',
    'shell.hospital.unknown': 'अज्ञात अस्पताल',
    'shell.hospital.switchTitle': 'सक्रिय अस्पताल बदलें',
    'shell.search.title': 'सार्वभौमिक खोज',
    'shell.search.placeholder': 'मरीज़, विज़िट खोजें...',
    'shell.search.empty': 'कोई मेल नहीं',
    'shell.search.error': 'खोज अनुपलब्ध',
    'shell.user.menu': 'खाता मेनू',
    'shell.user.signOut': 'साइन आउट',
    'shell.user.roles': 'भूमिकाएँ',
    'shell.user.permissions': 'अनुमतियाँ',
    'shell.user.language': 'भाषा',
    'shell.patient.context': 'मरीज़ संदर्भ',
    'shell.patient.clear': 'मरीज़ संदर्भ हटाएँ',
    'shell.notifications.title': 'सूचनाएँ',
    'shell.notifications.markAll': 'सभी पढ़ी हुई मार्क करें',
    'shell.notifications.empty': 'कोई सूचना नहीं',
    'shell.notifications.connected': 'रियलटाइम जुड़ा है',
    'shell.notifications.disconnected': 'रियलटाइम डिस्कनेक्ट है',
    'shell.notifications.count':
      '{count, plural, =0 {कोई नई सूचना नहीं} =1 {1 नई सूचना} other {# नई सूचनाएँ}}',
    'contextspine.allergy': 'एलर्जी: {summary}',
    'vitalchip.trendUp': 'बढ़ रहा है',
    'vitalchip.trendDown': 'घट रहा है',
    'vitalchip.trendFlat': 'स्थिर',
    'vitalchip.outOfRange': 'सीमा से बाहर',
    'labresult.normal': 'सामान्य',
    'labresult.high': 'उच्च',
    'labresult.low': 'निम्न',
    'medorderline.highAlert': 'हाई अलर्ट',
    'medorderline.verify': 'सत्यापित करें',
    'medorderline.verifiedBy': '{name} द्वारा सत्यापित',
    'dualconfirm.title': 'रोगी की पहचान की पुष्टि करें',
    'dualconfirm.instruction': 'आगे बढ़ने से पहले दोनों पहचानकर्ता रोगी की रिस्टबैंड से मिलाएँ।',
    'dualconfirm.verified': 'मैंने रोगी का नाम बैंड और MRN सामने खड़े रोगी से मिलाकर सत्यापित किया है',
    'dualconfirm.confirm': 'पुष्टि करें',
    'offlinebanner.title': 'आप ऑफ़लाइन हैं',
    'offlinebanner.description': 'पिछला सिंक किया डेटा दिख रहा है। कनेक्शन लौटने तक बदलाव कतार में रहेंगे।',
    'offlinebanner.retry': 'पुनः प्रयास',
    'errorstate.retry': 'फिर से कोशिश करें',
  },
};

let currentLocale: Locale = 'en';

export function setLocale(locale: Locale): void {
  currentLocale = locale;
}

export function getLocale(): Locale {
  return currentLocale;
}

/** Position of the closing brace matching the `{` at `open`, or -1. */
function matchBrace(text: string, open: number): number {
  let depth = 0;
  for (let i = open; i < text.length; i++) {
    if (text[i] === '{') depth++;
    else if (text[i] === '}') {
      depth--;
      if (depth === 0) return i;
    }
  }
  return -1;
}

const PLURAL_HEAD = /\{(\w+),\s*plural,\s*/;

function interpolate(template: string, params: Record<string, string | number>): string {
  return Object.entries(params).reduce(
    (text, [name, value]) => text.replaceAll(`{${name}}`, String(value)),
    template,
  );
}

/**
 * ICU plural subset: {count, plural, =0 {None} =1 {One} one {# item} other {# items}}.
 * Exact-match selectors win, then Intl.PluralRules for the active locale; `#`
 * inside the branch is the number. select/ordinal ICU forms are out of scope
 * (no shipped string needs them).
 */
function formatMessage(template: string, params: Record<string, string | number>): string {
  const head = PLURAL_HEAD.exec(template);
  if (!head) return interpolate(template, params);
  const outerClose = matchBrace(template, head.index);
  if (outerClose < 0) return interpolate(template, params);
  const value = Number(params[head[1]]);
  const selectors: Array<{ sel: string; body: string }> = [];
  let cursor = head.index + head[0].length;
  while (cursor < template.length) {
    const selector = /^\s*(=\d+|\w+)\s*\{/.exec(template.slice(cursor));
    if (!selector) break;
    const bodyOpen = cursor + selector[0].length - 1;
    const bodyClose = matchBrace(template, bodyOpen);
    if (bodyClose < 0) break;
    selectors.push({ sel: selector[1], body: template.slice(bodyOpen + 1, bodyClose) });
    cursor = bodyClose + 1;
  }
  const exact = `=${value}`;
  const category = selectors.some((s) => s.sel === exact)
    ? exact
    : new Intl.PluralRules(getLocale()).select(value);
  const branch =
    selectors.find((s) => s.sel === category)?.body ??
    selectors.find((s) => s.sel === 'other')?.body ??
    '';
  const formatted = branch.replaceAll('#', String(value));
  // Recurse: text after the branch may hold another plural or plain params.
  return formatMessage(
    template.slice(0, head.index) + formatted + template.slice(outerClose + 1),
    params,
  );
}

/** Translate a catalog key in the current locale; en is the fallback chain root. */
export function t(key: string, params?: Record<string, string | number>): string {
  const template = catalogs[currentLocale][key] ?? catalogs.en[key] ?? key;
  if (!params) return template;
  return formatMessage(template, params);
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
