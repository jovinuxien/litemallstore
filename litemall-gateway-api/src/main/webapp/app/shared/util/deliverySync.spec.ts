import { countryFromPhone, countryOfSavedAddress, isShippingCountry, regionAfterCountryChange, withCountry, withPhoneCountry } from './deliverySync';

/**
 * The rules that keep Country, the phone's dial code and Region together on the
 * checkout and address-book forms. Before these existed the phone could move the
 * country only until the country was first touched, the country never moved the
 * phone, and a region survived a change of country.
 */
describe('deliverySync', () => {
  describe('countryFromPhone', () => {
    it('follows a dial code the customer PICKED, even over a country already chosen', () => {
      expect(countryFromPhone('DE', 'pick', 'SE')).toBe('DE');
      expect(countryFromPhone('DE', 'pick', '')).toBe('DE');
    });

    it('lets the start-up default and a typed number fill an EMPTY country only', () => {
      expect(countryFromPhone('SE', 'init', '')).toBe('SE');
      expect(countryFromPhone('SE', 'typed', '')).toBe('SE');
      // A Swedish mobile on a German address is a legitimate order, not a mismatch to fix.
      expect(countryFromPhone('SE', 'init', 'DE')).toBeNull();
      expect(countryFromPhone('SE', 'typed', 'DE')).toBeNull();
    });

    it('never fills a country from the bare fallback — nothing named it', () => {
      // A plain `en` browser lands the selector on +1. The form must stay on "-- Country --".
      expect(countryFromPhone('US', 'fallback', '')).toBeNull();
      expect(countryFromPhone('US', 'fallback', 'SE')).toBeNull();
      expect(withPhoneCountry({ country: '', region: '' }, 'US', 'fallback')).toBeNull();
      // Whereas the same code as a confident guess does fill an empty country.
      expect(countryFromPhone('US', 'init', '')).toBe('US');
    });

    it('never echoes a change the country itself caused', () => {
      expect(countryFromPhone('DE', 'external', 'SE')).toBeNull();
      expect(countryFromPhone('DE', 'external', '')).toBeNull();
    });

    it('leaves the destination alone for a dial code we do not ship to', () => {
      expect(isShippingCountry('JP')).toBe(false);
      expect(countryFromPhone('JP', 'pick', 'SE')).toBeNull();
      expect(countryFromPhone('JP', 'init', '')).toBeNull();
    });

    it('reports nothing when both already agree', () => {
      expect(countryFromPhone('SE', 'pick', 'SE')).toBeNull();
    });
  });

  describe('countryOfSavedAddress', () => {
    it('is the address’s own country when we deliver there', () => {
      expect(countryOfSavedAddress('SE')).toBe('SE');
      expect(countryOfSavedAddress('DK')).toBe('DK');
    });

    it('is EMPTY for a country-less (legacy) address — never an inherited pick', () => {
      expect(countryOfSavedAddress(undefined)).toBe('');
      expect(countryOfSavedAddress('')).toBe('');
      expect(countryOfSavedAddress(null)).toBe('');
    });

    it('treats a country we do not deliver to like no country (the customer picks one)', () => {
      expect(countryOfSavedAddress('JP')).toBe('');
    });
  });

  describe('regionAfterCountryChange', () => {
    it('clears a region left over from the previous country', () => {
      expect(regionAfterCountryChange('Bayern', 'DE', 'SE')).toBe('');
      expect(regionAfterCountryChange('Skåne', 'SE', 'US')).toBe('');
    });

    it('keeps a region that belongs to the new country, in the list spelling', () => {
      expect(regionAfterCountryChange('skane', 'DE', 'SE')).toBe('Skåne');
      expect(regionAfterCountryChange('Bayern', '', 'DE')).toBe('Bayern');
    });

    it('keeps text typed before any country was chosen', () => {
      expect(regionAfterCountryChange('Somewhere', '', 'SE')).toBe('Somewhere');
    });

    it('does nothing when the country did not change or the region is empty', () => {
      expect(regionAfterCountryChange('Anything', 'SE', 'SE')).toBe('Anything');
      expect(regionAfterCountryChange('', 'SE', 'DE')).toBe('');
      expect(regionAfterCountryChange(undefined, 'SE', 'DE')).toBe('');
    });

    it('clears a stale region when the country is unset', () => {
      expect(regionAfterCountryChange('Bayern', 'DE', '')).toBe('');
    });
  });

  describe('form transitions', () => {
    it('withCountry moves country and region together', () => {
      expect(withCountry({ country: 'DE', region: 'Bayern' }, 'SE')).toEqual({ country: 'SE', region: '' });
      expect(withCountry({ country: 'SE', region: 'Stockholm' }, 'SE')).toEqual({ country: 'SE', region: 'Stockholm' });
    });

    it('withPhoneCountry returns the moved form for a pick and null when nothing moves', () => {
      expect(withPhoneCountry({ country: 'SE', region: 'Stockholm' }, 'DE', 'pick')).toEqual({ country: 'DE', region: '' });
      expect(withPhoneCountry({ country: 'SE', region: 'Stockholm' }, 'DE', 'typed')).toBeNull();
      expect(withPhoneCountry({ country: 'SE', region: 'Stockholm' }, 'DE', 'external')).toBeNull();
    });
  });
});
