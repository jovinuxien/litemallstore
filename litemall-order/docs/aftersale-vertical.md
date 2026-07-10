# Aftersale/RMA vertical — endpoints, state machine, refund semantics

- **Status:** implemented on `fix/order` (Wave 2, 2026-07-10)
- **Storage:** legacy `litemall_aftersale` (V1 baseline) via litemall-db's
  `LitemallAftersaleMapper` — **no new migration**. `litemall_order.aftersale_status`
  tracks the order-side flag.
- **Shape:** mirrors the CJ dispute vertical (`cj-dispute-vertical.md`) but with a
  locally-owned state machine (CJ disputes project remote state; aftersale is ours).

## Lifecycle (single transition source: `LitemallAftersaleAggregate`)

```
            ┌────────── cancel (customer) ──────────→ 5 CANCELLED
1 APPLIED ──┼────────── reject (admin) ─────────────→ 4 REJECTED
            └── approve (admin) → 2 APPROVED ── refund lands → 3 REFUNDED
```

Apply-time guards: caller must own the order (a non-owner reads "not found" — no
existence leak); order status must be PAID / SHIPPED / DELIVERED / AUTO_DELIVERED;
one open application per order; requested `amount` ≤ `actual_price` (null = full).
`aftersale_sn = {orderSn}-A{n}` (per-order sequence, mirrors the dispute
`businessDisputeId`).

Order state machine extension: `DELIVERED(401)` and `AUTO_DELIVERED(402)` may now
transition to `REFUND_REQUEST(202)` (received goods are the core RMA case); the
guarded conditional UPDATE (`markRefundRequestedIfPayable`) matches. The plain
customer refund action (`/actions/refund`) is unchanged (still PAID|SHIPPED via its
action gate) — received orders go through aftersale.

## Refund on approval (one transaction, tender parity)

Approve (orchestrator `approveAftersale`): aftersale APPLIED→APPROVED; the order is
driven through the existing transitions (…→ REFUND_REQUEST → REFUNDED via the same
guarded updates the plain refund uses); money settles through
`settleRefundToTender` with the customer-requested amount as an **extra cap**:

- WALLET → wallet credit = `min(requested, actual_price, captured wallet debit)`,
  idempotent on the ledger (`hasOrderRefundCredit` business key).
- CARD/digital → PSP-reversal seam (log + `LitemallOrderRefundedEvent`), no wallet credit.
- `refund_amount` records what actually settled; `after_sale_status` = 3.

Reject: aftersale → REJECTED (optional reason lands in `comment`),
`after_sale_status` = 4, order status and money untouched.

Timeline: every hop lands on the order's `litemall_order_status` history —
real status hops (refund_request / refund) as before, aftersale markers as
same-status entries (`aftersale_request|aftersale_cancel|aftersale_approve|aftersale_reject`)
— all visible via `GET /srv/order/{orderId}/timeline`.

## Customer endpoints (`/srv/order/{orderId}/aftersale`, identity = `X-User-Id` only)

| Method/path | Body | Returns (`{errno, data}` envelope, errno 730 on rule violations) |
|---|---|---|
| `POST /srv/order/{orderId}/aftersale` | `{type: 0\|1\|2, reason, amount?, pictures?: string[], comment?}` | the created application (see DTO below) |
| `GET /srv/order/{orderId}/aftersale` | — | applications, newest first |
| `GET /srv/order/{orderId}/aftersale/{aftersaleId}` | — | one application |
| `POST /srv/order/{orderId}/aftersale/{aftersaleId}/cancel` | — | `data: null` |

`type` follows upstream litemall: 0 = not-received refund, 1 = received / refund-only,
2 = return-and-refund.

DTO: `{id, aftersaleSn, orderId, userId, type, reason, amount, pictures[], comment,
status, statusText, handleTime, addTime}` — prices as numbers, dates
`yyyy-MM-dd HH:mm:ss`, `status` codes 1–5 as in the lifecycle above.

## Admin endpoints (`/srv/private/admin/aftersale`, gateway gates ROLE_ADMIN)

| Method/path | Params/body | Returns |
|---|---|---|
| `GET /list` | `status?, orderId?, userId?, page=1, limit=10` | `{errno:0, data:{list, total, page, limit, pages}}` |
| `POST /{aftersaleId}/approve` | — | `OrderOperationDtoResponse` (success ⇒ refunded) |
| `POST /{aftersaleId}/reject` | `{reason?}` | `OrderOperationDtoResponse` |

## Gateway handoff

- **gateway-api:** customer paths live under the existing `/srv/order/**` route —
  no route change needed; just wire the SPA (order-detail "apply for aftersale"
  form + application list/cancel).
- **gateway-admin:** verify the order route covers `/srv/private/admin/aftersale/**`
  (same service, `lb://` order); build the Aftersale queue page (list + approve/reject)
  on the endpoints above — the UserList/AddressList page pattern fits the list shape.
  The service-side svcsecurity gate needs BOTH `X-User-Id` and
  `X-User-Roles: ROLE_ADMIN` forwarded on this prefix (verified live: machine token
  alone → 403), same as promotion's admin surface.

## Implementation gotchas (verified live 2026-07-10)

- litemall-db's hand-maintained `Deleted` enums build their values with
  `Boolean.valueOf("1")`/`("0")` — **both are `false`** — so
  `Example.andLogicalDeleted(false)` renders `deleted <> false` and matches ONLY
  deleted rows. The aftersale repository binds `andDeletedEqualTo(false)` instead.
  Every other `andLogicalDeleted` caller in the codebase shares this landmine.
- litemall-db already ships a bean named `litemallAftersaleService`
  (`db.service.LitemallAftersaleService`); order's application service is
  therefore `LitemallAftersaleServiceLayer` (a duplicate default bean name fails
  boot with `ConflictingBeanDefinitionException`).
