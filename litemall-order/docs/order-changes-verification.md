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

## Follow-ups (not done here — other worktrees)

- Gateway route currently pointing at `litemall-wallet` (8086) should become an
  order route → `gateway-admin` worktree.
- Stripe `/{orderId}/actions/pay` REST endpoint activation (still deferred).
- `agregates` → `aggregates` spelling fix (explicitly out of scope).
