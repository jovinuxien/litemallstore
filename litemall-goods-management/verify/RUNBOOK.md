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
