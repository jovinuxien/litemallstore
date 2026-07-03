# Handoff → gateway-api / gateway-admin: order lifecycle timeline & new actions

Backend work landed on `fix/order` to make the order an **observable, fully-traversable
state machine**. Every state transition is now persisted and exposed; the missing
ship/confirm/refund transitions are implemented; transition rules have one source of
truth. This document is the contract the SPAs consume + the gateway routing follow-ups.

## 1. The lifecycle state machine (single source of truth)

Canonical transition graph lives on `LitemallOrderStatus.canTransitionTo(...)`.
`LitemallOrderStatusQuery.isValidTransition` and `LitemallOrderHandleOption.forStatus`
are aligned to it (no more drift).

| Status | Code | Label (`orderStatusText`) | Can move to |
|--------|------|---------------------------|-------------|
| CREATED | 101 | Unpaid | PAID, CANCELED, SYSTEM_CANCELED |
| PAID | 201 | To be shipped | SHIPPED, REFUND_REQUEST |
| SHIPPED | 301 | Shipped | DELIVERED, AUTO_DELIVERED, REFUND_REQUEST |
| DELIVERED | 401 | Completed | — (terminal) |
| AUTO_DELIVERED | 402 | Completed | — (terminal) |
| CANCELED | 102 | Cancelled | — (terminal) |
| SYSTEM_CANCELED | 103 | Cancelled | — (terminal) |
| REFUND_REQUEST | 202 | Refund in progress | REFUNDED |
| REFUNDED | 203 | Refunded | — (terminal) |

> **Policy change:** a *paid* order is never hard-CANCELED — post-payment "cancel" goes
> through the refund flow (PAID → REFUND_REQUEST → REFUNDED). The SPA must show **Refund**,
> not **Cancel**, once an order is paid (the `handleOption` flags below already encode this).

`handleOption` flags per status (drives which buttons the SPA shows):

| Status | cancel | pay | refund | confirm | delete | comment | rebuy | aftersale |
|--------|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|
| CREATED | ✓ | ✓ | | | | | | |
| PAID | | | ✓ | | | | | |
| SHIPPED | | | ✓ | ✓ | | | | |
| DELIVERED / AUTO_DELIVERED | | | | | ✓ | ✓ | ✓ | ✓ |
| CANCELED / SYSTEM_CANCELED / REFUNDED | | | | | ✓ | | | |

## 2. Status history / timeline

Every transition is written to `litemall_order_status` (V11 table, now actually
populated; V24 adds `old_status`/`new_status` codes) **in the same transaction** as the
status change — gap-free, never lost.

### New endpoint — `GET /srv/order/{orderId}/timeline`
Auth: gateway-injected `X-User-Id` (owner-scoped; non-owned/absent → 404 envelope).
Already covered by the existing gateway-api `customer-order` route (`/srv/order/**`).

Response (`ApiResponse` envelope, `data` = array, oldest first):
```json
{ "errno": 0, "data": [
  { "fromStatus": null, "fromStatusText": null, "toStatus": 101, "toStatusText": "UNPAID",
    "changeType": "create", "changeMessage": "Order placed", "operator": "user",
    "changeTime": "2026-06-28T10:02:11" },
  { "fromStatus": 101, "fromStatusText": "UNPAID", "toStatus": 201, "toStatusText": "PAID",
    "changeType": "pay", "changeMessage": "Payment received", "operator": "user",
    "changeTime": "2026-06-28T10:05:43" },
  { "fromStatus": 201, "fromStatusText": "PAID", "toStatus": 301, "toStatusText": "SHIPPED",
    "changeType": "ship", "changeMessage": "Shipped via SF (SF123...)", "operator": "admin",
    "changeTime": "2026-06-28T11:00:00" }
]}
```
**SPA task:** render this as a progress trail on the order-detail page (`changeType` →
icon, `changeMessage` → caption, `changeTime` → timestamp). `toStatusText` is the enum
display name; use `OrderStatusText` wording from `orderStatusText` for the headline state.

`changeType` vocabulary: `create, pay, ship, receive, auto_receive, cancel, system_cancel,
refund_request, refund`.

## 3. New / now-real customer actions

These paths the SPA already calls (they previously 404'd). All `POST`, `X-User-Id` auth,
return the standard `OrderOperationDtoResponse` (same envelope as cancel/pay):

| Action | Endpoint | Transition |
|--------|----------|-----------|
| Confirm receipt | `POST /srv/order/{id}/actions/confirm` | SHIPPED → DELIVERED |
| Request refund/return | `POST /srv/order/{id}/actions/refund` (body: reason string, optional) | PAID\|SHIPPED → REFUND_REQUEST |
| Delete order | `POST /srv/order/{id}/actions/delete` | terminal → soft-deleted |

HTTP status mapping is unchanged (`buildResponse`): success 200, invalid-state 4xx,
`actions/pay` insufficient-balance 402.

## 4. New admin actions (gateway-admin follow-up)

| Action | Endpoint | Transition |
|--------|----------|-----------|
| Ship | `POST /srv/private/admin/order/{id}/ship` (body: `{ shipChannel, shipSn }`) | PAID → SHIPPED |
| Approve refund | `POST /srv/private/admin/order/{id}/refund` | REFUND_REQUEST → REFUNDED |

**Refund money path (refund-to-tender, capped at capture — see
`plan-refund-tender-parity.md`):** approving a refund returns the money **to the tender
that paid**, never more than was captured, atomic with the status flip:

- **WALLET-paid** — credits the buyer's wallet with exactly the wallet debit recorded at
  pay time (the `litemall_user_bill` ledger is the captured amount), capped at
  `actualPrice`. The credit is idempotent on the ORDER/REFUND/orderId ledger key.
- **CARD/digital-paid** — **no wallet credit**; the charge lives at the PSP, so the
  reversal is a documented seam (log + `LitemallOrderRefundedEvent`), mirroring the
  client-confirmed-charge boundary in `handoff-gateway-api-order-payment.md`.
- **No tender / no capture** (legacy orders) — nothing to return; the status still flips
  with `refund_amount = 0`.

The tender is recorded in `litemall_order.pay_id` when the order is marked paid
(`"WALLET"` or `"<METHOD>:<pspReference>"`); orders paid before that fall back to the
wallet ledger. `refund_amount` always records what was actually returned.

## 5. Auto-confirm

`AutoConfirmOrderScheduler` sweeps SHIPPED orders past the grace window
(`litemall.order.auto-confirm-days`, default **15**) → AUTO_DELIVERED. Tunables:
`litemall.order.auto-confirm-days`, `litemall.order.auto-confirm-sweep-ms` (default 1h).

## 6. Reliability (transactional outbox)

Order domain events are now recorded in `litemall_event_outbox` **inside the order
transaction** (`OrderEventOutboxWriter`) and forwarded to Kafka by a scheduled relay
(`OrderEventOutboxRelay`) — replacing the old AFTER_COMMIT send that lost events on a
crash. At-least-once delivery; **consumers must be idempotent**. Tunables under
`litemall.order.outbox.*` (`relay-ms` default 10s, `batch` 100, `max-attempts` 10).

## 7. Gateway routing follow-ups (NOT done here — scope)

- **gateway-api:** `/srv/order/{id}/timeline` and the `actions/confirm|refund|delete`
  verbs are already covered by the existing `/srv/order/**` predicate — no change needed.
  Confirm the route is present.
- **gateway-admin:** add `/srv/private/admin/order/**` → order service so the new admin
  ship/approve-refund endpoints are reachable.

## 8. Manual verification runbook

With the order service up (run from MAIN after merge — shared `~/.m2`/litemall-db) and a
placed order:
1. `POST /srv/order/submit` → 201; `GET /srv/order/{id}/timeline` shows one `create` row.
2. `POST /srv/order/{id}/actions/pay` (WALLET) → 200; timeline gains `pay` (101→201);
   `litemall_order.pay_time` set.
3. `POST /srv/private/admin/order/{id}/ship {shipChannel,shipSn}` → 200; timeline gains
   `ship` (201→301); `ship_sn/ship_time` set.
4. `POST /srv/order/{id}/actions/confirm` → 200; timeline gains `receive` (301→401);
   `confirm_time` set; `orderStatusText` = "Completed".
5. Refund path: from PAID/SHIPPED `POST /srv/order/{id}/actions/refund` → REFUND_REQUEST;
   `POST /srv/private/admin/order/{id}/refund` → REFUNDED. Wallet-paid: wallet credited
   back exactly the recorded debit (`refund_amount` = that amount; net wallet movement
   zero). Card-paid: wallet balance UNCHANGED; the reversal is logged for the PSP seam.
6. Concurrency: a duplicate `pay`/`ship`/`confirm` affects 0 rows → clean conflict, no
   double transition (guarded `UPDATE ... WHERE order_status = <expected>`).
