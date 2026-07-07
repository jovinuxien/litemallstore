# Handoff spec — gateway routes + SPA wiring for the promotion service

- **Producer:** `litemall-promotion-service` (Wave 2 revive)
- **Consumers:** `litemall-gateway-api` (customer edge + SPA),
  `litemall-gateway-admin` (admin edge + SPA)
- Service registers in Eureka as **`PROMOTION-SERVICE-APP`** (port 8088), so
  `lb://promotion-service-app` resolves once the service is up.

## Decision (user-approved 2026-07-07): legacy-compatible paths

The customer SPA keeps its existing `/srv/coupon/*` + `/srv/groupon/list`
calls; promotion serves them natively via legacy-shape controllers
(`interfaces/rest/legacy/**`). Gateways only add routes — no SPA path changes,
with ONE parameter exception for `selectlist` (below).

## gateway-api (customer edge) — route to ADD

Insert BEFORE the `/srv/**` → litemall-goods-management catch-all:

```yaml
- id: promotion-service
  uri: lb://promotion-service-app
  predicates:
    - Path=/srv/promotion/**,/srv/coupon/**,/srv/groupon/**
```

(Replicate in the dev-proxy route block as for other services. The machine
token relay + `X-User-*` identity forwarding already fire on every `lb://`
route — no extra filter work.)

### Customer endpoints then reachable

| SPA call (exists today in `userApi.ts`/`contentApi.ts`)     | Served by |
|--------------------------------------------------------------|-----------|
| `GET /srv/coupon/list?page&limit`                             | legacy controller → `{errno,errmsg,data:{list:[ICoupon],total,page,limit}}` |
| `GET /srv/coupon/mylist?status&page&limit` (status 0/1/2 = the SPA tab index) | legacy controller, same envelope; item carries `id`(=userCouponId), `cid`(=couponId) |
| `GET /srv/coupon/selectlist` — **params change**              | legacy controller expects `amount=<subtotal>&goodsIds=csv&categoryIds=csv` instead of `cartId`/`grouponRulesId` (promotion has no cart access; Checkout.tsx already holds the cart client-side). Update the two call sites in `userApi.ts`/`Checkout.tsx`. |
| `POST /srv/coupon/receive {couponId}`                         | legacy controller |
| `POST /srv/coupon/exchange {code}` (new, optional UI)         | legacy controller |
| `GET /srv/groupon/list?page&limit`                            | legacy controller → `{errno,errmsg,data:{list:[GrouponItem],total}}`; `discountMember` = required headcount |
| Canonical DDD surface (new UI work may prefer these)          | `/srv/promotion/coupon/**`, `/srv/promotion/combination/**` (start/join/my/pink endpoints — see spec-groupon-priced-submit-contract.md) |

Remove the `isMissingEndpoint` empty-state assumption for these paths once the
route lands (graceful guards can stay; they just stop firing).

## gateway-admin (admin edge) — route to EXTEND

The existing route only matches `/srv/promotion/**`; the admin controllers
live under `/srv/private/admin/promotion/**`. Extend the predicate (both
route blocks):

```yaml
- id: promotion-service
  uri: lb://promotion-service-app
  predicates:
    - Path=/srv/promotion/**,/srv/private/admin/promotion/**
```

`/srv/private/admin/**` is gated to ROLE_ADMIN by litemall-svcsecurity inside
the service; the admin gateway's own SecurityConfig should keep these paths
`authenticated()`.

### Admin endpoints for the (currently 0-byte) Coupons/Groupons views

- Coupons (`/admin/promotion/coupon` menu leaf):
  `GET /srv/private/admin/promotion/coupon/list?page&limit`,
  `GET /{couponId}`, `POST` (create), `PUT /{couponId}`, `DELETE /{couponId}`,
  `GET /{couponId}/users?page&limit` (issue-records, carries `userId`),
  `POST /grant {couponId, userId}` (direct-grant).
- Groupon rules / activity (`/admin/promotion/groupon-*` leaves):
  `GET /srv/private/admin/promotion/combination/list`, `GET /{id}`, `POST`,
  `PUT /{id}`, `DELETE /{id}`, `POST /{id}/activate`, `POST /{id}/expire`,
  `GET /{id}/pinks?status=` and `GET /pinks?status=` (activity monitoring:
  leader slots with memberCount vs requiredMembers, expireTime, status).
- Campaign (targeting) surface unchanged:
  `/srv/private/admin/promotion/campaign/**`.

Admin mutation/read DTOs use the service's own JSON shapes (not the legacy
errno envelope): mutations return
`{success, operationType, message, data}` with HTTP 200/400.

## Boot prerequisite for both gateways

Promotion-service must be started **from a checkout containing this branch's
code** (or after merge, from main) — master's promotion-service fails to boot
with `ConflictingBeanDefinitionException: litemallDomainEventConfig`; fixed on
this branch by deleting promotion's duplicate config class.
