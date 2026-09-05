import { t } from 'app/i18n';
import { __resetLocale, applyEnabledLanguages, setLocale } from 'app/i18n/locale';

import { prettySortLabel } from './Search';

jest.mock('app/shared/config/siteConfig', () => ({ loadSiteConfig: jest.fn().mockResolvedValue({ i18nLanguages: ['en'] }) }));

/**
 * The backend labels its sort options with raw searcher strings ("price.asc").
 * Those resolve to catalogue keys so they follow the locale; anything that is
 * NOT that raw shape is a human-authored server label and passes through
 * verbatim — the same rule describeError applies to an uncatalogued errno.
 */
beforeEach(async () => {
  await __resetLocale();
});

afterAll(async () => {
  await __resetLocale();
});

it('maps a known raw field to its catalogued label in the current locale', async () => {
  expect(prettySortLabel('price.asc', t)).toBe('Price: low to high');
  expect(prettySortLabel('REVIEW_COUNT.DESC', t)).toBe('Most reviews');
  await applyEnabledLanguages(['sv']);
  await setLocale('sv');
  expect(prettySortLabel('price.asc', t)).toBe('Pris: lägst först');
  expect(prettySortLabel('created_epoch.desc', t)).toBe('Nyast först');
});

it('humanises an unknown raw field instead of leaking the searcher name', () => {
  expect(prettySortLabel('attr_weight.asc', t)).toBe('Weight (ascending)');
});

it('passes a human-authored server label through untouched', () => {
  expect(prettySortLabel('Best match', t)).toBe('Best match');
  expect(prettySortLabel('Pris: stigande', t)).toBe('Pris: stigande');
});

it('knows every raw sort field the live backend serves today', () => {
  // Measured on trovemo.com 2026-09-05: the searcher's sortOptions carry these
  // fields. A new one falls back to the humanised generic form, never a raw string.
  for (const f of ['deal_end_epoch', 'discount_pct', 'discount_price', 'price', 'variant_price', 'rating', 'review_count', 'listed_num', 'title', 'created_epoch']) {
    expect(prettySortLabel(`${f}.asc`, t)).not.toMatch(/\(ascending\)/);
  }
});
