jest.mock('app/shared/config/siteConfig', () => ({ loadSiteConfig: jest.fn().mockResolvedValue({ i18nLanguages: ['en'] }) }));

import { __resetLocale, applyEnabledLanguages, setLocale } from 'app/i18n/locale';

import { __applyCurrencyAnswer, __resetCurrency, setCurrency } from './currency';
import { chargedNote, CURRENCY, money, moneyCharged, moneyParts, moneyShort, toDisplayAmount } from './money';

// ICU writes the group/currency spacer as U+00A0 or U+202F depending on version;
// the assertion cares about the SHAPE, not which whitespace codepoint.
const plain = (s: string) => s.replace(/\s/g, ' ');

beforeEach(async () => {
  await __resetLocale();
  __resetCurrency();
});

describe('money (Wave-24 EUR formatter)', () => {
  it('formats 2dp with the € symbol', () => {
    expect(money(12.3)).toBe('€12.30');
    expect(money(0)).toBe('€0.00');
    expect(money('7.5')).toBe('€7.50');
  });

  it('groups thousands', () => {
    expect(money(1234.56)).toBe('€1,234.56');
    expect(money(9876543.2)).toBe('€9,876,543.20');
  });

  it('is safe on null/undefined/NaN', () => {
    expect(money(null)).toBe('€0.00');
    expect(money(undefined)).toBe('€0.00');
    expect(money(Number.NaN)).toBe('€0.00');
    expect(money('not-a-number')).toBe('€0.00');
  });

  it('splits int/cents for the superscript card price', () => {
    expect(moneyParts(129.99)).toEqual({ before: '€', int: '129', cents: '99', after: '' });
    expect(moneyParts(5)).toEqual({ before: '€', int: '5', cents: '00', after: '' });
    expect(moneyParts(null)).toEqual({ before: '€', int: '0', cents: '00', after: '' });
  });

  it('writes whole amounts without decimals for coupon-style figures', () => {
    expect(moneyShort(5)).toBe('€5');
    expect(moneyShort(50)).toBe('€50');
    expect(moneyShort(7.5)).toBe('€7.50');
  });

  it('with the feature off, charged and displayed are the same and there is no note', () => {
    expect(moneyCharged(12.3)).toBe(money(12.3));
    expect(chargedNote(12.3)).toBeNull();
    expect(toDisplayAmount(12.345)).toBe(12.345);
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

    await setLocale('da');
    expect(plain(money(1234.56))).toBe('1.234,56 €');
    expect(plain(money(0))).toBe('0,00 €');
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
    expect(moneyParts(129.99)).toEqual({ before: '€', int: '129', cents: '99', after: '' });
  });
});

/**
 * Display currency (docs/spec-display-currency.md): prices are WRITTEN in the visitor's
 * currency at the ECB rate and rounded to a shop price; what is CHARGED stays euros.
 */
describe('display currency', () => {
  const ANSWER = {
    country: 'SE',
    suggested: 'SEK',
    enabled: ['EUR', 'SEK', 'DKK', 'NOK', 'GBP'],
    rates: { SEK: 11.0125, DKK: 7.4641, NOK: 11.718, GBP: 0.8785 },
    asOf: '2026-11-13',
  };

  it('writes kronor as whole "<n> kr", converted at the rate', () => {
    __applyCurrencyAnswer(ANSWER);
    expect(toDisplayAmount(12.34)).toBe(136); // 135.89…
    expect(plain(money(12.34))).toBe('136 kr');
    expect(plain(money(1234.56))).toBe('13,596 kr');
    expect(moneyParts(12.34)).toEqual({ before: '', int: '136', cents: '', after: 'kr' });
    expect(plain(moneyShort(5))).toBe('55 kr');
  });

  it('rounds each krona currency to whole units at its own rate', () => {
    __applyCurrencyAnswer(ANSWER);
    setCurrency('DKK');
    expect(plain(money(12.34))).toBe('92 kr'); // 92.107
    setCurrency('NOK');
    expect(plain(money(12.34))).toBe('145 kr'); // 144.60
  });

  it('writes pounds with two decimals', () => {
    __applyCurrencyAnswer(ANSWER);
    setCurrency('GBP');
    expect(money(12.34)).toBe('£10.84'); // 10.84069
    expect(money(1234.56)).toBe('£1,084.56');
    expect(moneyParts(12.34)).toEqual({ before: '£', int: '10', cents: '84', after: '' });
    expect(moneyShort(100)).toBe('£87.85');
    expect(moneyShort(200)).toBe('£175.70');
  });

  it('uses the active language for separators, whatever the currency', async () => {
    __applyCurrencyAnswer(ANSWER);
    await applyEnabledLanguages(['sv', 'da']);
    await setLocale('sv');
    expect(plain(money(1234.56))).toBe('13 596 kr');
    setCurrency('GBP');
    expect(plain(money(1234.56))).toBe('£1 084,56');
    await setLocale('da');
    setCurrency('DKK');
    expect(plain(money(1234.56))).toBe('9.215 kr');
  });

  it('never rounds a real price or discount down to nothing', () => {
    __applyCurrencyAnswer(ANSWER);
    expect(plain(money(0.03))).toBe('1 kr'); // 0.33 kr
    expect(plain(money(0))).toBe('0 kr');
    expect(toDisplayAmount(-0.03)).toBe(-1);
  });

  it('keeps the charged amount in euros, to the cent, and offers it as the note', async () => {
    __applyCurrencyAnswer(ANSWER);
    expect(moneyCharged(12.34)).toBe('€12.34');
    expect(chargedNote(12.34)).toBe('€12.34');
    expect(CURRENCY).toBe('EUR');
    await applyEnabledLanguages(['sv']);
    await setLocale('sv');
    expect(plain(moneyCharged(1234.56))).toBe('1 234,56 €');
  });

  it('is euros again, with no note, once the visitor picks EUR', () => {
    __applyCurrencyAnswer(ANSWER);
    setCurrency('EUR');
    expect(money(12.34)).toBe('€12.34');
    expect(chargedNote(12.34)).toBeNull();
  });
});
