# ADR: Goods-data acquisition strategy for order placement

- **Status:** Accepted
- **Date:** 2026-05-31
- **Scope:** `litemall-order` — the checkout / order-placement path
  (`LitemallOrderServiceImpl.placeOrder` driven by
  `LitemallOrderOrchestratorService.handleOrderCreation`).

## Context

Placing an order needs goods data for three checkout-critical operations:

1. **Current price** of each cart line (to compute the order total).
2. **In-stock validation** — confirm the requested quantity is available.
3. **Stock decrement** — reserve/reduce stock so two buyers cannot oversell the
   same unit.

The owning service for this data is `litemall-goods-management`. Order can reach
it two ways:

- **Synchronous request/response (Feign):** ask goods-management *now* and get an
  authoritative answer, including a transactional stock decrement.
- **Event-driven / broker read-model:** maintain a local denormalized copy of the
  catalog, updated asynchronously from goods events, and read from that copy.

## Decision

**Use the synchronous Feign path for the order-placement path** (price, stock
validation, and stock decrement).

The three operations above all require an *immediate, consistent* answer and the
stock decrement must be a *reserve-at-checkout* against the authoritative
inventory. A request/response call gives exactly that: order asks, goods-management
answers (and decrements) within the placement transaction, and a failure is
observed synchronously so the order can be rejected before any stock is taken.

An event-driven broker read-model is eventually consistent by construction: the
local copy lags the source, so validating stock against it (and worse,
"decrementing" a projection) cannot prevent oversell under concurrency. That model
fits a **denormalized goods snapshot for browsing/listing**, not the
reserve-stock step of checkout.

## Consequences

- Order calls goods-management through the **ACL facade**
  `LitemallGoodsFacade` only. Domain and application code never import the Feign
  client directly; the facade is the single seam that adapts the remote contract
  to the order aggregates.
- The Feign client carries **connect/read timeouts and a circuit breaker with a
  fallback** (`GoodsServiceFeignClientFallbackFactory` — reads fail fast with
  `LitemallGoodsServiceUnavailableException`, `reduceStock` degrades to an
  unconfirmed reservation) so a goods-management outage fails the placement
  *cleanly* — a domain exception, no order persisted — rather than hanging or
  creating an order against unvalidated stock.
- **Actual reserve guarantee (known limitation).** goods-management exposes only a
  single per-product `POST /srv/goods/stock/reduce` — no batch/atomic reduce and no
  inverse restore endpoint — so the facade issues one reduce call per product and
  `restoreStock` compensation is a **logged no-op**. If a multi-line reserve fails
  midway, products already decremented remotely stay decremented; the placement
  still aborts (no order row) and the orphaned decrements are logged at ERROR with
  the exact `productId -> qty` ledger for reconciliation. Closing this for real
  (atomic batch reduce + restore endpoint, and fixing the currently no-op
  `reduceStock` service method) is the goods-management follow-up — see
  `handoff-goods-management-defects.md` Defects 1 & 3.
- No broker-based goods *read* path is built for placement. A cached catalog
  projection (broker-fed) may be added **later** for browsing/listing only; it
  must not back the reserve-stock step. There is no half-built broker goods-read
  scaffolding to remove — Feign is and remains the only goods-read path here.

## Alternatives considered

- **Event-driven read-model for placement** — rejected: eventual consistency
  cannot guarantee correct stock reservation under concurrent checkout.
