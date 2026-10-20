/**
 * Destination countries the store ships to (CJ createOrder needs a real
 * country + ISO code). One list for checkout and the address book; expanding
 * coverage later means editing HERE and verifying freight templates (V34)
 * for the new codes.
 */
export interface ShippingCountry {
  name: string;
  code: string;
}

export const SHIPPING_COUNTRIES: ShippingCountry[] = [
  { name: 'United States', code: 'US' },
  { name: 'United Kingdom', code: 'GB' },
  { name: 'Sweden', code: 'SE' },
  { name: 'Norway', code: 'NO' },
  { name: 'Germany', code: 'DE' },
  { name: 'Denmark', code: 'DK' },
  { name: 'France', code: 'FR' },
  { name: 'Canada', code: 'CA' },
  { name: 'Australia', code: 'AU' },
];

/**
 * The name to show for a saved address's country: the delivery list's own name, else
 * the raw code (an address from before the list changed still says WHERE it is), else
 * nothing — a country-less address shows no country rather than a guessed one.
 */
export const shippingCountryName = (code: string | undefined | null): string =>
  code ? (SHIPPING_COUNTRIES.find(c => c.code === code)?.name ?? code) : '';
