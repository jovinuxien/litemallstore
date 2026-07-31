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
> **Wave 13 STATUS:** goods-management SEO backend DEPLOYED (2026-07-29,
> `b4ca2d557`, live sitemap 11,698 URLs); gateway-api head injection
> MERGED to master (`88234261a`), VPS deploy pending. Admin CSP image
> fix `446c79eff` is on master (`img-src` now allows https product
> imagery) — ships with the next admin build. `promotion` Wave-12
> (`69d0aeb9d` on fix/promotion) still awaits merge — do not reassign
> that worktree.
>
> **Wave 14 (2026-07-29) — INVENTORY GOVERNANCE: 12k catalog target,
> retirement pipeline, arrival-category insight, auto daily deals,
> per-category margin tuning.** User asks + DECISIONS (2026-07-29):
> hold the catalog at ~**12,000** on-sale CJ goods — arrivals keep
> flowing in, weak products get retired. Retirement = **OFF-SALE**
> (reversible; viewable-unbuyable rule stays), candidates chosen +
> approved in admin, executed on a schedule (default Wednesday before
> the 03:30 enrichment, per-batch date override). Rank categories by
> NEW ARRIVALS since the last 1–2 catalog runs and by their deal
> quality (price/margin). Today's Deals gets **AUTO top-N daily deals**
> (cap + env kill-switch; price floor = max(suggestedDealPrice,
> cost×1.05) so an auto deal can never sell at a loss — SUPERSEDES
> Wave-12's manual-only rule, user re-approved 2026-07-29). Margin
> tuning = per-category **SIMULATE + APPLY** override (global 1.25
> stays the default).
> Audit facts (2026-07-29; full as-built map in the main session's
> component report):
> - **Inflow ceiling gotcha:** `CatalogTarget.limit` defaults to 200
>   (`CJDropshippingConfig.java:145`, consumed as an overall per-target
>   cap at `CjSnapshotSyncService.java:337`) — the 14 per-leaf fill
>   targets inherit it, so one full run lands ≈3,500 products, not the
>   ~5,400 the yml comment implies. Holding 12k needs explicit
>   `limit:` values per target.
> - Insight thresholds as built: low stock = stock_total ≤
>   `litemall.goods.stock-low-threshold` (5); unavailable = today's
>   metric row `available=0` (product vanished from CJ's live
>   catalog); potentialProfit = Σ(retail−cost)×min(stock, 50).
> - Deal chain to the storefront: `/deals` reads OCS `deal_flag=1`
>   (markdown ≥10% vs counter anchor); CJ goods qualify only while a
>   flash-deal price swap is live; lifecycle tick + reindex already
>   propagate swaps. Auto deals are enough to light the page — **NO
>   gateway-api work in this wave**.
> - Deal candidates as built: tiers hot ≥100 / featured ≥70 / watch;
>   proposal upsert never clobbers an admin decision; approve = the
>   `FlashDealService` author path, CJ floor at captured cost (652
>   when cost unknown).
> - Catalog-run boundaries live in `litemall_cj_sync_run` — use them
>   for "since last N runs" arrival windows.
> - Flyway: **V45 is taken** (Wave 12). Wave 14 claims **V46** after
>   checking `flyway_schema_history`; single migration,
>   goods-management scope.
> **Wave-14 CONTRACT (gateway-admin codes to THIS):** additions under
> `/srv/private/admin/insight` (existing routing; errno envelope; money
> plain decimals; margin null when cost uncaptured):
> - `GET /retire-candidates?status=proposed|approved|dismissed|executed`
>   → `{list:[{goodsId, name, picUrl, categoryId, cost, retailPrice,
>   marginPct, stockTotal, unavailableDays, views, salesQty, score,
>   reasons[], status, executeOn}]}` (higher score = retire sooner).
> - `POST /retire-candidates/approve` body `{goodsIds[], executeOn?}`
>   (executeOn defaults to the next scheduled day);
>   `POST /retire-candidates/{goodsId}/dismiss`. Both CAS from
>   `proposed`; errno 653 on lost race.
> - `GET /arrivals?runs=1|2` → `{since, runs, categories:[{categoryId,
>   name, arrivals, avgMarginPct, avgRetailPrice, dealScore}]}` sorted
>   dealScore desc (avg candidate score of the window's arrivals;
>   categories with zero arrivals omitted).
> - `GET /categories/{id}/simulate?margin=` → `{currentMargin,
>   simulatedMargin, goodsCount, avgPriceNow, avgPriceAt,
>   potentialProfitNow, potentialProfitAt}` (costed goods only; no
>   price mutation).
> - `PUT /categories/{id}/margin` body `{margin}` (bounds 1.05–3.0),
>   `DELETE /categories/{id}/margin`, `GET /margin-overrides` → list.
>   Overrides key on the L1 root and take effect at the nightly
>   reprice; global default stays `spring.cjdropship.pricing.margin`.
> - Auto-deal knobs (goods-management): `litemall.deals.auto-daily-
>   enabled` (default true — env kill-switch), `auto-daily-cap`
>   (default 12), `auto-daily-window-hours` (default 24).
> - **Wave-14.1 addendum (2026-07-29) — Meta catalogue feed:**
>   `GET /srv/goods/meta-catalog.csv` (goods-management; public read;
>   cached artifact regenerated alongside the sitemap). FULL SPEC:
>   `doc/meta-catalog-feed.md` (committed, annotated with verified
>   codebase facts). Highlights: same row set as the sitemap; exact
>   13-column header; description via the Wave-13 brief sanitizer;
>   price/sale_price `"<amount> <ISO>"` from the SAME currency config
>   the meta endpoint uses (Stripe charges
>   `LITEMALL_ORDER_STRIPE_CURRENCY`, default usd — one source of
>   truth); image_link absolutized (the relative-/_cdn og:image
>   gotcha); streaming write, RFC-4180 quoting, UTF-8 no BOM. The edge
>   route `/meta-catalog.csv` (mirror of /sitemap.xml) is done by the
>   MAIN session at merge; Commerce Manager setup is USER-SIDE.
>   **Wave-14.1 STATUS: SHIPPED + DEPLOYED** (2026-07-30, master
>   `c5fdae86f` + edge `0d8cdec96`; live at
>   https://trovemo.com/meta-catalog.csv — 12,549 rows validated
>   RFC-4180, 13 columns, absolute /_cdn images, "<amount> USD"
>   prices; regenerates with the nightly refresh).
>
> **Wave 14 STATUS:** goods-management backend (V46 inventory
> governance) and gateway-admin UI are MERGED + DEPLOYED to
> trovemo.com (2026-07-30, master `61992575c`; prod schema V46; the
> promotion-service container swap also put the parked Wave-12
> campaign scheduler live). Key behavior change: the nightly promote
> now PRESERVES `is_on_sale` on existing goods — retirement and admin
> off-sales survive the 03:00 cycle (proven with a full 9.6k-row
> promote). First post-deploy catalog run grows toward ~13.5k fetched;
> the governor then proposes roughly the overage above 12k — a BIG
> first retirement batch is EXPECTED and admin-gated. Wave-14.1 (meta
> catalogue feed, addendum above) is the next goods-management task;
> its edge half is already on master (`0d8cdec96`).
>
> **Wave 15 (2026-07-31) — TRANSACT & CONVERT / Wave 16 — IDENTITY &
> ONBOARDING.** Commissioned together (user approved the written plan
> 2026-07-31). Both are gateway-api-heavy, so they run SEQUENTIALLY
> in the `gateway-api` worktree — Wave 15 ships and merges first;
> full task specs in the gateway-api worktree block below.
> User decisions locked in: guest checkout = SHADOW ACCOUNTS keyed by
> email (true guest flow, order service unchanged); Stripe Tax =
> ENABLE (staged verify; fail-closed adapter); analytics = Matomo
> ecommerce events + ENV-GATED Meta Pixel (consent-gated, hidden
> until pixel id set); returns policy = 30-day window (buyer pays
> return shipping on remorse, store pays on defects; EU 14-day
> withdrawal stated). User additions: login/signup pages restyled to
> the storefront teal theme; Google Sign-In (env-gated client id,
> provision-or-link by verified email); register/address phone field
> gets a searchable country dial-code selector (static dataset);
> address autocomplete (env-gated Places key; unset ⇒ plain fields).
> Main-session activation AFTER the Wave-15 merge (not worktree
> work): SMTP env + `LITEMALL_CUSTOMERMAIL_ENABLED=true` + reset-mail
> enable; `LITEMALL_ORDER_TAX_*` wiring into prod compose with an
> immediate staged purchase test; Matomo prod config. USER-SIDE
> inputs: SMTP creds; Stripe Tax dashboard activation +
> registrations; legal entity name/address/jurisdiction (Wave-15
> item 1 is BLOCKED on this); Google OAuth client id; Meta Pixel id;
> Places API key (each env-gated — absent = feature hidden, never
> broken).
>
> **USER-SIDE PREREQUISITES:** Stripe **LIVE keys are deployed in prod
> (2026-07-31)** — real card payments enabled; live-mode e2e purchase +
> webhook still to be user-verified; `CJ_CATALOG_*` (goods-management
> catalog/enrichment creds)
> is LIVE; order-side `CJ_API_KEY` is still EMPTY — live CJ order placement
> stays blocked by design (⚠ real money now accepted while fulfilment
> queues — user warned + accepted 2026-07-31; CJ key is urgent). Everything must degrade honestly: typed errors,
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

### Worktree: `goods-management` — history (Wave 14, SHIPPED)
- **Task — Wave 14 (backend): inventory governance.** (Merged +
  deployed 2026-07-30, master `61992575c`; V46 applied in prod; dev
  acceptance green end-to-end incl. governor overage sizing, executor
  off-sale flip, margin override reprice at all sites, live-deal price
  lock, auto-deal kill-switch. Spec in git history.)
- **History:** Wave 13 SEO backend SHIPPED (`b4ca2d557`); Wave 12
  inventory intelligence SHIPPED (`68ebcbb38`); Wave 11 banners
  SHIPPED (`308082607`).
- **Wave 14.1 meta catalogue feed: SHIPPED + DEPLOYED** (2026-07-30,
  `c5fdae86f`; live feed validated). No new assignment — do not start
  work here without a new instruction.

### Worktree: `gateway-api` — ACTIVE: Wave 15, then Wave 16
- **Branch:** `fix/gateway-api` — FIRST: `git merge master`. Scope:
  `litemall-gateway-api/` only (SPA + edge + `/auth`), EXCEPT the
  Wave-16 migration which lands in litemall-db (shared-module
  discipline). Wave 15 has NO migration; Wave 16 claims **V47**
  (check `flyway_schema_history` immediately before first boot).
  Wave 15 merges to master BEFORE Wave 16 work starts.
- **Task — Wave 15: Transact & Convert.**
  1. **Legal copy** — replace the `LegalPlaceholder` banner pages
     (`app/modules/static/{Terms,Privacy,Cookies,Returns}.tsx`,
     `LegalPlaceholder.tsx`) with binding copy: 30-day returns
     (remorse = buyer pays return shipping; defect = store pays; EU
     14-day withdrawal right stated), dropshipping delivery-time
     disclosure, Stripe as payment processor, Matomo + Meta Pixel
     disclosure in the cookies policy. **BLOCKED-ON-USER:** legal
     entity name, registered address, jurisdiction — do NOT merge
     with placeholders; do the other items first if these are
     missing. `docs/LAUNCH-BLOCKER-legal-copy.md` closes with this.
  2. **Promo-code box at checkout** — input + Apply in the coupon
     section of `views/commonViews/cart/Checkout.tsx` (picker at
     :179-181), wired to the existing
     `userApi.couponExchange` (`shared/api/userApi.ts:133` →
     `POST /srv/coupon/exchange`, handler already live in
     promotion-service); on success refresh `selectlist` and
     auto-select the redeemed coupon; honest errno messages for
     invalid/expired/already-claimed. NO backend work.
  3. **Conversion events** — wire ecommerce events into the EXISTING
     consent-gated Matomo seam (`shared/tracking/matomo.ts` +
     `MatomoTracker.tsx`): product view (PDP), add-to-cart,
     begin-checkout, `trackEcommerceOrder` on the confirmation page.
     Plus an env-gated **Meta Pixel**: new `litemall.meta.pixel-id`
     @Value in `SiteConfigController` (the proven Stripe/social
     pattern — blank ⇒ null ⇒ nothing injected), compose env
     `LITEMALL_META_PIXEL_ID`; pixel script injection + ViewContent /
     AddToCart / InitiateCheckout / Purchase, gated behind the SAME
     cookie consent as Matomo — no consent, no pixel. Activation
     later = env + recreate, NO rebuild.
  4. **Checkout email capture** — make the existing shipping email
     field (`Checkout.tsx:722`) required; on submit, if the account
     has no email, persist it via the existing `POST /auth/profile`
     (`AuthController:165`) so the Wave-10 mail listener (recipient
     = `litemall_user.email`) stops silently skipping buyers.
- **Acceptance (15):** legal pages render binding copy (no
  placeholder banner); a code-type coupon redeems from the checkout
  box and applies to the total; with consent granted, Matomo dev
  console shows view/cart/checkout/purchase events and the pixel
  fires only when `LITEMALL_META_PIXEL_ID` is set; an order placed
  by an email-less account lands the email on the user row; existing
  checkout/coupon e2e regression-green.
- **Task — Wave 16 (start AFTER 15 merges): Identity & Onboarding.**
  5. **Guest checkout (shadow accounts)** — `/checkout` reachable
     without login: email + address in, edge auto-provisions a
     password-less account keyed by the email (V47: guest flag +
     `google_sub` column on `litemall_user`; hand-edit entity +
     mapper XML together) and issues a normal customer JWT — the
     ORDER SERVICE IS UNCHANGED (orders attach to a real user_id).
     Confirmation page offers "set a password to track your order"
     (reuse the reset-token flow, `AuthController:129-142`). Email
     already registered with a password ⇒ prompt login instead.
     Provisioning stays INSIDE the existing `/auth` public surface —
     NO new anonymous `/srv` paths (svcsecurity deny-by-default).
  6. **Google Sign-In** — env-gated `litemall.google.client-id` via
     `/auth/site-config` (unset ⇒ button hidden). GIS button on
     login + register; edge verifies the ID token server-side
     (signature/audience/issuer) in the `auth` package, then
     provisions or LINKS by verified email (existing password
     account with same email ⇒ link + store `google_sub`; guest
     shadow account ⇒ upgrade). Fetch name/email/avatar from the
     token claims.
  7. **Auth-page theming** — restyle `CustomerLogin.tsx`,
     `Register.tsx`, `ResetPassword.tsx` to the storefront teal
     theme (shared header/footer, buttons, typography).
  8. **Phone country codes** — searchable country selector (flag +
     name + dial code, STATIC dataset, no external service) on
     register + address forms; store E.164-normalized.
  9. **Address autocomplete** — env-gated Places suggestions on the
     address form (`litemall.places.api-key` via site-config; unset
     ⇒ plain manual fields, zero degradation); country selector
     scopes suggestions.
- **Acceptance (16):** fresh browser buys end-to-end with only
  email + card; that email can later claim the account (password) or
  sign in with Google and sees the order; Google button absent when
  env unset; themed auth pages; country-coded phone on register;
  all existing login/register/reset e2e green; no new anonymous
  service paths.
- **History:** Wave 13 SEO (edge+SPA) SHIPPED (`88234261a`); Wave
  9.1 trust surfaces SHIPPED (`3989e2053`).

### Worktree: `gateway-api` — history (Wave 9.1, SHIPPED)
- **Task — Wave 9.1: storefront trust surfaces (social links, help center,
  customer-service FAQ).** (Merged + deployed 2026-07-25, `3989e2053`.)

### Worktree: `gateway-admin` — history (Wave 14, SHIPPED)
- **Task — Wave 14 (admin UI): retirement, arrivals insight, margin
  tuning.** (Merged + deployed 2026-07-30 to admin.trovemo.com,
  master `61992575c` / UI merge `02ed9eea8`. Spec in git history.)
- **History:** Wave 12 insight UI SHIPPED (`fbb29812e`); Wave 8
  branding SHIPPED (`72446568f`).
- **No new assignment.** Do not start work here without a new
  instruction.

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
