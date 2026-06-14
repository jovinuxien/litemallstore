# Handoff → `gateway-api` worktree: route the customer order/cart/wallet surface

**Owner of the fix:** `fix/gateway-api` worktree (and its customer SPA).
**Why this doc lives here:** discovered while debugging "cannot place an order" from the
`order` worktree. The order service is correct and reachable on its own port; the failure is
entirely in the customer edge. Per the `order` worktree's scope rule we do **not** edit
`litemall-gateway-api` or the SPA here — this is the precise spec for that worktree.

## Symptom observed
Customer SPA (via `gateway-api`, port **8090**) → `srv/cart/index` and `srv/order/submit`
both return **404 Not Found**. Order never reaches the order service.

## Root cause (two layers, both in gateway-api)

### 1. The gateway has no route for `/srv/order/**`, `/srv/cart/**`, `/srv/wallet/**`
On this branch `litemall-gateway-api/src/main/resources/config/application.yml` only forwards
`/wx/**`. The customer order/goods routes are still commented-out placeholders
("STEP 2 (auth) will add the protected customer routes"). Anything under `/srv/order/**` or
`/srv/cart/**` dies at the gateway with 404 before reaching `order-service-app`.

### 2. The SPA calls a stale cart path and the checkout is an unwired stub
- The SPA calls `/srv/cart/index` — the **old litemall wx-api** contract. The DDD order
  service exposes `/srv/cart/items` (there is no `/index`), so even once routed this 404s
  at the order service.
- `app/views/commonViews/cart/Checkout.tsx` `handleSubmit` only does `console.log(formState)`.
  It never POSTs to the order service, so an order can never be placed from the SPA.

## What the order service actually exposes (target contract)

Order service runs on **port 8085**, app name `order-service-app` (Eureka-registered).
All customer endpoints are under `/srv/**`. Identity today is **mixed** (see "Caveats").

### Order — `LitemallOrderRestController` (`/srv/order`), identity via `X-User-Id` header
| Method | Path | Body / params |
|---|---|---|
| GET  | `/srv/order/list` | header `X-User-Id`; query `status[]`, `page`, `limit`, `sort`, `order` |
| POST | `/srv/order/submit` | header `X-User-Id`; body `LitemallPlaceOrderCommand` |
| POST | `/srv/order/{orderId}/actions/cancel` | header `X-User-Id`; body = reason string |

`LitemallPlaceOrderCommand` JSON fields: `userId` (ignored — overridden by the header),
`cartId`, `addressId`, `couponId`, `userCouponId`, `message`, `grouponRulesId`,
`grouponLinkId`. **All of `cartId/couponId/userCouponId/grouponRulesId/grouponLinkId` are now
optional** (order-side fix on `fix/order`): omit them or send `0` for a plain, non-groupon,
non-coupon order. `addressId` is required; `userId` comes from the authenticated header.

### Cart — `LitemallCartController` (`/srv/cart`), identity via `userId` query/body param
| Method | Path | Body / params |
|---|---|---|
| GET    | `/srv/cart/items` | query `userId` |
| GET    | `/srv/cart/items/{cartItemId}` | query `userId` |
| POST   | `/srv/cart/items` | body `AddCartItemRequest` (incl. `userId`) |
| PUT    | `/srv/cart/items/{cartItemId}` | query `userId`; body `UpdateCartItemRequest` |
| DELETE | `/srv/cart/items/{cartItemId}` | query `userId` |
| DELETE | `/srv/cart/items` | query `userId` (clear) |

> SPA must move from `/srv/cart/index` (+ other wx-api cart paths) to `/srv/cart/items`.

### CJ Dropshipping order — `LitemallCjOrderController` (`/srv/order/cj`)
| Method | Path | Body |
|---|---|---|
| POST | `/srv/order/cj/orders` | `CjOrderRequestDto` (orderNumber, shipping fields, `lines[].vid/quantity`) |

> Note: per `docs/adr-cj-order-placement.md` this REST endpoint is an **internal callable
> contract**; the intended customer checkout routes `cj_`-sourced lines through
> `CjDropshipOrderFacade`, not this endpoint. Clarify the intended customer CJ flow before
> wiring the SPA straight to `/srv/order/cj/orders`.

### Wallet self-service — `LitemallWalletRestController` (`/srv/wallet`), `X-User-Id` header
`GET /balance`, `POST /credit`, `POST /debit`, `POST /recharge`, `POST /extract`, `GET /bills`.

## Required gateway-api changes
1. Add routes (behind the customer self-signed JWT filter this gateway already validates),
   forwarding the authenticated user id as the **`X-User-Id`** header the order/wallet
   controllers expect:
   ```yaml
   - id: customer-order
     uri: lb://order-service-app
     predicates:
       - Path=/srv/order/**,/srv/cart/**,/srv/wallet/**
     # filters: add the customer JWT -> X-User-Id header relay used by the other srv routes
   ```
   (Split into separate route ids if you want different filters per surface.)
2. Point the SPA cart calls at `/srv/cart/items` (not `/srv/cart/index`).
3. Wire `Checkout.tsx` to `POST /srv/order/submit` with a `LitemallPlaceOrderCommand` body
   (at minimum `addressId`; `cartId`/coupon/groupon optional) — the auth header carries the user.
4. Decide and wire the customer CJ-order flow (see CJ note above).

## Caveats / follow-ups to raise
- **Identity is inconsistent on the order service:** order + wallet use the `X-User-Id`
  header (authoritative, set by the gateway), but **cart uses a caller-supplied `userId`
  query/body param** — an IDOR risk (a caller can read/modify another user's cart). Recommend
  the cart controller move to the `X-User-Id` header too. That change is order-worktree scope;
  raise it back to `fix/order` rather than papering over it at the gateway.
- Confirm `order-service-app` is the exact Eureka service id the gateway should load-balance to.
