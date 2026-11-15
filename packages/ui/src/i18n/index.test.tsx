import { cleanup, render } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { dirOf, getLocale, I18nProvider, setLocale, t } from './index';

afterEach(() => {
  cleanup();
  setLocale('en');
});

describe('i18n catalogs', () => {
  it('translates in en and hi with parameter substitution', () => {
    expect(t('datagrid.empty')).toBe('No data');
    setLocale('hi');
    expect(t('datagrid.empty')).toBe('कोई डेटा नहीं');
    setLocale('en');
    expect(t('datagrid.pageOf', { page: 2, pages: 5 })).toBe('Page 2 of 5');
    setLocale('hi');
    expect(t('datagrid.pageOf', { page: 2, pages: 5 })).toBe('पृष्ठ 2 / 5');
  });

  it('falls back to the key itself when a key exists nowhere', () => {
    setLocale('hi');
    expect(t('taskcard.open')).toBe('कार्य खोलें');
    expect(t('never.added.anywhere')).toBe('never.added.anywhere');
  });

  it('keeps module-level locale in sync with the provider', () => {
    expect(getLocale()).toBe('en');
    render(
      <I18nProvider locale="hi">
        <span>child</span>
      </I18nProvider>,
    );
    expect(getLocale()).toBe('hi');
    expect(document.documentElement.lang).toBe('hi');
    expect(document.documentElement.dir).toBe('ltr');
  });
});

describe('dirOf', () => {
  it('maps the RTL set to rtl and en/hi to ltr', () => {
    expect(dirOf('ar')).toBe('rtl');
    expect(dirOf('he')).toBe('rtl');
    expect(dirOf('fa')).toBe('rtl');
    expect(dirOf('ur')).toBe('rtl');
    expect(dirOf('ur-PK')).toBe('rtl');
    expect(dirOf('en')).toBe('ltr');
    expect(dirOf('en-US')).toBe('ltr');
    expect(dirOf('hi')).toBe('ltr');
  });
});

describe('ICU plural subset', () => {
  it('uses exact =0 / =1 selectors and # substitution in en', () => {
    setLocale('en');
    expect(t('shell.notifications.count', { count: 0 })).toBe('No new notifications');
    expect(t('shell.notifications.count', { count: 1 })).toBe('1 new notification');
    expect(t('shell.notifications.count', { count: 5 })).toBe('5 new notifications');
    expect(t('shell.notifications.count', { count: 12 })).toBe('12 new notifications');
  });

  it('selects the Intl.PluralRules category per locale in hi', () => {
    setLocale('hi');
    expect(t('shell.notifications.count', { count: 0 })).toBe('कोई नई सूचना नहीं');
    expect(t('shell.notifications.count', { count: 1 })).toBe('1 नई सूचना');
    expect(t('shell.notifications.count', { count: 3 })).toBe('3 नई सूचनाएँ');
  });

  it('keeps plain {param} interpolation working alongside plurals', () => {
    setLocale('en');
    expect(t('datagrid.pageOf', { page: 2, pages: 5 })).toBe('Page 2 of 5');
  });

  it('returns the template unchanged when params are absent', () => {
    expect(t('shell.notifications.count')).toBe(
      '{count, plural, =0 {No new notifications} =1 {1 new notification} other {# new notifications}}',
    );
  });
});
