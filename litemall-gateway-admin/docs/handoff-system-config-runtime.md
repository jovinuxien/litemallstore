# Handoff: runtime consumption of `litemall_system` config (Wave 4)

**From:** gateway-admin worktree (EdgeAdminConfigController, 2026-07-13)
**To:** order worktree (freight/schedulers), any service reading `litemall_system`

## What the edge now does

`litemall-gateway-admin` hosts `GET/POST /srv/private/admin/config/{mall,express,order}`
over the `litemall_system` table via litemall-db's `LitemallSystemConfigService`
(admin JWT required — SecurityConfig gates `/srv/private/admin/**`).

- GET returns the group's rows as a flat `{key: value}` map
  (groups = key prefixes `litemall_mall_*`, `litemall_express_*`, `litemall_order_*`).
- POST accepts the same map shape and **rejects (errno 402, no row touched)**
  any key that (a) doesn't carry the group prefix or (b) doesn't already exist
  as a live row — because `updateConfig` uses update-by-example and silently
  no-ops unknown keys. New keys must be seeded by migration/`addConfig`, not
  through this endpoint.
- The legacy `wx` group is not ported (WeChat out of scope).

## What the edge does NOT do — the reason this note exists

**A POST here changes the database only. It does NOT hot-reload any running
service.** Known consumers and their caching behavior:

| Key(s) | Consumer | Cache behavior |
|---|---|---|
| `litemall_express_freight_min`, `litemall_express_freight_value` | litemall-order freight quote/submit (Wave-4 `FreightCalculationService` ladder: freight-min free rule outermost, flat value fallback) | litemall-core `SystemConfig` is a **static per-JVM cache** populated at boot. Legacy admin-api called `SystemConfig.updateConfigs(...)` in-process, which worked only because admin-api shared that JVM. In the split-services world each service has its own copy. |
| `litemall_order_unpaid`, `litemall_order_unconfirm` | order-service auto-cancel / auto-confirm schedulers | Same static `SystemConfig` cache (or scheduler-local copies). |
| `litemall_mall_*` | storefront display values | Whatever the reading service caches. |

**Consequence:** after an admin edits express/order config, the new values take
effect in a given service only when that service (re)reads the table — today
that means on restart, unless the service adds its own refresh.

## Asks (pick per service, gateway-admin has no further work here)

1. **order-service (preferred):** read freight/scheduler values through a
   short-TTL cache (e.g. Caffeine ≤5 min, same pattern as the Wave-4 freight
   template cache) instead of the static `SystemConfig` snapshot — then admin
   edits land within minutes and no bus/endpoint is needed.
2. Alternatively expose an internal `POST /srv/private/refresh-config` (machine
   token) per consumer and the SPA can fan out — NOT implemented on the SPA
   side yet; tell us if you choose this and we'll add the call.
3. Do nothing — documented staleness until restart. The admin SPA config forms
   show a hint that changes may need a service restart to apply.

## SPA surfaces (for reference)

`/admin/config/mall`, `/admin/config/express`, `/admin/config/order` forms in
the admin SPA bind 1:1 to these endpoints (Wave-4 Task C bundle).
