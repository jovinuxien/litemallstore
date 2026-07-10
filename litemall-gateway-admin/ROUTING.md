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
| `/srv/promotion/**`, `/srv/private/admin/promotion/**` | `lb://promotion-service-app` | `ROLE_ADMIN` on the admin prefix |
| `/srv/private/admin/order/**`, `/srv/private/admin/aftersale/**` | `lb://order-service-app` | `ROLE_ADMIN`  |
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

### Edge-hosted admin CRUD (`web/admin/*`) — precedence over the catch-all

Some admin verticals have **no DDD service that owns them** (the only other
implementation is the unrouted `litemall-admin-api`), so they are served by thin
`@RestController`s inside this gateway (`gatewayadmin/web/admin/EdgeAdmin*`),
over the same `litemall-db` services the legacy admin-api used:

| Path                              | Controller                    | litemall-db service(s)                        |
|-----------------------------------|-------------------------------|-----------------------------------------------|
| `/srv/private/admin/ad/**`        | `EdgeAdminAdController`        | `LitemallAdService`                           |
| `/srv/private/admin/admin/**`     | `EdgeAdminAccountController`   | `LitemallAdminService` (BCrypt on create)     |
| `/srv/private/admin/notice/**`    | `EdgeAdminNoticeController`    | `LitemallNotice(Admin)Service`                |
| `/srv/private/admin/log/**`       | `EdgeAdminLogController`       | `LitemallLogService` (read-only)              |
| `/srv/private/admin/role/**`      | `EdgeAdminRoleController`      | `LitemallRole(+Admin)Service`                 |

The former `EdgeAdminCouponController` / `EdgeAdminGrouponController` were
**deleted 2026-07-10**: promotion-service (merged to master `73f12660c`) owns
the coupon/combination admin CRUD at `/srv/private/admin/promotion/**`, and the
SPA was re-pointed there (`adminPromotionApi.ts`). The edge pair wrote legacy
`litemall-db` tables that the customer claim center (served by
promotion-service) never reads, so keeping them would have been split-brain.
Ads stay at the edge — promotion-service has no ad vertical.

**Precedence:** these paths would otherwise fall through the `/srv/**` catch-all
to goods-management. They resolve locally because Spring's
`RequestMappingHandlerMapping` (order 0) is consulted **before** Spring Cloud
Gateway's `RoutePredicateHandlerMapping` (order 1) — the same mechanism that
lets the local `AuthController` own `/auth/**`. Blocking MyBatis calls run on
`Schedulers.boundedElastic()` (never the Netty event loop), mirroring
`AuthController`. Storage (`/srv/private/admin/storage/**`) is NOT edge-hosted —
it is served by goods-management via the catch-all.

**Operation-log auditing:** `AdminAuditLogFilter` (a `GlobalFilter`-ordered
`WebFilter`, after `IdentityForwardingFilter`) writes a `litemall_log` row for
every mutating (`POST`/`PUT`/`DELETE`) request under `/srv/private/admin/**`,
covering both the edge controllers and the proxied goods/order admin surface.
The admin name is resolved from the validated `X-User-Id`; the write is
fire-and-forget on boundedElastic and never fails the request. This replaces the
legacy admin-api `LogHelper`, which no DDD service carries.

> **Migration note (updated 2026-07-10):** the coupon/groupon halves of this
> migration are DONE — promotion-service owns them under
> `/srv/private/admin/promotion/{coupon,combination}/**` (routed above) and the
> SPA calls those paths directly; the edge controllers are deleted. The SPA
> change was unavoidable: promotion's admin surface is REST-shaped
> (`GET/POST/PUT/DELETE`, plain DTOs, no `{errno}` envelope), not
> legacy-shaped. Ads remain edge-hosted until a service claims them.

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

## Build gotcha — checksum "dirty skip" can produce a jar without the SPA

The `webapp` profile skips the npm/webpack build when
`target/checksums.csv` equals `target/checksums.csv.old`
(`eval-frontend-checksum` antrun → `skip.npm=true`). Both files are
(re)created by every build **even when webpack never ran** — so a
compile-only run on a fresh `target/` leaves matching checksums with **no**
`target/classes/static/`, and every later `mvn package` silently ships a jar
with no embedded SPA (`/` then 404s at runtime). Observed live 2026-07-06 on
the main checkout. Remedy: `rm target/checksums.csv.old` (or `mvn clean`)
before packaging, and sanity-check the artifact with
`unzip -l target/litemall-gateway-admin-*.jar | grep static/index.html`.

## Acceptance verification — 2026-07-06 (all green)

Static checks on `fix/gateway-admin` @ `777760fef` (== master):
`mvn -q -o -pl litemall-gateway-admin -am compile` clean;
`mvn -q -o -pl litemall-gateway-admin dependency:tree | grep litemall-core`
empty; exactly one `ApiResponse.java` (`web/ApiResponse.java`).

Runtime: gateway jar (embedded SPA) on `:18080` (Jenkins squats `:8080`),
default profile, against eureka `:8761`, authserver `:8089`, goods-management
(alt port `:18082`, lb via eureka), order `:8085`, loyalty `:8087`; no
webpack dev server running.

```
POST /auth/login (admin123)                        → 200, ROLE_ADMIN JWT
GET /srv/private/admin/goods/list  [goods-mgmt]    → 200 {"errno":0,...,"total":3709}
GET /srv/private/admin/order/list  [order]         → 200 {"errno":0,...,"total":36}
GET /srv/wallet/balance            [order/wallet]  → 200 {"userId":1,"balance":0.00,...}
GET /srv/loyalty/1/points/balance  [loyalty]       → 404 FROM THE SERVICE (servlet
                                                     body; route+relay OK — known
                                                     loyalty controller-mapping bug)
GET /srv/promotion/coupon/list     [promotion]     → 503 no instance (route OK;
                                                     promotion still fails boot:
                                                     ConflictingBeanDefinitionException
                                                     litemallDomainEventConfig)
GET /srv/catalog/index (public, no token)          → 200
GET /srv/wallet/balance (no token)                 → 401
GET /srv/private/admin/goods/list (no token)       → 401
GET /            (embedded SPA)                    → 200 index.html
GET /dashboard   (SpaWebFilter rewrite)            → 200
GET /main.05bb25fc.js (hashed bundle)              → 200
```

`MachineTokenRelayFilter` + `IdentityForwardingFilter` proven live by the
wallet call: the JWT subject's `X-User-Id=1` reached order-service behind the
machine token (balance scoped to user 1).

## Acceptance verification — 2026-07-07 (edge-hosted admin CRUD: coupon / groupon / ad / admin / notice / log / role / storage)

Adds the promotion + system admin surfaces that had no routed backend (only the
unrouted `litemall-admin-api`). Gateway jar (freshly built, embedded SPA — bundle
re-verified to contain the new routes and `/{ad,coupon,groupon,notice,log,role}`
endpoint strings) on `:18080`, default profile, eureka `:8761`, authserver
`:8089`, goods-management `:8082`, order `:8085`. Login `admin123`. Totals below
match the live DB (`litemall_ad`=3, `litemall_coupon`=5, `litemall_coupon_user`=6,
`litemall_groupon_rules`=3, `litemall_admin`=3, `litemall_role`=3,
`litemall_notice/groupon/storage`=0):

```
GET  /srv/private/admin/coupon/list (no token)     → 401  (edge control gated ROLE_ADMIN)
POST /auth/login (admin123)                        → 200, ROLE_ADMIN JWT
GET  /srv/private/admin/ad/list                    → 200 errno=0 total=3
GET  /srv/private/admin/coupon/list                → 200 errno=0 total=5 (name/discount/type resolved)
GET  /srv/private/admin/coupon/listuser            → 200 errno=0 total=6  (issued instances)
GET  /srv/private/admin/groupon/list               → 200 errno=0 total=3  (rules)
GET  /srv/private/admin/groupon/listRecord         → 200 errno=0 total=0  (activities)
GET  /srv/private/admin/admin/list                 → 200 errno=0 total=3  (password field REDACTED)
GET  /srv/private/admin/notice/list                → 200 errno=0 total=0
GET  /srv/private/admin/log/list                   → 200 errno=0 total=1
GET  /srv/private/admin/role/list                  → 200 errno=0 total=3
GET  /srv/private/admin/role/options               → 200 errno=0 total=3  ({value,label} picker)
GET  /srv/private/admin/storage/list  [goods-mgmt] → 200 errno=0 total=0
```

Write path + audit log (proves the edge controllers mutate and `AdminAuditLogFilter` fires):

```
POST /srv/private/admin/notice/create              → 200 errno=0 id=3
GET  /srv/private/admin/notice/list                → total=1 ["Verify edge notice"]
POST /srv/private/admin/notice/delete {id:3}       → 200 errno=0
GET  /srv/private/admin/notice/list                → total=0
POST /srv/private/admin/ad/create → list → delete  → total 3→4→3
GET  /srv/private/admin/log/list                   → total 1→3, new rows:
       admin123 · POST /srv/private/admin/notice/create · 200
       admin123 · POST /srv/private/admin/notice/delete · 200
```

The two audit rows carry the JWT-resolved admin name (`admin123` via
`X-User-Id`) and the request action/result — end-to-end proof of
`IdentityForwardingFilter` → `AdminAuditLogFilter` → `litemall_log`.

## Cross-module follow-ups (raised by the `gateway-admin` worktree)

These are backend gaps the admin SPA now depends on. They are intentionally NOT
implemented here (out of scope); track them in the named worktrees.

- **RESOLVED 2026-07-10 — `/srv/order/admin/stat` is superseded; nothing to
  build.** The follow-up as recorded was a mis-remembered path: order stats
  are served by goods-management's `AdminStatController` at
  `GET /srv/private/admin/stat/order` (plus `/user`, `/goods`), which the
  dashboard (`adminStateSlice.fetchOrderStats` → `ORDER_STATS_URL`) already
  calls and the `/srv/**` catch-all already routes. Verified live. No order-
  side endpoint is needed; if order ever ships a dedicated stat surface it
  would be a new contract, not this follow-up.

- **`loyalty` (whoever owns litemall-loyalty-service) — controller never
  registers; `/srv/loyalty/**` 404s from inside the service.** Root-caused
  2026-07-10, see `docs/handoff-loyalty-scanbasepackages.md`:
  `LitemallLoyaltyServiceApplication` sets
  `scanBasePackages = {"org.linlinjava.litemall.db",
  "org.linlinjava.litemall.core"}` and omits its own
  `org.linlinjava.litemall.loyalty` package, so
  `LitemallLoyaltyRestController` (and every loyalty bean) is never scanned.
  The gateway side (route `/srv/loyalty/**` → `lb://loyalty-service-app`,
  machine-token relay, identity forwarding) is verified correct — the fix is
  one line in the backend, out of this worktree's scope.

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

- **PRE-EXISTING (this module) — hard-refresh/deep-link to any `/admin/*` SPA
  route returns 401 instead of the SPA shell.** `SecurityConfig` gates
  `/admin/**` to `ROLE_ADMIN`, but the admin SPA's *client-side* routes also
  live under `/admin/*` (`/admin/dashboard`, `/admin/promotion/coupon`,
  `/admin/sys/log`, …). A document GET to one of those is 401'd by security
  *before* `SpaWebFilter` can rewrite it to `/index.html`, so a refresh on a
  deep page shows an error rather than loading the shell (which would then
  client-route and redirect to login). Verified 2026-07-07: `/admin/dashboard`,
  `/admin/goods`, `/admin/mall/brand` and the new `/admin/promotion/coupon`,
  `/admin/sys/log` all 401 identically — this is realm-wide and predates the
  edge-CRUD work, not specific to the new screens. Normal use (load `/`, log in,
  navigate via the sidebar) is unaffected because in-app navigation is
  client-side. Fix (separate change, whole-realm impact): since `/admin/**` no
  longer fronts any backend (the `/admin/** → admin-api` route was removed and
  admin-api is unrouted), either drop the `/admin/**` security rule so
  `SpaWebFilter` serves the shell, or exclude document/`Accept: text/html` GETs
  from it. Left for a focused SecurityConfig pass so it is reviewed on its own.
