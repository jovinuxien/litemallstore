# CLAUDE.md — litemall Per-Worktree Tasks

## Per-worktree tasks

> Active fix branches launched via `open-fix-worktrees.sh`. Each Claude session
> opens in `../litemall-wt/<short>` on branch `fix/<short>` and is told to read
> the matching `### Worktree: <short>` block below as its sole task. Edit the
> Task / Acceptance lines to redirect a worktree.
>
> **History:** Wave 2 (coupons, groupon, aftersale, engagement), Wave 3
> (CJ API parity, createOrderV2 lifecycle, tracking, admin panels), Wave 4
> (legacy wx-api/admin-api decommission + crmeb verticals + account
> self-service), the goods deals wave (flash-deal lifecycle + related items),
> Wave 5 (affiliate program), Wave 6 (social promotion + mail outbox +
> tracker), and **Wave 7 (production readiness: server-authoritative money
> path, real Stripe verification, edge auth, docker prod stack, TLS, CI)**
> are fully merged to master; their specs live in git history.
> **PRODUCTION IS LIVE at https://trovemo.com** (Hetzner VPS, 19-container
> compose stack, Cloudflare-proxied edge, Caddy DNS-01). Post-launch
> hardening — edge caching, RabbitMQ container limits, legacy-goods
> deactivation + on-sale enforcement (`doc/legacy-goods-deactivation-
> 2026-07-20.pdf`) — is merged and deployed.
>
> **Wave 8** (CJ fulfilment walk + CJ product reviews + Trovemo branding on
> both SPAs) is merged and DEPLOYED to trovemo.com (2026-07-22, `b6906aac2`);
> spec in git history. Flyway **V44** was consumed by it.
>
> **Wave 9 (2026-07-24) — SEARCH EXPOSURE: surface the dormant OCS features.**
> An audit (claims re-verified against master 2026-07-24) found seven
> FINISHED backend search capabilities the customer SPA never calls or
> renders. Backend anchors: `LitemallSearchController.java`
> (goods-management, `@RequestMapping("/srv/search")`), `SearchService.java`.
> SPA anchors: `app/modules/search/Search.tsx`, `app/Layout.tsx` (header
> search bar), `ProductHit.tsx`.
> 1. `GET /srv/search/index` (default + hot keywords + per-user history from
>    `litemall_search_history`) and **POST** `/srv/search/clearhistory` —
>    zero SPA references.
> 2. `GET /srv/search/helper` (curated keywords) — uncalled; SPA autocomplete
>    uses only `/srv/search/suggest` (Layout.tsx:67, 200ms debounce).
> 3. Response fields `queryStrategy` + `relaxed` (SearchService.java:92-93)
>    — the backend says when it fell back to fuzzy/relaxed matching; SPA
>    never renders a "no exact matches — showing similar results" line.
> 4. Response `sortOptions` (SearchService.java:90) — Search.tsx:46 hardcodes
>    `SORT_ITEMS` instead. (`searchSlice.ts:60-91` already parses
>    `sortOptions` into redux; nothing reads it — dead path, revive it.)
> 5. OCS highlighting — `search_products.sh` passes `highlight=true`, but
>    `OcsSearchClient.java` never requests it and ProductHit renders no
>    snippets.
> 6. Suggest harvests `category_names` (SUGGEST_INDEX_DEFAULT_SOURCEFIELDS),
>    but every suggestion click routes to `/search?q=` — the category
>    landing (`GET /srv/search/category/{id}` + SPA `/category/:id` with
>    breadcrumb + scoped facets, CategoryTree.tsx) exists and is never
>    deep-linked from suggestions.
> 7. Zero results render a dead-end empty grid (Search.tsx:250, no empty
>    state) while the backend already logs zero-result queries
>    (SearchService.java:80-82) and `/srv/search/index` has the "try these
>    instead" data.
>
> **Wave 9 is MERGED + DEPLOYED to trovemo.com** (2026-07-25;
> goods-management `c78118743`, gateway-api `d085062c7`). Spec +
> cross-module contract live in git history (`abc2d4b72`).
>
> **Wave 9.1 (2026-07-25) — STOREFRONT TRUST SURFACES: social links + help
> center + customer-service FAQ.** gateway-api only. Audit facts
> (2026-07-25): the footer social icons (`Layout.tsx:496-501` — facebook,
> instagram, twitter-x, youtube) are DECORATIVE `<i>` glyphs with NO anchors
> and no TikTok; `/help` (`app/modules/static/Help.tsx`) has only 5
> hardcoded Q&As; `/service` (`CustomerService.tsx`) shows a FAKE phone
> `+1 (800) 000-0000` next to the real `support@trovemo.com`;
> `app/views/userViews/pages/ContactUs.tsx` is an UNROUTED scaffold (submit
> = `alert()`, address literally "Twitter, Inc.") — dead code. The clean
> config seam for social URLs is `/auth/site-config`
> (`SiteConfigController.java` `@Value` bindings → `siteConfig.ts` observable
> store — the proven Matomo/Stripe pattern). User-confirmed facts: Facebook
> page = `https://www.facebook.com/trovemo` (live now); Instagram/TikTok
> pages DO NOT EXIST YET — their icons must stay hidden until an env var is
> set, activation must need NO rebuild (env change + container recreate
> only); escalation contact = `support@trovemo.com`.
> **Wave 9.1 is MERGED + DEPLOYED** (2026-07-25, `3989e2053`).
>
> **Wave 10 (2026-07-26) — ORDER CONFIRMATION EMAIL: enrich + light up the
> dormant Wave-6 customer-mail pipeline.** User ask: when a customer's
> order is paid, they receive an email with all the order information.
> Audit facts (re-verified against master 2026-07-26): the Wave-6 outbox
> pipeline EXISTS end-to-end — V41 `litemall_mail_outbox` (plain-text body,
> `send_at`), `CustomerMailEnqueueListener` (order) enqueues on
> `LitemallOrderPaidEvent`, `MailOutboxSweepScheduler` sweeps every 60s
> (max 5 attempts) — but it is DARK: `litemall.customer-mail.enabled`
> defaults false, no yml or compose file anywhere turns it on, and the
> existing `MailTemplates.orderConfirmation` body is orderSn + total ONLY
> (no line items, no amounts breakdown, no delivery info). Recipient is
> `litemall_user.email` (optional at registration; blank ⇒ silent skip).
> Dev SMTP sink: MailHog in `docker-compose.marketing.yml` (SMTP :1025,
> UI :8025). Details + anchors in the `order` worktree block below.
> Wave 10 runs in the `order` worktree IN PARALLEL with Wave 11 below.
>
> **Wave 11 (2026-07-26) — CJ-SOURCED HOMEPAGE BANNERS matched to the live
> category mix.** User ask: stop serving local/seed banners; fetch banner
> imagery from CJ dropshipping matching the store's current product
> categories. Audit facts (2026-07-26, dev DB + master): today's banners
> are 3 `litemall_ad` position=1 rows pointing at plain-HTTP
> `yanxuan.nosdn.127.net` (upstream seed CDN — third-party, mixed-content
> broken on the HTTPS storefront) with `link=''` (dead clicks). Serving
> path: `GET /srv/goods/index` `banner` key ← goods-management
> `LitemallGoodsController.index()` (:101-177) → `LitemallAdService
> .queryIndex()` (litemall-db :19-23 — position=1 + enabled + not-deleted,
> NO ordering/time-window/limit). SPA: `Home.tsx:186-205` react-bootstrap
> `<Carousel>` with `<a href={banner.link || '#'}>` full-page nav; empty
> list ⇒ blank 340px spacer. **CJ's APIs carry NO category/banner art**
> (`CategoryImageBackfillService.java:15-18` — which already derives
> category images from goods pics; the proven pattern). Usable imagery:
> ~9.6k on-sale CJ hero images on `cf.`/`oss-cf.cjdropshipping.com`, both
> covered by the `/_cdn` edge rewrite (`CjImageUrlRewriteFilter`, Caddyfile
> `/_cdn/cf/*` + `/_cdn/oss/*`); yanxuan + aliyuncs hosts are NOT covered.
> 14 CJ L1 roots have on-sale goods (Women's Clothing 1300 … Computer &
> Office 353); the 9 legacy yanxuan L1s have 0. `/category/:id` landing
> exists (Wave 9) — the natural banner click target.
> **Cross-module CONTRACT (both worktrees code to THIS, not to each
> other's branches):** `banner[]` in `/srv/goods/index` keeps the existing
> `LitemallAd` field shape. Semantics: `name` = category display name;
> `url` = CJ-hosted image (edge rewrite turns it into `/_cdn/...`);
> `link` = SPA-RELATIVE path `/category/<L1 id>`; `content` = optional
> short subtitle (e.g. "1,300 products"). Links starting with `/` are
> internal SPA routes. Manual admin-created banners keep working. worktrees merge to master as always. **Deployment to the
> VPS is done by the MAIN session after merge** (docker build + recreate +
> reindex where needed) — do NOT touch the production VPS or its DB from a
> worktree. Verify in dev through the gateways.
>
> **Wave 11 is MERGED + DEPLOYED to trovemo.com** (2026-07-26, master
> `308082607`, both halves).
>
> **Wave 12 (2026-07-28) — CJ INVENTORY INTELLIGENCE: cost capture +
> 1.25-margin repricing, arrival tracking, deal proposals, scheduled
> category campaigns (Spring Integration).** User asks: know the
> flow/flux of CJ inventory to (1) price by real margins, (2) judge from
> provider stock whether/how long a product can be advertised, (3)
> classify daily new arrivals into deal tiers, (4) track each product
> from its arrival date with nightly availability re-checks, and
> schedule category product campaigns on our media platforms. Backend
> flows follow Spring Integration patterns (Fisher et al., *Spring
> Integration in Action*: gateway ch5, transformer/enricher ch5,
> router/filter ch6, splitter/aggregator ch7, wire tap ch14, pollers
> ch15); the flow's entry point is data landed by
> `CjSnapshotSyncService`. Audit facts (2026-07-27, dev DB + master):
> - CJ raw cost (`CJProduct.sellPrice`, USD) is DISCARDED at sync:
>   `CjSnapshotSyncService.toRow()` (:194-220) persists only
>   `sellPrice × usdToCny 7.2 × margin 2.0` (a flat ×14.4;
>   `retailPrice()` :252-267); same per-variant in
>   `CjDetailEnrichmentService.retail()` (:225-233). Store
>   `retail_price` == snapshot `price` for all 9,644 CJ goods (avg
>   $260.91 retail for avg ≈$18 cost).
> - **USER DECISIONS (2026-07-28): new formula `retail = cjCost × 1.25`**
>   (drop the ×7.2 currency leftover entirely) — a DELIBERATE storewide
>   ~91% repricing at the first sync after deploy; deal candidates are
>   PROPOSED and admin-APPROVED, never auto-created; campaign scheduling
>   ships now and degrades honestly while Meta/TikTok tokens are absent.
> - `litemall_goods.cost` / `litemall_goods_product.cost` exist since V2
>   but are UNMAPPED and never written — the natural landing spot.
>   Enriched `variants_json` already carries real per-variant
>   `variant_price` + `stock`; `cj_create_time` is empty for all rows ⇒
>   "arrival date" = our `add_time`. No sync-history table exists.
> - `FlashDealService` REFUSES CJ goods (errno 652). V40
>   (`original_sku_prices` per-SKU swap JSON) IS applied — the parked
>   CJ-deals piece is CODE, ref impl `a82a19e0e` in git history.
> - CJ client hard rate limit: 1 req/s global + 3s-paced /product/list.
>   Nightly availability MUST ride the existing 03:00 `syncAll()`
>   (`SyncResult.livePids`/`removedPids`) plus targeted
>   `getInventory(vid)` for deal/tracked goods ONLY. NO live CJ calls on
>   request paths.
> - spring-integration is NOT on goods-management's classpath (only the
>   legacy analytic module has spring-integration-file). Add
>   `spring-boot-starter-integration` (Boot 3.1.6 BOM, Java 21) and use
>   the Java DSL (`IntegrationFlow` beans — the book's XML maps 1:1).
> **Wave-12 CONTRACT (gateway-admin codes to THIS, not to other
> branches):** goods-management serves `/srv/private/admin/insight/**`
> (the gateway-admin `/srv/**` catch-all already routes there — no yml
> change). Money = plain decimals; errno envelope as everywhere; margin
> fields are `null` (never 0) when cost is not yet captured.
> - `GET /insight/categories` → `{list:[{categoryId, name, onSaleCount,
>   newArrivals7d, stockUnits, lowStockCount, unavailableCount,
>   avgMarginPct, potentialProfit}]}`, sorted potentialProfit desc, L1
>   roots with on-sale goods only.
> - `GET /insight/goods/list?categoryId&sort=add_time|retail_price|
>   margin_pct|stock|sales&order=asc|desc&page&limit` → standard page
>   envelope; items = goods summary + `{cost, marginAmount, marginPct,
>   stockTotal, cjAvailable, arrivalDate, salesQty, dealStatus}`;
>   default sort `add_time desc`.
> - `GET /insight/goods/{id}` → `{goods, variants:[{productId, cjVid,
>   specifications, price, cost, stock, available}], series:[{day,
>   retailPrice, cost, marginPct, stockTotal, available, views,
>   salesQty}], totals:{views, salesQty, revenue, collects, comments},
>   deals:[...], recommendation:{suggestedRetail, marginPct,
>   advertisable, reasons[]}}`.
> - `GET /insight/deal-candidates?day=` → `{list:[{goodsId, name,
>   picUrl, day, tier, score, cost, retailPrice, suggestedDealPrice,
>   stockTotal, rating, status, reasons[]}]}`;
>   `POST /insight/deal-candidates/{goodsId}/approve` body `{dealPrice,
>   startTime, stopTime, stock}` creates the flash deal (CJ-unparked
>   path; deal price must never go below cost);
>   `POST /insight/deal-candidates/{goodsId}/dismiss`.
> - Promotion: `POST /srv/private/admin/promotion/campaign/from-category`
>   body `{categoryL1Id, name?, schedule:{start, stop}, platforms[],
>   goodsIds?}` → creates the campaign row + per-platform
>   `litemall_social_post` drafts; response = campaign id + post ids +
>   per-platform status (honest disabled/failed while tokens absent).
>
> **Wave 12 STATUS:** goods-management backend + gateway-admin insight
> UI are MERGED + DEPLOYED to trovemo.com (2026-07-28, master
> `68ebcbb38`; repricing LIVE — costs land via the sync/enrichment
> rotation, ~3.5k goods costed on day one, the rest reprice as rotation
> covers them). The `promotion` worktree is still IN FLIGHT on its
> Wave-12 block below — do not reassign it.
>
> **Wave 13 (2026-07-28) — SEO FOUNDATION: crawlable product pages,
> slugged URLs, sitemap + robots.** User ask: register trovemo.com in
> Google Search Console and do SEO ("referencing") properly. Audit
> facts (2026-07-28): URLs are already crawl-friendly real paths
> (`/product/10000553` — NOT hash-routing; keep that). The REAL gap:
> the SPA shell is empty HTML — `SpaHistoryFallbackFilter`
> (gateway-api web/, :49) rewrites every deep-link navigation to
> `/index.html` (webapp/public/index.html), so social crawlers
> (Facebook/WhatsApp/Twitter run NO JS) see no title/image/price, and
> Google only gets meta after deferred JS rendering (Googlebot DOES
> render JS — pages are not invisible today; this wave makes them
> first-class). NO robots.txt and NO sitemap exist anywhere in the
> repo. litemall has no slug concept — derive from `name`, NO new DB
> column. Approach: server-side HEAD INJECTION at the existing
> fallback seam (the gateway is WebFlux — reactive, cached,
> fail-open), NOT full SSR/prerender infra.
> **Wave-13 CONTRACT (both worktrees code to THIS):**
> - Slug rules (shared, deterministic): `slug(name)` = lowercase,
>   ASCII-fold, non-alphanumeric → `-`, collapse repeats, trim, max 80
>   chars. Canonical product URL = `/product/<id>-<slug>`; parser =
>   leading digits of the segment; bare `/product/<id>` stays valid
>   forever; the canonical tag always points at the slugged form.
> - `GET /srv/goods/meta/{id}` (goods-management, public read, local
>   tables only): `{id, name, brief, picUrl, retailPrice, currency,
>   onSale, rating, reviewCount, categoryId, categoryName, updateTime}`
>   — `updateTime` is an ISO-8601 STRING (beware: Wave-12 made
>   goods-management serialize LocalDateTime as ARRAYS module-wide;
>   this field must be an explicit string). Missing goods ⇒ errno
>   envelope, never 5xx.
> - `GET /srv/goods/sitemap.xml` (goods-management, public read): valid
>   sitemaps.org XML — homepage, on-sale category landings
>   (`/category/<id>`), every on-sale product at its SLUGGED URL,
>   `lastmod` = update_time (W3C format). Absolute URLs from config
>   `litemall.public-base-url` (env `LITEMALL_PUBLIC_BASE_URL`, default
>   `https://trovemo.com`). Regenerated after the nightly catalog
>   refresh + cached (no per-request DB sweep). ≈9.6k URLs today =
>   single file; if it ever nears the 50k limit, split into a sitemap
>   index — never truncate silently.
> - Edge (gateway-api) serves `/sitemap.xml` (proxy route to the goods
>   endpoint) and a static `/robots.txt` (`Allow: /`; `Disallow:` for
>   `/user`, `/checkout`, `/cart`; `Sitemap:` absolute URL).
>   Dot-containing last segments already bypass the SPA fallback.
> - Head injection serves IDENTICAL HTML to bots and humans (no UA
>   cloaking — Google penalizes it); ANY meta-fetch failure ⇒ plain
>   shell, 200, fail-open.
> User-side: Search Console "Domain property" for trovemo.com (DNS TXT
> in Cloudflare) can be registered NOW — no sitemap needed to start;
> after deploy, URL-Inspect a PDP ("View tested page" must show the
> injected head). Main-session deploy check: Cloudflare must NOT cache
> PDP HTML across products (today it caches only /_cdn + hashed
> bundles — re-verify).
>
> **USER-SIDE PREREQUISITES:** Stripe TEST keys are LIVE in prod (card pay
> verified e2e); `CJ_CATALOG_*` (goods-management catalog/enrichment creds)
> is LIVE; order-side `CJ_API_KEY` is still EMPTY — live CJ order placement
> stays blocked by design. Everything must degrade honestly: typed errors,
> retryable states (an order paid today must be placeable at CJ tomorrow
> when the key arrives), never a fake success, never a 5xx.
>
> **Cross-cutting landmines (apply to every block):**
> - **Flyway:** V44 is the last applied (dev `flyway_schema_history`
>   re-verified 2026-07-27; numbering contiguous V1–V44). V40 IS applied
>   (2026-07-16, "deal sku swap and cj suggest") — the old "V40 stays
>   earmarked" note is OBSOLETE; the parked CJ-deals piece is code, not
>   a migration. Wave-12's single migration (goods-management) claims
>   **V45** after checking `flyway_schema_history` immediately before
>   first boot; every other worktree expects NONE. `out-of-order: true`
>   is permanent. Never `flyway repair`.
> - **litemall-db is shared and hand-maintained:** never regenerate; hand-edit
>   entities + mapper XMLs together. After editing: `mvn install` litemall-db,
>   restart EVERY dependent, verify the nested `BOOT-INF/lib` copy in running
>   exec jars; concurrent `-am` builds overwrite `~/.m2`.
> - **`andLogicalDeleted()` is INVERTED across 33 domain classes** — both enum
>   constants evaluate `false`, so `andLogicalDeleted(false)` returns only
>   DELETED rows. **Do not call it.** Bind the literal, as
>   `LitemallAftersaleRepositoryImpl:45-60` does.
> - **litemall-core is shared:** install/restart-all-dependents discipline;
>   enable core-read config via ENV VARS (core profile yml outranks service
>   yml).
> - **svcsecurity is deny-by-default**; the Stripe webhook is the ONLY
>   anonymous service path (signature-gated). Don't add more.
> - **Never rebuild a jar under a running JVM** (hung statics). Kill first.
> - Verify live through the gateways (`:9000`/`:9001`→`:8090`, `:18080`) —
>   machine-token ~10-min TTL makes direct service curls flaky.
> - **On-sale is enforced** since 2026-07-20 at cart-add + submit
>   (order `LitemallGoodsFacadeImpl` maps `onSale`; missing field ⇒ true).
>   Off-sale goods must stay viewable but unbuyable — don't weaken this.

### Worktree: `order` — history (Wave 10, SHIPPED)
- **Task — Wave 10: rich order-confirmation email once an order is paid.**
  (Merged + deployed 2026-07-26, `77c55e027` — DARK until SMTP creds
  land; activation = SMTP env in `.env.prod` + container recreate, no
  rebuild, keep 1 replica. Spec in git history.)
- **No Wave-12 assignment.** Do not start work here without a new
  instruction.

### Worktree: `goods-management`
- **Branch:** `fix/goods-management` — FIRST: `git merge master`. ·
  **Scope:** `litemall-goods-management/` only. NO migration expected
  (V45 was consumed by Wave 12; if one becomes truly necessary, claim
  V46+ after checking `flyway_schema_history`).
- **History:** Wave 12 CJ inventory intelligence SHIPPED (2026-07-28,
  master `68ebcbb38`, repricing live); Wave 11 banners SHIPPED
  (`308082607`). Specs in git history.
- **Task — Wave 13 (backend): product meta + sitemap serving.** Serve
  the Wave-13 CONTRACT in the preamble note.
  1. **`GET /srv/goods/meta/{id}`:** slim, cache-friendly (in-memory
     TTL ~5 min is fine), local tables only; `updateTime` explicitly an
     ISO-8601 string (the module now serializes LocalDateTime as arrays
     — this contract field must not); missing/deleted goods ⇒ errno
     envelope. `currency` = what the storefront actually charges —
     verify against the order/Stripe config, do not guess.
  2. **Sitemap generation:** builder walks on-sale goods (id, name →
     contract slug, update_time) + on-sale category landings;
     regenerate after the nightly `CjCatalogRefreshTask` chain
     completes (hook alongside the Wave-12 flow notification) and
     lazily-if-stale on first read; serve cached bytes at
     `GET /srv/goods/sitemap.xml` (application/xml). Base URL from
     `litemall.public-base-url` (env `LITEMALL_PUBLIC_BASE_URL`).
  3. **Honesty:** off-sale products excluded from the sitemap but
     `/meta/{id}` stays serveable for them (crawlers may still visit;
     PDPs stay viewable-unbuyable). No live CJ calls in this wave.
  4. **Tests:** slugger edge cases (unicode, symbols, length cap),
     sitemap XML validity + only-on-sale + slugged absolute URLs +
     lastmod format, meta ISO date + null-cost-safe fields. Mind the
     surefire/@Nested "Tests run:" gotcha.
- **Acceptance:** through `:9000` (public `/srv/goods/**` reads already
  route): `/srv/goods/meta/<id>` returns the contract JSON with a
  STRING updateTime; `/srv/goods/sitemap.xml` validates (xmllint),
  lists only on-sale products at slugged absolute URLs plus `/` and
  category landings; regenerates after a catalog refresh; module tests
  green with real "Tests run" counts; no migration.

### Worktree: `gateway-api`
- **Branch:** `fix/gateway-api` — FIRST: `git merge master`. ·
  **Scope:** `litemall-gateway-api/` (edge Java + customer SPA). NO
  migration, no new anonymous DOWNSTREAM service paths (`/srv/goods/**`
  public reads already exist). Code to the Wave-13 CONTRACT.
- **History:** Wave 11 banner SPA SHIPPED (master `308082607`); Wave
  9.1 trust surfaces SHIPPED (`3989e2053`). Specs in git history.
- **Task — Wave 13 (edge + SPA): crawlable PDPs, slugged URLs, robots +
  sitemap.**
  1. **Head injection at the fallback seam:** for SPA navigations
     matching `/product/<id>[-slug]` (and `/category/<id>`), serve the
     shell with an injected head: `<title>`, meta description (brief),
     `<link rel=canonical>` (slugged absolute URL), OpenGraph
     (og:title/description/image/url, og:type=product — og:image must
     be an ABSOLUTE `https://…/_cdn/...` URL), twitter:card, and
     JSON-LD `Product` (name, image, description, productID, offers:
     price + priceCurrency + availability from onSale, aggregateRating
     only when reviewCount > 0). Implementation: REACTIVE — the
     gateway is WebFlux, no blocking; meta fetched from
     `/srv/goods/meta/{id}` via WebClient, short timeout (~300ms) +
     small TTL cache; ANY failure ⇒ plain shell, 200, fail-open.
     IDENTICAL HTML for bots and humans. While in there, give the
     default shell (webapp/public/index.html) a real site
     title/description as the sitewide fallback.
  2. **Slug routing:** the `product/:id` route (App.tsx:98) accepts
     `<id>-<slug>` (parse leading digits); catalog surfaces emit
     slugged hrefs (ProductHit, related items, home/category grids;
     cart/order internals may stay bare-id — canonical absorbs the
     difference). Client keeps setting document.title on navigation.
  3. **robots.txt + sitemap route:** static `/robots.txt` per CONTRACT;
     edge route proxies `/sitemap.xml` → goods-management
     `/srv/goods/sitemap.xml`.
  4. **Regression:** PDP deep-link + refresh on slugged AND bare URLs,
     search/category/cart/checkout green through `:9000`; the `:9001`
     dev-server flow must not break.
- **Acceptance:** through `:9000`: `curl -H "Accept: text/html"` on
  `/product/<id>` AND its slugged form returns HTML whose head has the
  real product name, OG tags, canonical slugged URL, and JSON-LD that
  passes a validator; goods service stopped ⇒ PDP navigation still
  serves the shell 200 within the timeout; `/robots.txt` and
  `/sitemap.xml` serve through the edge; SPA build clean; regression
  list green.

### Worktree: `gateway-api` — history (Wave 9.1, SHIPPED)
- **Task — Wave 9.1: storefront trust surfaces (social links, help center,
  customer-service FAQ).** (Merged + deployed 2026-07-25, `3989e2053`.)

### Worktree: `gateway-admin`
- **Branch:** `fix/gateway-admin` — FIRST: `git merge master`. ·
  **Scope:** `litemall-gateway-admin/` SPA only — no edge controllers,
  no yml route changes (the `/srv/**` catch-all already reaches
  goods-management; `/srv/private/admin/promotion/**` already routes to
  promotion). NO migration. Code to the Wave-12 CONTRACT, not to other
  branches.
- **Task — Wave 12 (admin UI): goods-by-category insight navigation +
  per-product decision page.**
  1. **Nav** (`menu.config.ts` goods group :75-86): add wired leaves
     "Goods by category" `/admin/goods/categories` and "Deal proposals"
     `/admin/goods/deal-candidates`; hidden leaves
     `/admin/goods/categories/:id` and `/admin/goods/:id/insight`. Keep
     the flat "Goods list" untouched. Routes in `admin-routes.tsx` —
     static segments BEFORE `:id` routes (comment at :91).
  2. **Views** (`adminModule/Insight/`, reuse `_shared/crudUi.tsx` +
     el-table/el-pagination conventions): `CategoryInsightList` — table
     ranked by potentialProfit desc (name, on-sale, new arrivals 7d,
     stock, low-stock, unavailable, avg margin %, potential profit); row
     → category goods page; plus a "Launch category campaign" dialog
     (POST `/campaign/from-category` per CONTRACT, rendering the honest
     per-platform disabled/failed statuses). `CategoryGoodsList` —
     DEFAULT sort = arrival date (`add_time desc`) with a sort box: date
     (default) | price | stock availability | margin | sales, asc/desc,
     paginated; row → insight page. `GoodsInsight`
     (`/admin/goods/:id/insight`) — chart.js Line series (StatPage.tsx
     pattern): price/cost/margin%, stock + availability, views/sales;
     totals cards; per-variant table (price, cost, stock, available);
     recommendation/deal panel with Approve-deal + Dismiss actions; link
     to the existing Wave-6 `PromoteComposerDialog` for social
     advertising. `DealCandidateList` — today's proposals with
     tier/score/reasons + approve/dismiss.
  3. **API:** new RTK Query `insightApi.ts` (baseUrl
     `/srv/private/admin/insight`, `adminGoodsApi.ts` pattern: Bearer +
     envelope unwrap); campaign-from-category on the existing promotion
     API slice; register the new api in BOTH `shared/reducers/index.ts`
     and `config/store.ts`.
- **Acceptance:** SPA build clean; through `:18080` — new nav entries
  render real payloads; category list ranked by potential profit;
  category goods page defaults to newest-arrival and every sort option
  round-trips server-side; insight page renders charts + variants +
  recommendation with no console errors; approving a candidate creates a
  deal visible in the existing deals panel; the campaign dialog creates
  campaign + drafts with honest statuses; existing Goods list/CRUD and
  all other panels regression-green.

### Worktree: `platform`
- **No Wave-8 assignment.** The Wave-7 stack is merged and DEPLOYED (prod compose,
  TLS via Caddy DNS-01 behind Cloudflare, CI). Do not start work here without
  a new instruction.
- **Informational:** the caddy image is a custom build
  (`docker/caddy/Dockerfile`, cloudflare DNS module); `CADDY_ACME_DNS` is
  env-injected — an empty value must keep staging bootable (see Caddyfile
  comment). CI's gitleaks job was RED on master per
  `docs/handoff-secrets-wave7.md` — fix belongs here if picked up later.

### Worktree: `promotion`
- **Branch:** `fix/promotion` — FIRST: `git merge master`. · **Scope:**
  `litemall-promotion-service/` only. NO migration, no new anonymous
  paths, fail-soft ACL discipline (Meta/TikTok/Mautic stay
  disabled-by-default; enabling later = env + container recreate, NO
  rebuild).
- **Task — Wave 12: scheduled category campaigns on media platforms.**
  1. **Campaign scheduler:** new
     `infrastructure/scheduling/CampaignScheduleTick` (@Scheduled
     fixedDelay `litemall.promotion.campaign.tick-ms:60000`,
     enabled-guard + try/catch-swallow like `PromotionExpirySweeper`):
     activate due draft campaigns whose schedule start has arrived
     (`activateCampaign` + `evaluateCampaign`), mark past-end active
     campaigns done. Today evaluation is admin-triggered only — keep the
     admin endpoints working unchanged.
  2. **Category campaign composer:**
     `POST /srv/private/admin/promotion/campaign/from-category` per the
     Wave-12 CONTRACT — target goods = the category's live-deal goods
     via the existing `SocialCatalogAdapter` (fallback: explicit
     goodsIds), campaign row linked to the deal mechanic, plus
     per-platform `litemall_social_post` drafts via the existing
     composer seams. Publishing honest-degrades (adapters disabled ⇒
     failed rows with reason) until Meta/TikTok tokens land (USER-SIDE
     PREREQUISITE).
  3. Leave `SocialDealAutoPoster` and the Mautic delivery listener
     untouched.
  4. **Tests:** tick activates/completes by schedule; from-category
     builds the right target set + drafts; disabled adapters ⇒ failed
     rows, never exceptions. Mind the "Tests run:" gotcha.
- **Acceptance:** through `:18080` — create a category campaign with a
  near-term schedule ⇒ the tick activates + evaluates it on time; social
  rows appear per platform with honest disabled/failed statuses;
  existing campaign/seckill/social panels regression-green; no
  migration; module tests green with real "Tests run" counts.
