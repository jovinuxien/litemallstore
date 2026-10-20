import { SHIPPING_COUNTRIES } from './countries';
import { checkPhoneLength, dialCountries } from './dialCodes';
import { REGIONS_BY_COUNTRY, regionsFor } from './regions';

/**
 * The delivery-country list is the ONE place coverage is declared; the dial table and
 * the region lists must keep up with it or a new country lands with a broken phone
 * field or a plain-text region. Denmark (DK) joined 2026-10-20: `da` is an enabled
 * language and Wave 24 names DK in the market.
 */
describe('shipping countries', () => {
  it('includes Denmark', () => {
    expect(SHIPPING_COUNTRIES.find(c => c.code === 'DK')?.name).toBe('Denmark');
  });

  it('every delivery country has a dial code and a numbering-plan rule', () => {
    const dials = new Set(dialCountries().map(c => c.iso2));
    for (const c of SHIPPING_COUNTRIES) {
      expect(dials.has(c.code)).toBe(true);
      // An unknown plan falls back to [4, 14]; a real one rejects a 3-digit number too but
      // also rejects 14 digits — which is how we tell a listed country from the fallback.
      expect(checkPhoneLength(c.code, '1'.repeat(14)).ok).toBe(false);
    }
    expect(checkPhoneLength('DK', '12345678').ok).toBe(true);
  });

  it('every delivery country has region suggestions', () => {
    for (const c of SHIPPING_COUNTRIES) expect(regionsFor(c.code).length).toBeGreaterThan(0);
    expect(REGIONS_BY_COUNTRY.DK).toEqual(['Hovedstaden', 'Midtjylland', 'Nordjylland', 'Sjælland', 'Syddanmark']);
  });
});
