# Verification notes — fix/order (Tasks A & B)

## Task A — goods acquisition through the ACL facade

- Decision recorded in `docs/adr-goods-acquisition.md`: **synchronous Feign** for
  the placement path (price / stock / reserve). No broker read path built; none
  existed to prune.
- `LitemallGoodsFacade` is now implemented (`LitemallGoodsFacadeImpl`) and is the
  **only** class that injects `GoodsServiceFeignClient`. Placement goes through it:
  - `LitemallOrderDomainService.validateProductStock(cartList, LitemallGoodsFacade)`
  - `LitemallOrderServiceImpl` batch goods/products fetch + `reduceStock`
  - `LitemallCouponServiceLayer` goods fetch
- **Domain is Feign-free:** `grep -rn feignclients litemall-order/.../order/domain` → none.
- **Clean failure:** the facade converts any error envelope / transport failure into
  `LitemallGoodsServiceUnavailableException`; `loadAggregatesValidationContext` no
  longer swallows it. Placement is `@Transactional`, so a goods-service outage rolls
  back → no order persisted, no stock decremented.
- **Resilience:** `spring-cloud-starter-circuitbreaker-resilience4j` added;
  `GoodsServiceFeignClient` has a `fallbackFactory`
  (`GoodsServiceFeignClientFallbackFactory`). Timeouts + breaker tuned in
  `config/application.yml` (`feign.client.config.goods-service`,
  `resilience4j.circuitbreaker/timelimiter.instances.goods-service`). No hardcoded
  hosts — all from `${goods.service.url}` / `litemall.search`-style config.
- Removed `litemall-order`'s unused `litemall-wx-api` dependency (no
  `org.linlinjava.litemall.wx` import exists in order); it only dragged wx-api's
  pre-existing compile debt into `-am` builds.

## Task B — wallet vertical absorbed into litemall-order

- All `org.linlinjava.litemall.wallet.*` relocated to `org.linlinjava.litemall.order.*`
  mirroring groupon: aggregates, `valueobjects/wallet` (+ enums), `commands/wallet`,
  repositories (+ `infrastructure/repositories/impl`), `domain/service/wallet`,
  `domain/events/wallet`, `application/util/exception/wallet`, `interfaces/dtos/wallet`,
  rest controller, application services.
- **Duplicates reconciled (not copied)** — exactly one of each under litemall-order:
  `ApiResponse`, `UserContext*`, `UserServiceFeignClient`, `LitemallUserAggregate`,
  `LitemallUserId`, `LitemallDomainEventPublisher`/`LitemallSpringDomainEventPublisher`,
  `FeignConfig`, `LitemallMoney` (order's enriched with `add`/`subtract`/
  `isGreaterThanOrEqual`). Wallet's `LitemallHttpResponseUtil` (different DTO, not a
  duplicate) renamed to `WalletHttpResponseUtil` to avoid a name clash with order's.
- **WALLET payment wired:** `LitemallOrderPaymentCommand` carries the domain
  `PaymentMethod`; `LitemallOrderOrchestratorService.handlePaymentAction` debits via
  `walletService.debit(LitemallWalletDebitCommand)` →
  `LitemallWalletDomainService.validateSufficientBalance` → repository, emitting
  `LitemallWalletDebitedEvent`. Insufficient balance raises
  `LitemallInsufficientBalanceException` and rolls back → no paid order.
- `litemall-wallet-service/` deleted and removed from the root `pom.xml` reactor.
  No module imported `litemall.wallet.` (grep clean), so no external breakage.

## Build / test evidence

- **`mvn -q -o -pl litemall-order -am compile` → clean** (the stated acceptance).
- Two focused unit tests added and **compile** clean:
  - `LitemallGoodsFacadeImplTest` — success unwrap + fallback/transport error →
    `LitemallGoodsServiceUnavailableException`.
  - `LitemallWalletDebitPathTest` — debit persists bill + emits
    `LitemallWalletDebitedEvent`; insufficient balance throws and does not debit.
- **Test execution caveat:** the module sets `maven.test.skip=true` by default and
  its surefire profile forks the JVM with a suspending JDWP agent
  (`-Xrunjdwp:...suspend=y`); the pre-existing integration tests also need
  TestContainers (`PostgreSQLContainer`), unavailable offline. The two new unit
  tests are self-contained (Mockito only) and were confirmed to **test-compile**;
  running them requires clearing the debug `argLine` and an online TestContainers
  resolve. Runtime price/stock + wallet-debit proof needs the running stack
  (goods-service up, broker up) and is out of scope for an offline build.

## Post-audit corrections (2026-06-05)

A deep audit of this diff found three behavioural blockers that compiled but were
wrong at runtime — the earlier draft of this note overclaimed them as done. All
three are now fixed:

- **B1 — order was never marked PAID.** `handlePaymentAction` had
  `//updateOrderStatusToPaid(orderId);` commented out and the real mark-paid method
  was a private, never-called dead method, so a WALLET payment debited the wallet
  yet left the order unpaid (and cancelled its sweeper task → orphaned). Replaced
  with `LitemallOrderServiceImpl.markOrderPaid(orderId)`: validates the CREATED→PAID
  transition on the aggregate, persists the status, and publishes
  `LitemallOrderPaidEvent` — inside the orchestrator's transaction, so the debit and
  the paid status are atomic.
- **B2 — `cancelOrder` never persisted, restored stock, or published events.** It
  mutated the aggregate in memory and discarded `getDomainEvents()`, so the
  DB-backed unpaid sweep was a no-op (orders never actually cancelled, reserved
  stock leaked). `cancelOrder` / new `autoCancelOrder` (the sweep now calls the
  latter → `SYSTEM_CANCELED`) now persist the status via `updateSelective`, release
  reserved stock (best-effort, see below), and drain/publish the aggregate's events.
- **B3 — remote stock decrement had no compensation.** The Feign `reduceStock` runs
  inside order's local DB tx but can't roll back with it, so a failure after a
  successful (or partially-successful batch) reduce orphaned stock. Fixes: the
  reserve is now the **last mutation** in `placeOrder`; `reduceStockForAllItems`
  fails on any *missing* product key (not only explicit `false`); and on rollback a
  `TransactionSynchronization` issues a compensating `restoreStock`.

**Best-effort restore + cross-service follow-up.** B2/B3 both call the new
`LitemallGoodsFacade.restoreStock` (Feign `POST /stock/batch-restore`, inverse of
`batch-reduce`). It is deliberately **best-effort**: invoked from rollback/cancel
paths, it never throws — on any failure (including the goods-management endpoint not
yet existing) it logs and returns empty. **Required follow-up for the
`goods-management` worktree: implement `POST /stock/batch-restore`.** Until it ships,
cancellation/rollback persist correctly but stock is not physically returned.

### Test evidence (post-audit)

- `mvn -q -o -pl litemall-order -am compile` → **clean**.
- `LitemallOrderPaidCancelTest` (new) — markOrderPaid persists PAID + publishes
  `LitemallOrderPaidEvent`; cancel/autoCancel persist CANCELED/SYSTEM_CANCELED,
  invoke `restoreStock`, and publish `LitemallOrderCancelledEvent`.
- `LitemallGoodsFacadeImplTest` (extended) — `restoreStock` unwraps success and
  swallows error-envelope/transport failures returning empty (never throws).
- Both test classes were **executed green offline** (10/10) by compiling with the
  project JDK 21 and running them through a `junit-platform-launcher` harness,
  bypassing the module's suspending-JDWP surefire `argLine` and the unrelated
  TestContainers/`PostgreSQLContainer` integration-test gap (which still blocks the
  full `test-compile` of the module's pre-existing integration tests offline).

## Medium findings — fixed (2026-06-05)

The five medium-severity audit findings are now resolved:

- **M1 — money precision.** `LitemallMoney` normalises every amount to
  `setScale(2, HALF_UP)` in its constructor (so `add`/`subtract` stay at 2dp), and
  `hashCode` now keys on the fixed-scale amount, consistent with the `compareTo`
  based `equals`.
- **M2 — wallet IDOR.** Every `LitemallWalletRestController` endpoint now takes the
  acting user from the gateway-trusted `X-User-Id` header (route `{userId}` path var
  dropped), exactly like `LitemallOrderRestController` — a caller can only touch
  their own wallet. `credit`/`debit` are flagged as privileged operations the
  gateway must restrict to internal/admin callers (gateway-admin follow-up). The
  route change (`/srv/wallet/{userId}/...` → `/srv/wallet/...`) is a gateway-admin
  follow-up.
- **M3 — bill `balanceAfter`.** `LitemallWalletServiceImpl` credit/debit now record
  the authoritative post-update balance (`walletRepository.getBalance` re-read after
  the atomic UPDATE) on the bill and on the returned aggregate, not the stale
  in-memory value. The overdraw-race `IllegalStateException` from `debitBalance` is
  mapped to `LitemallInsufficientBalanceException`.
- **M4 — duplicate publisher bean.** `LitemallSpringDomainEventPublisher` is no
  longer `@Component`; it is defined once via the `@Bean` in
  `LitemallDomainEventConfig`.
- **M5 — multi-instance sweep.** `findDue` → `claimDueBatch`, which uses
  `SELECT ... FOR UPDATE SKIP LOCKED` (MySQL/InnoDB). The `@Scheduled sweep` is now
  `@Transactional` so the claim-locks are held for the batch; a concurrent instance
  skips locked rows (no double-cancel). `autoCancelOrder` runs `REQUIRES_NEW`
  (order tables only, never the task table) so a per-row failure rolls back just
  that order and leaves the task row for retry without poisoning the claim tx. No
  schema change.

### Test evidence (mediums)

- `mvn -q -o -pl litemall-order -am compile` → **clean**.
- New `LitemallMoneyTest` (scale/rounding, overdraw, equals/hashCode); updated
  `LitemallWalletDebitPathTest` (authoritative `getBalance` re-read) and
  `UnpaidOrderTaskSchedulerTest` (`claimDueBatch` + `autoCancelOrder`).
- **23/23 tests green offline** across all five touched/new test classes via the
  JDK-21 + `junit-platform-launcher` harness.

### Known remaining findings (Low — NOT fixed here)

- No transactional outbox (AFTER_COMMIT events lost on crash); events not keyed by
  order id; `grouponEventHandler` lowercase class name.

## Follow-ups (not done here — other worktrees)

- Gateway route currently pointing at `litemall-wallet` (8086) should become an
  order route → `gateway-admin` worktree.
- `goods-management`: add `POST /stock/batch-restore` (compensating stock release)
  so B2/B3 best-effort restore actually returns stock.
- Stripe `/{orderId}/actions/pay` REST endpoint activation (still deferred).
- `agregates` → `aggregates` spelling fix (explicitly out of scope).
