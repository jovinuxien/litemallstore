# ADR: CJ order lifecycle parity (Wave 3 — Tasks C/D)

Status: accepted (2026-07-10). Extends `adr-cj-order-placement.md` (pay-first placement,
V27 linkage) with the full CJ order lifecycle: live stock at submit, createOrderV2 +
confirm + payment, status sync, delete-on-cancel, and the admin balance readout.

## Decision 1 — createOrderV2 payType=3 (create-only), pay post-commit

`createOrderV2` offers three payment modes: `1` (returns a pay-URL), `2` (auto-deducts
the CJ account balance in the same call), `3` (create-only draft). We send **payType=3**
and keep the placement INSIDE the local pay transaction (unchanged pay-first semantics:
`order_sn` is CJ's idempotency key, a CJ rejection rolls the wallet debit + PAID status
back).

Why not payType=2: it moves real money inside a transaction that can still roll back.
A rollback after CJ accepted would leave the balance spent with no local order — and
`deleteOrder` is blocked once a CJ order is paid, so the window is unrecoverable. A
payType=3 draft is free to create, replay-safe, and deletable.

The draft is then driven forward OUTSIDE the transaction:

1. **afterCommit hook** (orchestrator pay path): async first `CjLifecycleService.advance`
   pass right after the pay TX commits.
2. **`CjOrderStatusSyncScheduler`** (default every 5 min, batch 20, ~1.1s pacing):
   re-runs `advance` for every open CJ order until CJ reports DELIVERED/CANCELLED —
   self-healing if the hook's pass was lost.

Each `advance` pass: `getOrderDetail` → record the CJ status hop → at most one CJ
mutation: CREATED/IN_CART → `confirmOrder` (PATCH — Feign runs on OkHttp for this,
`spring.cloud.openfeign.okhttp.enabled`); UNPAID → `payBalance` **iff**
`spring.cjdropship.api.auto-pay-balance=true` (off = orders wait for manual payment in
the CJ dashboard; the shortfall is visible via the admin balance endpoint).

**Sandbox:** `spring.cjdropship.api.sandbox=true` (the dev default in this repo's yml)
sends `isSandbox=1` on createOrderV2, so confirm/payBalance are simulated by CJ and no
real balance moves. Production flips it to false via config.

**IOSS (found live, 2026-07-10; updated 2026-07-13):** unlike legacy createOrder,
createOrderV2 REJECTS EU-destination orders without an IOSS declaration (`100104: ...
7001: Please enter a IOSS number`). Live findings: `iossType=1` (no IOSS) does NOT
satisfy it — CJ still demands a number for an SE destination — and **`iossType=3`
(CJ's IOSS service) is ALSO rejected without a number unless the service is activated
on the CJ account**. So EU shipping requires a real IOSS arrangement, config-driven:
`CJ_IOSS_TYPE=2` + `CJ_IOSS_NUMBER=IM…` (own registration) or CJ's IOSS service
enabled account-side for type 3. A rejected EU placement still fails the payment
cleanly (402, wallet rolled back). Non-EU destinations (GB, NO, US…) are unaffected —
live-verified 2026-07-13 (order 79 → CJ 20260713619543).

**Customer email (found live, 2026-07-13):** createOrderV2 now also rejects a
missing/invalid `email` (`3001: Please enter your email address`). The placement
carries the ordering user's `litemall_user.email` when present; otherwise the
configured `spring.cjdropship.api.customer-email-fallback` (default
`orders@litemall.dev`) is sent, so guest/seed accounts without an email never block
fulfillment.

## Decision 2 — CJ→local status mapping (single-transition-source preserved)

`litemall_order.cj_order_status` (V33) is a pure projection of the last CJ status seen;
the LOCAL `order_status` still moves only through the guarded state-machine transitions.
Every cj_order_status change is also recorded on the `litemall_order_status` timeline as
a same-status marker (changeType `cj_sync`, operator `system`) — so the timeline shows
every CJ hop even when nothing moves locally.

| CJ status            | Local effect                                                        |
|----------------------|---------------------------------------------------------------------|
| CREATED / IN_CART    | none (drive `confirmOrder`)                                          |
| UNPAID               | none (drive `payBalance` if auto-pay enabled)                        |
| UNSHIPPED (PENDING/PROCESSING) | none — hop marker only (local already PAID)                |
| SHIPPED              | `shipOrder`: PAID→SHIPPED, `ship_sn`=CJ trackNumber, `ship_channel`=trackingProvider/logisticName |
| DELIVERED            | ship first if needed, then auto-confirm: SHIPPED→AUTO_DELIVERED(402) |
| CANCELLED            | WARN + hop marker; money moves only through the existing refund/aftersale paths |

`CjLifecycleService.advance` is deliberately NOT `@Transactional`: the local writes are
the same guarded single-row transitions the module already uses (safe statement-by-
statement), and `autoConfirmOrder` opens REQUIRES_NEW — inside an outer TX holding a
lock on the same order row that would self-deadlock.

## Decision 3 — deleteOrder window

`deleteOrder` is wired (`CjFulfillmentService.cancelAtCjIfDeletable`) into:
- customer cancel + unpaid-timeout auto-cancel (defensive: under pay-first a CREATED
  local order normally has no CJ order yet — "cancel before pay deletes the CJ order"
  is trivially satisfied by never having placed one);
- refund approval (`approveRefund` / `approveAftersale`) — the REAL window: a placed
  CJ order still CREATED/IN_CART/UNPAID (payBalance not yet through) must never ship
  after the money went back.

CJ only allows deletion in CREATED/IN_CART; we attempt UNPAID too per the Wave-3
contract and treat CJ's refusal as a logged no-op ("check the CJ dashboard"). A
successful delete sets `cj_order_status='CANCELLED'` (drops the order from the sync
sweep) and records a timeline marker.

Live finding (2026-07-10, order 65): CJ indeed refuses the delete once the order is
UNPAID ("Order delete fail") — the refusal path logged cleanly and the refund itself
was unaffected. Because the post-commit hook confirms a draft within seconds of
payment, the successfully-deletable window (CREATED/IN_CART) is short in practice;
an undeleted-but-unpaid CJ order can never ship (payment never happens for a refunded
local order — it also leaves the sync sweep, whose query excludes terminal local
statuses), so the residue is a dashboard-cleanup item, not a money risk.

## Decision 4 — stock check at submit is ADVISORY (Task C)

`CjOrderAvailabilityChecker` now adds a live `product/stock/queryByVid` check (per-vid
aggregated quantities) after the hard cj_vid gate:

- requested > CJ-reported stock → **422**, naming every offending line with
  requested/available figures;
- CJ unreachable / breaker open / **empty warehouse list** → log + proceed. Empty is
  treated as unknown, not zero: factory-fulfilled listings can carry no warehouse rows,
  and a false 422 is worse than a pass for an advisory gate — pay-time `createOrderV2`
  remains the arbiter.

Warehouse selection: an order ships from ONE warehouse, so the figure compared is the
single-warehouse `totalInventoryNum` of the configured `from-country-code` warehouse
(what placement uses), falling back to the best-stocked warehouse anywhere. Answers are
cached 60s per vid (Caffeine, negatives included) to respect CJ's ~1 QPS budget across
multi-line carts and submit retries. Separate Feign client (`cj-dropship-stock`) so
stock-check failures can never open the `cj-dropship-order` breaker.

## Rate-limit budget

CJ enforces ~1 QPS account-wide. Placement already paces 1.1s after freightCalculate;
`advance` paces 1.1s before its (at most one) mutation; the sync sweep paces 1.1s
between orders and caps the batch (default 20 → worst case well under its 5-min
cadence); stock answers cache 60s; tracking answers cache 1h.

## Config added (`config/application.yml`)

- `spring.cloud.openfeign.okhttp.enabled: true` (PATCH support; applies to all Feign clients)
- `spring.cjdropship.api.sandbox` (dev: true), `spring.cjdropship.api.auto-pay-balance` (true)
- `litemall.order.cj-sync-sweep-ms` (300000), `litemall.order.cj-sync-batch` (20)
- Feign timeouts + resilience4j breaker/timelimiter for `cj-dropship-stock`, `cj-dropship-tracking`

## Migration

V33 `order_cj_lifecycle`: `litemall_order.cj_order_status VARCHAR(32) NULL` (checked
against `flyway_schema_history` — V31 was the applied max, then a concurrent goods-management session landed V32 cj_sourcing_request). litemall-db `LitemallOrder`
domain + `LitemallOrderMapper.xml` + `OrderMapper.selectSyncableCjOrderIds` hand-edited
(litemall-db is hand-maintained; never regenerate).
