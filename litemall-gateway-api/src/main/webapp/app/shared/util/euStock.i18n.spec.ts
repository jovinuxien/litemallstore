import { __resetLocale, applyEnabledLanguages, setLocale } from 'app/i18n/locale';

import { euOriginName, euStockLabel, originCountryName } from './euStock';

jest.mock('app/shared/config/siteConfig', () => ({ loadSiteConfig: jest.fn().mockResolvedValue({ i18nLanguages: ['en'] }) }));

/**
 * The warehouse country and the "Ships from" phrasing come from common:euStock.*
 * so the PDP badge, trust row and (batch 3) checkout badges all switch together.
 * The honesty rules of euStock.spec.ts are unchanged: an unnameable code is still
 * null / the neutral phrase, never a raw ISO code, in every language.
 */
beforeEach(async () => {
  await __resetLocale();
  await applyEnabledLanguages(['sv', 'da']);
});

afterAll(async () => {
  await __resetLocale();
});

it('names Germany in the current locale', async () => {
  await setLocale('sv');
  expect(euStockLabel({ units: 9, countries: ['DE'] })).toBe('Skickas från Tyskland');
  expect(euOriginName({ units: 9, countries: ['de'] })).toBe('Tyskland');
  expect(originCountryName('DE')).toBe('Tyskland');
  await setLocale('da');
  expect(euStockLabel({ units: 9, countries: ['DE'] })).toBe('Sendes fra Tyskland');
});

it('keeps the neutral fallback and the null rule in every locale', async () => {
  await setLocale('da');
  expect(euStockLabel({ units: 9, countries: ['PL'] })).toBe('Sendes fra EU-lager');
  expect(euOriginName({ units: 9, countries: [] })).toBe('et EU-lager');
  expect(originCountryName('PL')).toBeNull();
  expect(originCountryName('CN')).toBeNull();
});
