# Plan — order worktree follow-ups after lifecycle packages A/B/C

Status: **AWAITING APPROVAL** (drafted 2026-09-06, every claim re-verified
against the tree the same day). Worktree `order`.

## 0. Where the assignment stands

The commissioned scope (`plan-order-lifecycle-e2e.md` §3, packages A → B → C,
raises D) is fully built, merged to master (`c241076f7`, `5cfe7b1d5`) and
deployed to trovemo.com on 2026-09-05. `fix/order` is clean, 0 commits ahead
of master and 8 behind (all gateway-api i18n). Nothing in the commissioned
scope remains for this worktree; the real acceptance (one small real EUR
order end to end) is user-side.

Two order-scoped items were raised but not built. Both were re-verified
against the tree today.

## 1. Prod SQL DEBUG firehose in the order container (raised 2026-09-05)

**Verified.** `litemall-order/src/main/resources/config/application.yml:374-377`
sets `org.linlinjava.litemall.db: DEBUG`; `config/application-prod.yml:26-34`
counters only `org.linlinjava.litemall: INFO` and `org.springframework.web:
INFO`. Spring resolves the MOST SPECIFIC logger key, so the `.db` DEBUG wins in
prod — the exact mechanism that OOM-killed goods-management on 2026-08-15 and
was countered only in THAT module's prod yml.

Difference from goods-management: order has no `logback-spring.xml`, no file
appender and no tmpfs — logs go to stdout under the json-file driver
(10m × 3). So there is **no OOM vector** here. The real cost is:
- every MyBatis statement is logged WITH its bound parameters — for the order
  service that is customer name, address, phone, email and Stripe intent ids,
  in plain text on the host, readable by anyone with `docker logs`. The prod
  yml's own comment already forbids exactly this for `spring.web`.
- the 30 MB rotation window is filled with SQL within minutes, so real ERRORs
  are evicted before anyone can read them (the 2026-08-27 lesson: "never
  measure a long prod run from `docker logs`").

Fix (mirror of goods-management, no behavior change in dev):
1. `application-prod.yml`: `org.linlinjava.litemall.db: INFO` at the same
   specificity, with the why.
2. `docker-compose.prod.yml` order block: `LOGGING_LEVEL_ORG_LINLINJAVA_
   LITEMALL_DB: ${LITEMALL_ORDER_DB_LOG_LEVEL:-INFO}` (env outranks yml, so
   MAIN can also apply this line with a container RECREATE, before any rebuild).
3. Test pin: a resource test asserting the prod yml holds `.db` above DEBUG
   (the `prodSilencesTheSqlDebugFirehose` pattern) so the line cannot be lost
   in a merge.

## 2. F3 — stock restored inside the cancel transaction (plan §1.1)

**Verified** in `application/internal/LitemallOrderServiceImpl.java`:
`cancelOrder` (:796, transactional via the `@Transactional` orchestrator that
is its only caller) and `autoCancelOrder` (:832, `REQUIRES_NEW`) call
`restoreStockForOrder` (:1145) — a Feign call that COMMITS remotely in
goods-management — and then run `cjFulfillmentService.cancelAtCjIfDeletable`,
`persistStatusHistory` + `publishAndClearEvents` inside the same local
transaction. If anything after
the restore rolls the transaction back, the order stays CREATED with its
reserved quantities ALSO back on sale: oversold by exactly the order. The
placement path guards the mirror case (`registerStockRestoreOnRollback`);
the cancel path does not. The coupon and pink releases in the SAME two methods
already run as after-commit hooks (`releaseCouponOnCancelCommit`,
`releasePinkOnCancelCommit`).

Fix: `restoreStockOnCancelCommit` — the restore runs in an `afterCommit`
synchronization (immediately when no transaction is active, the existing
pattern), so stock returns only once the cancellation is durable. A failed
restore stays best-effort and logged, exactly as today — no new failure mode.
Tests (new class beside `LitemallOrderCancelPinkReleaseTest`, same
`@InjectMocks` + `ReflectionTestUtils` wiring): (a) with no synchronization
active the restore calls `goodsFacade.restoreStock` inline with the summed
quantities, for both cancel paths — the existing pattern; (b) with
`TransactionSynchronizationManager.initSynchronization()` the facade is NOT
called until the registered hooks' `afterCommit` runs; (c) driving the hooks
with `afterCompletion(STATUS_ROLLED_BACK)` instead never calls the facade.
No test in the module drives synchronizations today — (b)/(c) are the first,
and they pin the semantics rather than the mock wiring.

## 3. Housekeeping

Fast-forward `fix/order` to master before editing (0 ahead ⇒ trivial).

## 4. Deliberately NOT proposed

- F15 / D5 (open a CJ dispute on a local refund after CJ was paid) — a
  separate user decision by the approved plan.
- F13 (JVM-local duplicate-placement guard) — single instance by compose
  design; documented.
- F16 Refunds-page visibility, SEPA `processing` copy, redirect-return
  reconciliation, withdraw button, timeline — gateway-api (built on
  `fix/gateway-api` 2026-09-06 per the session record, not yet on master).
- F17 review purchase check — goods-management.

## 5. Acceptance

- Module suite green with real "Tests run:" counts (baseline 387 / 0; ≈ +3).
- Item 1: the resource pin passes; dev keeps SQL DEBUG (verified by a dev boot
  on :18085 still printing `Preparing:` lines). Prod proof = MAIN, after
  recreate: `docker logs` shows 0 `Preparing:` lines.
- Item 2: unit tests as above; dev boot unchanged.
- Deploy (MAIN): the compose env line alone works with a recreate; the yml +
  hook change needs the order image rebuilt. No migration, no core/db change,
  no other container.
