# SESSION HANDOFF — Dual-Gateway Split + Self-Signed JWT (No Keycloak)

> Resumable state for the dual-frontend / split-auth refactor.
> Last updated: 2026-05-16. Branch: `master`. Nothing committed yet (all changes are working-tree only).

---

## 1. Objective

Split litemall's single entangled gateway/frontend into **two independent Spring Cloud Gateway edges**, each embedding its own React SPA, with **no Keycloak anywhere**:

| Gateway | Port | Role | Auth |
|---|---|---|---|
| `litemall-gateway-api` | 8090 | Customer edge (visitors + customers) | Self-signed **customer** JWT issued+validated at the edge |
| `litemall-gateway-admin` | 8080 | Admin edge (all DDD services) | Self-signed **admin** JWT issued+validated at the edge |

Service-to-service: OAuth2 **client-credentials machine token** from a new `litemall-authserver`; DDD services become resource servers validating that machine token; end-user identity forwarded as trusted headers. Customer/admin edge tokens are **never relayed downstream**.

## 2. Locked decisions (do not relitigate)

- **No Keycloak.** Both gateways mint/validate their own RS256 JWT at the edge.
- Admin auth = **self-signed JWT** (Keycloak dropped, not "Keycloak JWT").
- Service-to-service = **Option A**: dedicated `litemall-authserver` (Spring Authorization Server, client-credentials only, RS256+JWKS). DDD services validate the machine token.
- Admin gateway = **rename of existing `litemall-gateway`** (done, see Phase 3a).
- wx-api port collision with goods-management (both 8082) → **wx-api moved to 8084** (8083 = admin-api).
- `litemall-all-react-war` to be **retired** (Phase 5).
- Customer edge is **self-contained**: depends on `litemall-db`, verifies credentials directly against `LitemallUser` (BCrypt), persists V15 refresh tokens itself; blocking MyBatis on a `boundedElastic` scheduler. No runtime auth dependency on wx-api.
- Customer SPA keeps the litemall `{errno,errmsg,data}` envelope.
- Two separate Redux stores, no shared auth slice; shared FE code via npm workspace package (Phase 5).
- **Process:** user wants a written, approved plan before large/architectural code; surface genuine forks via questions first.

### Critical gotcha
`litemall-core` pulls `spring-boot-starter-web` (servlet MVC) → **incompatible with reactive Spring Cloud Gateway**. Therefore the shared JWT toolkit lives in **`litemall-db`** (`org.linlinjava.litemall.db.auth`), the lowest servlet-free shared module. Gateways depend on `litemall-db` and scan `org.linlinjava.litemall.db` (NOT `...core`).

## 3. Phase status

| Phase | Status | Notes |
|---|---|---|
| 0 — JWT toolkit | ✅ DONE, compiles | In `litemall-db` (relocated from core) |
| 1 — wx-api port → 8084 | ✅ DONE | |
| 2 — Customer auth @ gateway-api | ✅ DONE, compiles | Not boot-tested (needs MySQL+eureka+config) |
| 3a — Rename gateway→gateway-admin | ✅ DONE, validates offline | Still Keycloak-based |
| 3b — Strip Keycloak/OIDC | ⏳ NOT STARTED (files read, no edits) | Next up |
| 3c — Admin self-JWT | ⏳ pending | Mirror Phase 2 for admin realm |
| 3d — V16 admin refresh table | ⏳ pending | |
| 4 — authserver + resource servers | ⏳ pending | |
| 5 — Frontend split, retire all-react-war | ⏳ pending | |
| 6 — Integration verification | ⏳ pending | Needs MySQL + Eureka + config server |

## 4. What was created/changed (working tree)

**Phase 0 — `litemall-db/src/main/java/org/linlinjava/litemall/db/auth/`** (new):
`JwtService.java` (RS256 via `com.auth0:java-jwt` 3.4.1; `issue`/`verify`/`tryVerify`; `JwtService.create(props)`), `JwtProperties.java` (`@ConfigurationProperties("litemall.jwt")`: issuer/audience/accessTtlSeconds/privateKeyPem/publicKeyPem), `RsaKeys.java` (PEM PKCS8/X.509 or ephemeral 2048 pair), `InvalidJwtException.java`.
- `litemall-db/pom.xml`: added `com.auth0:java-jwt` + `spring-boot-configuration-processor` (optional).
- `litemall-core/pom.xml`: java-jwt add was **reverted** (toolkit is NOT in core).

**Phase 1:**
- `litemall-wx-api/src/main/resources/application.yml`: `server.port` 8082 → **8084**.
- `litemall-gateway-api/src/main/resources/config/application.yml`: `customer-wx-api` route uri → `http://localhost:8084`.
- `docker-compose-recover.yml`: wx-api `8082:8082` → `8084:8084`.

**Phase 2 — `litemall-gateway-api`:**
- `pom.xml`: + `spring-boot-starter-security`, + `org.linlinjava:litemall-db:0.1.0`.
- `GatewayApiApplication.java`: `@SpringBootApplication(scanBasePackages={"org.linlinjava.litemall.gatewayapi","org.linlinjava.litemall.db"})`.
- `src/main/resources/config/bootstrap.yml`: `spring.profiles.active: default,db`.
- `src/main/resources/config/application.yml`: `spring.flyway.enabled: false`; `litemall.jwt` realm (issuer `litemall-customer`, audience `litemall-customer-api`, ttl 7200, blank PEM → ephemeral).
- New Java under `org/linlinjava/litemall/gatewayapi/`:
  - `config/JwtConfig.java` — `@Bean customerJwtService` (`@EnableConfigurationProperties(JwtProperties.class)`).
  - `auth/CustomerCredentialsService.java` — BCrypt check vs `LitemallUserService.queryByUsername`; `BadCredentialsException`.
  - `auth/RefreshTokenService.java` — V15 DB-backed rotating tokens (SHA-256 hash, single-use rotation); `Rotation` record; uses `LitemallUserRefreshTokenMapper`.
  - `auth/InvalidRefreshTokenException.java`.
  - `auth/AuthController.java` — reactive `POST /auth/login|/auth/refresh|/auth/logout`, `{errno,errmsg,data}`, blocking work on `Schedulers.boundedElastic()`.
  - `web/ApiResponse.java` — litemall envelope helper (no core dependency).
  - `security/SecurityConfig.java` — `@EnableWebFluxSecurity`, no Keycloak/CSRF/formLogin, `anyExchange().permitAll()` (per-route authz deferred to Phase 4).
  - `security/IdentityForwardingFilter.java` — strips client `X-User-*`, injects `X-User-Id`/`X-User-Type` only after valid edge JWT (anti-spoofing).

**Phase 3a — rename:**
- `git mv litemall-gateway litemall-gateway-admin`; `git mv .../litemall/gateway .../litemall/gatewayadmin`.
- All 25 `.java`: package `org.linlinjava.litemall.gateway` → `org.linlinjava.litemall.gatewayadmin`.
- `litemall-gateway-admin/pom.xml`: artifactId/name → `litemall-gateway-admin`, description updated.
- root `pom.xml`: `<module>litemall-gateway</module>` → `litemall-gateway-admin`.
- `litemall-gateway-admin/.../config/bootstrap.yml`: `spring.application.name: gateway` → `gateway-admin`; jhipster `clientApp.name: 'gateway-admin'`.
- Module **still Keycloak-wired** (3b removes it). Class still named `GatewayServiceApplication`.

## 5. Remaining work — exact sub-steps

### Phase 3b — Strip Keycloak/OIDC from `litemall-gateway-admin`
Delete (OIDC-only): `infrastructure/config/GatewaySecurityConfig.java` (replace w/ new reactive self-JWT `SecurityConfig` + admin `IdentityForwardingFilter` mirroring gateway-api, with `ADMIN` role gate on `/srv/private/admin/**`), `infrastructure/config/security/oauth2/AudienceValidator.java`, `.../oauth2/JwtGrantedAuthorityConverter.java`, `application/web/filter/OAuth2ReactiveRefreshTokenWebFilter.java`, `infrastructure/feignclient/LitemallFeignUserClient.java`, OIDC bits of `interfaces/rest/AccountResource.java` / `LogoutResource.java` / `UserResource.java` (replace AccountResource with a minimal `/api/account` reading JWT claims if the admin SPA needs it).
Keep: `SpaWebFilter` (serves SPA), `WebConfigurer` (jhipster CORS/cache — but it pulls `tech.jhipster`/CSRF cookie filter; keep), `WebClientConfig`, domain aggregates/value objects (pure models), `AuthoritiesConstants`, `SecurityUtils` (rewrite if it references OIDC claims).
`GatewayServiceApplication.java`: remove OAuth2/OIDC imports + `@RestController` OIDC endpoint usage; keep jhipster startup banner; add `scanBasePackages` incl. `org.linlinjava.litemall.db`.
`pom.xml`: remove `keycloak-admin-client`, `spring-boot-starter-oauth2-client`, `spring-boot-starter-oauth2-resource-server`, `spring-security-oauth2-jose`, RESTEasy (`resteasy-*`), `jakarta.ws.rs-api`, testcontainers-keycloak. Keep `spring-cloud-starter-security`, jhipster-framework, webflux, gateway.
`bootstrap.yml`: delete `spring.security.oauth2.*`, the `default-filters: TokenRelay`, per-route `TokenRelay` filters, and the `token-service`/`/oauth/**` route; keep `admin-api`, `goods-service-app`, `order-service-app` routes (machine-token filter added Phase 4).

### Phase 3c — Admin self-JWT @ gateway-admin
Mirror Phase 2: add `org.linlinjava:litemall-db` dep + `profiles: default,db` + `flyway.enabled:false` + `litemall.jwt` (issuer `litemall-admin`, audience `litemall-admin-api`). `JwtConfig` (`adminJwtService`), `AuthController` `/auth/login|refresh|logout` verifying admin creds via `LitemallAdminService` (in litemall-db; find its `queryByUsername` equivalent + BCrypt) , `SecurityConfig` with `ADMIN` authority on admin paths, `IdentityForwardingFilter` (X-User-Type=admin, plus roles claim). Reuse `ApiResponse` pattern.

### Phase 3d — V16 admin refresh table
`litemall-db/src/main/resources/db/migration/V16__add_admin_refresh_token.sql` + `db/undo/U16__...sql` (mirror V15). `LitemallAdminRefreshToken` entity + `LitemallAdminRefreshTokenMapper` (mirror `LitemallUserRefreshToken*`). Admin `RefreshTokenService` in gateway-admin using it.

### Phase 4 — authserver + resource servers
New module `litemall-authserver` (Spring Authorization Server, client_credentials only, RS256, JWKS endpoint), add to root pom. Register both gateways as client_credentials clients. Add machine-token filter to gateway routes (replace removed TokenRelay). Make `litemall-order/wallet/loyalty/promotion/goodsapi-analytic` + re-point `litemall-goods-management` resource servers validating authserver JWKS; trust forwarded `X-User-*` only with a valid machine token.

### Phase 5 — Frontend split
Customer SPA → `litemall-gateway-api/src/main/webapp`; admin SPA stays in `litemall-gateway-admin/src/main/webapp`. npm workspace shared package; two Redux stores; retire combined `loginUserThunk`+`loginAdminThunk` auth slice. Remove `litemall-all-react-war` from root pom + delete module. Wire `frontend-maven-plugin` in gateway-api (admin already has it).

### Phase 6 — Integration verification
Boot eureka(8761)+config(8888)+authserver+both gateways+DDD services+MySQL. Verify customer & admin login→JWT→protected route→service via machine token; cross-domain token rejection; missing-machine-token rejection; refresh rotation+revocation.

## 6. Build/verify commands

- Compile a module offline: `mvn -q -o -pl <module> -am compile`
- Validate renamed module: `mvn -q -o -pl litemall-gateway-admin validate`
- Verified offline-compiling so far: `litemall-db`, `litemall-gateway-api` (+deps).
- `litemall-gateway-admin` full offline compile may fail until Phase 3b removes keycloak/resteasy deps (may be uncached); that's environmental, not a correctness signal.

## 7. Port map

8080 gateway-admin · 8081 (recover compose svc) · 8082 goods-management · 8083 admin-api · **8084 wx-api** · 8085 order · 8086 wallet · 8087 loyalty · 8088 promotion · 8090 gateway-api · 8761 eureka · 8888 config · authserver = TBD (pick a free port, e.g. 8089/8091).

## 8. Memory pointers (auto-recall)

`~/.claude/projects/-home-bimeni-shopping-apps-litemall-app-litemall/memory/`
- `project_frontend_split_auth.md` — full locked architecture + live progress (kept in sync each phase).
- `feedback_plan_before_code.md` — plan-before-code preference.
- Task list (TaskCreate IDs 1–10) tracks phases/sub-phases.

## 9. How to resume

1. Read this file + `project_frontend_split_auth.md`.
2. `git status` to see the working-tree changes listed in §4.
3. Continue at **Phase 3b** (§5). Keep updating memory + this file per phase.
4. Do not introduce `litemall-core` into either gateway (servlet-MVC conflict). Use `litemall-db` for shared auth/persistence.
