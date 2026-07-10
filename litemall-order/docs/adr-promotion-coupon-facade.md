# ADR — coupon-at-checkout through the promotion ACL facade

- **Status:** implemented on `fix/order` (Wave 2, 2026-07-10)
- **Contract consumed:** `litemall-promotion-service/docs/spec-coupon-checkout-contract.md`
  (validate / redeem / release), verified against the live controllers on master.

## Decision

The submit path's coupon seam is re-pointed from order's LOCAL coupon tables to the
promotion service, through `LitemallPromotionFacade` (interface in
`infrastructure/services/acl/facades/`, Feign client underneath) — the same ACL shape
as `LitemallGoodsFacade` (see `adr-goods-acquisition.md`). Domain and application code
depend on the facade only; nothing outside `infrastructure/` imports the Feign client.

Promotion owns the coupon tables now. Order's own coupon repositories
(`LitemallCouponRepositoryImpl`, `LitemallCouponUserRepositoryImpl`) and
`LitemallCouponServiceLayer`'s validate/redeem methods are retired from the placement
path (the classes remain, unused by submit, pending a later cleanup) — before this
change, a promotion-issued coupon at submit died as `errno:502` because order
validated it against its own empty tables.

## The transport seam

`PromotionServiceFeignClient` (`name = "promotion-service"`, `${promotion.service.url}`,
default `:8088`):

- Machine token: attached by the existing `FeignConfig#goodsMachineTokenInterceptor`
  (fires for every internal client whose name doesn't start with `cj-`).
- Customer identity: `X-User-Id` passed explicitly per call — promotion enforces
  ownership from that header, never from the body.
- **Redeem/release return raw `feign.Response`.** Promotion answers business
  rejections as HTTP 400 with `{success:false, message}`; with a decoded return type
  the module's error decoder + circuit fallback would swallow that into
  "service unavailable". Raw `Response` bypasses both for non-2xx, so a 400 stays a
  *coupon rejection* and only transport-level failures reach the fallback / count
  toward the circuit.
- Resilience: `feign.client.config.promotion-service {connectTimeout: 2000,
  readTimeout: 3000}`, `resilience4j` circuit + 4s time-limiter — mirrors goods-service.

## Placement flow (in `LitemallOrderServiceImpl.placeOrder`, one transaction)

1. **Validate** — with a coupon selected (`couponId` not 0/-1 or `userCouponId` > 0):
   `GET /srv/promotion/coupon/usable?amount=<checkedGoodsPrice>&goodsIds=…&categoryIds=…`
   and require the submitted `userCouponId` in the result. The cart facts come from
   the cart lines + one `batchGetGoods` (the goods facade now maps `categoryId`, so
   CATEGORY-scoped coupons match). Miss → `LitemallInvalidCouponException` → **422,
   no order row, coupon untouched**. The order is priced with the returned discount
   (`coupon_price`, totals floored at 0).
2. **Redeem exactly-once** — after the order + line rows are written (redeem stamps
   the consuming orderId), before the stock reserve:
   `POST /srv/promotion/coupon/user/{userCouponId}/redeem {orderId, orderSubtotal}`.
   A 400 aborts the placement (rollback; the coupon was never consumed). On success a
   rollback-time `TransactionSynchronization` is registered that calls release — so a
   later in-transaction failure (groupon insert, stock reservation) gives the coupon
   back, mirroring the stock-restore compensation. If the redeem-time discount
   disagrees with the priced discount, the placement aborts too (rollback → release).
3. **Release on cancel** — user cancel and the unpaid-order sweep register an
   after-commit hook: if `coupon_price > 0`, the consuming holding is looked up at
   promotion (`GET /my?status=USED`, matched by orderId — the order row doesn't carry
   the userCouponId) and released. Release is idempotent and replay-safe on the
   promotion side; failures are logged for manual replay, never rethrown.

## Failure matrix (HTTP surface of `POST /srv/order/submit`)

| Situation | Result |
|---|---|
| Coupon not owned / expired / below min-spend / out of scope / already used | **422**, message names the coupon (the SPA's inline "remove coupon and retry" keys on that), no order row |
| Redeem refused at step 2 | **422**, rollback, coupon untouched |
| Promotion down / timeout / circuit open, coupon selected | **503**, "Promotion service is unavailable — … retry, or remove the coupon", no order row |
| Promotion down, NO coupon selected | unaffected — no promotion call is ever made |
| Placement fails after a successful redeem | rollback + compensating release (coupon back to USABLE) |

## Notes

- `orderSubtotal` sent to redeem = `checkedGoodsPrice` (goods total after groupon,
  before freight), the same amount validated against `min` by `/usable`.
- The `userCouponId: -1` "none" sentinel is clamped to 0 (previously a raw 500).
- Known trade-off: between a successful redeem-commit at promotion and this
  transaction's commit there is a small window where a crash (not a rollback) loses
  the release hook; promotion's release is replay-safe by (userCouponId, orderId), so
  the recovery is a manual replay guided by the facade's error log line.
