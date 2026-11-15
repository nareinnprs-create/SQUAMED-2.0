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
