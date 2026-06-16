# Handoff → `gateway-api` worktree: route the customer order/cart/wallet surface

**Owner of the fix:** `fix/gateway-api` worktree (and its customer SPA).
**Why this doc lives here:** discovered while debugging "cannot place an order" from the
`order` worktree. The order service is correct and reachable on its own port; the failure is
entirely in the customer edge. Per the `order` worktree's scope rule we do **not** edit
`litemall-gateway-api` or the SPA here — this is the precise spec for that worktree.

---

## ⚠️ 2026-06-16 RUNTIME UPDATE — route is now *defined* but *not effective*

Re-verified live today. The picture has **moved on** from the original root cause below
(§1 "the gateway has no route" is now **stale** — the route IS in the config). New finding:

**`master`'s `litemall-gateway-api/src/main/resources/config/application.yml` now DOES define
the `customer-order` route** (`Path=/srv/order/**,/srv/cart/**` → `lb://order-service-app`,
ahead of the `/srv/**` goods catch-all). Yet the **running gateway still 404s** every
`/srv/order/**` and `/srv/cart/**` request. So the route exists on disk but the running route
table is not carrying it.

### Live evidence (gateway freshly restarted from `litemall/litemall-gateway-api`, master)
| Probe (`curl … :8090`) | Result | Meaning |
|---|---|---|
| `GET /srv/search?q=bag` | **200**, body `{"errno":0,"data":{"total":8,…}}` | gateway proxying works (goods catch-all reaches goods-mgmt) |
| `GET /srv/order/list` | **404**, body `{"timestamp":…,"status":404,"error":"Not Found","path":"/srv/order/list"}` | gateway's **own** unmatched-route 404, NOT a proxied backend response |
| `POST /srv/order/submit` | **404** (same shape) | same — never reaches a backend |
| `GET /srv/cart/list` | **404** (same shape) | same |
| direct `POST :8085/srv/order/submit` | **401** (security challenge) | order service **is** reachable and the handler **exists** |

Decisive detail: a full reactive stack trace on the 404 ends in
`org.springframework.web.reactive.resource.ResourceWebHandler` — i.e. the request fell through
**all** gateway routes to the **static SPA resource handler**. `/srv/order/**` does not even
hit the `/srv/**` goods catch-all (that would yield a *proxied* goods 404, `{"errno":…}` shape,
not this one). So the running gateway's effective route list lacks a usable `/srv/order/**`
mapping entirely, while `/srv/search` matches a route fine.

### Order side is proven done (NOT the blocker)
- Order service from `fix/order` runs on **:8085**, `/actuator/health` = 200, and
  `/actuator/mappings` confirms the **`/srv/order/submit`** handler is registered.
- The earlier 404 *was* an order bug (handler commented out on `master`'s controller); that is
  fixed on `fix/order` (`@PostMapping("/submit")` + `X-User-Id` identity). The running 404 is
  now purely the gateway.

### Likely cause to investigate in `gateway-api`
- The gateway's `bootstrap.yml` points at a **Spring Cloud Config server `http://localhost:8888`**
  ("non-fatal if unreachable; boots from local config"). If config-server was **up at an earlier
  gateway boot** it may have served a **stale gateway route set without the order route**, which
  the running instance cached. (When checked today `:8888` was **down** — HTTP 000 — so a clean
  restart *should* use the local file, but the order route still didn't take. Worth confirming
  whether `:8888` flaps, and exactly which route source the running gateway resolved.)
- Verify the **actually-loaded** route table: expose `GET /actuator/gateway/routes` (currently
  **not exposed** — returns 404) and confirm `customer-order` is present and ordered before the
  `/srv/**` catch-all. If absent, the config source/precedence is the bug.
- Confirm `lb://order-service-app` resolves the Eureka id **`ORDER-SERVICE-APP`** (it is
  registered, one instance on `:8085`). An empty-instance LB cache shows as **503**, not 404 —
  earlier probes did briefly return 503 on this route, so the route can match; the steady-state
  404 means it currently does **not** match.

The contract / SPA spec below remains valid. Treat §1's "no route exists" as **superseded** by
this update; §2 (stale SPA cart path + stub checkout) and the endpoint contract still stand.

---

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
