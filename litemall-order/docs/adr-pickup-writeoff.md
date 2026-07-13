# ADR — In-store pickup + write-off (核销) (Wave 4, Task B)

Status: accepted · 2026-07-13 · owner: `litemall-order`

## Decision

crmeb's stores/pickup/write-off vertical, entirely inside `litemall-order`. V35 adds
`litemall_store` and five `litemall_order` columns (`delivery_type` default 'express',
`store_id`, `verify_code varchar(12) UNIQUE`, `verify_time`, `verified_by`). The verify
code is ORDER STATE and the write-off is a STATE TRANSITION — both commit with the
timeline hop in one transaction through the orchestrator.

## Key choices (and deviations)

- **Verify code at PAY, not at create (deviation from crmeb):** generated inside
  `markOrderPaid`'s transaction (10-digit SecureRandom; UNIQUE-index dup-key retry,
  5 attempts; `verify_code IS NULL` guard makes pay retries idempotent). An unpaid
  order therefore NEVER carries a redeemable code.
- **Status model: reuse PAID (no READY_FOR_PICKUP status).** A paid pickup order sits
  in PAID until the counter scan. `PAID.canTransitionTo` gained DELIVERED, but the
  REAL guards are (a) the aggregate's pickup + explicit-PAID gate in `writeOff()`,
  (b) the explicit-SHIPPED gates added to `confirmDelivery()`/`confirmReceipt` so a
  customer receipt can never ride the widened graph, and (c) the conditional
  `WHERE order_status=201 AND verify_time IS NULL` UPDATE — a double scan loses on
  0 rows, cleanly.
- **All pickup submit 422s fire in the orchestrator PRE-CHECK zone** (kill-switch off,
  CJ lines in cart, store missing/hidden, blank pickup contact) — the in-TX-422→502
  landmine. `placeOrder` keeps typed safety-net copies (`LitemallPickupException`,
  rethrown through the orchestrator, mapped to 422 in the REST layer).
- **Address column stores ONLY `"PICKUP: <store.name>"`** (varchar(127) — a full
  store address would truncate). Store details render from `litemall_store` via
  `store_id`; consignee/mobile are the pickup contact; `address_id` stays NULL.
- **Freight is 0 by definition** (no shipping leg) — decided before the freight
  ladder runs; the coupon/groupon money pipeline is untouched.
- **Ship refuses pickup orders** (422 via the SHIP error mapping): fulfillment is the
  write-off, never a courier hand-off. CJ × pickup is rejected at submit (CJ ships
  from CJ warehouses).
- **Write-off audit:** `verified_by = "admin:<X-User-Id>"` (gateway-relayed admin id),
  timeline hop `writeoff`, `LitemallOrderDeliveredEvent(orderId, false)` — the same
  event a customer receipt raises, so downstream listeners need no new case.
- **Three distinct scan errors** (`LitemallWriteoffException.Kind`): UNKNOWN_CODE /
  ALREADY_VERIFIED / WRONG_STATE — each a 422 whose errmsg names the problem
  (`[KIND] message`), thrown out of the @Transactional orchestrator and caught in the
  controller (outside the proxy — no rollback-only trap).
- **Kill-switch `litemall.order.pickup-enabled` (default true):** off → NEW pickup
  submits 422; reads, write-off, and already-placed pickup orders keep working.
- **Staff RBAC deferred:** crmeb binds write-off staff to wx-customer uids — a
  different identity universe. Write-off is ROLE_ADMIN (edge-gated) this wave.

## Known gaps (flagged)

- **No code expiry / AutoConfirm gap:** a paid pickup order that is never collected
  stays PAID forever (the auto-confirm sweep only touches SHIPPED orders). A later
  wave needs a pickup-timeout policy (remind → refund?). Deliberately NOT auto-expired
  this wave — silently voiding a paid order's code is worse than a stale one.
- Store deletion is logical; historical orders keep rendering their store. New
  submits against a hidden/deleted store 422.
