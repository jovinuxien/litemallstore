# Plan — declare the store order amount to CJ (`shopAmount` + `unitPrice` on createOrderV2)

Status: DRAFT for approval, 2026-11-09. Worktree `order`, branch `fix/order`. No migration.

## 0. Why now

On 2026-11-09 the CJ account was funded (balance 100.00 USD) and the dashboard IOSS
setting was changed to "Declare with CJ's IOSS" for all 27 EU countries with
Declaration Amount = **Store Order Amount**. CJ's own rule for that option:

> "Your store order amount (if available) will be declared, otherwise, CJ order
> amount will be declared."

Our createOrderV2 request carries **no prices at all** (verified in the tree):
`CjCreateOrderRequest` has no `shopAmount`, and `CjOrderProduct` is `{vid, quantity}`.
So every placement falls back to CJ's own amount — CJ's cost price, not what the
customer paid. That is an undervalued customs/IOSS declaration made in our name on
every EU order. The fix is small and local to litemall-order.

CJ's documented request fields (developers.cjdropshipping.com, Shopping §createOrderV2):

| field | type | req | doc text |
|---|---|---|---|
| `shopAmount` | BigDecimal | N | "Order amount" |
| `products[].unitPrice` | BigDecimal | N | "item pricing" |
| `products[].storeLineItemId` | string | N | "lineItemId of your store order" |

Every amount CJ *returns* is documented "Unit: $ (USD)". The doc says nothing about the
currency of `shopAmount`/`unitPrice` — see D2.

## 1. Decisions to approve (reply in prose)

- **D1 — what "store order amount" is.** Recommend **goods value the customer actually
  paid**: `actualPrice − freightPrice − taxPrice` (coupon, group-buy and integral
  discounts are already inside `actualPrice`). Rationale: the €150 IOSS ceiling is on
  the *intrinsic value of the goods*; declaring freight and tax inside the amount could
  push a €140 basket over €150 and strip IOSS from it. Alternative: `actualPrice`
  (the literal order total). Either is one expression in one method.
- **D2 — currency.** Recommend sending **USD**, converted from the store's EUR with the
  inverse of the existing `litemall.order.fx-usd-eur` rate (prod 0.866 ⇒ ×1.1547),
  2dp HALF_UP — the same seam CjFreightQuoteService uses in the other direction, so
  one env var governs both. Shipped behind a knob
  `litemall.order.cj.declared-amount-currency = usd|store` (default `usd`) so CJ
  support's answer, whichever way it goes, is a container recreate, not a rebuild.
  **Ask CJ support in parallel**: "Is createOrderV2 `shopAmount` interpreted in USD?"
- **D3 — per-line `unitPrice`.** Recommend yes: `order_goods.price` (the unit price the
  line was sold at — group-buy lines already carry the combination price), same
  currency rule as D2. Also send `storeLineItemId` = our `litemall_order_goods.id`
  (free, aids reconciliation in the CJ dashboard). `storeProductName` is NOT sent
  (no value, Chinese-origin names would confuse CJ's own view).
- **D4 — kill switch.** `litemall.order.cj.declare-store-amount` (env
  `LITEMALL_ORDER_CJ_DECLARE_STORE_AMOUNT`, default **true**). `false` = today's
  request byte-for-byte. Explicit yml placeholders + prod compose passthroughs for
  both knobs (the price-floor / admin-notify lesson: no passthrough = silently inert).

## 2. Changes (one commit per numbered item)

1. **DTOs.** `CjCreateOrderRequest` + `shopAmount` (BigDecimal); `CjOrderProduct` +
   `unitPrice` (BigDecimal) + `storeLineItemId` (String). All `@JsonInclude(NON_NULL)`
   (already on the request class; add to the product class) — an absent price is
   OMITTED, never `0`.
2. **Placement model.** `CjOrderPlacement` + `declaredAmount` (BigDecimal, store
   currency); `CjOrderPlacement.Line` + `unitPrice` (BigDecimal, store currency) +
   `lineId` (Integer). The manual path `LitemallCjOrderController.toPlacement` leaves
   them null ⇒ request unchanged for that caller.
3. **Fill at the one builder that has the order:** `CjFulfillmentService.placeForPaidOrder`
   computes `declaredAmount` per D1 (null-safe: any missing component ⇒ `null`,
   negative result ⇒ `null` + WARN, never a clamp to 0) and sets each line's
   `unitPrice`/`lineId` from the `LitemallOrderGoodsAggregate`.
4. **Convert at the CJ boundary:** `CjDropshipOrderFacadeImpl.toRequest/toProducts`
   apply the knobs: when `declare-store-amount=false` ⇒ both fields null; when
   currency `usd` ⇒ `amount.divide(fxUsdEur, 2, HALF_UP)`; `store` ⇒ identity 2dp.
   fx guard identical to CjFreightQuoteService (non-positive ⇒ identity + WARN).
   One INFO line per placement: `CJ placement <sn>: declared <x> <cur> (store <y> EUR,
   fx <rate>, lines <n>)` — the audit trail for comparing against CJ's order view.
5. **Config.** yml (`litemall.order.cj.*`) with explicit placeholders; prod compose
   passthroughs beside `LITEMALL_ORDER_CJ_PLACEMENT_MODE` (docker-compose.prod.yml:574);
   `adr-cj-order-placement.md` gains a §"Declared amount" paragraph.

Nothing else moves: placement gating, sweep, approval, IOSS type (still omitted ⇒
dashboard rule), payType, email, logistics choice, money paths, mails.

## 3. Tests (real "Tests run:" counts — baseline 400 run / 0 failures, 2026-09-20)

- `CjFulfillmentServiceTest` (+5): declared = actual − freight − tax; coupon order
  (coupon already inside actual, not subtracted twice); group-buy line unitPrice =
  combination price; any null money ⇒ declaredAmount null, lines still priced where
  known; negative ⇒ null + no throw.
- `CjDropshipOrderFacadeImplTest` (+6, via the existing `ArgumentCaptor<CjCreateOrderRequest>`):
  usd conversion 2dp HALF_UP at fx 0.866 (e.g. 8.90 EUR ⇒ 10.28 USD); `store` mode
  identity; kill switch ⇒ `shopAmount` null AND every `unitPrice` null (byte-identical
  JSON to today, assert by serialising the request); null declaredAmount ⇒ field omitted
  from JSON (NON_NULL proven, not assumed); fx ≤ 0 ⇒ identity; manual-controller
  placement (no prices) ⇒ no price fields.
- Expected suite ≈ 411 run / 0 failures. ⚠ root pom skips `mvn test`; run the module
  with `-pl litemall-order` and read the surefire count.

## 4. Acceptance

**Dev (no CJ creds ⇒ the request shape is proven at the Feign seam, the Wave-24
precedent):** module suite green with the real count; order boots alone on :18085
(JDK 21) with Flyway validated and nothing applied; boot log shows the two new knobs.

**Prod (the only place the positive path exists):** after deploy, the first placed
order's INFO line states the declared amount; the CJ dashboard order view shows that
figure as the store order amount (not CJ's cost); `GET /srv/private/admin/order/{id}`
→ CJ detail (`iossTaxes`, `iossTaxHandlingFee`) is consistent with VAT on that amount.
Order 11 (€8.90, SE) is the natural first case **if it is Requeued after this deploys**;
if it is Requeued before, it is declared at CJ's amount — acceptable for a €2 VAT bill,
and the second order proves the change.

## 5. Deploy (MAIN or this session; never build on the VPS)

Order container only. Workstation jar → wrap `FROM litemall/order:<current tag>` +
`COPY app.jar` → `up -d --no-deps order`. Add to `.env.prod` only if a non-default is
wanted (both knobs default to the recommended values). Rollback = previous image tag,
or `LITEMALL_ORDER_CJ_DECLARE_STORE_AMOUNT=false` + recreate (no rebuild).

## 6. Explicitly out of scope (raised, separate decisions)

- **€150 copy** — 2,796 of 20,505 live products are above €150 (live feed 2026-11-09);
  no storefront page mentions customs, duties or import VAT. gateway-api copy and/or a
  catalogue cap — user decision.
- **Stripe Tax is OFF in prod** (yml default `enabled:false`, and the prod compose
  block has NO `LITEMALL_ORDER_TAX_*` passthrough). With CJ's IOSS, CJ pays import
  VAT and bills the balance VAT + 3% of VAT per order out of margin, uncollected from
  the buyer. Pricing-policy decision; if Stripe Tax is wanted, the passthrough is the
  first step.
- Whether the declared amount should ALSO be written to a CJ-side remark or to our
  timeline — not needed; the INFO line + CJ's own order record suffice.
