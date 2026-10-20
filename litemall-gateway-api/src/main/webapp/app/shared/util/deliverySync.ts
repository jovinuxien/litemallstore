import { SHIPPING_COUNTRIES } from 'app/shared/data/countries';
import { deliveryRegions, LabelledRegion, regionsFor } from 'app/shared/data/regions';

/**
 * The ONE set of rules that keeps a delivery form's Country, phone dial code and
 * Region in step. Checkout and the address book both call these, so the two forms
 * cannot drift apart again.
 *
 * Why a phone country change carries a `source`:
 *  - `pick`     the customer chose a dial code in the selector — an explicit choice,
 *               so the destination country follows it (when we ship there).
 *  - `typed`    the customer typed a number in full international form (`+49 170…`).
 *               That is a fact about their NUMBER, not about where the parcel goes
 *               (people keep a foreign mobile after moving), so it only fills a
 *               country that is still empty.
 *  - `init`     the selector's start-up default when something NAMED the country: a
 *               stored number, the form's own country, the site language, a browser
 *               region or a language that names one country. A guess: it may fill an
 *               empty country, never overwrite a chosen one.
 *  - `fallback` the selector's start-up default when NOTHING named a country (a plain
 *               `en` browser lands on +1). The selector has to show something; the
 *               form's Country must not pretend that something was a guess, so it
 *               never fills anything.
 *  - `external` the selector moved BECAUSE the form's country changed. Echoing that
 *               back would loop, so it changes nothing.
 */
export type PhoneCountrySource = 'init' | 'fallback' | 'pick' | 'typed' | 'external';

export const isShippingCountry = (iso2: string | undefined | null): boolean =>
  !!iso2 && SHIPPING_COUNTRIES.some(c => c.code === iso2);

/**
 * The destination a SELECTED SAVED ADDRESS dictates: its own country when it is one we
 * deliver to, else '' — never the previous pick or the start-up guess. A legacy
 * (country-less) address used to inherit whatever the field held, and that inherited
 * value was what the CJ order was submitted with.
 */
export const countryOfSavedAddress = (countryCode: string | undefined | null): string => (isShippingCountry(countryCode) ? (countryCode as string) : '');

/**
 * The destination country a phone-country change should set, or `null` to leave the
 * current one alone. A dial code for a country we do not ship to never changes the
 * destination — the customer simply has a foreign number.
 */
export const countryFromPhone = (phoneIso2: string, source: PhoneCountrySource, currentCountry: string | undefined | null): string | null => {
  if (source === 'external' || source === 'fallback') return null;
  if (!isShippingCountry(phoneIso2)) return null;
  if (phoneIso2 === currentCountry) return null;
  if (source === 'pick') return phoneIso2;
  return currentCountry ? null : phoneIso2;
};

const fold = (s: string) =>
  s
    .trim()
    .toLowerCase()
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '');

/**
 * The region text to keep after the country changed from `prevCountry` to
 * `nextCountry`. A region that belongs to the new country survives (in the list's
 * own spelling); one left over from a DIFFERENT country is cleared, because
 * "Bayern, Sweden" is an address no carrier can deliver to. Text typed before any
 * country was chosen is kept — the region field is free text by design.
 */
export const regionAfterCountryChange = (region: string | undefined | null, prevCountry: string | undefined | null, nextCountry: string | undefined | null): string => {
  const current = region ?? '';
  if (!current.trim() || (prevCountry ?? '') === (nextCountry ?? '')) return current;
  const match = regionsFor(nextCountry ?? undefined).find(r => fold(r) === fold(current));
  if (match) return match;
  return prevCountry ? '' : current;
};

/**
 * The delivery country a typed region names, or `null`: an exact (accent-folded) match
 * that is UNIQUE across the delivery countries' region lists. "Bayern" is Germany;
 * "Atlantis" is nobody's; a name two countries share names neither. Free text in every
 * other respect — the region field never rejects anything.
 */
export const countryFromRegion = (regionText: string | undefined | null, list: LabelledRegion[] = deliveryRegions()): string | null => {
  const q = fold(regionText ?? '');
  if (!q) return null;
  const hits = list.filter(r => fold(r.region) === q);
  return hits.length === 1 ? hits[0].country : null;
};

/** The two fields of a delivery form that must move together. */
export interface CountryRegion {
  country: string;
  region: string;
}

/** The form after its country changed: the new country, and a region that still fits it. */
export const withCountry = (state: CountryRegion, nextCountry: string): CountryRegion => ({
  country: nextCountry,
  region: regionAfterCountryChange(state.region, state.country, nextCountry),
});

/**
 * The form after the region changed: the text as typed, and — ONLY while the country is
 * still empty — the country that region names (a pick from the cross-country list
 * supplies `pickedCountry`; a typed exact unique match is looked up). A chosen country
 * is never overridden by the region: "Bayern" under Sweden is the customer's business.
 * When the region fills the country it takes the list's own spelling ("skane" → "Skåne").
 */
export const withRegion = (state: CountryRegion, regionText: string, pickedCountry?: string | null): CountryRegion => {
  if (state.country) return { country: state.country, region: regionText };
  const country = (isShippingCountry(pickedCountry) ? (pickedCountry as string) : null) ?? countryFromRegion(regionText);
  if (!country) return { country: '', region: regionText };
  const canonical = regionsFor(country).find(r => fold(r) === fold(regionText)) ?? regionText;
  return { country, region: canonical };
};

/** The form after the phone's country changed, or `null` when the destination stays put. */
export const withPhoneCountry = (state: CountryRegion, phoneIso2: string, source: PhoneCountrySource): CountryRegion | null => {
  const next = countryFromPhone(phoneIso2, source, state.country);
  return next ? withCountry(state, next) : null;
};
