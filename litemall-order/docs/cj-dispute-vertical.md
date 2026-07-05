# CJ dispute vertical — customer "report a problem" for dropship orders

**Status:** implemented 2026-07-05 (V28), decoupled-refund v1. SPA panel lives on the
order detail page (gateway-api). CJ dispute API reference:
`developers.cjdropshipping.com/en/api/api2/api/dispute.html`.

## Design rationale (Millett & Tune, *PPPoDDD*)

- **Anticorruption layer (ch. 7, 11).** `CjDisputeFacade` is the only place CJ's dispute
  jargon exists (`expectType`/`finallyDeal`/`refundType` integers, `lineItemId`,
  boolean-`data` envelopes). The application layer speaks `CjDisputeExpectation
  REFUND|REISSUE`, `CjDisputeResolution REFUND|REISSUE|REJECTED`, `CjDisputableLine`.
- **Supporting subdomain — don't over-model (ch. 2–3).** CJ owns the dispute lifecycle.
  Locally we keep a *projection* (`litemall_cj_dispute`, V28) plus the guards WE own
  (ownership, order must be a PAID `source='cj'` order, one open dispute at a time).
  No speculative local state machine mirroring CJ's internals.
- **Small aggregate, reference by id (ch. 19).** `LitemallCjDisputeAggregate` references
  the order by `LitemallOrderId`; its transaction boundary is one dispute row. It is NOT
  nested in the order aggregate — their lifecycles are independent.
- **Domain events (ch. 18).** `LitemallCjDisputeOpenedEvent` / `CancelledEvent` go
  through the existing V25 transactional outbox.
- **Eventual consistency across contexts (ch. 11–13).** CJ's `create` returns only
  `true`; CJ's dispute id appears later in `getDisputeList`. Our `businessDisputeId`
  (`{order_sn}-D{n}`, deterministic) is the retry-safe merchant key; reads lazily
  reconcile the projection (back-fill `cj_dispute_id`, status, resolution) and degrade
  to the local copy when CJ is down.
- **Thin application service (ch. 25).** `CjDisputeService` orchestrates
  guards → facade → repository → events; invariants live on the aggregates.

## Wire-up

- **Feign:** `CjDisputeFeignClient` (`disputeProducts`, `disputeConfirmInfo`, `create`,
  `cancel`, `getDisputeList`) — same base-url/token/fallback-factory pattern as the
  order client, ~1.1 s pacing after each CJ call (≈1 QPS account limit).
- **REST (X-User-Id scoped):**
  - `GET  /srv/order/{orderId}/disputes/context` — disputable lines + reasons + limits
  - `POST /srv/order/{orderId}/disputes` — open (server re-reads prices/limits from CJ;
    the client only picks lineItemIds + quantities)
  - `GET  /srv/order/{orderId}/disputes` — list, lazily refreshed from CJ
  - `POST /srv/order/{orderId}/disputes/{id}/cancel` — withdraw (CJ cancel first)
- **Config:** `spring.cjdropship.api.dispute-refund-type` (default 1 = CJ balance).
- **SPA:** `DisputePanel` on the order detail page for `source='cj'` orders (items,
  reason, refund/reissue, message; shows status/resolution; withdraw button). All
  dispute calls use 30 s timeouts (CJ-proxied + paced).

## Deliberate boundaries / follow-ups

- **Decoupled refund (approved v1):** a CJ resolution of `REFUND` shows on the dispute
  but does NOT move customer money; the wallet refund still runs through the existing
  request→approve tender-correct flow. Automation would subscribe to the resolution
  during refresh — future work, needs idempotency care.
- Evidence uploads are URL-based (CJ contract); the SPA form doesn't host image upload
  yet — customers can paste URLs via API; UI upload is a follow-up.
- `disputes/getDisputeDetail` (per-refund-line breakdown) not consumed in v1.
