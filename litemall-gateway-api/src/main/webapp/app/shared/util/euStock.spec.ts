import { euOriginName, euStockLabel, euStockOf, originCountryName } from './euStock';

describe('euStockOf', () => {
  it('reads a measured, non-zero reading', () => {
    expect(euStockOf({ euStock: { units: 230, countries: ['DE'] } })).toEqual({ units: 230, countries: ['DE'] });
  });

  it('treats absence, zero and negatives as no signal', () => {
    // The three backend states that must all collapse to "no badge": never probed
    // (no key), probed-and-empty (units 0), and a malformed payload.
    expect(euStockOf(null)).toBeNull();
    expect(euStockOf({})).toBeNull();
    expect(euStockOf({ euStock: null })).toBeNull();
    expect(euStockOf({ euStock: { units: 0, countries: ['DE'] } })).toBeNull();
    expect(euStockOf({ euStock: { units: -5, countries: ['DE'] } })).toBeNull();
    expect(euStockOf({ euStock: { units: Number.NaN, countries: ['DE'] } })).toBeNull();
  });

  it('survives a reading with no country list', () => {
    expect(euStockOf({ euStock: { units: 4 } })).toEqual({ units: 4, countries: [] });
    expect(euStockOf({ euStock: { units: 4, countries: 'DE' as unknown as string[] } })).toEqual({ units: 4, countries: [] });
  });
});

describe('euStockLabel / euOriginName', () => {
  it('names a single known warehouse country', () => {
    expect(euStockLabel({ units: 9, countries: ['DE'] })).toBe('Ships from Germany');
    expect(euOriginName({ units: 9, countries: ['de'] })).toBe('Germany');
  });

  it('falls back to a neutral phrase when no country is nameable', () => {
    // DE is CJ's only EU warehouse today (Wave-26 Phase 1a). If a second one ever
    // appears in the data before it appears in COUNTRY_NAMES, we must not print a
    // raw ISO code at a customer.
    expect(euOriginName({ units: 9, countries: ['PL'] })).toBe('an EU warehouse');
    expect(euOriginName({ units: 9, countries: [] })).toBe('an EU warehouse');
    expect(euStockLabel({ units: 9, countries: ['PL'] })).toBe('Ships from EU stock');
  });

  it('names the one warehouse it can name when the reading also lists unknown codes', () => {
    // Unnameable codes are filtered rather than making the whole label go vague.
    // Correct while DE is the only EU warehouse in COUNTRY_NAMES; if a second
    // nameable one is added, this collapses to the neutral phrase (asserted below)
    // rather than silently naming just one of two origins.
    expect(euStockLabel({ units: 9, countries: ['DE', 'PL'] })).toBe('Ships from Germany');
    expect(euOriginName({ units: 9, countries: ['DE', 'PL'] })).toBe('Germany');
  });

  it('reads as a value, not a sentence, so a labelled row does not stutter', () => {
    // The PDP trust row already prints "Ships from" as its <dt>.
    expect(euOriginName({ units: 1, countries: ['DE'] })).not.toMatch(/Ships from/);
  });
});

describe('originCountryName', () => {
  it('names an EU warehouse code', () => {
    expect(originCountryName('DE')).toBe('Germany');
    expect(originCountryName('de')).toBe('Germany');
  });

  it('returns null for absent, unknown and non-EU codes', () => {
    // "CN" is factual but deliberately unnamed: the checkout surfaces state a
    // positive measured origin or say nothing at all.
    expect(originCountryName(undefined)).toBeNull();
    expect(originCountryName(null)).toBeNull();
    expect(originCountryName('')).toBeNull();
    expect(originCountryName('CN')).toBeNull();
    expect(originCountryName('XX')).toBeNull();
  });
});
