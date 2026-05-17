# SESSION HANDOFF — Dual-Gateway Split + Self-Signed JWT (No Keycloak)

> Phases 0–5 COMPLETE and merged into `refactor/dual-gateway-base`.
> Only Phase 6 (integration verification) remains — it needs a running
> environment (MySQL + Eureka + config-server + authserver + Node), so it is
> a runbook to execute, not code to write.
> Last updated: 2026-05-17. master untouched at `b840e72e`.

---

## 1. Objective (unchanged)

Two independent Spring Cloud Gateway edges, each embedding its own React SPA,
**no Keycloak**; service-to-service via an OAuth2 client-credentials machine
token from a dedicated authserver; end-user identity forwarded as trusted
`X-User-*` headers, trusted by services only behind a valid machine token.

## 2. Branch / worktree layout

- `master` — untouched (`b840e72e`).
- `refactor/dual-gateway-base` — **integration branch; Phases 0–5 merged.**
  Root `CLAUDE.md` is the canonical shared brief (per-phase appendices stay
  in their worktree branches only).
- Per-phase branches (all merged into base): `refactor/phase-3b-strip-keycloak`,
  `refactor/phase-3c-admin-jwt`, `refactor/phase-4-authserver`,
  `refactor/phase-5-frontend-split`. `phase-3d` was folded into 3c and its
  worktree/branch deleted.
- Worktrees still on disk under `../litemall-wt/` (phase-3b/3c/4/5) — safe to
  `git worktree remove` now that all are merged.

## 3. Phase status

| Phase | Status |
|---|---|
| 0 — JWT toolkit in litemall-db | ✅ merged |
| 1 — wx-api 8082→8084 | ✅ merged |
| 2 — Customer self-JWT @ gateway-api | ✅ merged |
| 3a — rename gateway→gateway-admin | ✅ merged |
| 3b — strip Keycloak/OIDC from gateway-admin | ✅ merged |
| 3c — admin self-JWT @ gateway-admin (+ 3d folded: V16 admin refresh) | ✅ merged |
| 4 — litemall-authserver + litemall-svcsecurity + 6 services + gateway machine token | ✅ merged |
| 5 — frontend split (npm workspace, two SPAs, retire all-react-war) | ✅ merged |
| 6 — integration verification | ⏳ runbook below |

## 4. Architecture as built

- **litemall-gateway-api** (:8090, reactive) — customer edge. Self-JWT
  (issuer `litemall-customer`/aud `litemall-customer-api`), V15 refresh,
  `/auth/login|refresh|logout`, `IdentityForwardingFilter` (X-User-*),
  `MachineTokenRelayFilter` (Bearer machine token on `lb://` routes).
  Serves the **customer SPA** (`src/main/webapp`, frontend-maven-plugin).
- **litemall-gateway-admin** (:8080, reactive) — admin edge. Self-JWT
  (issuer `litemall-admin`/aud `litemall-admin-api`), V16 refresh, ADMIN
  authz enforced (`/admin/**`, `/srv/private/admin/**`), same machine-token
  relay. Serves the **admin SPA** (`src/main/webapp/app/litemall-admin`).
- **litemall-authserver** (:8089) — Spring Authorization Server,
  client_credentials ONLY, RS256, `/oauth2/jwks`, `/oauth2/token`. Clients
  `gateway-admin` / `gateway-api` (dev secrets `gateway-*-dev-secret`,
  NoOpPasswordEncoder — override for non-dev). Key reuses litemall-db
  `RsaKeys`/`JwtProperties` PEM (blank → ephemeral).
- **litemall-svcsecurity** — shared servlet auto-config on the 6 services:
  validates authserver JWKS (`litemall.svcsecurity.jwk-set-uri`),
  `MachineTokenUserContextFilter` trusts `X-User-*` only behind a valid
  machine token.
- **@litemall/shared** + npm workspace (root `package.json`): `{errno,errmsg,
  data}` envelope + `createApiClient` (Bearer). Two independent stores:
  `customerAuthSlice` (gateway-api) / `adminAuthSlice` (gateway-admin),
  same-origin relative `/auth/*` (webpack dev proxy → the edge).

## 5. Port map

`8080` gateway-admin · `8082` goods-management · `8083` goodsapi-analytic ·
`8084` wx-api · `8085` order · `8086` wallet · `8087` loyalty ·
`8088` promotion · **`8089` authserver** · `8090` gateway-api ·
`8761` eureka · `8888` config.

## 6. Verified so far

Offline `mvn` compiles: litemall-db, gateway-api, gateway-admin,
authserver, svcsecurity, loyalty/wallet/promotion (with svcsecurity dep).
Frontend is **structural only** (JSON/poms well-formed; Java compiles with
`-Dskip.npm=true`) — no Node in the build sandbox; npm build is Phase 6.

## 7. PHASE 6 — integration runbook (execute in a real environment)

Prereqs: JDK 21, Maven, Node 20+, MySQL with the litemall schema (+ Flyway
V1–V16), a reachable Spring Cloud Config (or run with local config).

### 7a. Pre-existing tech-debt fixes (NOT caused by this refactor)
1. `litemall-wx-api` `WxGoodsController.java:374` —
   `elasticService.getGoods(String,String)` does not exist on
   `LitemallElasticDataService`; fix the call/method (blocks `litemall-order`
   which transitively builds wx-api).
2. `litemall-goods-management` & `litemall-goodsapi-analytic`
   `LitemallGoodsManagement.java` — add the missing `commons-lang`
   dependency (or migrate the import to `commons-lang3`).
3. `litemall-all/pom.xml` still copies `../litemall-all-react-war/dist`;
   `litemall-all` is already commented out of the reactor — delete that
   stale copy-resources block (or the litemall-all module) for tidiness.
4. Optionally `rm -rf` the untracked `litemall-all-react-war/` on-disk
   residue (node_modules/target — not in git).

### 7b. External config-server repo (only the repo owner can do this)
In `github.com/jovinuxien/litemall-config`, for every DDD service config:
remove `spring.security.oauth2.client.*` and the Keycloak
`spring.security.oauth2.resourceserver.jwt.jwk-set-uri`; add
`litemall.svcsecurity.jwk-set-uri` → the authserver JWKS
(`http://litemall-authserver:8089/oauth2/jwks` or deployed host).
Also mirror the gateways' `spring.security.oauth2.client.registration.
authserver` + `litemall.jwt` realm if config-server overrides local yml.

### 7c. Frontend completion
- `npm install` at repo root (workspace hoists litemall-shared + both SPAs).
- Normalise the relocated source imports: customer files moved into
  `litemall-gateway-api/src/main/webapp/app` and admin into
  `litemall-gateway-admin/src/main/webapp/app/litemall-admin` still import
  legacy `app/config/*` / `BASE_URL_CONTEXT` / `X-Litemall-Token`; repoint to
  the local `app/*` aliases and `@litemall/shared` `createApiClient`; wire the
  migrated customer slices into `gateway-api` `config/store.ts` and the admin
  slices into the admin store.
- `npm run webapp:prod` in each SPA (or `mvn -pl litemall-gateway-api,
  litemall-gateway-admin -am package` — frontend-maven-plugin runs it;
  `-Dskip.npm=true` to skip).

### 7d. Boot order & smoke tests
1. MySQL → `litemall-eureka` (8761) → `litemall-config` (8888) →
   `litemall-authserver` (8089) → 6 DDD services → both gateways.
2. `GET http://localhost:8089/oauth2/jwks` returns a JWK set.
3. Customer: `POST /auth/login` via gateway-api (:8090) →
   `{errno:0,data:{token,refreshToken,userInfo}}`; call a protected
   `/srv/...` route → 200; confirm the downstream service received
   `Authorization: Bearer <machine>` + `X-User-*`.
4. Admin: same via gateway-admin (:8080) `/auth/login`; `/admin/**` and
   `/srv/private/admin/**` require ADMIN; non-admin → 403.
5. Cross-realm: a customer token rejected by the admin edge and vice versa
   (distinct issuer/audience).
6. Machine-token boundary: hit a service directly with only `X-User-*` and
   NO machine token → identity ignored / 401 on protected paths.
7. Refresh rotation: `/auth/refresh` issues a new refresh token and revokes
   the old (V15 customer / V16 admin); `/auth/logout` revokes.

## 8. How to resume

1. Read this file + `~/.claude/.../memory/project_frontend_split_auth.md`
   + root `CLAUDE.md`.
2. `git checkout refactor/dual-gateway-base` (Phases 0–5 are here).
3. Work Phase 6 from §7. Keep memory + this file updated.
4. Never introduce `litemall-core` into a gateway (servlet-MVC vs reactive);
   shared auth/persistence lives in `litemall-db`.
