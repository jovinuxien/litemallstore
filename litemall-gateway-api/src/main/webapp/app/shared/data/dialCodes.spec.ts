import { localeGuess, localeIso2 } from './dialCodes';

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

/**
 * The same guess with its confidence attached. The form's Country reads the confidence:
 * a plain `en` browser used to pre-select "United States" on an EU store because the
 * bare fallback was indistinguishable from a real guess.
 */
describe('localeGuess', () => {
  it('consults the site language the visitor chose FIRST', () => {
    expect(localeGuess({ siteLang: 'sv', languages: ['en-US'] })).toEqual({ iso2: 'SE', confident: true });
    expect(localeGuess({ siteLang: 'da', languages: ['de-DE'] })).toEqual({ iso2: 'DK', confident: true });
  });

  it('English as site language names nothing — the browser list decides', () => {
    expect(localeGuess({ siteLang: 'en', languages: ['de-DE'] })).toEqual({ iso2: 'DE', confident: true });
    expect(localeGuess({ siteLang: 'en', languages: ['sv', 'en-US'] })).toEqual({ iso2: 'SE', confident: true });
  });

  it('is confident for a browser region or a language that names one country', () => {
    expect(localeGuess({ languages: ['en-GB'] })).toEqual({ iso2: 'GB', confident: true });
    expect(localeGuess({ languages: ['fi'] })).toEqual({ iso2: 'FI', confident: true });
  });

  it("the bare 'US' fallback is NOT confident", () => {
    expect(localeGuess({ languages: ['en'] })).toEqual({ iso2: 'US', confident: false });
    expect(localeGuess({ languages: [] })).toEqual({ iso2: 'US', confident: false });
    expect(localeGuess({ siteLang: 'en', languages: ['xx-ZZ'] })).toEqual({ iso2: 'US', confident: false });
  });

  it('an actual US browser is a confident US', () => {
    expect(localeGuess({ languages: ['en-US'] })).toEqual({ iso2: 'US', confident: true });
  });
});
