# Display currency — show local prices, charge euros (stage A)

User-commissioned 2026-11-15 (plan approved the same day). Module: `litemall-gateway-api` only
(edge + storefront). No migration, no change to order / promotion / goods-management.

## 1. Decisions (user, 2026-11-15)

1. **Two stages.** A = SHOW prices in the visitor's currency while the store keeps CHARGING EUR
   (this document). B = charge in the local currency — its own multi-service wave, not started.
2. **Currencies:** EUR (default), SEK, DKK, NOK, GBP. Visitor country → currency: SE→SEK, DK→DKK,
   NO→NOK, GB→GBP, everyone else EUR.
3. **Rates:** the European Central Bank's daily euro reference rates, fetched automatically.
4. **Rounding:** shop prices — kronor to whole units; pounds keep two decimals (a pound is worth
   about a euro, whole-pound rounding would move cheap prices by up to 10%).

This reverses nothing in Wave 24: `spec-i18n-foundation.md` §0 named an indicative local price as
the honest display-only option, and that is what stage A is. A converted price is an ESTIMATE — the
customer's bank debits euros at its own rate — so every place a total is about to be paid shows the
euro amount as the binding figure.

## 2. Edge

- `web/currency/EcbRates` — parses `eurofxref-daily.xml` (attributes only, DTDs refused); a file
  without a dated cube or without a usable rate is rejected, one unreadable currency is dropped alone.
- `web/currency/EcbRateService` — last good copy in memory. Fetched at boot and then LAZILY: a read
  that finds the copy older than `refresh-hours` (6) starts one background fetch and answers with
  what it has; a failed fetch keeps the copy and retries after 10 min. No scheduler. `current()` is
  **null** when there has never been a fetch or when the ECB's own date is more than `max-age-days`
  (7 — the ECB publishes on working days only) old: a stale rate is a wrong price, no rate is the
  euro price we charge anyway.
- `GET /auth/currency` (`CurrencyController`, public, `Cache-Control: no-store`) →
  `{country, suggested, enabled[], rates{}, asOf}`. `enabled` = the operator's list narrowed to
  currencies with a fresh rate, EUR first; `suggested` = the currency of Cloudflare's
  `CF-IPCountry` when enabled, else EUR; `rates` = units per one euro.
- Config `litemall.currency.display` ← **`LITEMALL_CURRENCY_DISPLAY`** (default `EUR` = OFF: no
  selector, no conversion, no ECB fetch). Prod value `EUR,SEK,DKK,NOK,GBP`. Explicit yml
  placeholders; prod compose passthrough added with the feature. Off again = env + recreate.

## 3. Storefront

- `shared/util/currency.ts` — observable store (the `locale.ts` pattern). Resolution, first ENABLED
  hit wins: `?currency=` → cookie `lm_cur` → the edge's suggestion → EUR. A currency without a
  usable rate is never enabled. The last answer is cached in localStorage for a day so a returning
  visitor's first paint is already in their currency. Any failure ⇒ EUR-only.
- `shared/util/money.ts` — two families, and choosing between them is the design:
  - `money` / `moneyShort` / `moneyParts`: the DISPLAY currency, converted and rounded. For
    everything a shopper browses: cards, product page, cart lines, coupons, delivery options.
  - `moneyCharged`: always euros to the cent. For anything that is or records a charge.
  - `chargedNote(v)`: the euro amount to show beside a converted total, null on EUR.
  Kronor are written `<n> kr`, pounds `£<n>`, with the active language's separators.
- **Euro stays binding where money moves:** product buy box and cart total carry "Charged in euros:
  €x"; the checkout summary shows "Total in SEK (estimate) ≈ 297 kr" and the bold row "You pay
  €26.93". Order list / detail / confirmation / refunds / pay page — including their line items
  (`GoodsLineCard charged`) — are euros only.
- **Unchanged, deliberately euros:** Stripe Elements currency, analytics events, the server-injected
  page head (JSON-LD), feed, sitemap. Bots see the same price as every visitor.
- `CurrencySwitcher` beside the language switcher (header + footer); hidden while only EUR is on
  offer, and on `/checkout` + `/pay/*`.
- Re-render: prices are written by a plain formatter, so `App.tsx` keys the route tree on
  currency + rate (`CurrencyKeyed`). Redux state survives; page-local state resets — the reason the
  selector is absent on checkout.

## 4. Known limits (stated, not hidden)

- **Rounded prices do not add up.** 3 × 139 kr reads 417 kr while the converted total may read
  416 kr. The euro total is the binding one. Stage B removes this.
- **The search price filter takes euros.** The index is priced in EUR and the stock range widget
  cannot convert; with a converted display its heading reads "Price in euros (€)". (The plan said
  "convert typed values back" — not built; needs a custom range widget.)
- Help/FAQ prose that quotes the flat shipping rate keeps its € wording.
- A non-euro visitor's FIRST page load paints euros for the instant before `/auth/currency`
  answers, then re-renders; repeat loads paint from the cache.
- `kr` does not say which krona; the selector names the currency.

## 5. Acceptance (as run 2026-11-15)

- Java (gateway-api): **106 run / 0 failures** (+22: `EcbRatesTest` 4, `EcbRateServiceTest` 7,
  `CurrencyControllerTest` 10, policy test +1).
- Storefront: jest **71 suites / 552 tests** (was 69 / 523), tsc 0, `i18n:check` 0, prod build clean.
- Headless real-click run of the BUILT bundle against a stub edge, one browser context per case
  (`docs/headless/display-currency/`): **67/67, 0 page errors** — SE/DK/NO/GB each on product page,
  cart, checkout and order record; DE on euros; selector pick + reload; cookie beats country;
  `?currency=` beats cookie; sv/da writing; old edge (404), edge down, feature off and stale rates
  all fall back to euros with no selector. Regression: checkout email-guarantee harness 39/39.
- The real ECB file was read on 2026-11-15 (fixed 2026-11-13) and has exactly the shape the parser
  tests use. The edge's own outbound fetch is first proven by the prod boot line
  `ECB reference rates loaded, fixed <date>`.

## 6. Stage B (not started)

Charge in SEK/DKK/NOK/GBP: per-order currency column, Stripe presentment currency, refunds in the
tender currency, coupon/freight/margin guards per currency, mails, admin. Should follow the open
landed-cost decisions (`litemall-order/docs/plan-landed-cost-2026-11-15.md`) — both change how
prices are set.
