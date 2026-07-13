# ADR — Admin offline mark-paid (Wave 4, Task C)

Status: accepted · 2026-07-13 · owner: `litemall-order`

## Decision

`POST /srv/private/admin/order/{orderId}/pay` marks a CREATED order PAID for money
that moved out-of-band (bank transfer, counter cash, external PSP). It reuses the
customer pay machinery end-to-end rather than inventing a parallel path:

- **Tender convention:** `pay_id = "OFFLINE:" + (reference | "admin:"+timestamp)` —
  inside the existing `<METHOD>:<reference>` format, so **refund tender-parity
  resolves automatically**: OFFLINE is a non-wallet tender → refunds take the
  PSP-seam/manual path; no wallet credit is ever invented for money that never
  entered the wallet.
- **Guards:** the only legal source state is CREATED. Pre-checked in the controller
  OUTSIDE the orchestrator transaction (422, the in-TX-422→502 landmine); the
  `markPaidIfCreated` 0-row guard still wins any race (IllegalStateException →
  controller 422, rollback, no double pay).
- **Timeline:** the normal `pay` transition hop PLUS a same-status
  `admin_offline_pay` marker (aftersale-marker pattern) recording the reference and
  `admin:<X-User-Id>`.
- **Parity side-effects:** groupon settlement, payment-success notification +
  event, unpaid-timeout cancellation, and — for pickup orders — the verify-code
  generation all run exactly as for a customer pay (they live in/around
  `markOrderPaid`).

## The CJ decision (the reason this ADR exists)

**A CJ order marked paid offline LIVE-FIRES the in-TX createOrderV2 replay and the
after-commit confirm/payBalance lifecycle pass — identical to a customer pay.**
Rationale: an offline-paid CJ order is a paid CJ order; skipping the replay would
strand it unfulfilled with no other trigger (the status sweep only advances orders
already placed at CJ). Consequences:

- CJ placement failure rolls back the PAID flip — the admin sees the CJ error, the
  order stays CREATED/unpaid. Correct: don't take money (even offline money) for an
  order CJ refuses.
- **The admin SPA's confirm dialog MUST name the live-fire** ("this will place the
  order at CJ Dropshipping and may move real money") — `spring.cjdropship.api.sandbox`
  governs whether CJ simulates the balance payment (dev default: sandbox=true).

## Rejected alternative

A "quiet" mark-paid that skips CJ (flag on the request) — rejected: it creates a
paid-but-never-fulfilled state that looks identical to a sync lag and needs a manual
CJ replay tool we don't have. If a genuinely-CJ-free mark-paid is ever needed,
cancel + re-place the order as source=local instead.
