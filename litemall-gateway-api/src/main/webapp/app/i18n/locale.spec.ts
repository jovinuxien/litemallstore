/**
 * The locale store: detection order, the enabled-list gate, switching, and what a
 * switch touches (i18next language, <html lang>, the cookie).
 */
jest.mock('app/shared/config/siteConfig', () => ({
  loadSiteConfig: jest.fn(),
}));

import { loadSiteConfig } from 'app/shared/config/siteConfig';

import { i18n, t } from './index';
import {
  __resetLocale,
  applyEnabledLanguages,
  browserPreferredLang,
  currentLocale,
  currentLocaleTag,
  initLocale,
  LANG_COOKIE,
  readLangCookie,
  setLocale,
} from './locale';

const mockLoadSiteConfig = loadSiteConfig as jest.Mock;

const setUrl = (search: string) => {
  window.history.replaceState({}, '', `/${search}`);
};

beforeEach(async () => {
  mockLoadSiteConfig.mockReset();
  mockLoadSiteConfig.mockResolvedValue({ i18nLanguages: ['en'] });
  setUrl('');
  await __resetLocale();
});

describe('boot defaults', () => {
  it('starts in English with English only enabled, and <html lang> says so', async () => {
    await initLocale();
    expect(currentLocale()).toBe('en');
    expect(currentLocaleTag()).toBe('en-IE');
    expect(document.documentElement.lang).toBe('en');
    expect(t('header.cart')).toBe('Cart');
  });

  it('ignores a Swedish browser while Swedish is not enabled — an unreviewed language never flashes', async () => {
    const spy = jest.spyOn(navigator, 'languages', 'get').mockReturnValue(['sv-SE', 'en']);
    await initLocale();
    expect(currentLocale()).toBe('en');
    spy.mockRestore();
  });

  it('follows the browser once the operator enables the language', async () => {
    const spy = jest.spyOn(navigator, 'languages', 'get').mockReturnValue(['sv-SE', 'en']);
    mockLoadSiteConfig.mockResolvedValue({ i18nLanguages: ['en', 'sv', 'da'] });
    await initLocale();
    expect(currentLocale()).toBe('sv');
    expect(t('header.cart')).toBe('Varukorg');
    // A browser default is applied, not pinned: no cookie written for a choice nobody made.
    expect(readLangCookie()).toBeNull();
    spy.mockRestore();
  });

  it('survives a site-config failure in English', async () => {
    mockLoadSiteConfig.mockRejectedValue(new Error('down'));
    await initLocale();
    expect(currentLocale()).toBe('en');
    expect(document.documentElement.lang).toBe('en');
  });
});

describe('browserPreferredLang', () => {
  it('picks the first sv/da ranked above English, and nothing when English ranks first', () => {
    expect(browserPreferredLang(['da-DK', 'sv'])).toBe('da');
    expect(browserPreferredLang(['en-GB', 'sv'])).toBeNull();
    expect(browserPreferredLang(['de', 'fr'])).toBeNull();
    expect(browserPreferredLang([])).toBeNull();
  });
});

describe('explicit preference', () => {
  it('?lang=da wins over the cookie and is persisted to the cookie', async () => {
    document.cookie = `${LANG_COOKIE}=sv; Path=/`;
    setUrl('?lang=da');
    mockLoadSiteConfig.mockResolvedValue({ i18nLanguages: ['en', 'sv', 'da'] });
    await initLocale();
    expect(currentLocale()).toBe('da');
    expect(readLangCookie()).toBe('da');
    expect(document.documentElement.lang).toBe('da');
  });

  it('an unknown ?lang= falls back to English, never to a raw key', async () => {
    setUrl('?lang=xx');
    mockLoadSiteConfig.mockResolvedValue({ i18nLanguages: ['en', 'sv', 'da'] });
    await initLocale();
    expect(currentLocale()).toBe('en');
    expect(t('header.cart')).toBe('Cart');
  });

  it('a cookie choice is pulled back to English when the operator has disabled that language', async () => {
    document.cookie = `${LANG_COOKIE}=sv; Path=/`;
    mockLoadSiteConfig.mockResolvedValue({ i18nLanguages: ['en'] });
    await initLocale();
    expect(currentLocale()).toBe('en');
  });
});

describe('setLocale', () => {
  it('switches strings, <html lang>, the cookie and the formatting tag together', async () => {
    await applyEnabledLanguages(['sv', 'da']);
    await setLocale('sv');
    expect(i18n.resolvedLanguage).toBe('sv');
    expect(t('header.cart')).toBe('Varukorg');
    expect(t('cart:itemCount', { count: 1 })).toBe('Du har 1 vara i varukorgen');
    expect(t('cart:itemCount', { count: 3 })).toBe('Du har 3 varor i varukorgen');
    expect(document.documentElement.lang).toBe('sv');
    expect(readLangCookie()).toBe('sv');
    expect(currentLocaleTag()).toBe('sv-SE');

    await setLocale('da');
    expect(t('header.cart')).toBe('Kurv');
    expect(currentLocaleTag()).toBe('da-DK');
  });

  it('refuses a language that is not enabled and stays in English', async () => {
    await applyEnabledLanguages([]);
    await setLocale('sv');
    expect(currentLocale()).toBe('en');
    expect(readLangCookie()).toBe('en');
  });

  it('falls back to the English string for a key a translation lacks, not the key itself', async () => {
    await applyEnabledLanguages(['sv']);
    await setLocale('sv');
    i18n.addResource('en', 'common', 'onlyInEnglish', 'English only');
    expect(t('onlyInEnglish')).toBe('English only');
  });
});

describe('applyEnabledLanguages', () => {
  it('always keeps English, drops unknown codes, and orders canonically', async () => {
    await applyEnabledLanguages(['DA', 'xx', 'sv', 'da', '']);
    const { localeSnapshot } = await import('./locale');
    expect(localeSnapshot().enabled).toEqual(['en', 'sv', 'da']);
  });
});
