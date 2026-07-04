# Handoff (gateway-api): CJ orders are now first-class local orders — pay-first, then placed at CJ

**Status:** order-side DONE on `fix/order` (2026-07-04), unit-tested 56/56. Activates for
customers once the SPA follow-up below lands. Until then the deprecated direct endpoint
keeps working, but its orders remain invisible in My Orders and unpaid.

## What changed on the order service

1. **Schema (V27, litemall-db):** `litemall_order` gains `address_id`, `country_code`,
   `source` ('local' | 'cj', default 'local'), `cj_order_id`, `cj_order_num`.
2. **Submit tags the order.** `POST /srv/order/submit` resolves each cart line's
   `litemall_goods.source`; an all-CJ cart creates a normal UNPAID (101) order with
   `source='cj'`. A cart mixing CJ and local items is rejected with the clean
   submit-failed envelope ("submit them as separate orders") — the SPA already splits.
3. **Optional submit field `countryCode`.** The address book has no country, but CJ
   `createOrder` requires a destination country. The checkout country picker's ISO code
   must now ride the submit body (`{ ..., countryCode: "NO" }`) and is persisted on the
   order. Fallback: `spring.cjdropship.api.ship-to-country-code` (empty by default →
   CJ placement fails cleanly with a message telling ops to set it).
4. **Pay places at CJ.** `POST /srv/order/{id}/actions/pay` on a `source='cj'` order:
   wallet debit (or client-confirmed CARD) → order marked PAID → **CJ createOrder inside
   the same transaction** (merchant orderNumber = `order_sn`, CJ's idempotency key;
   structured address re-resolved from `address_id`) → `cj_order_id`/`cj_order_num`
   recorded. **CJ rejection rolls the whole payment back**: debit undone, order stays
   101, response is the pay-failed envelope `"CJ fulfillment could not be placed: <CJ
   message>"`. No paid-but-unfulfillable order; no CJ order without payment.
5. **List/detail expose it.** `/srv/order/list` items carry `source` + `cjOrderNum`;
   `/srv/order/detail` carries `source` + `cjOrderId` + `cjOrderNum` (all omitted when
   null / 'local' rows show `source: "local"`). CJ orders now appear in every showType
   bucket, the detail page, and `/srv/order/{id}/timeline` like any other order.
6. **`POST /srv/order/cj/orders` is @Deprecated** — it bypasses payment and persists
   nothing. Delete it once the SPA stops calling it.

## SPA follow-up (this makes the feature live)

In `orderSlice.ts` / `Checkout.tsx`:

- Route CJ items through the SAME two-step place→pay as local items: submit the CJ
  cart group to `/srv/order/submit` (include `countryCode` from the country picker),
  then pay the returned `orderId` via `/actions/pay`. Drop the `baseAxios.post(
  '/order/cj/orders')` call and the client-generated `CJ-<uid>-<ts>` order number.
- Mixed carts: keep submitting two orders (one local, one CJ) — the backend now 422s
  a mixed submit.
- Give `/actions/pay` a per-request `timeout` (≥ 30 s) for CJ orders: the CJ placement
  runs inside the pay call and CJ regularly takes 3–10 s (same 5 s-global-timeout trap
  as the 2026-07-04 incident on the old endpoint).
- My Orders / detail: render a "Dropship" badge when `source === 'cj'` and show
  `cjOrderNum` on the detail view.

## Rollout order (services run from MAIN)

1. Merge `fix/order` → master.
2. `mvn -pl litemall-db clean install` (V27 + hand-maintained domain/mapper), then
   restart the order service — Flyway applies V27 on boot.
3. Land the SPA follow-up (above) in gateway-api.

## Deliberate boundaries / follow-ups

- CJ HTTP call runs inside the payment DB transaction (~3–10 s). Accepted for now;
  the evolution is outbox-based async placement with compensation. If the commit
  failed AFTER CJ accepted (rare), `order_sn` idempotency prevents a duplicate on
  the retried pay.
- Refund of a paid CJ order settles money to the tender (wallet credit) but does NOT
  cancel the CJ order — CJ-side cancellation/dispute is a separate follow-up.
- Auto-cancel of unpaid CJ orders is safe by construction: nothing exists at CJ
  before payment.
