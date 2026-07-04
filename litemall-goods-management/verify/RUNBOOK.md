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
