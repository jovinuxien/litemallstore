# CLAUDE.md — litemall Dual-Gateway Split + Self-Signed JWT

Generated from `SESSION.md`. This is the shared root brief inherited by every
worktree branch. Each worktree appends a phase-scoped section at the bottom.
Edit freely — this file is meant to be refined per task.

## Objective

Split litemall's single entangled gateway/frontend into **two independent
Spring Cloud Gateway edges**, each embedding its own React SPA, **no Keycloak**:

| Gateway | Port | Role | Auth |
|---|---|---|---|
| `litemall-gateway-api` | 8090 | Customer edge (visitors + customers) | Self-signed **customer** JWT issued+validated at the edge |
| `litemall-gateway-admin` | 8080 | Admin edge (all DDD services) | Self-signed **admin** JWT issued+validated at the edge |

Service-to-service: OAuth2 **client-credentials machine token** from a new
`litemall-authserver`; DDD services become resource servers validating that
machine token; end-user identity forwarded as trusted headers. Customer/admin
edge tokens are **never relayed downstream**.

## Locked decisions (do not relitigate)

- **No Keycloak.** Both gateways mint/validate their own RS256 JWT at the edge.
- Service-to-service = dedicated `litemall-authserver` (Spring Authorization
  Server, client-credentials only, RS256+JWKS). DDD services validate it.
- Admin gateway = rename of existing `litemall-gateway` (done, Phase 3a).
- wx-api moved 8082 → **8084** (8083 = admin-api, 8082 = goods-management).
- `litemall-all-react-war` is retired in Phase 5.
- Customer edge is self-contained: depends on `litemall-db`, verifies
  credentials directly against `LitemallUser` (BCrypt), persists V15 refresh
  tokens itself; blocking MyBatis on a `boundedElastic` scheduler.
- Two separate Redux stores, no shared auth slice; shared FE via npm workspace.
- Customer SPA keeps the litemall `{errno,errmsg,data}` envelope.
- **Process:** written, approved plan before large/architectural code; surface
  genuine forks via questions first.

### Critical gotcha — DO NOT VIOLATE

`litemall-core` pulls `spring-boot-starter-web` (servlet MVC) →
**incompatible with reactive Spring Cloud Gateway**. The shared JWT toolkit
lives in **`litemall-db`** (`org.linlinjava.litemall.db.auth`), the lowest
servlet-free shared module. Gateways depend on `litemall-db` and scan
`org.linlinjava.litemall.db` — **never introduce `litemall-core` into either
gateway.**

## Phase status (at base-branch creation)

| Phase | Status |
|---|---|
| 0 — JWT toolkit (in litemall-db) | ✅ DONE, compiles |
| 1 — wx-api port → 8084 | ✅ DONE |
| 2 — Customer auth @ gateway-api | ✅ DONE, compiles |
| 3a — Rename gateway → gateway-admin | ✅ DONE |
| 3b — Strip Keycloak/OIDC from gateway-admin | ⏳ worktree `phase-3b` |
| 3c — Admin self-JWT @ gateway-admin | ⏳ worktree `phase-3c` |
| 3d — V16 admin refresh table | ⏳ worktree `phase-3d` |
| 4 — authserver + resource servers | ⏳ worktree `phase-4` |
| 5 — Frontend split, retire all-react-war | ⏳ worktree `phase-5` |
| 6 — Integration verification | ⏳ on base branch after merges |

## Build / verify commands

- Compile a module offline: `mvn -q -o -pl <module> -am compile`
- Validate a module: `mvn -q -o -pl <module> validate`
- Offline-verified so far: `litemall-db`, `litemall-gateway-api` (+deps).
- `litemall-gateway-admin` full offline compile may fail until Phase 3b
  removes keycloak/resteasy deps — environmental, not a correctness signal.

## Port map

`8080` gateway-admin · `8081` recover-compose svc · `8082` goods-management ·
`8083` admin-api · **`8084` wx-api** · `8085` order · `8086` wallet ·
`8087` loyalty · `8088` promotion · `8090` gateway-api · `8761` eureka ·
`8888` config · authserver = TBD (pick free, e.g. 8089/8091).

## Worktree layout

| Branch | Worktree dir | Phase |
|---|---|---|
| `refactor/dual-gateway-base` | (this repo) | base / Phase 6 |
| `refactor/phase-3b-strip-keycloak` | `../litemall-wt/phase-3b` | 3b |
| `refactor/phase-3c-admin-jwt` | `../litemall-wt/phase-3c` | 3c |
| `refactor/phase-3d-admin-refresh` | `../litemall-wt/phase-3d` | 3d |
| `refactor/phase-4-authserver` | `../litemall-wt/phase-4` | 4 |
| `refactor/phase-5-frontend-split` | `../litemall-wt/phase-5` | 5 |

> 3b → 3c → 3d → 4 are **sequential** and touch overlapping `gateway-admin`
> files. Worktrees are isolation sandboxes for per-phase work, not a
> safe parallel-merge strategy — merge 3b before building 3c on top, etc.

## Memory pointers

`~/.claude/projects/-home-bimeni-shopping-apps-litemall-app-litemall/memory/`
- `project_frontend_split_auth.md` — full locked architecture + live progress.
- `feedback_plan_before_code.md` — plan-before-code preference.

---

## ACTIVE WORKTREE — Phase 3c: Admin self-JWT @ `litemall-gateway-admin`

Branch `refactor/phase-3c-admin-jwt`. **Depends on Phase 3b merged first.**

Mirror Phase 2 (customer auth at gateway-api) for the admin realm:

- `pom.xml`: add `org.linlinjava:litemall-db:0.1.0` dependency.
- `bootstrap.yml`: `spring.profiles.active: default,db`.
- `application.yml`: `spring.flyway.enabled: false`; `litemall.jwt` realm —
  issuer `litemall-admin`, audience `litemall-admin-api` (blank PEM →
  ephemeral key, like gateway-api).
- `JwtConfig` → `@Bean adminJwtService`
  (`@EnableConfigurationProperties(JwtProperties.class)`).
- `AuthController` — reactive `POST /auth/login | /auth/refresh |
  /auth/logout`, `{errno,errmsg,data}` envelope (reuse the `ApiResponse`
  pattern from gateway-api), blocking work on `Schedulers.boundedElastic()`.
  Verify admin creds via `LitemallAdminService` in `litemall-db` — find its
  `queryByUsername` equivalent + BCrypt check.
- `SecurityConfig` — `@EnableWebFluxSecurity`, `ADMIN` authority on admin
  paths.
- `IdentityForwardingFilter` — strip client `X-User-*`; inject
  `X-User-Id` / `X-User-Type=admin` + a `roles` claim only after a valid
  edge JWT (anti-spoofing).

> Refresh-token persistence (`RefreshTokenService`) needs the V16 table from
> Phase 3d. Either land 3d first, or stub refresh and wire it after 3d merge.

**Verify:** `mvn -q -o -pl litemall-gateway-admin -am compile`