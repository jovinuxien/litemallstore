# ADR: CJ placement decoupled from the money transaction (Wave 8)

**Status:** accepted, 2026-07-21 · **Supersedes** the in-transaction placement contract in
`adr-cj-order-placement.md` / `adr-cj-lifecycle-parity.md` / `adr-offline-mark-paid.md`
(their lifecycle/idempotency content still holds; only the transaction boundary changed).

## Problem

Since Wave 3, `CjFulfillmentService.placeForPaidOrder` ran INSIDE the payment transaction:
a CJ rejection rolled back the wallet debit + PAID flip together. That coupling made a CJ
outage — or the CJ ACL being *disabled* (prod has no `CJ_API_KEY` yet) — a **payment**
failure: no CJ order could be paid at all, and there was no durable retry. Wave 8's
requirement is the opposite: payment settles, the order lands in an honest retryable
fulfilment state, and an order paid today is placed automatically the day the key appears.

## Decision

1. **Pay settles unconditionally.** All three pay sites (customer pay, Stripe webhook,
   admin offline pay) call `queueCjPlacement`: an in-TX timeline hop (`cj_placement`,
   "Queued for CJ fulfilment placement") + an `afterCommit` hand-off to
   `CjPlacementService.placeAsync`.
2. **The order row IS the durable job — no outbox table, no migration.**
   `source='cj' AND order_status=201 AND cj_order_id IS NULL AND deleted=0
   AND cj_order_status <> 'PLACEMENT_REJECTED'` (`OrderMapper.selectPlaceableCjOrderIds`)
   never expires. `CjPlacementSweepScheduler` (default 5 min, batch 10, 1.1 s pacing)
   retries it forever; `order_sn` is CJ's idempotency key.
3. **Disabled is a first-class state** (`CjTokenService.isEnabled()`): both `CJ_EMAIL` +
   `CJ_API_KEY` blank ⇒ typed `LitemallCjDisabledException` with zero network calls, both
   sweeps skip cheaply, orders are retained. Half-configured ⇒ boot refusal (Stripe-seam
   stance). Credentials are env-only now (`${CJ_EMAIL:}` / `${LITEMALL_CJ_API_KEY:${CJ_API_KEY:}}`).
4. **Failure classification** (`CjDropshipOrderFacadeImpl`): transport / auth / HTTP /
   rate-limit / accepted-but-unparseable ⇒ `LitemallCjRetryableException` (retained);
   a CJ business rejection (`result=false`) ⇒ terminal `LitemallCjOrderException` ⇒ the
   order is parked with the LOCAL sentinel `cj_order_status='PLACEMENT_REJECTED'`, a
   `cj_placement_failed` timeline hop, and a `CjOpsNotifier` mail (`litemall.order.cj-ops-mail`,
   Wave-6 mail outbox). Money is never moved automatically (user decision 2026-07-20).
   Requeue: `UPDATE litemall_order SET cj_order_status = NULL WHERE id = <id>;`
5. **Reconcile-by-orderNumber.** The sweep's first step asks CJ `getOrderDetail(order_sn)`
   (it accepts the merchant id) and ADOPTS an order CJ already holds — closing both the
   accepted-but-unparseable-response case (which no longer records a null `cj_order_id`)
   and the crash-after-CJ-accept window.
6. **Races.** In-JVM single-flight set (fast path vs sweep); post-placement re-check —
   if the order left PAID while the CJ call was in flight, the fresh CREATED draft is
   deleted at CJ (`cancelAtCjIfDeletable`). The sweep predicate (`201` only) keeps
   refund/cancel states out of the queue entirely.
7. **Honesty on cancel/refund of a placed order:** the not-deletable and delete-refused
   branches of `cancelAtCjIfDeletable` now write customer-visible `cj_sync` hops; CJ-side
   `CANCELLED` seen by the status sync additionally mails ops exactly once (on the
   transition).
8. **Customer surface:** `GET /srv/order/detail` now carries `shipSn`, `cjOrderStatus`,
   and a derived `fulfillmentStatus` phrase ("Processing" / "Preparing shipment" /
   "Shipped" / "Delivered" / "Attention required — contact support").

## Sentinel containment

`PLACEMENT_REJECTED` lives in the `cj_order_status` column but is only ever written while
`cj_order_id IS NULL`; every CJ consumer (lifecycle sync, tracking, delete) additionally
gates on `hasText(cj_order_id)`, and `selectSyncableCjOrderIds` requires
`cj_order_id IS NOT NULL` — the sentinel cannot leak into the CJ lifecycle. Admin readouts
show the raw string; that is deliberate.

## Test-infra fallout fixed en route

The module's tests had NEVER run: `debugForkedProcess=true` suspended every surefire JVM
on :5005 (removed), JHipster Postgres-testcontainers scaffolding didn't compile (removed:
`PostgreSqlTestContainer`, `TestContainerSpringContextCustomizerFactory` + its
`spring.factories`, `AbstractMyBatisTest`, `MyBatisTestContainer`, `LitemallOrderMapperTest`),
and `verifyNoInteractions` needed Mockito 3+ while the root pom pins mockito-core 2.28.2
(replaced with `verifyNoMoreInteractions`). Root-pom mockito/powermock cleanup is out of
this worktree's scope — flagged for platform.
