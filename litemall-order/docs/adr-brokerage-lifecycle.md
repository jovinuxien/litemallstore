# ADR: brokerage commission lifecycle (Wave 5)

Status: accepted (design locked in
`doc/affiliate-program-feasibility-plan-2026-07-16.pdf`, approved 2026-07-16).

## Context

Affiliates are `litemall_user` rows with `is_promoter = 1` (admin-granted;
never `litemall_admin`). Attribution is permanent `spread_uid` binding at
registration. Commission is a flat global single-level rate. The ledger is V7's
`litemall_user_brokerage_record`; the withdrawable balance is
`litemall_user.brokerage_price`. Engine: order's
`application/internal/BrokerageService` (+ `BrokerageUnfreezeScheduler`,
`LitemallExtractServiceLayer`).

## Ledger semantics

Income rows (`pm = 1`, `link_type = 'order'`, `link_id = orderSn`):

```
            pay commits                    freeze window elapses
  (none) ────────────────▶ FROZEN (0) ────────────────────────▶ VALID (1)
                               │                                    + brokerage_price += price
                               │ aftersale approved pre-maturity
                               ▼
                          INVALID (-1)      (balance never touched)
```

- **FROZEN (0)** — written by the AFTER_COMMIT listener on
  `LitemallOrderPaidEvent`: `price = goodsPrice × rate% (HALF_UP, 2dp)`,
  `balance` = snapshot of the promoter's `brokerage_price` at write time,
  `freeze_time = now`, `unfreeze_time = now + litemall_brokerage_freeze_days`.
  The pay path's guarded 0-row UPDATE means the event fires at most once per
  order → exactly one frozen row. The money is NOT in `brokerage_price` yet.
- **VALID (1)** — the unfreeze sweep (5-min cadence, one transaction per row)
  flips 0→1 with a guarded UPDATE and credits `brokerage_price` atomically.
  A 0-row flip = a concurrent sweep or a clawback won the race → skip, never
  retry-credit. A failed credit (user vanished) rolls the flip back; the row
  stays frozen and is retried next sweep.
- **INVALID (-1)** — `approveAftersale` invalidates the order's still-frozen
  row inside the same transaction as the refund (guarded `status = 0` → -1).

Withdrawal rows (`pm = 0`, `link_type = 'extract'`, `link_id = extract id`)
are born VALID: the guarded `debitBrokerage` (with `>=` overdraw guard)
already moved the money when the row is written. They double as the marker
that an extract is brokerage-sourced (`litemall_user_extract` has no source
column — deliberate, this wave adds no schema). An admin rejection refunds
`brokerage_price` and flips the debit row to INVALID with an audit mark.

## Decision: unfrozen-then-refunded rows stay VALID

If an aftersale is approved AFTER the commission matured, the clawback is a
no-op (guarded on `status = 0`) and the promoter keeps the money. Rationale:
once VALID the amount is withdrawable — possibly already withdrawn — and a
negative clawback would either overdraw or create debt bookkeeping we don't
want in v1. **The freeze window exists precisely to make this case rare**:
size `litemall_brokerage_freeze_days` to cover the realistic aftersale window.
Accepted, documented deviation — not a bug.

## Kill switch and config reads

`litemall_brokerage_enabled = 'false'` stops NEW commission rows at the pay
listener. It does NOT stop the unfreeze sweep (already-earned frozen rows
still mature), clawbacks, or withdrawals — earned money stays owed. Config is
read through `LitemallSystemConfigService` per event/request, never through
litemall-core's static `SystemConfig` cache (per-JVM, stale across services);
V39 seeds the four rows because `updateConfig` silently no-ops unknown keys.

## Failure containment

Payment must never block or fail on commission bookkeeping: the listener only
enqueues onto a single-thread bounded executor; the worker catch-alls into
WARN. Unlike the receipt printer (discard-oldest, reprintable), a dropped
award is logged loudly with reconciliation guidance — but still dropped rather
than ever back-pressuring pay.

## Out of scope in v1 (locked)

Cookie/click attribution, multi-level commission, per-goods rates, self-serve
promoter signup, `pay_count` maintenance (the column is surfaced as stored;
nothing increments it yet), and negative-balance clawback of matured
commissions.
