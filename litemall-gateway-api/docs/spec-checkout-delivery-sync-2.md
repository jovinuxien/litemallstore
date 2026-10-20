# Checkout delivery sync, pass 2 — Country / Region / Mobile refinement

**Status:** BUILT 2026-10-20 on `fix/gateway-api` — four commits in the order D `17ff4b36f`,
A `507e874f4`, B `c291c555d`, C `1391f3864` (D first because every later test needs Denmark
to exist). jest 68 suites / 516 tests, tsc 0, `i18n:check` 0, headless acceptance 69/69 with
0 page errors (§6). **MERGED to master `ddbfc13e9` + DEPLOYED to trovemo.com 2026-10-20
17:22 UTC** (image `litemall/gateway-api:build-ddbfc13e9`, rollback `build-96978ece4`; the
repo Dockerfile's gateway-admin SPA build failed with an EPIPE in fork-ts-checker — unrelated
module — so the jar was built from a gateway-api-only Dockerfile copy). This document is the
contract; the CLAUDE.md block is only its index.

Scope: storefront SPA only (`litemall-gateway-api/src/main/webapp`). No backend, no
migration, no env. Deploy = the gateway-api container from a WORKSTATION-built jar
(never build on the VPS).

## 0. What pass 1 (2026-10-03, `f11979839`) already fixed — do NOT redo

Rules live in ONE module, `app/shared/util/deliverySync.ts`, called by checkout
(`app/views/commonViews/cart/Checkout.tsx`) and the address book
(`app/modules/user/AddressEdit.tsx`):

- Selecting a Country sets the dial code (digits kept).
- Picking a dial code (`source: 'pick'`) sets the Country when we deliver there; a pick
  outside the delivery list leaves Country alone and `PhoneInput` says why
  (`common:forms.phone.notDelivered*`).
- `init` (selector start-up default) and `typed` (full international number) only fill an
  EMPTY Country. `external` (selector followed the form's Country) changes nothing.
- A full international number, typed or stored, is never re-prefixed.
- A Region survives a Country change only if it belongs to the new Country
  (`regionAfterCountryChange`).
- `PhoneInput` lists delivery countries first under "We deliver to" (`4c86928bf`).

Specs: `deliverySync.spec.ts` (12), `PhoneInput.spec.tsx` (17), `dialCodes.spec.ts`.

## 1. Audit facts (2026-10-20, read off the tree at `73ca7d0ee`)

1. **Saved-address path can disagree with the destination.** `Checkout.tsx` renders an
   editable "Destination country" `<Form.Select>` for the CJ block whenever
   `!usingNewAddress` (around :1385). Submit sends `countryCode: country.code` for the CJ
   group (:869) while the address text and the review summary (:1032) show the saved
   address's OWN `countryCode`. A Stockholm address + select switched to Germany is
   accepted silently. `pickAddress` (:699) only calls `setCountry` when the address HAS a
   country, so a legacy (pre-V48, country-less) address inherits the previous pick.
   `AddressCard` detail (:1253) = province/city/county/addressDetail — no country.
2. **Start-up guess writes the fallback into Country.** `localeIso2()`
   (`app/shared/data/dialCodes.ts:131`) returns `'US'` when nothing in
   `navigator.languages` matches; `PhoneInput` emits it with `source: 'init'`;
   `countryFromPhone` fills an empty Country with it. A plain `en` browser therefore
   pre-selects United States on an EU store. The site language the visitor chose
   (`app/i18n/locale.ts`, cookie `lm_lang`, sv/da) is never consulted.
3. **Region never informs Country.** `RegionInput` gets `regionsFor(country.code)`, which
   is `[]` while Country is empty, so the field is plain text; a typed "Skåne" or
   "Bayern" is ignored as a signal.
4. **Denmark absent.** `SHIPPING_COUNTRIES` (`app/shared/data/countries.ts`) = US, GB,
   SE, NO, DE, FR, CA, AU. `da` is an enabled language, Wave 24 names the market
   DE/FR/DK/SE, `LANGUAGE_COUNTRY` already maps `da → DK`. A Danish browser lands on +45
   with Country blank and NO note (the note requires an explicit pick). Nothing
   server-side blocks DK: `AddressSaveRequest` only length-checks, the charged CJ freight
   is the flat `litemall_system` rule (Wave 24.1), CJ quotes by country code.

## 2. Deliverables

### A. Saved-address honesty
- When the selected saved address carries a delivery country, the CJ block renders a
  read-only line ("Delivering to Sweden", i18n `checkout:delivery.deliveringTo`) instead
  of the select, and `country` state == that address's country (already set by
  `pickAddress`; make the select unreachable so they cannot diverge).
- The select stays ONLY for a saved address with no `countryCode`. A pick there is
  written back to that address at submit — the same pattern as step 1a (phone write-back,
  :776-790): `userApi.addressSave({ ...selectedSavedAddress, countryCode })`, local
  `addresses` updated, failure ⇒ `addrError` + back to step 1, never a silent order.
- Selecting a country-less saved address RESETS `country` to empty (no inheritance from
  the previous pick or the init guess).
- `AddressCard` detail gains the address's country name (from `SHIPPING_COUNTRIES`,
  else the raw code, else nothing).

### B. Honest start-up guess
- `dialCodes.ts`: add `localeGuess(): { iso2: string; confident: boolean }` (or
  equivalent) — confident when the match came from a browser region (`sv-SE`, `en-GB`),
  a language that names one country (`LANGUAGE_COUNTRY`), or the SITE language the
  visitor chose (`sv → SE`, `da → DK`, consulted FIRST — an explicit choice outranks the
  browser list). The bare `'US'` fallback is NOT confident. `localeIso2()` keeps its
  signature for existing callers.
- `PhoneInput` reports `init` only for a confident guess; the fallback is emitted as a
  new source `'fallback'` (or `init` with a flag — pick one, document it in
  `PhoneCountrySource`). `countryFromPhone` never fills Country from the fallback. The
  dial selector itself keeps today's default.
- A confident init guess OUTSIDE the delivery list shows the existing
  `notDeliveredChoose` note, so a blank Country is explained (today only an explicit pick
  earns the note).

### C. Region informs Country
- While Country is empty, `RegionInput` suggestions = regions of EVERY delivery country,
  each labelled with its country ("Skåne · Sweden"); picking one sets BOTH region and
  Country. Typing an exact (accent-folded) match that is unique across delivery countries
  also fills Country. Goes through the same fill-only-empty rule as `typed`; a chosen
  Country is never overridden by the region.
- New helper in `deliverySync.ts` (e.g. `countryFromRegion(regionText): string | null`)
  so the rule is testable without React. Region stays free text in every case.
- `regions.ts` exports the flat labelled list once (computed, cached).

### D. Denmark
- `SHIPPING_COUNTRIES` += `{ name: 'Denmark', code: 'DK' }`; `REGIONS_BY_COUNTRY.DK` =
  Hovedstaden, Midtjylland, Nordjylland, Sjælland, Syddanmark. Address book inherits it
  (same list). Verify the dial `LEN.DK = [8, 8]` stays correct (it does).

## 3. Tests and acceptance
- jest + tsc + `npm run i18n:check` green with REAL counts (baseline 66 suites / 479
  tests). Extend `deliverySync.spec.ts` (A reset rule, B fallback never fills, C
  countryFromRegion incl. ambiguous/unique/accent cases), `PhoneInput.spec.tsx` (fallback
  source, note on confident non-delivery init), `dialCodes.spec.ts` (site-language-first
  guess), NEW `RegionInput.spec.tsx` (labelled cross-country list, pick sets country).
- Headless real-click run of the BUILT bundle against the stub edge (recipe:
  `docs/CHECKOUT-VERIFICATION-RUNBOOK.md` + memory
  `project_gateway_api_delivery_sync_pay_reviews.md`: serve `target/classes/static`, node
  stub answering `/srv` + `/auth` canned envelopes, seed `sessionStorage.customerToken` +
  `localStorage.cart`, puppeteer-core + google-chrome, native value setter + `input`
  event) in en, sv-SE, da, de-DE:
  1. saved address WITH country ⇒ read-only line, no select, submit body carries that
     country;
  2. legacy address WITHOUT country ⇒ select shown, pick DE ⇒ the stub observes
     `addressSave` with `countryCode: 'DE'` before the order submit;
  3. switching from a DE address to a country-less one ⇒ Country empty;
  4. new address, plain `en` browser ⇒ Country stays "-- Country --", dial +1;
  5. new address, site language `sv` on an `en-US` browser ⇒ Country Sweden;
  6. new address, Country empty, type "Bayern" and pick ⇒ Country Germany, dial +49;
  7. Danish browser ⇒ Country Denmark, dial +45, 5 region suggestions.
  0 page errors; all new strings in en/sv/da together (`i18n:check` reorders keys —
  commit the reorder).
- Address-book regression: `AddressEdit` still round-trips (it shares the rules).

## 4. Deploy and record
- Commit per item (A, B, C, D) on `fix/gateway-api`; merge to master as usual; deploy =
  MAIN or this session with the workstation-build recipe; rollback = previous image tag.
- Update the CLAUDE.md gateway-api block and the memory note on completion.

## 5. Deferred (raised, not in scope)
- Edge geo hint from Cloudflare's `CF-IPCountry` (new Java `/auth/geo` endpoint) — most
  accurate default, but browser + site language cover this market; revisit if the
  fallback case stays common.
- City suggestions need the Places API key (`placesApiKey` null in prod).

## 6. As built (2026-10-20)

- **A.** `deliverySync.countryOfSavedAddress(countryCode)` is the reset rule (own country when
  we deliver there, else `''`), used by `pickAddress` AND the initial default-address load.
  The CJ block renders `[data-delivering-to]` ("Delivering to <strong>Sweden</strong> — the
  country saved on this address", `checkout:delivery.deliveringTo`) when the saved address
  carries a delivery country, the select only when it does not. The phone's dial-code pick
  cannot move a saved address's country either (`followPhoneCountry` returns early). Submit
  step 1a is ONE write-back patch `{tel?, countryCode?}` → one `addressSave`; failure ⇒
  `errors.saveCountry` when the country was part of it, else `errors.savePhone`.
  `countries.shippingCountryName(code)` feeds `AddressCard` detail in checkout and the
  address book.
- **B.** `dialCodes.localeGuess({ siteLang?, languages? })` → `{ iso2, confident }`; site
  language first (`LANGUAGE_COUNTRY`; `en` names nothing), then browser region, then
  language; bare `'US'` ⇒ `confident:false`. `localeIso2()` = `localeGuess().iso2` (kept for
  `splitInternational`). `PhoneCountrySource` gains `'fallback'`; `countryFromPhone` returns
  null for it. `PhoneInput` reads `useLocale().lang`, computes the start-up country once
  (`start` state: supplied by form/stored number ⇒ `init`, no note; locale guess ⇒ `init`
  when confident else `fallback`), and shows `notDeliveredChoose` for a confident guess
  outside the delivery list while the form has no country (`standingGuess`).
- **C.** `regions.deliveryRegions()` (cached `{region, country, label}` list, "Skåne ·
  Sweden", delivery-list order); `deliverySync.countryFromRegion(text, list?)` (fold, exact,
  unique) + `withRegion(state, text, pickedCountry?)` (fill-only-empty; canonical spelling
  when it fills). `RegionInput` props `labelled` + `onPick(region, country?)`; single-country
  behaviour unchanged. Checkout `applyRegion` and AddressEdit `applyRegion` route every
  region change through `withRegion`. No new strings (labels use the delivery list's English
  country names, as the Country select does). `countries.spec` pins region-name uniqueness
  across delivery countries.
- **D.** Denmark + 5 regions; `countries.spec` pins dial + length rule + regions for every
  delivery country.
- Tests added: countries.spec 5 (new), dialCodes +5, deliverySync +12, PhoneInput +6,
  RegionInput.spec 6 (new), AddressEdit +3.
- Headless: `docs/headless/checkout-delivery-sync-2/` (stub + driver + README). All seven §3
  cases plus fi-FI (confident non-delivery guess) and typed-exact "skane"; profiles en /
  sv-SE+site sv / da+site da / de-DE; 69/69, 0 page errors.
- Not changed, by design: the "Use new address" button keeps the previous saved address's
  country as the new form's starting value (§2.A only asked for the country-less saved
  address to reset; a customer adding a second address is likelier in the same country).
