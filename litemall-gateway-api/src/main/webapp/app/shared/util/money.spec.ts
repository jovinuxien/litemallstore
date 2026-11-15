jest.mock('app/shared/config/siteConfig', () => ({ loadSiteConfig: jest.fn().mockResolvedValue({ i18nLanguages: ['en'] }) }));

import { __resetLocale, applyEnabledLanguages, setLocale } from 'app/i18n/locale';

import { CURRENCY, EURO, money, moneyAmount, moneyParts } from './money';

// ICU writes the group/currency spacer as U+00A0 or U+202F depending on version;
// the assertion cares about the SHAPE, not which whitespace codepoint.
const plain = (s: string) => s.replace(/\s/g, ' ');

beforeEach(async () => {
  await __resetLocale();
});

describe('money (Wave-24 EUR formatter)', () => {
  it('formats 2dp with the € symbol', () => {
    expect(money(12.3)).toBe('€12.30');
    expect(money(0)).toBe('€0.00');
    expect(money('7.5')).toBe('€7.50');
  });

  it('groups thousands', () => {
    expect(money(1234.56)).toBe('€1,234.56');
    expect(moneyAmount(9876543.2)).toBe('9,876,543.20');
  });

  it('is safe on null/undefined/NaN', () => {
    expect(money(null)).toBe('€0.00');
    expect(money(undefined)).toBe('€0.00');
    expect(money(Number.NaN)).toBe('€0.00');
    expect(money('not-a-number')).toBe('€0.00');
  });

  it('splits int/cents for the superscript card price', () => {
    expect(moneyParts(129.99)).toEqual({ int: '129', cents: '99' });
    expect(moneyParts(5)).toEqual({ int: '5', cents: '00' });
    expect(moneyParts(null)).toEqual({ int: '0', cents: '00' });
  });

  it('exports the symbol for renders that compose it themselves', () => {
    expect(EURO).toBe('€');
  });
});

/**
 * i18n foundation: one currency, written the way each language writes it. This is
 * presentation, NOT conversion — the euro amount is what is charged (see money.ts).
 */
describe('money follows the active language', () => {
  it('is still EUR in every language', () => {
    expect(CURRENCY).toBe('EUR');
  });

  it('writes Swedish and Danish with a decimal comma and the € after the amount', async () => {
    await applyEnabledLanguages(['sv', 'da']);
    await setLocale('sv');
    expect(plain(money(1234.56))).toBe('1 234,56 €');
    expect(plain(money(12.3))).toBe('12,30 €');
    expect(plain(moneyAmount(1234.56))).toBe('1 234,56');

    await setLocale('da');
    expect(plain(money(1234.56))).toBe('1.234,56 €');
    expect(plain(money(0))).toBe('0,00 €');
    expect(plain(moneyAmount(1234.56))).toBe('1.234,56');
  });

  it('returns to the English shape after switching back', async () => {
    await applyEnabledLanguages(['sv']);
    await setLocale('sv');
    await setLocale('en');
    expect(money(1234.56)).toBe('€1,234.56');
  });

  it('keeps the card split locale-stable — it feeds a layout, not prose', async () => {
    await applyEnabledLanguages(['da']);
    await setLocale('da');
    expect(moneyParts(129.99)).toEqual({ int: '129', cents: '99' });
  });
});
