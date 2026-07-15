# OCS search/index — verification runbook (master)

Verifies the goods-management **OCS (Open Commerce Search)** path on `master` and
ships a CI-safe guarded integration test. Captured 2026-06-05 against the live
docker-compose OCS stack (es:9200, indexer:8535, searcher:8534, suggest:8081) with
`litemall_index` holding 238 documents.

> **Scope of this file.** The OCS layer (index → searcher → suggest) and the guarded
> test below were **executed** against the live stack. Master's *application*
> endpoints are documented here **from the controller source** (`LitemallSearchController`,
> `LitemallGoodsAdminController`, `MessageConsumer`); boot the service (see §5) to
> re-observe them live. These assets were ported from the `fix/goods-management`
> worktree and adapted to master's contract — master's OCS implementation
> (`OcsSearchClient` / `OcsSuggestClient` / `OcsIndexerClient` / `OcsGoodsDocumentMapper`)
> is the one being verified, not the worktree's parallel implementation.

## 0. Stack

OCS stack from `docker-compose/` (es 9200, indexer 8535, searcher 8534, suggest 8081,
kibana 5601, rabbitmq 5672). The `litemall_index` field configuration
(`docker-compose/application.indexer-service.yml`) defines nine fields:
`product_id, title, price, discount_price, description, image_url, brand,
category_names, category_ids`.

## 1. Document model (DB → OCS)  — `OcsGoodsDocumentMapper` / `OcsProductDocument`

`OcsProductDocument` serializes all nine fields as snake_case `@JsonProperty`. Field
usages from the index config: `title` Search+Result+Sort; `price` Result+Sort+Facet;
`discount_price` Result+Sort; `description` Search+Result; `image_url` Result;
`brand` Search+Result+Facet; `category_names` Search+Result+Facet; `category_ids`
**Facet only**; `product_id` Result (rides as `document.id`).

Live ES state (executed):

```
GET http://localhost:9200/litemall_index/_count -> {"count":238}
```

== on-sale goods (`SELECT COUNT(*) FROM litemall_goods WHERE is_on_sale=1 AND deleted=0` -> 238).

## 2. Search (OCS → goodsList)  — `OcsSearchClient` / `GET /srv/search`

Master's `OcsSearchClient` calls the searcher and maps the slice envelope onto the
`goodsList` DTO the SPA consumes. The OCS searcher contract (executed):

```
GET http://localhost:8534/search-api/v1/search/litemall_index?q=silk&offset=0&limit=2
-> {"slices":[{"matchCount":12,"hits":[{"document":{"id":"1152161",
     "data":{title,price,discount_price,description,image_url,category_names}}}],
     "facets":[{"fieldName":"brand"},{"fieldName":"price"},
               {"fieldName":"category_ids"},{"fieldName":"category_names"}]}]}
```

- `matchCount=12` for `silk`; top-hit `data` carries the six always-present Result
  fields; `brand` is omitted from `data` when the goods has no brand (it stays a
  facet); `category_ids` is Facet-only (never in `data`).

Master's **application** contract (from `LitemallSearchController`, `@RequestMapping("/srv")`):

```
GET /srv/search?q=<term>&offset=<o>&limit=<n>
-> 200 {"total":<n>,"offset":<o>,"limit":<n>,
        "goodsList":[{"id","name","brief","picUrl","retailPrice","counterPrice",
                      "brand","categoryNames"}, ...]}      # raw map, no errno envelope
```

`retailPrice = discount_price ?? price`, `counterPrice = price` (`OcsSearchClient.toGoodsListResponse`).

## 3. Suggest (OCS → phrases)  — `OcsSuggestClient` / `GET /srv/suggest`

OCS suggest contract (executed):

```
GET http://localhost:8081/suggest-api/v1/litemall_index/suggest?userQuery=quilt
-> [{"phrase":"quilt pillow", ...}, ... 10 total]
```

Master's application contract (from `LitemallSearchController`):

```
GET /srv/suggest?q=<prefix>  -> 200 ["quilt pillow", ...]   # List<String>
```

Both `/srv/search/**` and `/srv/suggest/**` are public in `litemall-svcsecurity`
(`SvcSecurityProperties.publicPaths`); no SQL fallback on the search path.

## 4. Indexing — full reindex + incremental

- **Full reindex** (`LitemallGoodsAdminController`, `@RequestMapping("srv/admin")`):
  `POST /srv/admin/goods/reindex` streams on-sale goods in pages through
  `OcsIndexerClient`. (Note: this path sits under `/srv/admin/**`, which is neither
  in `publicPaths` nor `adminPaths` (`/srv/private/admin/**`) — it is authenticated
  but not ADMIN-gated. Flagged as an observation; tightening it is a master concern,
  out of scope for this port.)
- **Incremental** (`MessageConsumer.onGoodsChange`): every goods add/update/delete in
  `LitemallGoodsManagementServiceImpl` publishes a `GoodsChangeMessage` to Rabbit;
  the listener re-fetches the aggregate (UPSERT) or deletes (DELETE) and calls
  `OcsIndexerClient.upsert/delete` via `OcsGoodsDocumentMapper` — wrapped in
  try/catch so a transient OCS error doesn't poison the queue.

## 5. Booting master standalone to re-observe §2–§4 live

Master has no `verify` profile/harness. To exercise the app endpoints against the
live stack, run goods-management with config-server/eureka disabled, MySQL +
OCS hosts pointed at localhost, and a security setup that permits the public search
paths (the resource-server chain needs either a reachable JWKS or a permit-all
override). Then:

```
POST /srv/admin/goods/reindex          # -> indexed == on-sale count, ES _count 238
GET  /srv/search?q=silk&offset=0&limit=5
GET  /srv/suggest?q=quilt
# create/update/delete one goods -> GoodsChangeMessage -> MessageConsumer ->
#   OcsIndexerClient PUT/DELETE update/litemall_index (no full reindex)
```

## 6. Guarded integration check

`src/test/java/org/linlinjava/litemall/goods/search/OcsSearchRoundTripVerificationTest.java`
talks to the OCS searcher/suggest REST contracts **directly** (the same endpoints
`OcsSearchClient`/`OcsSuggestClient` call) and asserts: (1) a reindexed document
round-trips through search carrying all nine `litemall_index` fields — the six
always-present Result fields + `product_id` on the top hit, `brand` + `category_ids`
present as facets, and a self-adapting check that pulls a real brand from the brand
facet, searches it, and asserts that document carries `brand` in result `data`; and
(2) suggest returns phrases. It **skips** (`assumeTrue`) when no OCS answers, so it is
CI-safe. Hosts/index overridable via `-Docs.search-url`/`-Docs.suggest-url`/`-Docs.index-name`.

Two project-wide test-harness quirks must be worked around to run it (pre-existing,
not search-specific): the module surefire config sets `<debugForkedProcess>true>`
(forks a *suspending* JDWP debugger on 5005) and an `<argLine>` carrying
`-XX:MaxPermSize=256m` (invalid on JDK 21). The `webapp` profile (active by default)
also runs an `npm` build that is irrelevant here. Against a live stack:

```bash
# in litemall-goods-management/pom.xml, for the duration of the run:
#   <argLine>-Xmx1024m -XX:MaxPermSize=256m</argLine> -> <argLine>-Xmx1024m</argLine>
#   remove the <debugForkedProcess>true</debugForkedProcess> line
MAVEN_OPTS="" mvn -o -pl litemall-goods-management test -P'!webapp' \
  -Dmaven.test.skip=false -Dtest=OcsSearchRoundTripVerificationTest \
  -Docs.search-url=http://localhost:8534 \
  -Docs.suggest-url=http://localhost:8081 -Docs.index-name=litemall_index
# -> Tests run: 2, Failures: 0, Errors: 0, Skipped: 0  BUILD SUCCESS   (observed 2026-06-05)
```

`mvn -o -pl litemall-goods-management -am compile -P'!webapp'` and `test-compile`
are both clean under JDK 21 (after the three test-source fixes shipped with this
runbook: imports in `LitemallGoodsRepositoryImplTest` and `SpringBootTestClassOrderer`,
plus a repaired empty `src/test/resources/logback.xml`).

> The sibling `LitemallGoodsRepositoryImplTest` now compiles but still ERRORS at
> run time (`NoClassDefFoundError: org.mockito.cglib.proxy.MethodInterceptor`): the
> root `pom.xml` pins `mockito-core` 2.28.2 while `mockito-junit-jupiter` is 5.3.1,
> and Mockito 2.x can't create mocks on JDK 21. The fix is a root-pom dependency
> bump affecting every module — out of scope here, raised as cross-cutting debt.

---

## §8 — Category-targeted CJ catalog fetch + nightly cron indexing (2026-06-13)

**Goal.** Index a *chosen set of CJ categories* with a *per-category product cap*, on a
nightly cron, instead of the single default CJ page. Driven entirely by config — no hardcoded
hosts, counts, or categories.

**What changed (code).**
- `CJProductClient.getProductList(categoryId, pageNum, pageSize)` — the CJ `/product/list`
  endpoint filtered server-side by a CJ **leaf** category id, paged. (The old no-arg call —
  no params, one default page — is kept for the `/srv/cjAuth/*` endpoints.)
- `CJProductService.fetchByCategory(categoryId, targetCount, pageSize)` — pages a category up
  to `targetCount`, **blocking `fetch-pace-seconds` between calls** (Guava `RateLimiter`,
  first acquire immediate) so a multi-category plan stays within the CJ quota. Stops at the
  cap, at upstream exhaustion, on an empty page, or on the first failed page.
- `CjProductIndexingService.fetchByPlan()` — resolves each `catalog-targets` entry to CJ leaf
  ids (explicit `category-id`, else `category` name matched case-insensitively at ANY tree
  level → all leaf ids beneath it) and pulls up to `limit` across those leaves (stops early,
  so a first-level name does **not** fan out across every leaf). Reuses `toDocument(...)`
  unchanged; falls back to the committed sample only if the whole plan yields nothing.
- `CjCatalogRefreshTask` — `@Scheduled(cron = "${spring.cjdropship.refresh-cron:0 0 3 * * *}")`
  (was a 6h fixed-delay), still upsert-only.

**Config (`spring.cjdropship`).**
```yaml
refresh-cron: "0 0 3 * * *"     # nightly 03:00
fetch-pace-seconds: 300         # block between CJ calls (CJ quota)
page-size: 200                  # CJ caps ~200
catalog-targets:
  - { category: "Toys, Kids & Babies", limit: 200 }   # baby
  - { category: "Women's Clothing",    limit: 300 }   # women
  - { category: "Consumer Electronics", limit: 200 }  # electronics
```

**Rate-limit math (the binding constraint).** One paced call ≈ `fetch-pace-seconds`. A target
costs ≈ `ceil(limit / page-size)` calls when its leaves are dense (≈1 call per 200). The plan
above ≈ 1+2+1 = 4 calls ≈ ~15 min once nightly. Want 500 of a category → `ceil(500/200)=3`
calls for that target alone.

> **NOTE — "categories in ONE request":** CJ `/product/list` filters by a *single* leaf
> categoryId per HTTP call, so multiple categories cannot be fetched in one literal request.
> The *plan* is the one-shot config; the job executes it as N paced calls. A first-level name
> resolves to many leaves but the fetch stops at `limit`, keeping N small.

**Manual verification procedure (live CJ, paced — budget ~15 min).**
1. Boot goods-management (RUNBOOK §6 boot steps) with the OCS stack up and `enabled: true`.
2. Trigger the job out-of-band by temporarily setting `refresh-cron` to a near-future minute
   (e.g. `"0 */2 * * * *"`), OR call `POST /srv/private/admin/search/reindex` if wired to the
   CJ path; watch the log for:
   `CJ catalog-target '<name>' fetched <n> products (limit <l>, <k> leaf categories)`.
3. Confirm pacing: consecutive `getProductList` calls are ≥ `fetch-pace-seconds` apart in the
   timestamps.
4. Confirm in `litemall_index` (searcher 8534 / Kibana 5601): CJ docs for exactly the
   configured categories, ≤ each `limit`, all `cj_<pid>` ids + `source=cj_dropshipping`, folded
   into the shared local category facet via `category-mapping`.

**Deferred (logged, not done here):** stale-CJ-doc deletion (upsert-only path); real per-SKU
inventory (still `default-stock`); CJ order placement (lives in `litemall-order`).

`mvn -o -pl litemall-goods-management -am compile` clean.

---

## §11 — CJ Dropshipping product detail on `/srv/goods/detail` (2026-06-14)

**Bug (confirmed at runtime, before fix).** Clicking a CJ search hit could not open a detail page:
- `GET /srv/goods/detail` bound `@NotNull Integer id` → a `cj_<uuid>` id failed Spring type
  conversion before any handler logic (HTTP 400). CJ products are OCS-only (index-only ADR, no
  `litemall_goods` row), so the DB aggregation could never serve them anyway.
- `GET /srv/cjAuth/productCJDetail` was a dead stub: `@RequestParam long productId` (a CJ pid is a
  UUID, not a long) with its whole body commented out → always returned `ok()` with no data.
- `CJProductClient` had **no** product-detail method; the detail URL + `CJProductDetailResponse`
  /`CJProductDetailData` DTOs existed but nothing called them.

**Fix (this branch, all in `litemall-goods-management`).**
- `CJProductClient.getProductDetail(String pid)` → CJ "Query Product", config-driven URL
  `spring.cjdropship.api.product.product-detail-url` + `?pid=<uuid>`, token-auth, same
  `makeGetRequest` path as the list call. URL value corrected to `.../api2.0/v1/product/query`.
- `CJProductService.getProductDetail(String pid)` → 1h per-pid memo cache (CJ quota is tight),
  returns `null` on not-found.
- `CJProductDetailData.variants` retyped to `List<CJProductVariantData>` (`@JsonProperty("variants")`)
  — CJ returns an array, the old single-object field was wrong.
- `CjGoodsDetailService.detail(cj_<pid>)` maps the CJ detail into the SAME key shape the local
  detail returns (`info / productList / specificationList / attribute / brand / issue / comment /
  groupon / share / shareImage`). Pricing mirrors the indexing path: retail = sellPrice(USD) ×
  usd-to-cny × margin (CNY) — never raw wholesale. CJ-absent sections come back empty/zero.
- `LitemallGoodsController.privateGoodsDetails` now takes `@NotBlank String id`: `cj_`-prefixed →
  `CjGoodsDetailService`; otherwise `Integer.valueOf` → unchanged local aggregation
  (backward-compatible with numeric ids the SPA already sends).
- `LitemallCJProductController.getProductDetail(@RequestParam String pid)` returns the raw CJ
  detail (debug/admin), no longer a dead stub.

**Manual verification (live CJ + OCS, paced — CJ detail is 1 req then cached).**
1. Boot goods-management (§6 boot) with `enabled: true`; ensure CJ docs are indexed (§10).
2. `GET /srv/search?q=<a CJ term>` → note a hit whose `id` starts `cj_`.
3. `GET /srv/goods/detail?id=cj_<pid>` → expect `data.info.id = cj_<pid>`, `info.name` = English
   title, `info.retailPrice` = marked-up price (not raw wholesale), `productList[*]` one row per
   CJ variant, `info.source = cj_dropshipping`. Capture request/response here:

```
$ curl -s 'http://localhost:8082/srv/goods/detail?id=cj_<pid>' | jq '.data.info'
   <PASTE LIVE RESPONSE>
```
4. `GET /srv/goods/detail?id=1006002` (a local id) → still returns the local aggregation unchanged.

> **Live capture PENDING a booted stack.** Compile is green; the live CJ detail request/response
> capture (steps 3) must be pasted above on the next run of the OCS+CJ+MySQL stack — the CJ API is
> rate-limited (1/300s) and needs real network + key, so it is not exercised in CI.

`mvn -o -pl litemall-goods-management -am compile` clean.

---

## §12 — Goods-domain admin operations on goods-management, ROLE_ADMIN-gated (2026-06-14)

**Goal.** Host the goods-domain admin CRUD on this service (not litemall-admin-api), protected by
the admin account. Scope (agreed): **Goods, Category, Brand, Keyword, Comment, Issue, Storage**.
Other admin domains (order/coupon/groupon/user/wallet/region/role/config/stat) stay in their own
worktrees. `litemall-admin-api` is **left untouched** — these are ported copies.

**Enforcement (no new security code).** All ported controllers mount under `/srv/private/admin/**`,
which `litemall-svcsecurity` already gates: `requestMatchers(adminPaths).hasAuthority("ROLE_ADMIN")`
where `adminPaths` defaults to `/srv/private/admin/**`. The gateway-admin edge validates the admin
JWT and forwards `X-User-Id` + `X-User-Roles: ROLE_ADMIN` behind the machine token;
`MachineTokenUserContextFilter` promotes those to the security context. This is the SAME mechanism
that already protects `POST /srv/private/admin/search/reindex` — so no per-method `@PreAuthorize`
(which is currently inert here: `@EnableMethodSecurity` is off) and no verify-profile breakage.

**Path map (admin-api → goods-management).**

| Operation            | admin-api route       | goods-management route                 |
|----------------------|-----------------------|----------------------------------------|
| Goods CRUD/detail    | `/admin/goods/**`     | `/srv/private/admin/goods/**`          |
| Category CRUD/tree   | `/admin/category/**`  | `/srv/private/admin/category/**`       |
| Brand CRUD           | `/admin/brand/**`     | `/srv/private/admin/brand/**`          |
| Keyword CRUD         | `/admin/keyword/**`   | `/srv/private/admin/keyword/**`        |
| Comment moderation   | `/admin/comment/**`   | `/srv/private/admin/comment/**`        |
| Issue (FAQ) CRUD     | `/admin/issue/**`     | `/srv/private/admin/issue/**`          |
| Storage upload/list  | `/admin/storage/**`   | `/srv/private/admin/storage/**`        |

**Port mechanics (verbatim, db-service-backed — not a DDD rewrite).**
- Controllers → `...goods.interfaces.rest.admin`; service `AdminGoodsService` →
  `...goods.application.goods.admin`; `GoodsAllinone` → `...interfaces.rest.admin.dto`;
  `CatVo`/`CategoryVo` → `...interfaces.rest.admin.vo`.
- `@RequiresPermissionsDesc` (admin-api Shiro menu-scan) dropped; `javax.validation` → `jakarta.validation`;
  `GOODS_NAME_EXIST` (611) inlined. db services (`LitemallGoods/Category/Brand/Keyword/Comment/Issue/Storage
  Service`), core `QCodeService`/`StorageService`/`ResponseUtil`/validators all already on this module's
  classpath (litemall-db + litemall-core).
- Storage: added `litemall.storage.active=local` (+ path/address) to `application.yml` so the core
  `StorageService` bean resolves standalone (the core `StorageAutoConfiguration` is component-scanned
  here and NPEs without `active`).

**Manual verification.**
1. Boot goods-management behind gateway-admin (or with the `verify` profile for unauthenticated local
   testing — VerifySecurityConfig permits all).
2. Real-deployment auth check (no `@EnableMethodSecurity` needed — URL matcher is load-bearing):
   - No machine token / no `ROLE_ADMIN` → `GET /srv/private/admin/goods/list` returns **401/403**.
   - With machine token + `X-User-Roles: ROLE_ADMIN` (as gateway-admin forwards) → **200** with the
     goods page. Capture:
```
$ curl -s -H 'Authorization: Bearer <machine-jwt>' -H 'X-User-Id: 1' -H 'X-User-Roles: ROLE_ADMIN' \
       'http://localhost:8082/srv/private/admin/goods/list?page=1&limit=10' | jq '.errno, (.data.total)'
   <PASTE LIVE RESPONSE>
```
3. Smoke each surface: `…/category/list`, `…/brand/list`, `…/keyword/list`, `…/comment/list`,
   `…/issue/list`, `…/storage/list`, and `…/goods/catAndBrand`.

> **Live capture PENDING a booted stack** (same reason as §11). Compile is green.

**Follow-ups (NOT done here, by scope).**
- `gateway-admin` worktree: re-point `/admin/{goods,category,brand,keyword,comment,issue,storage}/**`
  to `lb://litemall-goods-management` `/srv/private/admin/**` (today the admin SPA still hits admin-api).
- Storage file-*serving* (`address` fetch URL) wiring through the admin gateway.
- Optional: delete the now-duplicated goods-domain controllers from `litemall-admin-api` once the
  gateway re-route lands.

`mvn -o -pl litemall-goods-management -am compile` clean.

## §13 — Redis-staged, DB-backed CJ indexing (re-architecture, 2026-06-16)

**Why.** The CJ path was index-only and fully in-memory: a restart lost the cache and forced a
re-hit of the 1-request/300s CJ API, and stale CJ docs could never be deleted (nothing recorded what
was indexed). Per user direction the flow is now layered:

```
CJ API (paced 1/300s) ─▶ Redis (raw /product/list payloads, TTL'd, survives restart)
                          └▶ normalize ─▶ litemall_cj_product (DB snapshot, RAW CJ pid PK)  ◀── system of record
                                          └▶ OCS litemall_index (doc id = "cj_"+pid; DB-sourced; upsert + STALE DELETE)
```

**Id strategy.** The snapshot keys on the **raw CJ pid (UUID)** so it is usable directly for CJ
detail fetch and CJ order placement; the `cj_` prefix is applied ONLY when forming the OCS document
id (`CjProductIndexingService.toDocument`) and stripped on lookup (`CjGoodsDetailService`). The
unified-search contract (`cj_<pid>` hits alongside numeric local ids, `source=cj_dropshipping`) is
unchanged, so facets/filters/sort keep working — `ProductDocument` was not touched.

**What changed.**
- New table `litemall_cj_product` (`litemall-db` V17 + undo U17) + `LitemallCjProduct` entity +
  hand-written `LitemallCjProductMapper`(.xml) + `LitemallCjProductService` (plain MyBatis,
  `map-underscore-to-camel-case`). PK = raw CJ pid `varchar(64)`; soft-delete via `deleted`.
- Redis activated: `RedisConfig` now wires a `JedisConnectionFactory` + `StringRedisTemplate` from
  `redis.server.*` (profile-overridable; `application-docker.yml` → host `redis`). New `redis` service
  in `docker-compose.yml` + `verify/docker-compose.verify.yml` (6379, healthcheck).
- New `CjRawCacheRepository`: raw CJ payloads in Redis (`cj:raw:list:<cat>:<page>`, `cj:raw:categories`,
  `cj:raw:detail:<pid>`), TTL `spring.cjdropship.redis.raw-ttl-seconds` (6h). `CJProductService`'s
  in-memory caches were repointed here; `fetchByCategory` now reads-through Redis (cached page → no
  pace/no API; miss → paced fetch + cache).
- New `CjSnapshotSyncService` (single home of CJ normalization, relocated out of the indexing service):
  fetch plan → normalize (English title, retail = wholesale×usdToCny×margin, CJ→local category map,
  default-stock variant) → `upsert` rows; diff live pids → soft-delete vanished → return removed pids.
- `CjProductIndexingService.buildDocuments()` is now **DB-sourced** (reads `litemall_cj_product`,
  maps row → `ProductDocument`, applies `cj_` prefix) — no CJ API call, no normalization.
- `CjCatalogRefreshTask` (nightly cron) = `syncAll()` → upsert snapshot to OCS → `delete(cj_<pid>)`
  for removed pids. Stale deletion is no longer deferred.
- New admin endpoint `POST /srv/private/admin/search/cj-sync` (paced, API-touching snapshot refresh);
  `POST …/reindex` stays fast/DB-only.

**Verified here.**
- `mvn -q -o -pl litemall-goods-management -am compile` clean (litemall-db built as dep).
- `LitemallCjProductMapper.xml` well-formed (`xmllint --noout`).
- Redis staging proven on the live `ocs` network: `SET cj:raw:list:TESTCAT:1 … EX 60` → `GET` returns
  the payload, `TTL` = 60 — i.e. the exact key/TTL shape `CjRawCacheRepository` uses.
- No hardcoded search/redis hosts in Java (only `@Value` defaults, profile-overridable); no SQL fallback.

**Live end-to-end capture PENDING a booted goods-management** (same infra caveats as §11/§12 — DB +
config-server + machine-token). Runbook once booted (use the `verify` profile + `docker-compose.verify.yml`):
1. `docker compose -f verify/docker-compose.verify.yml up -d` (now includes `redis`); confirm
   `docker exec ocsverify_redis redis-cli ping` → `PONG`.
2. `POST /srv/private/admin/search/cj-sync` → `{upserted, removed}`; confirm raw pages cached
   (`redis-cli KEYS 'cj:raw:list:*'`) and rows persisted (`SELECT count(*) FROM litemall_cj_product`,
   spot-check English `title`, retail `price`, JSON `category_ids`).
3. `POST /srv/private/admin/search/reindex` → `indexed` = local + live CJ rows; CJ docs visible in
   `litemall_index` with `cj_<pid>` ids and `source=cj_dropshipping`.
4. `GET /srv/search?q=<term>` → one unified ranked `goodsList` (local + cj_ hits), facets intact.
5. Stale deletion: soft-delete a row (or drop it upstream) → re-run cj-sync → its `cj_<pid>` leaves
   `litemall_index` without a full reindex.
6. Restart goods-management with Redis warm → re-index works with no CJ API call (raw pages served
   from Redis within TTL).

## §14 — Anonymous customer endpoints (litemall-wx-api parity, 2026-06-16)

**Why.** The customer SPA (gateway-api) needs the *anonymous* litemall-wx-api surface, but only the
goods/catalog bounded-context slice belongs here. Added the missing wx reads in the module's DDD
layering (thin `interfaces/rest` controller → `application/<area>` query service → `litemall-db`
service, returning the wx JSON contract); goods-core/write keeps its aggregates — these read-only
lists deliberately do not invent aggregates. Other wx contexts (auth/user/address/cart/order/
aftersale/collect/footprint/feedback/coupon/groupon/msg/storage) are OUT OF SCOPE → gateway-api +
order/user/promotion follow-ups.

**Security.** svcsecurity is deny-by-default (publicPaths permitAll → adminPaths ROLE_ADMIN → else
authenticated). Added the full customer set to `litemall.svcsecurity.public-paths` in
`config/application.yml` (`/srv/goods,brand,issue,comment,topic,home` + the pre-existing
catalog/search/cjAuth/authenticate/actuator). `/srv/private/admin/**` stays ROLE_ADMIN.

**wx-api → goods-management mapping (all anonymous):**

| wx-api | goods-management | backing |
|---|---|---|
| `/wx/brand/list` `/detail` | `/srv/brand/list` `/detail` | `BrandQueryService` → LitemallBrandService |
| `/wx/issue/list` | `/srv/issue/list` | `IssueQueryService` → LitemallIssueService |
| `/wx/comment/list` `/count` | `/srv/comment/list` `/count` | `CommentQueryService` → LitemallComment+UserService (post=follow-up) |
| `/wx/search/index` `/helper` | `/srv/search/index` `/helper` | `SearchKeywordService` → LitemallKeyword+SearchHistoryService |
| `/wx/topic/list` `/detail` `/related` | `/srv/topic/*` | `TopicQueryService` → LitemallTopic+GoodsService |
| `/wx/goods/count` | `/srv/goods/count` | LitemallGoodsServiceApi.getGoodsOnSale |
| `/wx/home/about` | `/srv/home/about` | `MallInfoProperties` (`litemall.mall.*`) |
| (already present) | `/srv/goods/*` `/srv/catalog/*` `/srv/search`(+suggest) | unchanged |

**Verified LIVE (2026-06-16).** `mvn -q -o -pl litemall-goods-management -am compile` clean. Booted
the verify-profile exec jar on :8093 against local MySQL + the OCS stack and curled every new endpoint
**with no auth header** — all returned the `{errno:0,errmsg,data}` envelope:
```
GET /srv/goods/count                                    → data: 238
GET /srv/home/about                                     → {name:litemall, address, phone, qq, lon, lat}
GET /srv/brand/list?page=1&limit=3                      → {total:49, pages:17, page:1, list:[…brands]}
GET /srv/brand/detail?id=1001000                        → {id,name,desc,picUrl,floorPrice,…}
GET /srv/issue/list?page=1&size=2                       → {total:4, pages:2, list:[…FAQ]}
GET /srv/search/index                                   → {defaultKeyword, historyKeywordList:[], hotKeywordList}
GET /srv/search/helper?keyword=a                        → []   (no keyword matches)
GET /srv/topic/list?page=1&limit=2                      → {total:20, pages:10, list:[…topics]}
GET /srv/topic/detail?id=264                            → {topic:{…}, goods:[…], userHasCollect:0}
GET /srv/topic/related?id=264                           → {list:[…]}
GET /srv/comment/count?type=0&valueId=1006002           → {allCount:27, hasPicCount:2}
GET /srv/comment/list?type=0&valueId=1006002&showType=0 → list of {addTime,content,adminContent,picList,star,userInfo:{nickName,avatarUrl}}
```
Paging totals are real (PageHelper-backed). `userHasCollect` is fixed 0 (collect = user context; follow-up).

**Boot notes for a clean standalone run here (pre-existing, NOT from this change).** The verify exec
jar needs JDK21 with `--add-opens=java.base/{java.io,java.lang,java.util,java.util.concurrent,
java.util.concurrent.locks}=ALL-UNNAMED`, and the component-scanned legacy litemall-core beans need
config or they NPE at startup: `--litemall.wx.app-id/app-secret/mch-id/mch-key`,
`--litemall.notify.mail.enable=false --litemall.notify.sms.enable=false`, and dummy
`litemall.storage.{aliyun,tencent,qiniu}.*` (StorageAutoConfiguration builds all three providers
unconditionally). The shared dev DB also has a conflicting **V17** (promotion branch) so Flyway
validation fails → run with `--spring.flyway.enabled=false` (the customer reads use existing tables,
not the CJ snapshot). The svcsecurity `public-paths` whitelist is verified by inspection (the verify
profile permits all, so it does not exercise gating).

**Out-of-scope follow-ups (other bounded contexts).** gateway-api routes + order/user/promotion
services own: auth, user, address, cart, order, aftersale, collect/favorites, footprint, feedback,
msg (user/order); coupon, groupon (promotion/order); storage download (cross-cutting). The
authenticated comment POST and real `userHasCollect`/search-history-clear also belong to user/order.

## §15 — Daily-sync Redis cleanup, DB-paged CJ list, unified browse, CJ relevance (2026-06-19)

Closes four gaps so the local+CJ catalog is fetched once/day, cleaned out of Redis after it lands in
the DB, paged from the DB (no per-page refetch), browsed through the unified OCS index, and confirmed
findable for CJ-sourced docs. Java changes compile clean via
`mvn -q -o -pl litemall-goods-management -am compile -P '!webapp'`.

### WS1 — Redis raw-list purge after a successful land
- `CjRawCacheRepository.purgeRawListKeys()` deletes the consumed `cj:raw:list:*` staging pages
  (`categories`/`detail:*` kept). `CjSnapshotSyncService.syncAll()` calls it **only when `upserted > 0`**
  and `spring.cjdropship.redis.purge-after-sync=true` (default), so a failed/empty fetch keeps the
  cache and never forces an extra 1-req/300s CJ hit.
- **Verify:** after `POST /srv/private/admin/search/cj-sync`, `redis-cli KEYS 'cj:raw:list:*'` → empty;
  `redis-cli KEYS 'cj:raw:*'` still shows `categories`/`detail:*`. Re-run with CJ auth broken → keys
  remain (no purge on a 0-upsert sync).

### WS2a — `/srv/cjAuth/productCJList` paged from the DB snapshot (the refetch fix)
- Was: `productService.fetchProductList()` (a live CJ API call) on **every** request, no paging.
- Now: `LitemallCjProductService.queryLivePaged(page,size)` + `countLive()` over `litemall_cj_product`
  (`selectLivePaged`/`countLive`, `order by add_time desc, pid asc`). No CJ API call on read.
- **Verify:** `GET /srv/cjAuth/productCJList?page=1&size=20` then `?page=2` return distinct rows with
  `{list,total,page,limit,totalPages}`; the CJ rate-limiter stays idle across page flips (no upstream
  call in logs).

### WS2b — customer browse routed through the unified OCS index
- `GET /srv/goods/list` now delegates to `SearchService.search(...)` (unified local+CJ, faceted) when
  the request is OCS-servable (`brandId`/`isNew=true`/`isHot=true` absent). `categoryId → category_ids`
  filter; `sort` maps `retail_price→price`, `name→title` (`-` prefix for desc), `add_time→` default
  order. Response keeps the legacy `list/total/page/limit/pages` shape, adds `filters` (facets) and
  `source:"ocs"`. Falls back to the local-DB listing (`source:"db"`) for brandId/isNew/isHot or on OCS
  outage. **Limitation:** `brandId`/`isNew`/`isHot`/`add_time`-sort have no indexed field, so those stay
  local-DB-only by design (no new index fields per the agreed scope).
- **Verify:** `GET /srv/goods/list?categoryId=<x>` returns `source:"ocs"`, a unified hit list including
  `cj_<pid>` ids, and a `filters` facet block; `GET /srv/goods/list?brandId=<y>` returns `source:"db"`.

### WS3 — daily "new products" visibility
- `SyncResult` now carries `inserted`/`updated` (diffed against `queryLivePids()` before upsert);
  `CjCatalogRefreshTask` logs `N new / M updated / K removed`, and `cj-sync` returns them.
- **Verify:** first sync logs all-new; a second identical sync logs `0 new / N updated / 0 removed`.

### WS4 — CJ/new-doc relevance (config already multifield; add a regression set)
The searcher `query-configuration` already weights `title`/`category_names`/`brand`/`description`
(+`.standard` stemming twins, `fuzziness: AUTO`, `CROSS_FIELDS`, `tieBreaker`). With `CROSS_FIELDS`,
CJ docs' **null `brand` does not penalize** — they still match/rank via title/category/description, so
no config change is needed for CJ findability. Guard it with a golden-query regression set spanning
both origins (catalog-targets seed CJ into Women's Clothing / Toys-Kids-Babies / Consumer Electronics):

Regression contract = each query returns at `query_stage 0` (exact, not the ngram/relaxed fallback) and
the unified list contains BOTH origins where the catalog has them. OBSERVED LIVE 2026-06-19 (700 CJ +
local in `litemall_index`): the local catalog out-ranks at #1 for these terms (legitimate relevance —
local items match the term well), with CJ products blended lower. CJ-at-#1 was an over-optimistic guess.

| # | `q=` | Observed #1 hit (id, origin) | CJ in top-6 | query_stage |
|---|------|------------------------------|-------------|-------------|
| 1 | `quilt`   | 1006014 local (mulberry silk quilt) | — | 0 |
| 2 | `luggage` | 1152101 local                        | — | 0 |
| 3 | `sofa`    | 1009024 local (Japanese lazy sofa)   | — | 0 |
| 4 | `hoodie`  | 1111007 local                        | 1 | 0 |
| 5 | `dress`   | 1046001 local                        | 4 | 0 |
| 6 | `charger` | 1021001 local                        | 5 | 0 |
| 7 | `baby`    | 1116005 local                        | 0 (lower) | 0 |

CJ hits are identifiable solely by the `cj_<pid>` id prefix; results are ONE unified ranked list (no
source-scoping). For a future change to regress this, watch for: any query dropping to stage >0, or the
CJ-in-top-6 counts for `dress`/`charger` collapsing to 0 (would mean CJ docs stopped blending).

### Verified LIVE 2026-06-19 — post-merge, new code on :8082 (ALL §15 checks green)

Ran the merged code from the MAIN checkout: `mvn -o -pl litemall-db -am install` (the shared `~/.m2`
litemall-db jar was stale → goods-mgmt couldn't see the new `queryLivePaged`/`countLive`; reinstalling
fixed the `cannot find symbol` build error), then booted the exec jar with the `verify` profile
(`--spring.profiles.active=…,verify --spring.config.additional-location=file:./litemall-goods-management/verify/`,
cloud-config/eureka OFF, local DB `root/Calliste_1006` + OCS + redis, `--spring.flyway.enabled=false`,
dummy wx/notify/storage, JDK21 `--add-opens`). config-server was DOWN, so the `verify` profile (not
config-server) supplied the datasource — that is the canonical standalone path here.

- **WS2a — VERIFIED LIVE.** `GET /srv/cjAuth/productCJList?page=1&size=3` → `{total:700, totalPages:234,
  page, limit, list}` (the DB snapshot count, NOT CJ's 1.42M catalog), English titles, and page-1 vs
  page-2 pids DISTINCT. Served from `litemall_cj_product` with no CJ API call (response instant). The
  per-page refetch is fixed.
- **WS2b — VERIFIED LIVE.** `GET /srv/goods/list?categoryId=1008000` → `source:"ocs"`, `total:9,
  pages:2`, `filters` facets `[price, category_ids, category_names, variant_price, source]`. `?brandId=…`
  → `source:"db"` (local-only fallback). Unified OCS browse works; fallback works.
- **WS1 — VERIFIED LIVE.** `POST /srv/private/admin/search/cj-sync` fetched 4 list pages into Redis then
  logged `Purged 4 raw CJ list staging keys from Redis after sync`; post-sync `cj:raw:list:* = 0` while
  `cj:raw:categories`/`detail:*` (8 keys) remained — purge fires after a successful land, correctly
  scoped to list keys.
- **WS3 — VERIFIED LIVE.** `cj-sync` returned `{upserted:700, updated:700, inserted:0, removed:0}` and
  logged `CJ snapshot sync: 0 new, 700 updated, 0 soft-deleted stale` (the 700 pids already existed →
  all updates). New/updated/removed split surfaced end-to-end.
- **Golden queries — VERIFIED LIVE.** All 7 return at `query_stage 0`; unified blend confirmed (`dress`
  4 CJ / `charger` 5 CJ in top-6). See the corrected table above.

Notes for re-running: `cj-sync` paces live CJ calls at `spring.cjdropship.fetch-pace-seconds` (default
300s → slow); the run above set `=5` (+ `refresh-on-startup=false`) so the full 700-product sync took
~21s. The OCS index is SHARED — a cj-sync from this instance updates whatever goods-mgmt instances read
`litemall_index`. **Normal (eureka-registered) operation needs config-server up**; the `verify` profile
is the standalone substitute used here.

## §16 — Real CJ inventory + variant/attribute enrichment into OCS + DB-served CJ detail (2026-06-19)

Makes CJ products first-class in OCS — real warehouse stock, real per-SKU variant prices, color/size +
material attributes, gallery — through the existing **CJ API → Redis → DB → OCS** pipeline, and serves
the CJ detail page from the DB (no live CJ call). Branch `feat/cj-inventory-enrichment`.

**Pipeline (unchanged shape, deeper data):** a paced, incremental `CjDetailEnrichmentService` takes a
capped batch of the least-recently-enriched `litemall_cj_product` rows (`enriched_time` NULL first),
per pid fetches CJ `product/query` detail (variants `vid`/`variantSellPrice`/`variantKey`, gallery,
material) + per-variant `product/stock/queryByVid` inventory THROUGH the Redis staging buffer, writes
real `variants_json` / `attributes_json` / `images_json` onto the row (mapper `enrich` — the list-sync
`upsert` no longer clobbers these), then `productIndexer.upsert(toDocument(row))`. `CjProductIndexingService`
already reads `variants_json`/`attributes_json` generically, now filtered to the
`litemall.search.facet-attributes` allow-list. On-demand trigger: `POST /srv/private/admin/search/cj-enrich?batch=N`.

**Key constraint — CJ enforces a hard 1 request/second global QPS** (`429 code 1600200 "QPS limit is
1 time/1second"`). Inventory is per-VARIANT (`queryByVid`), so a product costs 1 detail + N stock calls.
`CJProductService.getInventory` retries once after a >1s backoff on a 429; enrichment is incremental
(`enrich-batch-size`, default 20; `enrich-cron` 03:30) and converges over runs — it CANNOT enrich the
whole catalog in one shot. CJ has no real discount concept (`suggestSellPrice` is a recommendation, not
a strike-through), so `discount_price` stays null by design.

### Verified LIVE 2026-06-19 (verify-profile exec jar on :8092, flyway applied V21)
Flyway applied V21 cleanly (`validate-on-migrate=false` to skip the unrelated promotion-V17 history
checksum; recorded `now at version v21`). `POST /cj-enrich?batch=2` → `{enriched:2, failed:0}`.

- **Real variants/prices/stock — DB.** Row `2606170304541613900` → `variants_json` with **7 variants**,
  each `{vid, variant_sku, options, variant_price:57.89, stock}`; first variant `stock:7246` (REAL, ≠ the
  old flat 100). `attributes_json` `{"Material":"Cloth","Weight":"325.00"}`; `images_json` a 13-URL gallery.
- **OCS facets over CJ.** Searcher doc `cj_2606170304541613900` carries `source=cj_dropshipping`,
  `Material:Cloth`, `Weight:325.00`. Broad `GET /srv/search?q=cape` (49 hits) → facets `[price,
  category_names, category_ids, variant_price, source, Material, color, Origin]` with a real **variant_price
  price-range spread** `<29.99 / 35-59.99 / 65-89.99 / 95-319.99 / >345` and CJ Material values — price-range
  + attribute facets now span CJ, not just local.
- **DB-served detail (no live CJ call).** `GET /srv/goods/detail?id=cj_2606170304541613900` → 13 gallery
  images, 7 SKUs (`price`/`number` incl. real `7246`), `attributes [Material=Cloth, Weight=325.00]` — built
  by `CjGoodsDetailService.buildFromRow` straight from the snapshot; the live CJ `product/query` is only a
  fallback when no row exists.
- **429 retry.** A later `batch=1` run logged **0 inventory failures** and the variant landed REAL stock —
  the backoff-retry recovers the 1-QPS bursts the plain limiter let through.

### Follow-up — full detail description (V22, 2026-06-19)
The DB-served detail page showed an empty/brief description: the snapshot `description` column holds only
the CJ list `remark` (or the title fallback) and feeds OCS/search-result `brief` (kept short like local
goods' brief), while the rich full description lives only in the CJ `product/query` detail — which the
enrichment pass discarded. Fix: new `litemall_cj_product.detail_html` column (V22) that enrichment fills
from `d.getDescription()`; `CjGoodsDetailService.buildFromRow` serves it as `goods.detail` (falling back
to the brief for a not-yet-enriched row). Verified live: enriched pid `1373927926633992192` →
`detail_html` 818 chars; `GET /srv/goods/detail?id=cj_<pid>` returns `goods.detail` with the real HTML
("Product Information … Wire Core Material: Bare Copper Wire …") while `brief` stays the short category leaf.

## §17 — End-to-end runtime proof of the OCS pipeline + incremental write-path fix (2026-07-04)

Full re-verification of the indexing → search → application-surfacing path against the live
docker-compose OCS stack (elasticsearch 9200, indexer 8535, searcher 8534, suggest 8081,
kibana 5601, RabbitMQ 5672 — all healthy), plus the ONE real break found and fixed: **no REST
write path published `GoodsIndexEvent`, so incremental indexing never fired.**

### WS1 — Full reindex (DB → OCS) — VERIFIED LIVE
`POST /srv/private/admin/search/reindex` on :8082 (master code) with a machine token
(`client_credentials` gateway-admin @ authserver :8089) + `X-User-Roles: ROLE_ADMIN`:
```
{"errno":0,"data":{"indexed":943},"errmsg":"success"}     # == SELECT COUNT(*) on-sale (943)
litemall_index/_count → 943; alias litemall_index → ocs-42-litemall_index-en (full replace, new gen)
```
Nine-field spot checks (ES `_search` by `_id`):
- `1009009` (local, branded): `brand:"MUJI Manufacturer"` (resolved NAME, not id), `price:2019.0`
  vs `discount_price:1999.0` (counter/retail split correct), `category_names:["home","quilt pillow"]` +
  `category_ids:["1005000","1008008"]` (root→leaf), title/description/image_url/product_id(`_id`) all set.
- `10000516` (CJ): real 3-level chain `["Toys, Kids & Babies","Toys & Hobbies","Electronic Pets"]` /
  `["1036364","1036365","1036366"]` root→leaf; `discount_price` absent BY DESIGN (§16: CJ has no
  discount concept; OCS omits null fields from result data).

### WS2 — Search + suggest (OCS → app) — VERIFIED LIVE
```
GET /srv/search?q=quilt&page=1&size=5 → total:37, totalPages:8 (=ceil(37/5)), limit:5,
  goodsList[{id,name,brief,picUrl,retailPrice,counterPrice,brand,categoryNames,source}]
  page 1 hit: 1006014 brand:"Rollei Manufacturer" retail:1399.0 counter:14199.0
GET /srv/search?q=quilt&page=2&size=5 → distinct ids (paging → OCS offset/limit correct)
GET /srv/search/suggest?q=quil → ["quilt pillow", "Soft and cool Tencel hemp silk …", …] (phrases)
```
No SQL fallback exists on `/srv/search` (`SearchService` → `OcsSearchClient` only).

### WS3 — THE BREAK: incremental indexing had no publisher — FOUND, FIXED, VERIFIED LIVE
**Symptom.** `POST /srv/private/admin/goods/create` (the real §12 admin write path) persisted goods
`10000816` (on-sale) but the doc NEVER appeared in `litemall_index`; `goods.index.queue` stayed at
0 messages. Root cause: the event-publishing writes (`LitemallGoodsManagementServiceImpl.addGoods/
updateGoods/deleteGoods`) are reachable from NO controller, and the actual write path
(`AdminGoodsService.create/update/delete`) never published `GoodsIndexEvent`. Broker plumbing was
fine all along: exchange `appExchange` / routing key `goods.index` / queue `goods.index.queue`,
1 live consumer (`MessageConsumer`).

**Fix (this worktree).**
- `AdminGoodsService` now publishes `GoodsIndexEvent` UPSERT after create/update and DELETE after
  delete — via `TransactionSynchronization.afterCommit` (the consumer re-fetches the row by id, so a
  mid-transaction publish could be consumed before commit and a create would degrade to DELETE).
- `MessageConsumer` UPSERT now removes missing **or off-sale** goods from the index (reindexAll only
  ever indexes on-sale — same invariant, now enforced incrementally).

**Verified live** (worktree exec jar, verify profile, :8092; events consumed from the shared queue):
```
create OCS-INCR-TEST-002 → id 10000817 → doc appears in litemall_index WITHOUT reindex,
  all fields correct (brand resolved, category chain, price 222.0 / discount_price 199.0)
update 10000816 (title "... UPDATED") → doc APPEARS (was missing from the index — pre-fix debt)
  with the updated title
update 10000817 isOnSale=false → consumer log:
  "UPSERT for missing/off-sale goods id=10000817, removing from index" + doc deleted
delete 10000816, 10000817 → both docs hits:0; DB rows deleted=1; index count back to 938 == on-sale 938
```
**Mixed-version rollout note:** while an OLD-code instance shares `goods.index.queue`, an off-sale
update consumed by the old consumer re-upserts the doc (old behavior). Deploy order: merge + restart
every goods-management instance, then off-sale removal is deterministic.

### WS4 — Legacy duplicate reindex path — RETIRED
`POST /srv/admin/goods/reindex` (old `OcsIndexerClient`+`OcsGoodsDocumentMapper`, guessed non-session
contract, brand/category unresolved — 3+2 TODOs) returned `{"errno":502}` against the real indexer
and failed cleanly (no partial import; the two extra index generations observed today were the CJ
startup refresh on :8082, not this endpoint). The controller now delegates to the same
`SearchReindexService.reindexAll()` as `/srv/private/admin/search/reindex`; the three legacy ACL
classes are DELETED. Verified live on :8092: `POST /srv/admin/goods/reindex → {"indexed":938}`,
alias swung to `ocs-44-litemall_index-en`, `_count` 938 == on-sale 938.

### WS5 — Customer-path surfacing — VERIFIED (read-only)
`/srv/search` item shape == `/srv/goods/list` item shape, field-for-field:
`{id,name,brief,picUrl,retailPrice,counterPrice,brand,categoryNames,source}` — the OCS search path
is drop-in for the SQL/browse listing before data reaches the customer. Envelope differs by design
(`goodsList`/`totalPages`/`sortOptions` vs `list`/`pages`; browse already reports `source:"ocs"`).
No gateway-api change required for shape compatibility.

### WS6 — Guarded integration checks — 3/3 GREEN against the live stack
- `OcsSearchRoundTripVerificationTest` (2 tests): nine-field round-trip + suggest phrases. Updated
  for the post-CJ index: `discount_price` asserted via a `sort=-discount_price` search (CJ docs have
  none), `brand` proven via result data on a branded doc found by paging match-all hits (the searcher
  ranks/caps displayed facets, so brand-as-facet is no longer a stable assertion).
- **NEW** `OcsIncrementalIndexVerificationTest` (1 test): PUT one synthetic doc through the exact
  incremental contract (`PUT /indexer-api/v1/update/litemall_index`, `{"id","data":{nine fields}}`) →
  polls the searcher until searchable → DELETE → polls until gone; cleans up in `finally`.
  Both `assumeTrue`-skip when OCS is down (CI-safe). Run recipe unchanged from §6 (temp pom tweak:
  drop `-XX:MaxPermSize`, drop `debugForkedProcess`), plus `-Docs.indexer-url=http://localhost:8535`:
  `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0` (observed 2026-07-04).

### WS7 — Config hygiene
- No hardcoded search hosts/ports in main Java — all via `litemall.search.*` (`LitemallSearchProperties`).
- NEW `config/application-docker.yml`: `docker` profile overrides to compose service names
  (`http://indexer:8535`, `http://searcher:8534`, `http://suggest:8081`, rabbit `rabbitmq`) — the
  override the `LitemallSearchProperties` javadoc always claimed existed.

**Boot recipe** for the :8092 verify run — §15/§16 flags plus the full dummy-storage set:
`--litemall.storage.{aliyun,tencent,qiniu}.*=x` (all four keys each; StorageAutoConfiguration
instantiates every provider), `--spring.cjdropship.refresh-on-startup=false` (else a paced CJ list
refresh fires ~25 min after boot and runs its own reindex — this is what produced the two extra
index generations during today's session).

---

## §18 — Full-path re-verification against the live stack (2026-07-05)

Executed from the `fix/goods-management` worktree (branch even with master, HEAD `bb5d0f20a`)
against the running compose stack; goods-management on :8082 from the MAIN checkout, classes
built 2026-07-04 14:04 (post-`cbf3c7ffb`), JVM started 23:46.

**Stack (step 1).** es :9200 cluster `yellow` (single node — normal), indexer :8535 `{"status":"UP"}`,
searcher :8534 `UP`, suggest :8081 answers on `/suggest-api` (no actuator — expected), kibana :5601,
rabbit :5672 all up. Machine token: `client_credentials` `gateway-admin` @ authserver :8089.

**Full reindex (step 2).**
```
POST /srv/private/admin/search/reindex  (Bearer machine-jwt + X-User-Roles: ROLE_ADMIN)
-> {"errno":0,"data":{"indexed":938},"errmsg":"success"}
SELECT COUNT(*) FROM litemall_goods WHERE is_on_sale=1 AND deleted=0 -> 938
litemall_index/_count -> 938 ; alias flipped ocs-45 -> ocs-46-litemall_index-en (full replace, new gen)
```
Nine-field spot checks: `1009009` (local) — `price:2019.0` vs `discount_price:1999.0`
(= DB counter/retail), `brand:"MUJI Manufacturer"` (= litemall_brand 1001000 NAME),
`category_names:["home","quilt pillow"]` + `category_ids:["1005000","1008008"]` root→leaf
(= litemall_category pid chain), title/description/image_url set, `product_id` rides as `_id`.
`10000516` (CJ, source=`cj`) — real 3-level chain `["Toys, Kids & Babies","Toys & Hobbies",
"Electronic Pets"]` / `["1036364","1036365","1036366"]` root→leaf; `discount_price` absent BY
DESIGN (CJ counter==retail); `brand` absent (brand_id=0).

**Search + suggest (step 3).** Contract is `page`/`size` (the §2 `offset`/`limit` shape is the
OLD master contract, superseded by the faceted-search work):
```
GET /srv/search?q=silk&page=1&size=5 -> data{total:62, totalPages:13, page:1, limit:5,
    goodsList:[{id,name,brief,picUrl,retailPrice,counterPrice,brand,categoryNames,source}...],
    filters[price/category_ids/category_names/variant_price/source], sortOptions, queryStrategy}
page=2 -> disjoint ids (paging real); sort=price -> [9.22, 9.5, 9.79, 9.94, 10.66] ascending
GET /srv/search/suggest?q=quilt -> {"errno":0,"data":["quilt pillow", ...]} (phrases, live)
```
No SQL fallback on the path (`SearchService` → `OcsSearchClient` only).

**Incremental (step 4).** Create → update → delete one goods (`goods_sn VERIFY-INC-20260705`,
id `10000821`) via `POST /srv/private/admin/goods/{create,update,delete}`; alias stayed on
`ocs-46` throughout (NO reindex):
- create → doc `10000821` visible in `ocs-46` ≤8 s, `_count` 938→939, brand/category resolved;
- update (name) → `title:"...UPDATED"` refreshed in place ≤8 s. Note: posted `retailPrice:88`
  but DB kept `99.00` — AdminGoodsService recomputes retail from product rows (litemall-standard),
  index mirrors DB faithfully → NOT a defect;
- delete → `found:false`, `_count` back to 938. Proves `GoodsIndexEvent` → Rabbit
  `MessageConsumer` → `upsert/delete` end-to-end (after-commit publish).

**Customer surface (step 5).** `/srv/goods/list` item keys == `/srv/search` item keys
(`brand,brief,categoryNames,counterPrice,id,name,picUrl,retailPrice,source`) — search output is
drop-in for the SQL listing. Gateway/SPA wiring unchanged (already live per gateway-api worktree).

**Acceptance re-runs.** `mvn -q -o -pl litemall-goods-management -am compile -P'!webapp'` clean
(the bare `compile` fails ONLY in the `webapp` profile's `npm run build`, exit 127 = npm not on
the plugin's PATH — pre-existing env issue, not Java). Hardcoded-host grep over `src/main/java`:
only the boot banner, javadoc, and `redis.server.*` defaults — no OCS hosts/ports. Guarded tests
vs live stack: `OcsSearchRoundTripVerificationTest` (2) + `OcsIncrementalIndexVerificationTest` (1)
→ `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0` (temp pom tweak per §6, reverted).

**Verdict: every task step green at runtime; no code defect found, no code change needed.**

---

## §19 — Per-leaf CJ catalog fill + derived category images + count-ranked catalog (2026-07-05)

**Task.** Every CJ L1 tree had goods in only its FIRST leaf (target `limit` = shared budget), 11/14
trees empty; CJ categories imageless; SPA needed a home sidebar tree + /search browse tree.

**Backend changes** (merged to master: `e0f875f01` per-leaf fill + images + ranking; catalog-all L1
page-size fix; `promote resurrection fix`):
- `CatalogTarget.perLeafLimit` / `CjFetchRequest.Target.perLeafLimit`: each resolved leaf gets its
  own budget; `limit` becomes an optional overall cap (absent = uncapped in per-leaf mode).
- `application.yml`: `fetch-pace-seconds` 300→3; `catalog-targets` = 3 legacy deep targets + 14
  `per-leaf-limit: 10` L1 targets (NIGHTLY-PRUNE INVARIANT documented); `category-mapping` RETIRED
  (it diverted mirrored leaves to native buckets ahead of tree resolution).
- `CategoryImageBackfillService`: blank `icon_url`/`pic_url` filled depth-first from a
  representative on-sale goods image; runs after every promote cycle; never overwrites curated art.
- `GET /srv/catalog/all`: L1 page size 10→100 (the old cap HID every CJ tree), `categoryList`
  ordered by subtree on-sale count (PageHelper total, 5-min memo), new `goodsCounts` map.
- **Resurrection fix** (`LitemallCjLinkageMapper.findAnyGoodsIdByCjPid` + `promoteOne` sets
  `deleted=false`): a full sync's stale-prune soft-deletes goods; the old `deleted=0`-only lookup
  made re-promotes collide with `uk_goods_source_cjpid` (`DuplicateKeyException`, 255 stuck rows).

**Fill run** (14 × `POST /srv/private/admin/search/cj-fetch {"targets":[{"category":"<L1>",
"perLeafLimit":10}]}` — targeted runs are ADDITIVE/prune-free). Ran on a private
`--server.port=8092` boot (no eureka registration, devtools off, `refresh-on-startup=false`)
after concurrent-session interference twice killed the :8082 JVM mid-run — and the :8082
instance's own startup refresh (full sync + prune) raced the fill and soft-deleted 2,255
just-landed goods, which is exactly what surfaced the resurrection bug. Final converged state:

```
on_sale 5275 == litemall_index/_count 5275 ; cj_on_sale 5037 ; unpromoted 0 ; promoteFailed 0
CJ leaves with goods: 443/540 (97 leaves return nothing from CJ /product/list — upstream sparse)
per-L1: Women's 731, Toys 550, Electronics 537, Autos 390, Sports 382, Jewelry 353, Computer 310,
        Phones 305, Bags&Shoes 300, HomeImprovement 280, Pets 269, Men's 220, HomeGarden 210, Health 200
images: every populated L1/L2 has pic_url+icon_url (only the empty legacy "Imported" L1 blank)
search: q=pumps → 20 hits w/ real 3-level chains; /srv/search/category/1036342 (Pumps) → 10 hits,
        breadcrumb Bags & Shoes → Women's Shoes → Pumps; suggest live after harvest
guarded tests: 3 run, 0 fail (1 skip = documented suggest cold-harvest window right after reindex)
```

**SPA** (fix/gateway-api `0380695cb`, merged `5f2aae6cb`): Home hero menu → top-10 count-ordered
L1s w/ images + mobile "All categories" toggle (hover flyout kept); /search rail → new
`CatalogTreeNav` (expandable L1→L2, links `/category/:id`) above the query-scoped category facet.

**Follow-ups (not done here):**
- Legacy empty "Imported" bucket (cats 1036007–1036011, all verified 0 goods/0 children) still
  shadows 4 mirrored leaf names for NAME-based resolution (UUID path unaffected). Soft-delete the
  five categories, then the last blank L1 disappears too. (Blocked by permissions this session.)
- 97 CJ leaves upstream-empty; re-check occasionally or hide zero-count leaves in the SPA.
- Concurrent-session hygiene: only ONE goods-management instance should run a startup/cron full
  sync at a time — a second instance's prune races any in-flight targeted fill.

---

## §20 — Cold-start re-proof of the full OCS path + §19 follow-up closure (2026-07-06)

**Starting state.** The ENTIRE runtime stack was down and every OCS container had been *removed*
(`docker ps -a` showed only a dead `litemall-redis-verify`); only host MySQL :3306 was alive. The
`docker-compose_ocs_esdata` volume survived, so `litemall_index` came back with the containers
(alias `ocs-79`, 3,708 docs). The shared DB had also moved since §19: a full-sync prune had
soft-deleted ~1,567 CJ goods (on-sale 5,275 → 3,708; leaves-with-goods 443 → 286).

**Stack (step 1).** Recreated from THIS worktree's `docker-compose/`:
`docker compose -f docker-compose.yml -f docker-compose.local.yml up -d elasticsearch indexer
searcher suggest kibana rabbitmq redis` (keycloak skipped — locked no-Keycloak architecture).
es :9200 `yellow` (single node), indexer :8535 `UP` (+ `application-custom.yml` mount carrying the
nine-field `litemall_index` config verified inside the container), searcher :8534 `UP`, suggest
:8081, kibana :5601, rabbit :5672/15672, redis PONG. authserver :8089 (jar) + goods-management
:8082 (main-checkout exec jar, Jul-5 build incl. the §19 resurrection fix) booted standalone.
Machine token: `client_credentials` `gateway-admin` @ :8089.

> **BOOT GOTCHA found & fixed (the one real break this session).** First boot used
> `--spring.profiles.active=verify` ONLY — §16's recipe says `…,verify` and the `…` matters: it is
> the module default `dev,db,core,admin,wx`. Without the `db` profile, litemall-db's
> `application-db.yml` (`pagehelper.reasonable: true`) never loads, and every repository call site
> that passes **page 0** to `PageHelper.startPage(pageNum, size)` (e.g.
> `LitemallCatalogRepositoryImpl.queryL1(0,100)`) silently returns an EMPTY page — PageHelper skips
> the row select when startRow==endRow==0. Symptoms observed live: `/srv/catalog/first-categories`
> → `l1CatList: []` and `/srv/catalog/all` → `errno 502`
> (`IndexOutOfBoundsException` at `LitemallCatalogController.queryAll` `l1CatList.get(0)`).
> Fixes shipped here: (1) `verify/application-verify.yml` now carries the `pagehelper` block so a
> verify-only boot is self-sufficient; (2) `LitemallCatalogController.queryAll` guards the empty
> catalog (`isEmpty() ? null : get(0)` — the code's own `null != currentCategory` check downstream
> always intended that) so an empty catalog returns an empty payload instead of 502. Search/reindex/
> incremental were unaffected (their callers pass real page numbers). Canonical boot restated:
> `--spring.profiles.active=dev,db,core,admin,wx,verify` + §16/§17 flags.

**Full reindex (step 2).**
```
POST /srv/private/admin/search/reindex (Bearer machine-jwt + X-User-Roles: ROLE_ADMIN)
-> {"errno":0,"data":{"indexed":3708},"errmsg":"success"}
SELECT COUNT(*) WHERE is_on_sale=1 AND deleted=0 -> 3708 ; _count -> 3708 ; alias ocs-79 -> ocs-80
```
Nine-field spot checks: `1009009` (local) — `price:2019.0`/`discount_price:1999.0`,
`brand:"MUJI Manufacturer"`, `category_names:["home","quilt pillow"]` +
`category_ids:["1005000","1008008"]` root→leaf, title/description/image_url set, `product_id`
as `_id`. `10000000` (CJ) — 3-level chain `["Consumer Electronics","Accessories & Parts",
"Digital Cables"]` / `["1036453","1036454","1036455"]`, `discount_price`/`brand` absent BY DESIGN,
CJ attribute facets (`Material`, `Weight`) riding along.

**Search + suggest (step 3).**
```
GET /srv/search?q=silk&page=1&size=5 -> total 99, totalPages 20, item keys
    {id,name,brief,picUrl,retailPrice,counterPrice,brand,categoryNames,source},
    filters [price, category_ids, category_names, variant_price, source]
page=2 -> disjoint ids ; sort=price -> [8.64, 9.22, 9.5, 9.79, 9.94] ascending
GET /srv/search/suggest?q=quilt -> phrases (live ~20s after reindex — harvest window)
```

**Incremental (step 4).** `goods_sn VERIFY-INC-20260706` → id `10006816` via
`/srv/private/admin/goods/{create,update,delete}`; alias stayed `ocs-80` throughout:
create → doc visible ≤2 s (brand/category resolved, price 222.0 / discount_price 199.0);
update → `title "... UPDATED"` in place ≤4 s; delete → `found:false` ≤2 s, `_count` back to 3708
(the momentary 3709 right after delete is ES near-real-time refresh lag, settles <5 s).

**Customer surface (step 5).** `/srv/goods/list` item keys == `/srv/search` item keys
(`brand,brief,categoryNames,counterPrice,id,name,picUrl,retailPrice,source`), `source:"ocs"`.

**Acceptance re-runs.** `mvn -q -o -pl litemall-goods-management -am compile -P'!webapp'` clean;
guarded tests vs live stack `OcsSearchRoundTripVerificationTest` (2) +
`OcsIncrementalIndexVerificationTest` (1) → `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`
(temp pom tweak per §6, reverted); hardcoded-host grep over `src/main/java` clean.

**Follow-up A — "Imported" bucket RETIRED.** Re-verified cats 1036007–1036011 all 0 goods
(L1 `Imported` + empty L2s `Digital Cables`/`Scarves & Wraps`/`Electronic Pets`/`Men's Sleep &
Lounge`), then soft-deleted all five via the existing `POST /srv/private/admin/category/delete`
(children first). After: `/srv/catalog/all` → 23 L1s, count-ranked, NO `Imported`, every L1 carries
`picUrl`; each of the 4 previously-shadowed names now resolves to exactly ONE live category — the
mirrored CJ leaf (1036455, 1036014, 1036366, 1036282). NAME-based CJ resolution is unambiguous.

**Follow-up B — the 97 upstream-empty leaves are now 0: 540/540 leaves populated.** Re-ran the 14
per-leaf targets (`POST /srv/private/admin/search/cj-fetch {"targets":[{"category":"<L1>",
"perLeafLimit":10}]}`, additive/prune-free), which also had to RECOVER from a live reproduction of
the §19 interference scenario:

> **Concurrent-session interference, round 2 (23:22).** Another session booted the full platform
> from the MAIN checkout mid-fill — including a second goods-management on **:18082** with default
> config, whose one-shot startup CJ refresh (full sync + STALE-PRUNE) fired ~25 min after its boot,
> exactly per the §17 note. Because the in-flight targeted fill held CJ's 1-QPS budget, the
> refresh's own list fetches 429'd en masse, its "seen" set collapsed, and its global stale-prune
> soft-deleted 1,020 `litemall_cj_product` rows (+ their goods mirrors) — including whole
> just-filled trees. The targeted path itself is provably prune-free (`pruneStale=false`, every run
> logged `0 soft-deleted stale`); the deletes were `softDeleteByPids` from the OTHER instance's
> full sync. Recovery: re-ran the 5 damaged targets after the refresh settled — the §19
> resurrection fix re-promoted everything (0 promoteFailed throughout). The §19 hygiene rule
> stands, now with the mechanism spelled out: a starved full sync prunes what a concurrent fill
> just landed. Consider `refresh-on-startup=false` as the default posture on dev boxes.

> **Reindex paging bug found & FIXED (the second real break).** After the final fill,
> `reindex → {"indexed":6246}` but `_count` stuck at 6229 — 17 docs short on every fresh
> generation, no indexer/ES errors, last batch "46 of 46 converted". Root cause:
> `SearchReindexService.reindexAll` paged `querySelective(..., "add_time", "desc")` — add_time is
> NON-UNIQUE (bulk promotes stamp hundreds of rows in the same second), MySQL tie order is
> arbitrary per query, so page boundaries read 17 tied rows twice and skipped 17 others (the
> missing block was exactly the newest ids 10007141–10007157). Fix: page by the unique PK
> (`"id","asc"`); order is irrelevant for a full replace. Verified live on the worktree exec jar
> (:8092, same stack): `{"indexed":6246}` → `_count` **6246** on `ocs-102`, all 17 ids `found:true`.
> NOTE: the main-checkout :8082 jar predates this fix — rebuild/restart from main after the merge
> (do NOT overwrite main's target jar while another session's instance runs from it).

**Final converged state (2026-07-07 00:2x):**
```
on_sale 6246 == litemall_index/_count 6246 (post-fix, ocs-102) ; cj_on_sale 6008 ; unpromoted 0
CJ leaves with goods: 540/540 — the §19 "97 upstream-empty" set is GONE (CJ now returns products
  for every mirrored leaf; targeted fills are idempotent to re-run)
per-L1: Women's 730, Pets 664, Toys 550, Electronics 537, Men's 420, HomeGarden 399, Health 390,
        Autos 390, Sports 382, Jewelry 348, Computer 311, Phones 306, Bags&Shoes 301, HomeImpr 280
search: q=pumps → 24 hits ; /srv/search/category/1036342 (Pumps) → 10 hits,
        breadcrumb Bags & Shoes → Women's Shoes → Pumps ; /srv/catalog/all → 23 L1s count-ranked,
        every L1 with picUrl, no Imported bucket
```

**Follow-ups (not done here):**
- Rebuild + restart the main-checkout goods-management (:8082/:18082) once this merge lands so the
  reindex paging fix serves live (coordinate the jar swap with any session running from main).
- SPA zero-count leaf hiding is moot while 540/540 are populated; `goodsCounts` on `/srv/catalog/all`
  already carries the data if it recurs (gateway-api concern).

---

## §21 — Engagement verticals: collect / footprint / feedback / comment-post (2026-07-07)

New Wave-2 task (CLAUDE.md): the customer SPA already calls `/srv/collect/**`, `/srv/footprint/**`,
`/srv/feedback/**`, `/srv/comment/post` behind an `isMissingEndpoint` guard; the backends did not
exist. Built them over the legacy tables (`litemall_collect`/`footprint`/`feedback`/`comment`) — no
new migrations. Identity is the gateway-injected `X-User-Id` on every customer endpoint (cart-IDOR
rule); a numeric goods id OR `cj_<pid>` reference is accepted and resolved to the promoted native
goods row via `LitemallCjLinkageMapper.findGoodsIdByCjPid` (new `EngagementGoodsResolver`).

**New code.** `application/engagement/{EngagementGoodsResolver,CollectService,FootprintService,
FeedbackService}`, `application/comment/CommentPostService`, controllers
`interfaces/rest/{LitemallCollectController,LitemallFootprintController,LitemallFeedbackController}`
+ `POST /post` on the existing `LitemallCommentController`, admin `interfaces/rest/admin/
AdminEngagementController` (`/srv/private/admin/{collect,footprint,feedback}/list`). Added
`UserContext.getUserIdAsInt()`. Purchase check for reviews: v1 trusts the authenticated user
(no order facade in goods-management — order-worktree follow-up); documented in
`docs/handoff-engagement-endpoints.md`.

**Verified LIVE** — worktree exec jar on `:8093` (`dummy storage` + `refresh-on-startup=false`,
profile `dev,db,core,admin,wx,verify`), shared MySQL + OCS stack, as `user123` (id 1):
```
unauth (no X-User-Id) GET /srv/collect/list           -> {errno:501,"Please log in"}
collect add 1009009 -> collected:true ; add cj_04A66F1D... -> resolved valueId 10000516, collected:true
collect list        -> 2 rows goods-enriched {id,type,valueId,name,brief,picUrl,retailPrice}, total 2
collect toggle 1009009 -> collected:false ; list -> total 1 (only 10000516)  [toggle semantics]
footprint record 1009009 + cj_04A66F1D... + 1009009-again -> list total 2  [same-day dedupe holds]
footprint delete id 3 (mine) -> ok ; delete id 3 again -> 402
feedback submit {content,type,mobile} -> ok ; missing content -> 402
comment post 1009009 star5 -> {id:1015} ; post cj_04A66F1D... star4 -> {id:1016} ; star9 -> 402
GET /srv/comment/list valueId=1009009  -> total 31, my star-5 row on top w/ userInfo.nickName user123
GET /srv/comment/list valueId=10000516 -> total 1, my CJ review (local rows take precedence over CJ proxy)
admin feedback/list -> total 1 (the submitted row) ; admin collect/footprint/list -> counts + userId filter
```
**Owner scoping proven.** `X-User-Id:2` → `GET /srv/collect/list` total 0 (user1's rows hidden);
`POST /srv/footprint/delete {id:4}` (user1's row) as user2 → 402 (`findById(userId,id)` returns null).

**Shape parity.** Cross-checked every path/body/param/response field against the SPA call sites
(`userApi.ts`) and the gateway-api handoff spec — zero drift (`docs/handoff-engagement-endpoints.md`).

**Security posture.** `/srv/{collect,footprint,feedback}/**` are authenticated (NOT added to
`svcsecurity.public-paths`). `/srv/comment/**` stays public for anonymous review *reads*; `/post`
enforces its own `X-User-Id` login check (no id → 501), and the edge strips client `X-User-*` so a
spoofed header can't attribute a review.

**Acceptance re-runs.** `mvn -q -o -pl litemall-goods-management -am compile -P'!webapp'` clean.
Test-data left in the shared dev DB is benign (user123's own favorites/footprint/feedback + two
real reviews on 1009009 / 10000516).

**Follow-up (not done here):** `order` worktree — expose a "user U purchased goods G" query so a
later revision can set a real `hasPurchased` on posted reviews (v1 leaves it false/untracked).

---

## §22 — Relevance boosting: price · popularity · recency · reviews for discovery (2026-07-08)

New task: boost product relevance by price, popularity and reviews across BOTH local and CJ, and
surface it as SuperDeals (popular), New Arrivals (recent+affordable) and All Products / navbar
Products (boosted browse). Grounded in *Relevant Search* ch. 7 — signal modeling (§5.1/§7.4.2),
sqrt/log-damped general-quality metric (§7.4.6), combine-by-multiplication (§7.4.8) — mapped onto
OCS's existing `scoring-configuration` (which already multiplies in `stock`).

**Signals + storage (V31).** `litemall_goods` gains `listed_num`/`review_count`/`rating`;
`litemall_cj_product` gains those plus `cj_create_time`/`reviews_synced_time`. Recency rides
`litemall_goods.add_time` (CJ rows: set to CJ createTime when present). Domain fields hand-added to
`LitemallGoods`/`LitemallCjProduct` (+ Goods BaseResultMap/Base_Column_List read mappings); writes go
through a dedicated `LitemallCjLinkageMapper.updateGoodsRankingSignals` (COALESCE, so a null preserves)
rather than the generated insert/update.

**Acquisition — ~0 new CJ calls.** The CJ list endpoint is V1 `/product/list`; detail is
`/product/query`. `listedNum` + `createTime` ride the detail response the existing per-product
enrichment (`CjDetailEnrichmentService.enrichOne`, nightly `enrich-cron`, quota-aware) already
fetches — previously discarded, now persisted. The CJ review aggregate (`productComments` `total` =
count, avg `score` = rating) is folded into the SAME paced loop (shares the 1-QPS limiter + Redis
cache), so no separate sweep. Local review aggregate (`litemall_comment`) is written onto goods by
`RankingSignalService` — incrementally on `POST /srv/comment/post` and in bulk via
`POST /srv/private/admin/search/refresh-signals`. Promote copies the four CJ aggregates onto the
goods row.

**Indexing.** `ProductDocument` + `createProductDocument` emit `listed_num`/`review_count`/`rating`
(0 when absent — NOT null) and `created_epoch` (add_time millis). `application.indexer-service.yml`
adds the four as `number` with `Result,Sort,Score`, and excludes them from the dynamic-fields
catch-all. OCS places master-level Score fields in the doc `scores` block (verified:
`scores:{rating,review_count,listed_num,stock,created_epoch}`) + `sortData`.

**Scoring (`application.search-service.yml`).** Added `field_value_factor` on `rating`,
`review_count`, `listed_num`, all `MODIFIER: ln2p` (= ln(2+x)), multiplied alongside the existing
`stock` (ln1p). ln2p CHOSEN over sqrt/ln1p because score-mode multiply would let those hit 0 and
zero a product; ln2p floors every no-signal product at a UNIFORM ~0.69 (local goods legitimately
carry listed_num 0) — no product zeroed, no source structurally buried, each rises only on the
signals it has (rating≈1.95@5★, review_count≈4.6@100, listed_num≈3.1@20). Recency drives the New
Arrivals rail via `-created_epoch` sort; a global recency *decay* is a follow-up (needs a decay
function, not field_value_factor).

**Discovery (`DiscoveryService`).** SuperDeals = empty browse `sort=-listed_num`; New Arrivals =
empty browse `sort=-created_epoch` + `price=0,<affordable-max>` (config
`litemall.discovery.affordable-max-price`, default 100). `LitemallGoodsController.index()` now sources
`hotGoodsList`←SuperDeals and `newGoodsList`←New Arrivals (same goodsList DTO the SPA rails already
render — no SPA change), with the DB is_new/is_hot lists as an OCS-down fallback. All Products / navbar
Products keep `/srv/search` (default scoring = the global boost).

**Verified LIVE** — worktree exec jar :8093, OCS stack (indexer+searcher recreated from THIS
worktree's docker-compose), shared MySQL:
```
V31 applied (Flyway "31 - ranking signals"); refresh-signals -> {localGoodsUpdated:32}
cj-enrich-one pid 2607070501241636200 -> cj_product{listed_num:1, review_count:15, rating:4.9}
   -> goods 10007645{listed_num:1, review_count:15, rating:4.9}  (promote copy verified)
reindex -> {indexed:3708}; ES _doc 10007645 scores{rating:4.9, review_count:15, listed_num:1,
   created_epoch:1783452964000}; local 1009009 scores{review_count:31, rating:1.1, listed_num:0}
All Products browse (q=, default scoring) -> reviewed/popular lifted to top:
   10007645(cj,rev15) , 1181000(local,rev97), 1009009(rev31), 1006007(rev30)...  (was doc-id order)
SuperDeals (-listed_num) -> 10007645 first, then reviewed local via score tie-break
New Arrivals (-created_epoch, price<=100) -> newest CJ, all <=100 (78.77, 66.1, 50.69, 45.22...)
```

> **SEARCHER-RESTART GOTCHA (the one real snag).** OCS's ScoringCreator resolves a scoring field
> against the CURRENT index field-config at searcher startup. Recreating the searcher BEFORE the
> reindex that first writes the new fields → `WARN ScoringCreator - Field rating for scoring does not
> exist. Will ignore scoring function`, and the boost silently no-ops (browse stays doc-id order).
> Fix: reindex FIRST (creates the fields in the index), THEN restart the searcher so it re-reads the
> field-config. Canonical order for a scoring-field change: edit indexer yml -> recreate indexer ->
> reindex -> edit searcher yml -> restart searcher -> verify no "does not exist" warning.

**Acceptance re-runs.** `mvn -q -o -pl litemall-goods-management -am compile -P'!webapp'` clean;
hardcoded-host grep over src/main/java clean; V31 applies on the shared dev DB.

**Follow-ups (not done here):**
- `cj_create_time` comes back null from `/product/query` (CJ's detail `createTime` is often null).
  Recency falls back to our `add_time` (promote/ingest time — still monotonic for "newness"). To get
  CJ's TRUE creation date, switch the list sync to the V2 endpoint `/product/list` V2 (§1.2) which
  returns `createAt` (ms) — a larger change, raised not done.
- Price-value is carried implicitly (SuperDeals sorts by listed_num; browse boost has no explicit
  price term — the book cautions against naive price boosts). A price-value tie-break / composite
  deal_score field is a tuning follow-up.
- Global recency decay for the All Products browse needs an OCS decay function (gauss), not
  field_value_factor — deferred; New Arrivals already covers recency via sort.
- Rebuild + restart the main-checkout goods-management (:8082) after merge so :8082 serves this
  (its jar predates V31 + the new code); litemall-db jar in ~/.m2 was refreshed by this build.

## §23 — Test-driven relevance: judgment replay + deals assertions + zero-results telemetry (2026-07-15)

Deals Phase D (Relevant Search §9.3–§9.6, §10.6–§10.8). Three standing tools, all committed:

**1. Judgment replay** — `verify/relevance/judgments.csv` (12 queries × top-8 graded 0–3; seeded
from litemall_keyword + §15 golden queries; search-history was empty in dev at seed time) +
`RelevanceJudgmentVerificationTest` (guarded live test, same skip-when-down pattern as the §6
round-trip tests). Computes nDCG@10 per query against the OCS searcher (:8534 direct) and asserts
the MEAN doesn't drop more than 0.05 below `verify/relevance/baseline.properties`.
Baseline pinned 2026-07-15: **mean-ndcg=0.9286** (per-query: dress 1.00, charger .95, pillow 1.00,
sunglasses 1.00, flat shoes .90, summer quilt .98, memory foam pillow .74, phone case .83,
backpack .90, kitchen .97, dog toy 1.00, silk quilt .88). Run after EVERY scoring/query-config
change (hand-run — surefire's jdwp fork is still broken):

```
mvn -q -o -pl litemall-goods-management test-compile -Dmaven.test.skip=false
# assemble platform 1.10.0 console+launcher jars + module test cp, then:
java -cp "…/target/test-classes:…/target/classes:<platform-1.10 jars>:<test cp>" \
  org.junit.platform.console.ConsoleLauncher execute \
  -c org.linlinjava.litemall.goods.search.RelevanceJudgmentVerificationTest \
  -c org.linlinjava.litemall.goods.search.OcsDealsVerificationTest
```

Re-pin the baseline ONLY for a deliberate judged improvement, never to green a red run. Regrade
judgments from real `litemall_search_history` rows once traffic accumulates; grades live in the
CSV precisely so a stakeholder can edit them without touching code.

**2. Deals assertions** — `OcsDealsVerificationTest`: deal_flag=1 hits all carry deal_flag=1 AND
discount_pct ≥ threshold; `discount_pct=25,50` closed range respected (bounds inclusive);
`sort=-discount_pct` monotonic; exact-full-title query ranks its product top-3 (top-3 not top-1:
multiplicative business boosts may promote another all-terms match — an exact title pushed off
the first row means text lost control, ch.-7 failure mode). All 4 verified green live 2026-07-15.

**3. Zero-results telemetry** — `SearchService` WARNs `zero-results search: q='…' filters=…` when
a NON-EMPTY query returns 0 hits (empty-q browses with narrow filters are not relevance failures).
Harvest into querqy-rules.txt synonyms / judgment-list additions.

**Tuning protocol (standing, Relevant Search §9.3.5):** one knob at a time — (1) tune text weights
with business functions zeroed; (2) each business signal tuned against a zeroed field, then
restored; (3) judgment replay is the scoreboard; (4) `explain=true` on the searcher for surprises.
Diminishing-returns rule: past ~93% "perfect", corner-case fixes make search MORE brittle — stop.

**Ops note (2026-07-15):** the whole OCS compose stack was externally `docker compose down`'d
mid-session (all containers destroyed ~13:06); `ocs_esdata` survives a plain down — brought back
up from this worktree's compose dir, index generation ocs-123 intact, deal fields verified
present. If containers are missing, check `docker events` before assuming a crash.

## §24 — Flash deals (price-swap lifecycle) + co-occurrence related items (2026-07-16)

Deals Phases B + C. Design: `docs/adr-flash-deals-price-swap.md` (B),
`docs/handoff-gateway-admin-flash-deals.md` (admin contract). B reuses the V9
`litemall_seckill` table; V38 (applied 2026-07-15) adds `original_retail_price` +
`price_swapped` and creates `litemall_goods_related`. The deal IS the goods row's
`retail_price` while live — `FlashDealLifecycleTask` (60s tick, `litemall.deals.tick-ms`;
kill switch `litemall.deals.lifecycle-enabled`) is the only writer. C fills
`litemall_goods_related` nightly (`RelatedGoodsRefreshTask`, 02:45,
`litemall.recommend.refresh-cron` / `refresh-enabled`; manual
`POST /srv/private/admin/recommend/rebuild`) with a co-purchase(×3)+co-view(×1, 90-day)
blend, top 8; `/srv/goods/related` serves the row in stored order (hydration drops
off-sale) and tops up / falls back to the legacy same-category query (now self-excluding).

**THE gotcha this session — OCS resolves filter/sort/score fields against the index
MAPPING, not just the yml field config.** First full reindex ran with no live deal →
no doc carried `deal_active`/`deal_urgency`/`deal_end_epoch` → searcher logged
`ScoringCreator - Field deal_urgency ... does not exist` and SILENTLY ignored the
`deal_active=1` filter (returned all 9042 docs). Fix shipped: the three queryable deal
fields are emitted on EVERY document (`deal_active:0`, `deal_urgency:0`,
`deal_end_epoch:4102444800000` = 2100-01-01 sentinel so "ending soon" asc keeps live
deals first); Result-only `deal_claimed_pct` stays live-only. §22's restart order still
applies for NEW fields: indexer yml → recreate indexer → reindex → searcher restart.

**Verified LIVE 2026-07-16** — worktree exec jar :8093 (profiles dev,db,core,admin,wx,verify,
flyway off, tick 15s), OCS indexer+searcher recreated from THIS worktree's compose
(previous mounts pointed at the MAIN checkout — the §20 gotcha again), full reindex →
ocs-125 (9042 docs), searcher restart → zero ScoringCreator warnings:
```
create 650s: CJ goods → 652; price ≥ retail → 650 (msg names both); past window → 650
create ok → id 2 {enabled:true, live:false}; overlapping create → 651
tick ≤15s: goods 1009012 retail 59→41.30 (counter 79 kept — no lift needed),
   seckill{price_swapped:1, original_retail_price:59}; ES resultData
   {deal_active:1, deal_end_epoch, deal_claimed_pct:0} + scores{deal_urgency:100, discount_pct:48}
/srv/search?deal_flag=1&deal_active=1 → total 1, DTO {dealActive,dealEndEpoch,dealClaimedPct}
sort=deal_end_epoch → live deal FIRST, sentinel docs after; facet rail unchanged (no deal noise)
live edit price → 651 "disable it before changing price or start"; stop extension → ok
/srv/goods/deal?id=1009012 → {dealPrice,originalPrice,endEpoch,stock,claimed,claimedPct};
   dealless/CJ id → data:null
disable → unwind ≤15s: retail 59.00/counter 79.00 byte-identical, price_swapped:0,
   ES back to deal_active:0 + sentinel, deal_active=1 search → 0; delete → total drops
claimed refresh: sales stayed 0 (correct — no paid orders in window); sumPaidQuantityInWindow
   shape sanity-checked vs manual GROUP BY (goods 1055022 → 13). Sold-out early-unwind NOT
   exercised e2e (no orderable checkout path this session) — unwind() itself proven via disable.
related: rebuild → {rows:15}; blend for 1010000 == manual UNION query (ties → id asc);
   /srv/goods/related?id=1010000 → stored order top 6; id without row → category fallback,
   self-excluded; cj_* → []; aggregate envelope byte-compatible (nested goodsId{id} as before)
harness (§23, platform-1.10 console+launcher+ENGINE+COMMONS jars — mixing 1.10 launcher
   with the module's 1.9.3 platform-engine dies mid-run on TestDescriptor.getAncestors):
   5/5 green, mean nDCG 0.9286 == pinned baseline (always-emit urgency 0 = uniform ln2p, no shift)
regression: /srv/search?q=pillow total 236 + filters rail unchanged; suggest live (empty
   result mid-harvest is the ~30s SuggestionsUpdater window, retry); /srv/goods/index rails 6/6/3
SPA: gateway-api tsc touched-files clean + prod webpack clean; gateway-admin tsc app-code
   clean (6 node_modules NoInfer errors pre-existing) + vite prod bundle built via module mvn
   (root reactor does NOT contain the gateways — build from litemall-gateway-admin/).
   `webapp:test` phase fails PRE-EXISTING: web-test-runner glob src/**/*.test.js matches zero
   files — nothing to do with deals; skip test phase or fix upstream.
```

**Landmines re-confirmed:** `mvn -q ... | tail; echo EXIT=$?` reports TAIL's exit — a
litemall-db install "succeeded" that way while ~/.m2 kept a jul-13 jar (package then failed
`cannot find symbol LitemallGoodsRelatedService`). Use `${PIPESTATUS[0]}` or drop -q and
grep "Installing". In-browser SPA click-through happens post-merge from main (gateways not
booted here) — countdown chip / claimed bar / DealBanner / admin Deal CRUD are tsc+bundle
verified only.
