# Handoff — gateway-admin: parked CJ placements + Requeue (lifecycle package B)

Order side SHIPPED on `fix/order` 2026-09-05 (plan-order-lifecycle-e2e.md, F7/F8/F9).
Code to THIS; do not read the order branch.

## What changed on `GET /srv/private/admin/order/cj-placement/pending`

Rows now include orders that were **approved and then parked** — before, an order
approved by an admin and then rejected by CJ vanished from this list (the query
required `cj_placement_approved_time IS NULL`). Same paged envelope; two ADDITIVE
fields on every row:

| field | type | meaning |
|---|---|---|
| `parked` | boolean | `true` when `cj_order_status` is `PLACEMENT_REJECTED` (CJ terminally rejected the order) or `PLACEMENT_STALLED` (retryable failures for `stall-park-hours`, default 24 h). The admin action for a parked row is **Requeue**, not Approve. |
| `parkReason` | string, absent when not parked | CJ's own words from the latest `cj_placement_failed` timeline hop, e.g. `CJ rejected the fulfilment order: 7001: Please enter a IOSS number.` |

`holdReason` (existing) still carries the human sentence, now ending in
"… — fix the cause and use Requeue (approval alone does nothing)". `cjReady` is
`false` for parked rows.

## Approve refusals — two new typed kinds (422, `[KIND] message`)

- `[AFTERSALE_OPEN]` — a refund/aftersale request is open on the order. Settle it
  first. (The sweep independently refuses to place such an order even if approved.)
- `[PARKED]` — the order is parked; approval does nothing. Show the Requeue action.

## New: `POST /srv/private/admin/order/{orderId}/cj-placement/requeue`

Headers: the usual machine token + `X-User-Roles: ROLE_ADMIN` + `X-User-Id` (recorded
as the requeuing admin on the timeline). No body.

- 200 `{errno:0, data:{orderId, status:"REQUEUED", message}}` — sentinel cleared
  (CAS), timeline hop `Requeued for CJ placement by admin <id>`, the placement sweep
  retries on its next 5-min tick. The approval stamp is KEPT (an approved order stays
  approved).
- 422 `[NOT_PARKED] …` — not parked (already placed, already requeued, or not paid).
- 422 `[NOT_FOUND] …`.

Idempotent in effect: a second click answers `[NOT_PARKED]`.

## UI ask (small)

1. Pending tab: render parked rows distinctly (badge "Parked — CJ rejected" /
   "Parked — placement stalled"), show `parkReason` verbatim, replace the Approve
   button with **Requeue** (confirm dialog: "Retry sending this order to CJ? Fix the
   cause first — CJ said: <parkReason>"). Result message verbatim.
2. Order detail: same Requeue action when `cjOrderStatus` is one of the two sentinels
   and `cjOrderId` is empty; the existing `cjPlacementState` derivation gains a
   `parked` state (today a parked-after-approval order renders as
   `approved-awaiting-placement` forever).
3. Dashboard "Pending CJ approval" tile: parked rows now count — that is correct
   (they need a human), but consider a second number "of which parked".

## Also new on the order timeline (for the admin timeline view, if any)

- change type `cj_stall`: "Fulfilment placement has been failing since …" (ops
  warned after 1 h) and "CJ payBalance failed: <reason> — retrying automatically"
  (a placed order CJ will not pay from balance — typically an empty CJ balance;
  repeated at most every 24 h).
- change type `cj_placement_failed` with message starting "Fulfilment placement
  parked after N h …" — the stalled park.
- change type `cj_sync`: "CJ shipped this order while a refund request is open …".
