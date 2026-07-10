# Handoff — loyalty controller 404 through the admin gateway (root-caused)

- **Raised by:** `gateway-admin` worktree, 2026-07-10 (Wave-3 task 4 — recorded
  follow-up "loyalty controller 404 via the gateway: route or backend?").
- **Owner:** whichever worktree next touches `litemall-loyalty-service`.
- **Verdict: backend bug. The gateway side is correct — no gateway change is
  needed or included.**

## Symptom

`GET :18080/srv/loyalty/1/points/balance` (admin JWT, machine-token relay
active) → **404 with a servlet-style body served by loyalty-service itself**
(so the request WAS routed, load-balanced, and accepted by the service).
First recorded in `ROUTING.md` §"Acceptance verification — 2026-07-06".

## Root cause

`litemall-loyalty-service/src/main/java/org/linlinjava/litemall/loyalty/LitemallLoyaltyServiceApplication.java`:

```java
@SpringBootApplication(scanBasePackages = {"org.linlinjava.litemall.db",
                                           "org.linlinjava.litemall.core"})
```

Setting `scanBasePackages` **replaces** the default scan of the application's
own package and this list omits `org.linlinjava.litemall.loyalty`. Nothing
under that package is ever registered: `LitemallLoyaltyRestController`
(`@RequestMapping("/srv/loyalty")`), the `UserContextFilter` `@Component`,
application services, repository impls. The service boots "healthy" with zero
loyalty beans, so every `/srv/loyalty/**` request 404s at the dispatcher.

Every sibling service includes its own package — e.g. order lists
`"org.linlinjava.litemall.order"`, promotion lists
`"org.linlinjava.litemall.promotion"`. Loyalty is the only one missing it.

## The fix (one line, in litemall-loyalty-service)

```java
@SpringBootApplication(scanBasePackages = {"org.linlinjava.litemall.loyalty",
                                           "org.linlinjava.litemall.db",
                                           "org.linlinjava.litemall.core"})
```

Then rebuild + restart and re-run the probe below.

## Evidence the gateway side is fine (verified 2026-07-10)

- Route exists in both profiles of
  `litemall-gateway-admin/src/main/resources/config/application.yml`:
  `Path=/srv/loyalty/**` → `lb://loyalty-service-app`.
- Target id matches the service's registration:
  loyalty's `application.yml` sets `spring.application.name:
  LOYALTY-SERVICE-APP` (its bootstrap.yml value is overridden; Eureka ids are
  case-insensitive) — same `-service-app` convention as order/promotion,
  both of which work through identical route blocks.
- `MachineTokenRelayFilter` + `IdentityForwardingFilter` are global on `lb://`
  routes and proven live on the order/wallet paths; irrelevant anyway — the
  404 is emitted before any security check, and the controller reads `userId`
  from the path, not headers.

## Acceptance probe (after the backend fix)

```
POST :18080/auth/login (admin123)            → ROLE_ADMIN JWT
GET  :18080/srv/loyalty/1/points/balance     → 200 {errno:0,...} (not 404)
```

## Companion note — `/srv/order/admin/stat` follow-up is CLOSED

The other recorded follow-up chased in the same pass needs **no code
anywhere**: the path was mis-remembered. Order stats are served by
goods-management's `AdminStatController` at `GET /srv/private/admin/stat/order`
(also `/user`, `/goods`), routed via the gateway's `/srv/**` catch-all, and the
admin dashboard already calls exactly that
(`adminStateSlice.fetchOrderStats` / `adminStatApi`). Verified live — see
ROUTING.md "Cross-module follow-ups".
