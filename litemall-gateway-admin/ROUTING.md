# litemall-gateway-admin — Routing Contract

The admin edge (`:8080`, reactive Spring Cloud Gateway) is the single entry
point for the admin SPA. This file is the **contract** downstream services must
respect. The gateway does **not** depend on their source — services conform to
the convention below, not the other way around. Parallel work on other modules
is expected; coordinate via this contract, not by reading each other's code.

## Path → service map

| Path predicate                | Target (eureka service-id)       | Auth (SecurityConfig)      |
|-------------------------------|----------------------------------|----------------------------|
| `/srv/order/**`, `/srv/cart/**` | `lb://order-service-app`       | per `/srv/...` rules below |
| `/srv/wallet/**`              | `lb://order-service-app` ¹       | authenticated              |
| `/srv/loyalty/**`             | `lb://loyalty-service-app`       | per `/srv/...` rules below |
| `/srv/promotion/**`           | `lb://promotion-service-app`     | per `/srv/...` rules below |
| `/srv/private/admin/order/**` | `lb://order-service-app`         | `ROLE_ADMIN`               |
| `/srv/**` (catch-all)         | `lb://litemall-goods-management` | per `/srv/...` rules below |
| `/` (default profile)         | `forward:/index.html`            | public                     |

¹ The wallet vertical was absorbed into `litemall-order`
(`litemall-wallet-service` is deleted from the reactor); order-service serves
`/srv/wallet/**` via its `LitemallWalletRestController`.

**`litemall-admin-api` is deliberately NOT routed.** The admin SPA's whole
surface is served by goods-management (`/srv/private/admin/{brand,category,
keyword,issue,comment,goods}/**`) and order-service (`/srv/private/admin/
order/**`, `/srv/order/admin/**`) — verified live; the earlier
`/admin/** → admin-api` route was removed as bogus in `debcbfb6f`. admin-api
also sets no `spring.application.name`, so an `lb://` route cannot resolve it
through eureka. Do not re-add it without both fixing that and identifying a
path only admin-api can serve.

Routes are matched **in declaration order**; the specific `/srv/<svc>/**`
routes are declared **before** the `/srv/**` goods catch-all (see
`src/main/resources/config/application.yml`).

## Rules downstream services MUST follow

1. **Stable eureka `spring.application.name`** exactly as in the table:
   `order-service-app`, `loyalty-service-app`, `promotion-service-app`,
   `litemall-goods-management`. Changing a service id breaks its route.
2. **No `StripPrefix`.** The gateway forwards the **full path** unchanged. A
   service owns its `/srv/<svc>/**` namespace and must map its controllers
   under that prefix (e.g. `litemall-order` serves `/srv/order/...`). Goods
   management owns the remaining `/srv/**` space (e.g. `/srv/catalog/**`,
   `/srv/private/admin/**`).
3. **`lb://` scheme only.** Every route is load-balanced through eureka so the
   two global filters apply automatically (no per-route wiring):
   - `MachineTokenRelayFilter` sets `Authorization: Bearer <machine-token>`
     (client_credentials from the authserver) on the proxied request.
   - `IdentityForwardingFilter` strips any client-supplied `X-User-*`, then —
     only if the admin self-JWT validates — injects `X-User-Id`,
     `X-User-Type`, `X-User-Roles`.
   Downstream `litemall-svcsecurity` trusts `X-User-*` **only** behind a valid
   machine token. The admin edge JWT is **never** relayed downstream.
4. **Auth surface** (enforced at the edge by `SecurityConfig`):
   - public: `/auth/**`, `/srv/authenticate/**`, `/srv/catalog/**`,
     `/srv/cjAuth/**`, the SPA shell/assets, `/actuator/health/**`.
   - `ROLE_ADMIN`: `/admin/**`, `/srv/private/admin/**`, `/srv/order/admin/**`.
   - authenticated: `/srv/private/**`, `/srv/wallet/**`.
   A service exposing a new admin-only endpoint should place it under
   `/srv/<svc>/private/...` or `/srv/private/...` and rely on the edge gate
   (and its own `litemall-svcsecurity` check), not invent a new public path.

## Frontend serving (profile-split)

- **`dev`**: the gateway proxies the SPA paths
  (`/`, `/index.html`, `/static/**`, `/assets/**`, `/app/**`,
  `/sockjs-node/**`) → `http://localhost:9000` (webpack dev server). Route
  `frontend` in the dev profile block of application.yml.
- **default / prod**: route `frontend` forwards `/` → `forward:/index.html`
  (root only — a `/**` + `forward:` catch-all recurses through the WebFlux
  dispatcher and overflows the Reactor stack when the SPA isn't on the
  classpath). The built SPA (`classpath:/static`, via frontend-maven-plugin)
  is served by WebFlux static handling, and `SpaWebFilter`
  (`@Profile("!dev")`) rewrites HTML5 client routes to `/index.html`.
  `SpaWebFilter` explicitly excludes the backend prefixes above so it never
  shadows a gateway route.

## What this module must NOT do

- Never depend on `litemall-core`, `litemall-wx-api`, or `litemall-admin-api`
  (servlet MVC → incompatible with the reactive gateway). Only `litemall-db`
  (servlet-free) is allowed. See `pom.xml`.
- Never edit other modules to paper over a gateway misconfiguration.

## Cross-module follow-ups (raised by the `gateway-admin` worktree)

These are backend gaps the admin SPA now depends on. They are intentionally NOT
implemented here (out of scope); track them in the named worktrees.

- **`order` worktree — implement `GET /srv/order/admin/stat`.** STILL OPEN as
  of 2026-07-05: `litemall-order` serves `/srv/private/admin/order/{list,
  detail,{orderId}/ship,{orderId}/refund}` (`LitemallAdminOrderController`)
  but no stats endpoint. The admin dashboard (`Dashboard.tsx` →
  `adminStateSlice.fetchOrderStats`) calls this as an authenticated admin.
  Expected payload: per-day rows `{ day, orders, customers, amount, pcr? }`
  (array, or `{ rows: [...] }`), in the litemall `{errno,errmsg,data}`
  envelope. Until it exists the dashboard degrades gracefully (empty state +
  "stats unavailable" banner; no mock data). The edge already gates
  `/srv/order/admin/**` to ROLE_ADMIN (`SecurityConfig`) and the
  `/srv/order/**` → `lb://order-service-app` route exists, so only the
  service endpoint is missing.

- **RESOLVED — `goods-management` admin goods JSON on
  `/srv/private/admin/goods`.** `AdminGoodsController` now serves the admin
  catalog surface and the SPA was verified against it live (merged
  `faaa177f8`). Original expectation kept for reference — the admin
  list/detail (`adminGoodsApi` + `AdminGoodsList.tsx` / `GoodsDetail.tsx`)
  expects:
  - `GET /srv/private/admin/goods/list?page&limit&sort&order` →
    `data: { total, pages, limit, page, list: IGood[] }`, each `IGood` carrying
    `status`, `salesQuantity`, `picUrl`, `retailPrice`, plus a summed-SKU
    `stock` and optional `brand` / `categoryNames` for the row markers.
  - `GET /srv/private/admin/goods/detail?id=` →
    `data: { goods, specificationList, products (per-SKU incl. number),
    brand?, categoryNames? }`.
  Prices may be a number or `LitemallMoney {amount}` (the SPA reads both
  defensively). Reconcile any divergence there.
