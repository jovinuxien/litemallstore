import { SHIPPING_COUNTRIES } from './countries';
import { checkPhoneLength, dialCountries } from './dialCodes';
import { deliveryRegions, REGIONS_BY_COUNTRY, regionsFor } from './regions';

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

  it('offers every delivery region labelled with its country, in delivery-list order', () => {
    const all = deliveryRegions();
    expect(all.length).toBe(SHIPPING_COUNTRIES.reduce((n, c) => n + regionsFor(c.code).length, 0));
    expect(all.find(r => r.region === 'Skåne')).toEqual({ region: 'Skåne', country: 'SE', label: 'Skåne · Sweden' });
    expect(all[0].country).toBe(SHIPPING_COUNTRIES[0].code);
    expect(deliveryRegions()).toBe(all); // computed once
  });

  it('no region name is shared by two delivery countries (so a typed region can name its country)', () => {
    const fold = (s: string) => s.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '');
    const seen = new Map<string, string>();
    for (const r of deliveryRegions()) {
      const prev = seen.get(fold(r.region));
      expect(prev == null || prev === r.country ? null : `${r.region}: ${prev} and ${r.country}`).toBeNull();
      seen.set(fold(r.region), r.country);
    }
  });

  it('every delivery country has region suggestions', () => {
    for (const c of SHIPPING_COUNTRIES) expect(regionsFor(c.code).length).toBeGreaterThan(0);
    expect(REGIONS_BY_COUNTRY.DK).toEqual(['Hovedstaden', 'Midtjylland', 'Nordjylland', 'Sjælland', 'Syddanmark']);
  });
});
