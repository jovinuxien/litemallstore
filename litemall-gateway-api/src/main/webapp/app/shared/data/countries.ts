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
  { name: 'France', code: 'FR' },
  { name: 'Canada', code: 'CA' },
  { name: 'Australia', code: 'AU' },
];
