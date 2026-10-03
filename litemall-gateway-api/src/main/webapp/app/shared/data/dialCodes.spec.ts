import { localeIso2 } from './dialCodes';

/**
 * The phone selector's start-up country. It used to read only `navigator.language`
 * and fall back to 'US' for anything without a region — so a browser set to plain
 * `sv` opened the checkout on +1 with "United States" pre-selected.
 */
describe('localeIso2', () => {
  const original = { languages: navigator.languages, language: navigator.language };
  const setLanguages = (languages: string[] | undefined, language = languages?.[0] ?? '') => {
    Object.defineProperty(navigator, 'languages', { value: languages, configurable: true });
    Object.defineProperty(navigator, 'language', { value: language, configurable: true });
  };
  afterEach(() => setLanguages(original.languages as string[], original.language));

  it('reads the region of the first language that has one', () => {
    setLanguages(['sv-SE', 'sv', 'en-US']);
    expect(localeIso2()).toBe('SE');
    setLanguages(['en', 'de-DE']);
    expect(localeIso2()).toBe('DE');
  });

  it('guesses from a region-less language of the store market before a later fallback language', () => {
    setLanguages(['sv', 'en-US', 'en']);
    expect(localeIso2()).toBe('SE');
    setLanguages(['da']);
    expect(localeIso2()).toBe('DK');
  });

  it("falls back to 'US' when nothing names a country", () => {
    setLanguages(['en']);
    expect(localeIso2()).toBe('US');
    setLanguages(['xx-ZZ']);
    expect(localeIso2()).toBe('US');
  });

  it('still works on a browser without navigator.languages', () => {
    setLanguages(undefined, 'fr-FR');
    expect(localeIso2()).toBe('FR');
  });
});
