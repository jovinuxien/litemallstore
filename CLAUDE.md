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
> covers them). The promotion half (campaign scheduler) merged later
> and went prod-live 2026-07-30.
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
> (`69d0aeb9d`) has since merged (prod-live 2026-07-30).
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
> **Wave 17 (2026-08-04) — POSTIZ SOCIAL PUBLISHING: admin-picked
> product posts by category, scheduled across channels via Postiz's
> public API.** User approved the written plan 2026-08-04 (decisions:
> DEV PILOT first — no prod deploy this wave; DYNAMIC channel list
> from Postiz; START+INTERVAL scheduling). Postiz checkout:
> `~/postiz_dir/postiz-app`, runs via its own docker compose, API
> base `http://localhost:4007/api/public/v1`. Verified API facts
> (code-audited 2026-08-04): public API is always-on; auth = BARE
> key in `Authorization` (NO `Bearer` prefix); `POST /public/v1/posts`
> targets N channels in ONE call via `posts[]` (one throttle hit per
> CALL, not per channel); `date` = explicit UTC ISO with
> `type:"schedule"` (`"now"` discards date; backend forces TZ=UTC);
> images pass as external URLs ONLY when the path ends in a real
> extension (.png/.jpg/.jpeg/.gif/.webp — our `/_cdn/...jpg` qualify;
> query string ignored); `shortLink:false` + `tags:[]` are MANDATORY
> keys (400 if omitted); content = DOMPurify-sanitized HTML (`p, br,
> strong, u, a, ul, li, h1-h3, span`); per-provider `settings`
> required EVEN FOR DRAFTS (facebook `{}`, instagram `{post_type}`,
> x `{who_can_reply_post}`, tiktok ~9 fields; schema via
> `GET /integration-settings/:id`); throttle = Postiz env `API_LIMIT`
> (30/h in its shipped compose) per org; Temporal must be up or
> scheduled posts sit in QUEUE forever (error swallowed); validation
> errors return structured 400 `{provider, name, error}`.
> **Wave-17 CONTRACT (gateway-admin codes to THIS, not the promotion
> branch):** promotion-service serves
> `/srv/private/admin/promotion/postiz/**`, env-gated on
> `litemall.postiz.base-url` + `litemall.postiz.api-key`
> (`LITEMALL_POSTIZ_BASE_URL`, `LITEMALL_POSTIZ_API_KEY`; either
> absent ⇒ typed "not configured" errno, never 5xx). Errno envelope;
> money plain decimals.
> - `GET /status` → `{enabled}` (+ `channelCount` when enabled) —
>   the UI visibility switch.
> - `GET /channels` → `{list:[{integrationId, identifier, name,
>   picture, supported, reason?}]}` — Postiz integrations, ~5-min
>   cache; `supported:false` + reason when the composer can't satisfy
>   that provider's required settings yet.
> - `POST /preview` body `{goodsIds[], channelIds[], startTime,
>   intervalMinutes}` → `{batch:[{goodsId, name, picUrl, scheduleAt,
>   warnings[], perChannel:[{integrationId, content, settings}]}],
>   warnings[]}` — ZERO side effects; includes non-blocking
>   "posted N days ago" dedup warnings.
> - `POST /publish` same body → `{results:[{goodsId, scheduleAt,
>   channels:[{integrationId, ok, postizPostId?, error?}]}]}` — one
>   Postiz call per product; batch cap 25; Postiz 400s surfaced
>   verbatim per channel.
> - `GET /log?page&limit` → standard page envelope over
>   `litemall_postiz_post`.
>
> **Wave 17 STATUS: MERGED + DEPLOYED to production** (2026-08-04;
> merges `8f19f2857` promotion + `b5dca8e7c` gateway-admin, compose
> wiring `aa46273b2`). Module tests 69/0 real counts. Prod Postiz =
> **https://social.trovemo.com** (user-deployed; Trovemo Facebook page
> connected, integration `cms1876qi0001n86m5rub0w3g`). Direct prod
> pipeline probe green pre-merge: composer-shaped payload scheduled →
> visible via GET /posts (QUEUE, facebook) → deleted (Postiz DELETE
> returns 200 with quirky `{"error":true}` body — verify by re-GET,
> not by body). Prod schema at V50 (out-of-order; V49 behavioral
> targeting still unmerged), both containers healthy, admin bundle
> `main.769a4325.js`, `.env.prod` carries LITEMALL_POSTIZ_* (backup
> `.env.prod.bak-postiz`). Final UI click-through acceptance =
> user-side (admin creds not available to sessions).
>
> **Wave 18 (2026-08-06) — COUPON MERCHANDISING PHASE 1: scoped,
> percent-capable, PROFIT-GUARDED coupons on the CJ catalog.** First of four
> user-approved coupon/groupon phases (Phase 2: margin-driven coupon candidate
> scorer + `coupon_flag` in search; Phase 3: groupon priced submit — already
> specced in `litemall-order/docs/followup-groupon-priced-submit.md` +
> `litemall-promotion-service/docs/spec-groupon-priced-submit-contract.md`;
> Phase 4: RFM-targeted delivery via the existing targeting engine).
> USER DECISIONS (2026-08-06): margin guard is a **HARD BLOCK** (no admin
> override); floor `litemall.promotion.coupon.margin-floor` (env
> `LITEMALL_PROMOTION_COUPON_MARGIN_FLOOR`, default 1.05); full scope approved
> incl. percent-off, register-gift activation, public coupon center.
> Audit facts (2026-08-06, verified against master): the coupon backend is
> product-source-AGNOSTIC (no CJ check anywhere in promotion — the whole CJ
> catalog is couponable); BUT `CouponForm.tsx:21` hardcodes `goodsType:0`
> (scoping unreachable from admin); checkout selectlist omits `categoryIds`
> (`Checkout.tsx:417`) while order submit resolves them — preview vs charge
> disagree for scoped coupons; `matchesGoods`
> (`LitemallCouponAggregate.java:97-115`) matches LEAF category ids only — an
> L1-scoped coupon would silently match NOTHING; redeem
> (`LitemallCouponServiceImpl.java:172-224`) re-checks threshold only, not
> scope; `TYPE_REGISTER` has no issuance trigger; `discount` is flat-only.
> **Wave-18 CONTRACTS (worktrees code to THESE, not to each other's
> branches):**
> - **Margin-guard basis:** goods-management serves
>   `POST /srv/private/admin/insight/margin-basis` — FROZEN spec
>   `litemall-goods-management/docs/handoff-coupon-margin-basis.md` (already
>   BUILT on `fix/goods-management`, unit tests 4/4). Auth = machine token +
>   forwarded `X-User-Roles: ROLE_ADMIN` (the order-service recipe for
>   admin-prefixed paths). Promotion fails CLOSED (typed errno, never an
>   unguarded save) when it is unreachable.
> - **Category semantics:** coupons store the admin's picked category ids
>   AS-IS (any level, L1 encouraged); promotion expands the CART's leaf ids up
>   the `litemall_category` ancestor chain before `matchesGoods`, so L1-scoped
>   coupons work and future CJ-sync leaves are covered automatically. When a
>   caller omits `categoryIds`, promotion derives them server-side from the
>   passed `goodsIds` (one batched read) — the SPA picker and order submit
>   then agree by construction; NO SPA selectlist change needed.
> - **Percent-off:** migration **V51** (promotion claims it; check
>   `flyway_schema_history` immediately before first boot — prod applied
>   through V50) adds `discount_type` (0 flat / 1 percent) + `discount_cap`
>   decimal to `litemall_coupon`; when percent, `discount` holds the rate
>   (validated 1–90). `usable`/`selectlist` return the COMPUTED effective
>   discount for the passed amount (cap applied) so order-side math is
>   UNCHANGED; redeem re-computes against `orderSubtotal`. List/read payloads
>   expose `discountType`/`discountCap` for rendering.
> - **Guard formula (promotion-side, on create AND update):** flat
>   `D ≤ min × (1 − maxCostRatio × floor)`; percent
>   `rate ≤ (1 − maxCostRatio × floor) × 100` (the cap bounds exposure but is
>   NOT a substitute for the rate check); `maxCostRatio == null` ⇒ reject with
>   typed "cost not yet captured for this scope". Rejections state the
>   computed maximum so the admin can adjust; `uncostedCount > 0` rides along
>   as a non-blocking warning.
> - **Register-gift:** promotion `POST /srv/promotion/coupon/register-gifts`
>   (machine token + `X-User-Id`) grants every active `TYPE_REGISTER` coupon
>   to that user, idempotent via the per-user claim limit; gateway-api fires
>   it once after successful registration, fail-silent.
>
> **Wave 18 STATUS: SHIPPED + DEPLOYED to trovemo.com (2026-08-06, master
> `3168148e7`, prod schema V51).** Merges `1d02deeab` promotion /
> `bc75d67b5` order / `9fbe80120` admin UI / `c9b0848a6` storefront; module
> tests promotion 107/0, order 189/0; webapp checks jest 19/19 + headless
> 16/16 (admin), 24/24 + 17/17 (storefront). **Live dev acceptance PASSED**
> (guard max stated 15.98% at floor 1.05 on the 0.8001 scope; SCOPE_UNCOSTED
> typed reject; register-gift grant idempotent; order 111 wallet-paid with
> capped percent coupon, DB-verified) — and CAUGHT a blocker, fixed as
> `3168148e7`: `MarginBasisClient` must forward `X-User-Id` or svcsecurity
> ignores `X-User-Roles` and every coupon create fails closed (handoff spec
> corrected). Prod deploy staged order/promotion before edges; pass 2
> converged promotion + gateway-admin on `3168148e7` (also carrying the
> concurrent `6386f613c` admin audit-ip hotfix). Gotchas: guard rejections
> ride as `guardError` in the admin envelope data (HTTP 400); percent
> `discount_cap` can be raised but not cleared via selective update; dev's
> 4 costed goods (10008302–05) have `cj_vid` NULL ⇒ unbuyable on dev — buy
> an enriched same-L1 good for order-leg tests. USER-SIDE: prod admin
> click-through (create a scoped/percent coupon against prod costs; view
> /coupons on the storefront).
>
> **Wave 19 (2026-08-08) — PROMO CANDIDATE INTELLIGENCE (coupon Phase 2):
> margin-driven coupon/groupon suggestions in admin + coupon visibility in
> search.** User approved the written plan 2026-08-08. USER DECISIONS:
> groupon promotion stays ADMIN-SIDE ONLY (suggestions may create DRAFT
> campaigns; no customer-facing groupon pages/posts) until Phase 3 priced
> submit ships — group checkout still charges RETAIL today; two-wave split
> (Wave 19 candidates + coupon_flag, Wave 20 DIY/social below).
> Audit facts (2026-08-08, agent-mapped vs master): the template to copy is
> the deal pipeline — `DealCandidateScorer` (score = marginPct ×
> ln(1+stock) × social, tiers hot≥100/featured≥70/watch, upsert never
> clobbers a decision) → `litemall_deal_candidate` (V45) →
> `AdminInsightController` list/approve/dismiss → `AutoDailyDealTask`.
> Cost/margin context comes from `InventoryContextEnricher` (cost 0.00 ⇒
> null, never fake 0%); daily series in `litemall_product_metric_daily`;
> retire-candidates (V46) mark clearance targets. NOTE: DealCandidateScorer
> fires on NEW_ARRIVAL only — coupon candidates need a NIGHTLY BATCH over
> the costed on-sale catalog too. Groupon admin create =
> `POST /srv/private/admin/promotion/combination` (goodsId, title, picUrl,
> combinationPrice, originalPrice, requiredMembers, limitPerUser, window);
> campaigns are DRAFT until activated (`GrouponRuleForm.tsx` mirrors it).
> **Wave-19 CONTRACT (worktrees code to THIS):** goods-management serves
> under the existing `/srv/private/admin/insight` routing; errno envelope;
> money plain decimals; margin/suggestion fields null when cost uncaptured.
> - **V53** (goods-management scope; check `flyway_schema_history`
>   immediately before first boot — dev applied through V52):
>   `litemall_promo_candidate` — kind ('coupon'|'groupon'), goods_id, day,
>   tier, score, suggestion JSON, reasons JSON, status
>   proposed|dismissed|consumed, unique (kind, goods_id, day); upsert never
>   overwrites a decided row.
> - `GET /insight/promo-candidates?kind=coupon|groupon&status=&day=` →
>   `{list:[{goodsId, name, picUrl, categoryId (L1 root), kind, day, tier,
>   score, cost, retailPrice, marginPct, stockTotal, rating, reviewCount,
>   reasons[], status, suggestion}]}` sorted score desc; `day` defaults to
>   the latest day having rows for that kind. `suggestion` for coupon =
>   `{scopeType:'category'|'goods', categoryId?, goodsIds?, discountType:
>   0|1, discount, discountCap?, minAmount, maxDiscount}` — PRE-VALIDATED
>   against the Wave-18 guard formula at floor 1.05 (maxCostRatio math is
>   local to goods-management), so a suggestion can never be rejected by
>   the guard; for groupon = `{combinationPrice (≥ cost×1.05),
>   originalPrice, requiredMembers, limitPerUser, windowDays}`.
> - `POST /insight/promo-candidates/{goodsId}/dismiss` body `{kind, day?}`;
>   `POST /insight/promo-candidates/{goodsId}/consume` body `{kind, day?,
>   refId?}` (refId = created coupon/combination id). Both CAS from
>   `proposed`; errno 653 on lost race. Admin UI flow: Create buttons
>   PREFILL the existing CouponForm / GrouponRuleForm (router state), and
>   on successful create the UI calls `consume`. No cross-service create
>   endpoint — creation stays on the existing promotion admin paths.
> - **`coupon_flag` in the OCS index** (mirror of deal_flag): 1 when ≥1
>   ACTIVE, in-window, claimable coupon's scope matches the product
>   (whole-catalog coupons count; category scope expands ancestor-aware,
>   Wave-18 semantics). Computed at index time from the shared
>   `litemall_coupon` table (read-only — the promotion-reads-goods
>   precedent, inverse direction). Freshness = nightly reindex +
>   goods-write upserts + manual `POST /srv/private/admin/search/
>   refresh-signals`; same-day coupon creates may lag until the next
>   refresh — ACCEPTED v1 semantics. Facet/filter exposure like deal_flag
>   (`coupon_flag=1` filter param + hit source field). Reindex BEFORE
>   searcher restart (scoring-field discipline).
> - Nightly scorer targeting (goods-management internals, not contract):
>   coupon candidates from high-margin slow movers, retire-candidates
>   (clearance), and high-margin arrival categories; groupon candidates
>   favor margin + social proof (rating/reviews). Config knobs under
>   `litemall.promo-candidates.*` with an enabled kill-switch.
>
> **Wave 19 STATUS: MERGED to master `e9d441382` (2026-08-08) — all three
> halves (goods-management `35cd49ced`, gateway-admin `13060e260` jest 37/37,
> gateway-api `3917c884f` 17/17 + honest 502 outage state). LIVE DEV
> ACCEPTANCE PASSED same day (goods-management booted from MAIN at master;
> V53 applied at boot): manual run proposed 4 coupon + 4 groupon candidates
> with guard-bounded suggestions (maxDiscount 16 vs live guard max 15.98%
> — coherent); dismiss→653 on repeat; consume records refId; re-run never
> clobbers decisions; L1 root resolution verified (Spatulas→Imported);
> coupon 15 (10% goods-scoped) created through the REAL promotion guard,
> 50% negative control rejected with stated max; full reindex 9,642 docs;
> `/srv/search?...&coupon_flag=1` returns exactly the couponed product
> with coupon_flag:1 in the hit (the passthrough fix the gateway-api agent
> caught). **DEPLOYED to trovemo.com 2026-08-08**: staged script (indexer
> recreate → goods-management V53 → reindex 12,824 docs → searcher recreate
> → gateways), smoke 200s; live probe: 3 prod products already return
> coupon_flag:1 through the edge (an existing scoped coupon matched).
> Nightly scorer runs 04:30; UI click-through = user-side.**
>
> **Wave 20 (2026-08-08) — DIY PROMO PAGES + SOCIAL PAGE PUBLISHING
> (commissioned together with Wave 19; starts AFTER Wave 19 merges).**
> Audit facts (2026-08-08): DIY pages = `litemall_page` (V36), schema-driven
> palette editor (`PageEditor.tsx`, `GET /page/palette`), closed component
> set in `PageConfigValidator.KNOWN_TYPES` (banner, image-row, goods-list,
> coupon-strip, seckill-strip, article-strip, rich-text; MAX 30 components,
> 64KB config, jsoup-sanitized rich-text); NO page category, NO templates,
> NO clone, NO groupon component, NO social linkage; customer render =
> `PageRenderer.tsx` at `/page/:id` (public `/srv/page/**`) + home
> override; single-active-home enforced by generated column. Postiz posts
> are goods-only (`litemall_postiz_post` V50, dedup by goods).
> **Wave-20 CONTRACT:**
> - **V54** (goods-management scope): `litemall_page.category` varchar(31)
>   NOT NULL default 'general' ('general'|'coupon'|'groupon') +
>   `is_template` tinyint default 0; seed TWO designed template pages
>   (is_template=1, status draft, category coupon/groupon): "Coupon
>   spotlight", "Group-buy rally".
> - goods-management content: `POST /srv/private/admin/page/{id}/clone` →
>   new DRAFT copy (name "Copy of …", position custom, category+config
>   inherited, never copies active status); `/page/list` gains `category`
>   + `template` filters and returns both fields; palette v1.1 adds
>   `groupon-strip` (auto-active combinations or explicit ids, maxItems)
>   and extends `coupon-strip` (optional explicit couponIds[], headline,
>   style variant). Palette version bump must keep v1 configs valid.
> - gateway-api: PageRenderer renders groupon-strip (from
>   `/srv/promotion/combination/active`) + extended coupon-strip; edge
>   head-injection (Wave-13 seam) adds og:title/og:image for `/page/:id`
>   from `/srv/page/{id}` (fail-open, no UA cloaking). Groupon-strip
>   renders ONLY the join/browse links that exist today (PDP links) —
>   no price-promise copy until Phase 3.
> - promotion-service (**V55**, its scope): `litemall_postiz_post.page_id`
>   INT NULL + Postiz composer accepts a page source: `POST .../postiz/
>   preview|publish` body alternative `{pageId, channelIds[], startTime}`
>   → one post: page name + `https://trovemo.com/page/<id>` + hero image
>   (first image-bearing component, absolutized; skip-with-warning when
>   no valid-extension image). REFUSES groupon-category pages with a
>   typed errno until Phase 3 (the gating decision). `/log` rows carry
>   pageId; goods dedup warnings unchanged.
> - gateway-admin: DIY page list category/template filters + "New from
>   template" (clone → open editor); Postiz panel gains a source picker
>   (Products | DIY page) honoring the groupon-category refusal verbatim.
>
> **Wave 20 STATUS: MERGED to master `3e16420d5` (2026-08-08) — all four
> halves (goods-management `77aa8784a`+`f1bf46cd6` V54 + palette v1.1 +
> clone + seeded templates, tests 278-suite green; promotion `066b8e270`
> V55 + Postiz page source, 114/0; gateway-api `53123bcb0` groupon-strip
> renderer + /page/:id og-meta, jest 26/26 + module 65/0; gateway-admin
> `8efc7197d` page filters/template flow/Postiz source picker, jest 57/57).
> FlywayMigrationTest merge conflict union-resolved (floor 55). LIVE DEV
> ACCEPTANCE PASSED same day: V54+V55 applied at boot; both seeded
> templates listed → cloned (draft "Copy of …", isTemplate false) →
> activated → public read serves category + components; Postiz page
> preview composes the coupon page (text-only warning — seeds carry no
> images by design), REFUSES the groupon page with errno 765, 764 on
> not-active, env gate + real channel resolution live; edge /page/5
> injects title/og:title/og:type/og:url, missing page = plain shell 200
> fail-open. V55 also made litemall_postiz_post.goods_id NULLABLE (page
> rows). Errnos: 764 page-not-active, 765 groupon-held, 766 page source
> unavailable. **DEPLOYED to trovemo.com 2026-08-08** (staged: goods
> V54+V55 — prod schema verified at 55 — then promotion, then gateways;
> smoke 200s incl. /page/ shell + public read envelope). Templates are
> seeded as DRAFTS in prod — nothing customer-visible until an admin
> clones + activates one (by design). Dev gotchas: an OLD
> promotion jar from a prior session held :8088 and answered with 402s —
> verify the pid/cwd behind a port before trusting acceptance results;
> pkill -f a jar name self-matches the launcher shell — launch via script
> file.**
>
> **Wave 21 (2026-08-08) — TRANSACTIONAL GROUP-BUY (coupon roadmap Phase 3):
> priced pinkId submit, shareable group links, PDP entry, groupon_flag,
> gate unlock.** USER DECISIONS (2026-08-08): pay-at-submit; a PAID order
> whose group EXPIRES unfilled is AUTO-CANCELLED + REFUNDED to the original
> tender via the existing tender-parity refund path (customer mail rides the
> existing pipeline); submitting on a failed/expired slot is REJECTED with a
> typed message ("this group has expired — start a new one or buy at regular
> price"), never silently re-priced. The FROZEN base contract is
> `litemall-promotion-service/docs/spec-groupon-priced-submit-contract.md`
> (+ `litemall-order/docs/followup-groupon-priced-submit.md`); code to it.
> **Wave-21 CONTRACT additions (on top of the spec):**
> - **V56** (order scope; check `flyway_schema_history` — prod applied
>   through V55): `litemall_order.pink_id` INT NULL + KEY. Hand-edit
>   LitemallOrder + OrderMapper.xml together (⚠ the recurring
>   OrderMapper.xml-goes-missing gotcha). Order is the ONLY litemall-db
>   writer this wave.
> - **promotion** implements the two follow-ups the spec parked:
>   `POST /srv/promotion/combination/pink/{pinkId}/attach-order {orderId}`
>   (machine + X-User-Id; CAS on order_id null) and
>   `POST /srv/promotion/combination/pink/{pinkId}/release {orderId}`
>   (frees the slot ONLY while the group is Pending; a released slot drops
>   memberCount; idempotent). GROUP_EXPIRED/GROUP_COMPLETED Kafka payloads
>   gain `memberPinkIds[]` (additive) so order can find affected orders.
>   UNLOCK: delete the Postiz errno-765 groupon-page refusal (groupon pages
>   become publishable — priced submit makes the promise real).
> - **order**: optional `pinkId` on submit → validate slot (owner, status
>   Pending|Success, order_id null; else the typed stale-slot reject) +
>   campaign (goodsId match) → price the line at combinationPrice, qty
>   capped by limitPerUser → persist pink_id → attach-order after placement
>   (fail-soft, logged). Order-cancel before completion → release (fail-
>   soft). GROUP_EXPIRED listener → for each memberPinkId with a PAID local
>   order: auto-cancel + tender-parity refund + existing customer-mail
>   trigger; idempotent per order; unpaid orders just cancel. On-sale and
>   coupon paths unchanged.
> - **goods-management**: `groupon_flag` in the OCS index (1 when an ACTIVE,
>   in-window `litemall_combination` campaign exists for the goods — shared
>   read-only, mirror CouponSignalResolver incl. 60s TTL fail-soft snapshot
>   + always-emit + hit passthrough + indexer yml field + BOTH dynamic-field
>   regex whitelists + refresh-signals coverage).
> - **gateway-api**: shareable `/groupon/:id` campaign landing (detail,
>   members progress, start/join CTAs, share link; og-meta OPTIONAL this
>   wave); PDP group-buy entry when the product has an active campaign;
>   start/join flows route into checkout carrying `pinkId`; checkout
>   renders the GROUP price from the server; stale-slot reject surfaced
>   verbatim; groupon page cards deep-link `/groupon/:id`; "Group buy"
>   badge + filter riding `groupon_flag` (mirror the coupon badge/facet).
> - errno envelope everywhere; money plain decimals; NO changes to the
>   legacy litemall_groupon path.
>
> **Wave 21 STATUS: SHIPPED + DEPLOYED to trovemo.com (2026-08-08, master
> `e3b44b072`, prod schema V56, reindex 12,843, smoke 200s).** All four
> halves: order `74a0d4306` (V56 pink_id; priced submit; attach/release
> hooks afterCommit; GROUP_EXPIRED auto-refund listener — order's first
> inbound Kafka binding; module 235/0), promotion `f65baa1e9` (attach-order
> CAS + release w/ leader-dissolution semantics; memberPinkIds[] on both
> events; Postiz 765 groupon gate DELETED; slot-view fix on GET /pink/{id};
> 126/0), goods-management `4f04a7295` (groupon_flag incl. passthrough;
> 288-suite), gateway-api `01d7b6957` (/groupon/:id landing + invite links
> surviving login; PDP strip; checkout pinkId; Group-buy badge/filter; jest
> 61/61). LIVE DEV ACCEPTANCE PASSED end-to-end: campaign 7 → start/join →
> order 113 CHARGED GROUP PRICE 46.31 (+8 freight), pink_id + attach-order
> DB-verified → wallet-paid → leader release dissolved the group →
> GROUP_EXPIRED consumed → order 113 REFUNDED 54.31, wallet restored to
> the cent, processor log honest → stale-slot re-submit rejected with the
> exact typed message → groupon_flag=1 search returns exactly the campaign
> product (searcher restart REQUIRED after reindex for new-field
> resolution — encoded in the deploy). Groupon publishing is now UNLOCKED
> (Postiz accepts groupon pages; the gating decision is retired).
> gateway-admin container unchanged this wave. USER-SIDE: storefront
> click-through of /groupon/:id + PDP strip + a real group checkout.
> Coupon roadmap remaining: Wave 22 = Phase 4 (RFM targeting + analytics
> loop) — not yet commissioned.**
>
> **Wave 22 (2026-08-08) — TARGETED DELIVERY & DEMAND ANALYTICS (coupon
> roadmap Phase 4, the final commissioned phase).** Close the loop: deliver
> coupons to RFM segments, learn what search demand wants, measure what
> converts. Flyway: **V57** (goods-management) + **V58** (promotion); prod
> applied through V56 — check history before first boot.
> **Wave-22 CONTRACT:**
> - **goods-management (search analytics):** V57 `litemall_search_stat_daily`
>   (day, keyword, searches, zero_results, clicks, unique KEY day+keyword) —
>   filled by a nightly rollup task (kill-switch litemall.search-stats.*)
>   joining litemall_search_history AND the Phase-0 behavioral log
>   (litemall_user_event `search` + `click_result` events; consent-ramped,
>   read-only). SearchService starts recording result_count into the history
>   write path (V57 adds the column) so zero-result queries stop being
>   log-only. Insight endpoints: `GET /insight/search-stats?days=7|30` →
>   {topQueries:[{keyword, searches, zeroResults, clicks, ctrPct}],
>   zeroResultQueries:[...], totals}; `POST /insight/search-stats/
>   trending/refresh` → recompute the hot-keyword set from real demand (top
>   N by searches, N configurable, curated defaults PRESERVED — additive,
>   never deletes admin keywords; same behavior nightly after the rollup).
> - **promotion (RFM delivery + measurement):** V58
>   `litemall_coupon_delivery` (coupon_id, segment_json, matched, granted,
>   skipped, add_time). `POST /srv/private/admin/promotion/coupon/
>   {couponId}/deliver` body {recencyDays?, minFrequency?, minMonetary?,
>   preview?} — computes the matching user set from the EXISTING RFM seam
>   (or a read-only shared-table query over litemall_order paid orders when
>   the seam lacks a bulk segment query); preview:true = counts only, zero
>   side effects; else grants via the EXISTING coupon grant path (idempotent
>   per user via the claim limit; expired/inactive coupon = typed refusal;
>   margin guard NOT re-run — the coupon was already guarded at create).
>   `GET .../coupon/{couponId}/performance` → {granted, used, redemptionPct,
>   ordersCount, revenue, avgOrderValue} read-only over litemall_coupon_user
>   + litemall_order. `GET .../coupon/deliveries?couponId=` → history page.
> - **gateway-admin:** Search-analytics panel (Insight group: top/zero-result
>   query tables w/ CTR, totals, a "Refresh trending" button); CouponList/
>   detail gains "Deliver to segment" dialog (RFM inputs → Preview count →
>   Deliver; results + typed refusals verbatim) + a performance card +
>   deliveries history. NO gateway-api work (trending flows through the
>   existing /srv/search/index path).
> - errno envelope; money plain decimals; consent data stays aggregate-only
>   (no per-visitor drill-down in admin — privacy posture).
>
> **Wave 22 STATUS: SHIPPED + DEPLOYED to trovemo.com (2026-08-08, master
> `da29b1f77`, prod schema V58).** All three halves merged same day:
> goods-management `66fc6d327` (V57 search stats + rollup + result_count +
> insight endpoints + demand-derived trending, 04:45 nightly after the 04:30
> promo scorer), promotion `7eb513d1d` (V58 coupon_delivery + deliver w/
> preview + performance + history; audience hard cap 10k = errno 772 typed
> refusal, never silent truncation), gateway-admin `76e47cd13` (analytics
> panel + deliver-to-segment dialog + performance card). Dev acceptance
> evidence: rollup produced real rows incl. a captured zero-result query;
> coupon 15 delivered to a recencyDays:30 segment — first run granted 7/7,
> re-run skipped 7/7 (idempotency proven). Prod deploy staged goods (V57) →
> promotion (V58) → gateway-admin, images built 13:16, containers healthy,
> smoke 200s; new admin endpoints answer 401 (routed + auth-gated) through
> the edge. NO new prod env needed (all knobs env-backed with defaults:
> `LITEMALL_SEARCHSTATS_*`, `LITEMALL_PROMOTION_COUPON_DELIVERY_MAX_
> AUDIENCE`). No reindex (no new index fields), no gateway-api half.
> USER-SIDE: admin click-through (Search analytics panel; Deliver-to-segment
> preview→deliver on a real coupon). The coupon roadmap (Phases 1–4) is
> COMPLETE. Wave 23 (admin-gated CJ placement) stays CANCELLED — spec
> preserved at `cb4909fee`; CJ key still HELD disarmed.
>
> **Wave 23 (RE-COMMISSIONED 2026-08-08, after Wave 22 shipped) —
> ADMIN-GATED CJ PLACEMENT + ADMIN ORDER NOTIFICATIONS.** USER DIRECTIVE
> (2026-08-08, reconfirmed same day: "first take wave-23 approved before
> the arming process"): paid orders must NOT auto-place at CJ — they wait
> PENDING until an admin validates them in the admin panel; the admin is
> notified by email (contact@trovemo.com) when an order is paid; approval
> sends the order to CJ. Full admin control. Context: the user's
> CJ_API_KEY is VALIDATED (live token issued 2026-08-08) but sits DISARMED
> in prod env (marked HELD) until this wave deploys — paid orders keep
> queueing harmlessly (prod backlog today: orders 7 $38.38, 10 $83.31,
> 11 $8.90 at status 201; order 9 is 202 refund-applied and must NOT be
> placed). ⚠ CJ config is all-or-nothing: CjTokenService refuses boot when
> exactly one of CJ_EMAIL/CJ_API_KEY is set (observed live) — deploy must
> set BOTH + the mode env together.
> **Wave-23 CONTRACT:**
> - **order**: config `litemall.order.cj.placement-mode` = auto|manual (env
>   `LITEMALL_ORDER_CJ_PLACEMENT_MODE`, DEFAULT **manual**). **V59** (order
>   scope; dev+prod applied through V58 — check `flyway_schema_history`
>   immediately before first boot): `litemall_order.
>   cj_placement_approved_time` DATETIME NULL + `cj_placement_approved_by`
>   VARCHAR(63) NULL. In manual mode the placement sweep ONLY places
>   paid CJ orders with an approval stamp; auto mode = today's behavior.
>   Admin endpoints on the EXISTING order admin surface (X-User-Roles
>   recipe): `GET .../cj-placement/pending` → paged {orderId, orderSn,
>   addTime, payTime, actualPrice, consignee, country, items[], cjReady
>   (bool: variants resolvable), holdReason?}; `POST .../{orderId}/
>   cj-placement/approve` → stamps approval (approved_by = X-User-Id;
>   idempotent; typed refusal when not paid / not CJ / already placed);
>   the sweep then places on its next tick. Unapproved orders NEVER
>   place, even with CJ configured.
> - **admin notify mail**: on order PAID, enqueue an ADMIN notification to
>   `litemall.customer-mail.admin-notify-email` (env
>   `LITEMALL_CUSTOMERMAIL_ADMIN_NOTIFY`, prod = contact@trovemo.com;
>   blank ⇒ no-op) through the EXISTING mail outbox/SMTP (Brevo live):
>   subject "New paid order <sn> — $<amount>", body = order summary
>   (items, buyer country, total) + "approve it for fulfilment in the
>   admin panel". Rides the same enabled flag as customer mail.
> - **gateway-admin**: order panel gains a "Pending CJ approval"
>   filter/tab (count badge), order detail gains "Approve for CJ
>   fulfilment" (confirm dialog, result verbatim, shows approval stamp
>   after). No blocking dependency — code to THIS contract, not the
>   order branch.
> - Deploy activation (main session): rebuild order + gateway-admin; set
>   CJ_API_KEY (held value) + CJ_EMAIL (= CJ_CATALOG_EMAIL) +
>   LITEMALL_ORDER_CJ_PLACEMENT_MODE=manual +
>   LITEMALL_CUSTOMERMAIL_ADMIN_NOTIFY=contact@trovemo.com together;
>   watch the boot (the half-config guard + the one unexplained unhealthy
>   first-arm) and confirm orders 7/10/11 appear in the pending list and
>   place ONLY on admin approval; user reviews orders 7/10 for test-
>   purchase status BEFORE approving them.
>
> **Wave 23 STATUS: SHIPPED + DEPLOYED + CJ ARMED (2026-08-09 CET, prod
> schema V59).** Halves: order `3ed350725` (V59, MANUAL gate, admin
> pending/approve, admin-notify mail; 256/0) + gateway-admin `9e162e643`
> (pending tab + approve, jest 100/100, live dev 21/21) + detail-stamp
> projection `0dcd94892`. ARMING (main session, staged): CJ_API_KEY
> (user-supplied) + CJ_EMAIL (= catalog email, copied server-side) +
> mode manual + notify contact@trovemo.com set TOGETHER in .env.prod
> (backup .env.prod.bak-wave23-arm); order recreated (V59 applied,
> healthy) then gateway-admin. **Arming caught a landmine:** order yml
> hardcoded `spring.cjdropship.api.sandbox: true` ("flip off in
> production" was never wired) — an approved order would have been CJ-
> SIMULATED, a fake success. Fixed `a646d52e5`: yml placeholder
> `LITEMALL_ORDER_CJ_SANDBOX` (default true = dev-safe), compose
> passthrough, prod .env.prod=false; order rebuilt + recreated. Boot
> verified: "CJ dropshipping ENABLED (sandbox=false)" + "CJ placement
> mode: MANUAL"; sweep `selectApprovedPlaceableCjOrderIds` → Total: 0;
> orders 7/9/10/11 unplaced/unapproved in DB; pending endpoint
> 401-routed; smoke 200s. Checkout delivery options now serve LIVE CJ
> freight (V52 chooser was on honest-empty fallback). USER-SIDE NEXT:
> review orders 7/10 (pre-Stripe wallet buys — likely test purchases;
> cancel/refund instead of approving if so), then approve real orders in
> the admin pending tab — approval spends real money from the CJ account
> balance (auto-pay-balance on). EU orders park on missing IOSS until
> the CJ dashboard IOSS option is set.
>
> **Wave 24 (2026-08-09) — EUR STOREFRONT: single-currency flip for the
> DE/FR/DK/SE market.** USER DECISIONS (2026-08-09): the store prices and
> charges **EUR** storewide (single currency, NOT multi-currency); lead
> marketing category = **Home, Garden & Furniture** (Home Improvement
> adjacent) — recorded for the upcoming feed/ads waves, NO code impact
> this wave; EU local payment methods (Klarna/SEPA/iDEAL/Bancontact/
> MobilePay) are enabled USER-SIDE in the Stripe Dashboard AFTER this
> wave deploys (the Stripe adapter already sends
> `automatic_payment_methods=true` — verified, no code needed there).
> Audit facts (2026-08-09, verified against master + prod DB):
> - Pricing math has ONE home: `CjPricing` (retail = cost × margin,
>   per-L1 overrides). CJ costs land in USD at TWO seams:
>   `CjSnapshotSyncService.toRow()` and `CjDetailEnrichmentService`
>   (per-variant). Everything downstream (margin guard, insight, deal
>   floors, coupon guard) is ratio- or cost-based — landing cost AND
>   retail in EUR keeps it all coherent with ZERO changes there.
> - Currency labels: `litemall.goods.currency` (default "USD") feeds
>   /srv/goods/meta JSON-LD + meta-catalog.csv (single source of truth);
>   Stripe charge currency = `LITEMALL_ORDER_STRIPE_CURRENCY` (usd).
>   Storefront + admin SPAs hardcode "$" at money display sites
>   (13 toFixed money files in the customer SPA).
> - CJ freight quotes arrive in USD (`CjFreightQuoteService`, order) and
>   flow to the V52 chooser + the server-side submit recompute — ONE seam.
> - Prod USD-denominated live rows are SMALL (checked 2026-08-09): 1
>   active coupon, open paid orders 7/9/10/11 (order history is NEVER
>   rewritten — mixed-currency history is ACCEPTED and renders as plain
>   numbers), possibly active flash-deal swaps (`original_sku_prices`
>   JSON) + combinations; wallet balances checked at deploy.
> - **NO Flyway migration this wave** (prod applied through V59; every
>   worktree expects NONE). The storewide flip is a DEPLOY-DAY script
>   (MAIN session): one transaction converting
>   `litemall_goods.{cost,retail_price,counter_price}` +
>   `litemall_goods_product.{cost,price}` + live coupon/deal/swap/
>   combination money ×fx, then full reindex. After the flip, the
>   fx-at-cost-landing seams keep new arrivals EUR automatically.
> **Wave-24 CONTRACT (worktrees code to THIS, not to each other's
> branches):**
> - Shared env **`LITEMALL_FX_USD_EUR`** (decimal; DEFAULT 1.0 =
>   identity, dev-safe; prod sets the real rate at deploy). EXPLICIT yml
>   placeholders everywhere (the admin-notify-email relax-binding
>   lesson). 2dp HALF_UP after multiplication, always.
> - **goods-management:** multiply CJ USD amounts by fx AT COST LANDING
>   (both seams) so cost and retail persist EUR; verify
>   `LITEMALL_GOODS_CURRENCY` actually env-binds
>   `litemall.goods.currency` (add an explicit placeholder if not) so
>   feed/meta/JSON-LD flip by env alone; confirm the Wave-14
>   margin-override reprice paths stay coherent (they reprice FROM the
>   stored cost — automatic once cost is EUR).
> - **order:** multiply CJ freight amounts by fx at
>   `CjFreightQuoteService` (covers chooser + submit recompute + persisted
>   freight_price). Stripe charge currency stays env-driven — no code.
> - **gateway-api:** ONE shared money formatter module (€, 2dp)
>   replacing every $-money display site; Matomo ecommerce events + Meta
>   Pixel events carry currency "EUR".
> - **gateway-admin:** same formatter swap ($→€) across admin money
>   surfaces (insight, orders, coupons, deals, dashboards).
> - Money stays plain decimals; NO currency columns; errno envelope
>   unchanged; NO changes to stored order history.
> **Wave 24 STATUS: SHIPPED + DEPLOYED — THE STORE CHARGES EUR
> (flipped 2026-08-09 ~18:45 UTC, rate 0.866, master `c6f7fa3bf`).**
> All four halves + the PDP variant-tiles rider live. Flip executed as
> two user-fired guarded scripts (classifier boundary): part 1 = env +
> decimal SQL (avg retail €15.33, freight 6.93; DIED at the JSON step —
> ⚠ `litemall_cj_product` keys on `pid` varchar, not `id`); part 2 =
> JSON conversion (72 seckill swap maps + 27,093 variants_json —
> derived `variant_price` only, raw CJ USD fields untouched) + recreate
> all four + machine-token reindex (client-credentials gateway-api @
> authserver:8089 + X-User-Id/X-User-Roles → `{"indexed":13585}`) +
> smoke. Verified live: PDP €5.32/EUR exact (was $6.14), JSON-LD EUR,
> new bundle `main.183c3207…`, order boot "Stripe payments ENABLED
> (currency=eur)". Deliberately skipped: dead-feature tables (bargain/
> topic/shipping-templates/recharge/user-level/groupon_rules legacy).
> Known follow-ups: /meta-catalog.csv + sitemap money regenerate with
> the 03:30 nightly (stale-USD until then, nothing consumes them yet);
> deal/promo candidates re-propose in EUR at 04:30/04:45.
> USER-SIDE after deploy: ~~Matomo ecommerce currency SITE SETTING →
> EUR~~ (MOOT, verified 2026-08-10: Matomo was NEVER ACTIVATED in prod —
> live site-config serves matomoUrl/matomoSiteId null; the open item is
> whether to activate analytics at all: deploy the marketing-stack
> Matomo container + envs, or rely on the live first-party event log +
> Meta Pixel for now); enable EU payment methods in the
> Stripe Dashboard (Klarna/SEPA/iDEAL/Bancontact/MobilePay — appear in
> checkout with no rebuild); Stripe Tax registrations + activation;
> ONE real-card live EUR purchase end-to-end.
>
> **Wave 25 (2026-08-09, QUEUED — starts after Wave 24 merges; same
> worktrees) — MERCHANT FEED QUALITY + SUPPLIER/BRAND ATTRIBUTION.**
> USER DECISIONS (2026-08-09): CJ `supplierName`/`supplierId` become an
> honest **"Store"** attribution on the PDP (NEVER presented as a
> consumer "Brand"); the architecture must accept a FUTURE brand/
> supplier API with no schema rework (adapter seam + source-tagged
> rows); feed stops claiming `brand=Trovemo` (misrepresentation risk).
> Audit facts (2026-08-09 scan, verified against master + prod):
> - `litemall_brand` exists (49 legacy seed rows; **0/13,492** on-sale
>   goods carry brand_id>0); `goods.brand_id` ready; the promote path
>   ALREADY resolves brand strings → rows
>   (`CjProductPromotionService.resolveBrandId`, currently dead because
>   `CjSnapshotSyncService.java:207` sets brand null — "CJ has no brand
>   for most items").
> - CJ detail DTO parses `supplierName`/`supplierId` TODAY but nothing
>   persists them; CJ has NO brand field in list OR detail APIs.
>   **Live probe (2026-08-09, prod creds, n=4 valid):** supplierName
>   populated ~half the time, and the values are RAW LEGAL-ENTITY
>   names ("Wenling Chengdong Jiuwei Shoe and Hat Business", "XIN BO
>   EDUCATIONAL CONSULTATION PTE. LTD.") — unpolished for customer
>   display. Hence the display-curation gate below. The wave's FIRST
>   deliverable stays a full coverage probe over an enrichment
>   rotation; honest degradation when absent (row simply absent).
> - ⚠ CJ API has a DAILY POINTS budget shared account-wide (observed
>   exhausted at 85,600 used after the nightly catalog run; resets
>   daily; exhaustion answers errno 16900500). Supplier capture MUST
>   ride the existing enrichment rotation — no extra standalone CJ
>   call loops.
> - SPA has ORPHANED brand surfaces: `/brands` + `BrandDetail.tsx`
>   (header + goods via `/srv/goods/list?brandId=`). The PDP renders NO
>   brand/store row at all.
> - Feed gaps (live-verified): `google_product_category` empty on ALL
>   rows, no `identifier_exists`, description==title for most rows,
>   brand hardcoded "Trovemo", 23 on-sale goods still Chinese-named.
> **Wave-25 CONTRACT (worktrees code to THIS):**
> - **V60** (goods-management scope; check `flyway_schema_history`
>   immediately before first boot — prod applied through V59, Wave 24
>   claims NONE): `litemall_brand` gains `source` varchar(31) NOT NULL
>   default 'manual' ('manual' | 'cj-supplier' | future API names),
>   `external_id` varchar(63) NULL, `kind` tinyint NOT NULL default 0
>   (0 = consumer brand / 1 = supplier store), `display_enabled`
>   tinyint NOT NULL default 0, UNIQUE (source, external_id).
>   Existing rows → manual/kind 0/display_enabled 1.
> - **Display-curation gate (probe-driven):** provider-created rows
>   land display_enabled=0 — captured and linked (goods.brand_id set)
>   but NOT rendered until an admin renames the store to something
>   customer-worthy and enables it (raw CJ legal-entity names must
>   never render as-is by default). Manual rows default enabled.
>   Curation = the existing admin brand CRUD + an enable toggle (tiny
>   gateway-admin half; if no brand panel exists, a minimal
>   list+rename+toggle lands under the existing admin surfaces).
> - **Attribution seam (the future-API hook):** goods-management
>   interface `AttributionProvider` — in: goods/snapshot context; out:
>   `{kind, name, externalId, logo?}`. Impl #1 = CJ supplier (fields
>   captured at detail enrichment). A future brand/supplier API = ONE
>   new provider bean writing the SAME table via the SAME upsert
>   (keyed source+external_id) — no schema change, no SPA change.
>   Providers NEVER overwrite a manual admin assignment (manual wins;
>   provider writes only fill brand_id==0 or rows they own).
> - Enrichment persists supplier fields; promote links
>   `goods.brand_id` through the existing resolveBrandId seam
>   (extended to source+externalId+name).
> - **Display semantics (gateway-api):** kind=1 renders as "Store" —
>   PDP row "Sold by <name>" + "More from this store" linking the
>   EXISTING brand page; kind=0 renders as "Brand". A supplier is
>   NEVER labeled "Brand". PDP row absent when brand_id==0 (no fake
>   attribution).
> - **Feed rules:** `brand` column filled ONLY from kind=0 rows; else
>   blank + `identifier_exists=false`. Same single feed artifact
>   (extra columns are legal for both Meta and Google) additionally
>   gains real `google_product_category` (static CJ-L1 → Google
>   taxonomy map) and brief-derived descriptions (Wave-13 sanitizer)
>   replacing title-duplicates.
> - Catalog hygiene rides along: the 23 Chinese-named goods renamed
>   (or off-saled with a reason) — they poison feed review. ALSO
>   (spotted 2026-08-09): some goods carry a Chinese `litemall_goods.
>   unit` glyph ("件") that renders beside the € price on the PDP —
>   normalize units (map to "pc"/blank) in the same hygiene pass.
> - errno envelope; no new anonymous paths; money untouched (Wave 24
>   owns money).
> **Acceptance (dev):** coverage probe logged; an enriched good gets a
> brand row (source='cj-supplier', kind=1, display_enabled=0) +
> goods.brand_id set with NO PDP row yet; after admin rename+enable the
> PDP shows "Sold by" and the store page lists that supplier's goods; a
> manually created kind=0 brand renders as "Brand" AND lands in the
> feed brand column while the supplier-attributed good exports blank
> brand + identifier_exists=false; feed validates for both Meta and
> Google column rules; admin brand CRUD regression-green.
>
> **Wave 24.1 (2026-08-10) — CHECKOUT MONEY HONESTY: courier upgrade-delta
> pricing + scoped PDP coupons + coupon empty-state reasons.** USER
> DECISIONS (2026-08-10): freight = **UPGRADE-DELTA** (standard shipping
> keeps the flat-rule promise incl. free ≥ litemall_express_freight_min;
> picking a faster courier charges exactly the CJ price difference);
> PDP shows ONLY coupons whose scope matches that product/category; the
> two junk prod test coupons are EXPIRED (status=1, 2026-08-10 — create
> real coupons in admin, the margin guard protects profitability).
> Audit facts (2026-08-10, code-verified): the V52 courier chooser is
> INFORMATIONAL — `FreightCalculationService` short-circuits CJ carts to
> the legacy flat rule and pricing calls `chooseLogistics(options, null)`
> (CjFreightQuoteService:99 — the null is where the pick should flow);
> the customer's `cjLogisticName` is persisted and used at CJ placement,
> so the STORE pays the real courier price while charging flat €6.93 —
> margin leak on every upgrade. The checkout preview endpoint takes NO
> courier param; the SPA sends the pick at submit only. PDP
> `CouponStrip.tsx` reads `/srv/coupon/list` UNFILTERED (goods-scoped
> test coupon rendered on every PDP). Checkout's picker lists only
> usable coupons — an inapplicable coupon vanishes silently, reading as
> "coupons don't work". Coupon selectlist responses are BARE ARRAYS
> (legacy shape — do not change it in place).
> **Wave-24.1 CONTRACT (worktrees code to THIS):**
> - **order — upgrade-delta freight:** charged CJ freight =
>   `flatComponent + max(0, selectedOption.price − defaultOption.price)`
>   where flatComponent = today's ladder result (incl. FREE_MIN) and
>   defaultOption = the `chooseLogistics(options, null)` pick; all
>   prices post-fx (Wave-24 seam). Applies IDENTICALLY at preview and
>   submit: the checkout preview endpoint gains optional
>   `cjLogisticName`; submit reads it from the existing command field.
>   Unknown/absent name ⇒ delta 0 (today's charge — never an error).
>   The order row's freight_price carries the full charged amount.
>   **Contract raise — SUPERSEDED by the shipped implementation
>   (2026-08-10, order merge `39db2bbbb`):** `cj.options[]` entries
>   expose **`upgradeDelta`** (post-fx decimal; 0.00 ⇒ "Included";
>   null ⇒ unpriceable, render no label) — NOT raw `price`; raw CJ
>   courier costs are never surfaced. The SPA renders upgradeDelta
>   verbatim (no client math); the CHARGED delta stays
>   server-authoritative at preview/submit; SPA degrades-honest to
>   label-less options when the field is absent.
> - **promotion — scoped list + reasons:** `GET /srv/coupon/list` gains
>   optional `goodsId`: response keeps its exact shape but includes
>   ONLY coupons matching that goods (whole-catalog coupons included;
>   goods-scope must contain the id; category scope ancestor-expanded —
>   Wave-18 semantics; promotion derives the goods' categories
>   server-side). Omitted param = today's behavior (coupon center).
>   Selectlist: NEW optional param `verbose=true` switches the response
>   to `{usable:[...], unusable:[{...coupon, reason, minGap?}]}` with
>   typed reasons `threshold|scope|expired|exhausted` (`minGap` = amount
>   still to spend, threshold only); WITHOUT the param the legacy bare
>   array is byte-identical (order-service facade and old SPA untouched).
> - **gateway-api (worktree FREE — its Wave-25 attribution half shipped
>   2026-08-10, deployed `f41c3bda8`):** CouponStrip passes `goodsId` (no
>   client-side scope guessing); checkout coupon cell calls
>   `verbose=true` and renders unusable coupons greyed with the reason
>   verbatim ("Spend €X more…", "Not valid for these items", …);
>   delivery-option chooser labels each courier with its upgrade delta
>   ("+€3.20" / "Included") and sends `cjLogisticName` on the PREVIEW
>   call (add to the effect deps) so the total updates live with the
>   pick; € formatter everywhere.
> - errno envelope; money plain decimals; NO migration anywhere.
>
> **Wave 24.1 STATUS: SHIPPED + DEPLOYED to trovemo.com (2026-08-10,
> master `caeadd828`).** order `39db2bbbb` (278/0) + promotion
> `12f928ce9` (155/0) + gateway-api `caeadd828` (jest 125/125, dev e2e
> 7/7 incl. scoped-strip both-directions + greyed reasons + byte-shape
> checks). Deploy: 3 containers recreated healthy, smoke 200s, scoped
> list live. ⚠ The order image carried Wave-25's V60 — applied at boot
> (success=1, prod schema NOW AT 60, additive) — the Wave-25 goods/
> admin container deploy rides on top with NO migration left to run.
> Dev had no CJ creds ⇒ first nonzero "+€x.xx" delta check = prod
> storefront smoke (gateway-api session running it). USER-SIDE: create
> real coupons in admin (the junk test pair was expired 2026-08-10).
>
> **Wave 25 STATUS: SHIPPED + DEPLOYED to trovemo.com (2026-08-10 ~02:2x
> UTC; prod schema V60).** All halves live: goods-management `31922ed8b`
> (attribution seam + feed quality + hygiene), gateway-admin `3ee441669`
> (curation UI), gateway-api `65ab8c75b` (Sold-by surfaces, fail-closed).
> Deploy evidence: startup refresh 543 new / 13,257 updated, promote
> 27,640/0 failed; reindex 14,132; sitemap 14,147 URLs; feed 14,131 rows
> with google_product_category on ALL rows + blank brand +
> identifier_exists=false (Merchant-Center-valid); hygiene run scanned
> 27,879 → 239 unit glyphs normalized, 0 renames needed (the audit's 23
> CJK-named goods had already aged out via retire cycles); live bundles
> verified by CHUNK CONTENT (SoldByRow 493 / BrandList 923 / upgradeDelta
> 340); smoke all green. Known limits: CF serves a cached pre-deploy
> feed copy until it ages out/nightly regen — the .env.prod
> CLOUDFLARE_API_TOKEN is DNS-SCOPED and CANNOT purge cache (a
> purge-scoped token is needed if deploy-day purges are ever wanted);
> ⚠ CF 403s python-urllib UA silently — use a browser UA for chunk
> sweeps. USER-SIDE: admin curation click-through (rename + enable a
> supplier store → PDP "Sold by" + store page appear) and Merchant
> Center registration with the live feed.
>
> **Wave 26 (2026-08-12, COMMISSIONED — assignment lands when Wave 25.1
> finishes) — COMMERCIAL VIABILITY: anchor-category focus, price floor +
> margin reset, EU-warehouse sourcing truth, content de-duplication.**
> Origin: two external strategy conversations reviewed 2026-08-12. Their
> STRATEGIC frame is accepted (narrow to one category; seasonality INSIDE
> a category, never rotation across categories; keep the CJ pipeline broad
> and the storefront narrow; unpublish, never delete; treat own analytics
> as directional below ~100 conversions; external signal — Trends, keyword
> volume, CJ `listedNum` — until then). Their TECHNICAL phases are
> DISCARDED: they describe a Vue SPA behind nginx needing a prerender
> layer, JSON-LD, a sitemap and funnel instrumentation — all of which are
> React-based and ALREADY LIVE here (Waves 13/22/24/25). **Do NOT rebuild
> head injection, JSON-LD, sitemap, robots.txt, event instrumentation, or
> EU payment plumbing.** Re-verified live 2026-08-12: PDP head injection
> serves `<title>` + og:* + one JSON-LD Product (`"price":"5.31"`,
> `"priceCurrency":"EUR"`, InStock); sitemap 15,274 URLs; robots.txt live
> with /user,/checkout,/cart disallowed.
> **Audit facts (2026-08-12, measured on the LIVE prod feed
> https://trovemo.com/meta-catalog.csv, 15,258 rows — NOT dev; dev prices
> are stale pre-flip and uncosted, do not reason from them):**
> - Median price **€6.83**; mean €16.14; p25 €2.94; p75 €13.13. **79.5%
>   (12,135) under €15**; only **8.9% (1,357) in the €25–80 band**.
> - **1,056 SKUs priced under €1** (e.g. a €0.25 RGB cable, a €0.54 USB-C
>   cable). With flat freight €6.93 and CJ cost these are guaranteed
>   losses, a Merchant-Center review risk, and the most likely source of
>   the "$0 checkout" impression in the external conversation. **No
>   evidence of a genuine zero-amount bug exists** — live catalog prices
>   are non-zero EUR and orders 106/107/111/113/114 all charged and
>   refunded correct amounts. Treat a real €0 report as Phase-0 blocking
>   ONLY if the user supplies page + product + total + Stripe intent.
> - Pricing is `retail = cost × 1.25` (Wave 12) ⇒ gross margin is 20% of
>   sale price ⇒ **≈€1.37 gross profit per order at the median**, against
>   DE/DK Shopping CAC of €15–40. **Paid acquisition is arithmetically
>   impossible at current pricing, in any category.** Category count is
>   NOT the binding constraint — price is.
> - **48% of feed rows (7,369) have description == title** — the Wave-25
>   brief fallback only fires where CJ detail enrichment landed.
> - Live per-category reality (Google taxonomy, in-band = €25–80):
>   Apparel & Accessories 6,586 / median €7.79 / 5.0%; Home & Garden
>   1,627 / €6.95 / **15.1% (245)**; Hardware 659 / €15.70 / **23.7%
>   (156)**; Electronics 1,655 / €5.91 / 13.0%; Health & Beauty 1,214 /
>   €2.53 / 3.2%.
> - **CJ EU-warehouse filtering (`countryCode` + `verifiedWarehouse=1`) is
>   NOT implemented anywhere.** Everything ships from China today ⇒ 10–20
>   day delivery into a next-week-delivery market. This is the single
>   largest un-built lever and it GATES the anchor choice.
> **USER DECISIONS (2026-08-12):** reduce 14 L1 categories to **ONE
> anchor — Home, Garden & Furniture** (already the recorded lead category
> since 2026-08-09; lightest regulatory load — non-electric home goods
> dodge EN71/WEEE/RoHS/battery rules; 245 in-band SKUs ≫ the ~30 needed),
> **hard ceiling of TWO**: the optional second is Hardware / Home
> Improvement (highest in-band share, same "home" audience, reinforces
> rather than resets topical authority). Apparel is REJECTED as anchor
> despite being the largest bucket (5% in band, sizing returns, textile
> labelling). Switching rule fixed NOW while unbiased: **category two goes
> live only after the anchor holds profitable CAC for 60 consecutive
> days.** Non-anchor categories are OFF-SALED, never deleted — data stays
> in MySQL, CJ jobs keep running.
> **PHASE 1a RESULT (measured 2026-08-13, prod CJ account; full report +
> raw rows: `litemall-goods-management/docs/spec-wave26-eu-sourcing.md`
> + `data-wave26-cj-warehouse-survival.csv`):** **Germany is CJ's ONLY EU
> warehouse** — ES/CZ/IT/NL/BE/PL/SE/DK/AT and "EU" all return 0; GB has
> stock but is post-Brexit. Storewide DE share of CJ supply = **0.30%**
> (594 of 201,089 across 84 sampled leaves); 25 of 84 leaves hold any DE
> stock, mostly single digits; catalogue-wide extrapolation ≈ **3,800
> DE-stocked products** (order of magnitude, not a count). **EU stock is
> a property of individual SKUs, not of categories.** Consequence that
> rewrites Phase 2: the existing 15k catalogue CANNOT be filtered into an
> EU-stocked store (0.30% would leave ~40 products) — EU sourcing must
> **ACQUIRE** DE-stocked SKUs via `/product/list` with `countryCode=DE`
> (ONE code, 4-char max — no lists) + `minPrice`/`maxPrice`, not filter
> what we already mirror. Densest: Home Improvement 5.18% (though 365 of
> its 394 sit in Garden Tools alone), Computer & Office 2.43%, Home &
> Garden 0.95%; everything else ≤0.37%.
> **USER DECISIONS (2026-08-13, after Phase 1a):**
> - **Anchor = the home/garden/tools CLUSTER** — Home, Garden & Furniture
>   **+ Home Improvement** treated as ONE merchandising anchor. Home
>   Improvement is promoted from "optional second" to equal billing: it
>   holds both the densest DE stock and the best €25–80 price band
>   (Hardware 23.7% in-band, median €15.70 — the highest of any category).
>   Same audience, so authority compounds instead of resetting. The
>   60-day-profitable-CAC rule now governs adding a THIRD category.
> - **Margin 1.25 → 2.5** on the anchor, via the EXISTING Wave-14
>   per-category override (bounds 1.05–3.0, so 2.5 is in range) —
>   SIMULATE first, then apply. Global default stays 1.25 for anything
>   still on sale outside the anchor (moot once Phase 2 off-sales them).
>   Modelled against the live feed: anchor median €9.13 → €18.26; gross
>   profit per order at the median **€1.83 → €10.96**; in-band €25–80
>   share 17.5% → **24.0% (549 SKUs)**, whose gross is €15.10–47.64 —
>   the first point in this wave where the numbers clear a €15–40 CAC.
>   ⚠ Ads should target the ~549 in-band anchor SKUs, NOT the median
>   product: 35.3% of the anchor still sits under €10 even after 2.5×.
> **PHASE 2 STATUS (2026-08-13):** code deliverables DONE on
> `fix/goods-management` — 5/5 QPS retry (`fa593f8fc`), 4/5 sourcing
> filters + 2/5 price floor (`556abad5f`), runbook (`8eae073d7`); module
> suite 371 run / 0 failures / 8 skipped. Deliverables **1 (margin 2.5)
> and 3 (narrowing) are DEPLOY-TIME operations, NOT worktree code** —
> both ride existing machinery. They were deliberately NOT run on dev:
> the anchor cluster has **ZERO costed goods on dev** (546+520 on sale,
> 0 costed) and `repriceForCategory` returns early without a cost, so an
> override there reprices nothing and would fake a green acceptance.
> Runbook: `litemall-goods-management/docs/runbook-wave26-phase2-anchor.md`.
> **STEP 1 EXECUTED ON PROD 2026-08-13 12:12 UTC — margin 2.5 APPLIED** to both
> anchor L1s (ids `1036143` Home, Garden & Furniture + `1036495` Home
> Improvement — prod ids happen to match dev). Prod cost coverage is REAL,
> unlike dev: 16,395 of 16,431 on-sale goods costed. Simulate baseline
> (record it — this is the before): 1036143 goodsCount **1,753**, avgPrice
> €32.81 → €65.62; 1036495 goodsCount **745**, avgPrice €42.56 → €85.12;
> 2,498 anchor goods total. `margin-overrides` confirms both at 2.50, global
> still 1.25. ⚠ **The reprice lands at the NEXT nightly promote (03:00 sync →
> 03:30 enrich)** — prices had NOT moved at time of writing. Reversible:
> `DELETE /srv/private/admin/insight/categories/{id}/margin`.
> Script: `docker-compose/wave26-anchor.sh` (simulate|status|floor-check|
> apply-margin; read-only by default).
> **STEP 1 VERIFIED LANDED 2026-08-14** (record:
> `litemall-goods-management/docs/wave26-reprice-verification-2026-08-14.md`).
> Measured on the live feed: ANCHOR 2,701 rows, median €9.13 → **€16.90**
> (1.85×), €25–80 band 17.5% → **23.5%** vs a modelled 24.0%; CONTROL median
> **6.63 → 6.63 exactly**, band 7.4% → 7.3% — the unchanged control is what
> proves the move is confined to the two overridden categories and is neither
> a site-wide change nor a cached feed. Within the anchor, **Hardware is the
> strong half**: median €28.90, 34.5% in band (Home & Garden €13.18, 18.8%) —
> ads should target Hardware first.
> **PHASE 2 CODE MERGED + DEPLOYED 2026-08-14** (master `4d1bdf289`; module
> suite 371 run / 0 failures / 8 skipped; container healthy, schema still V60,
> no migration, smoke 200s). The deploy also carried **Wave 25.1** (CJ
> per-variant images + supplier-junk gate), which had been merged but never
> shipped. ⚠ Prod junk-brand cleanup from Wave 25.1 is still OUTSTANDING:
> delete brand row `1046002` (name and external_id both the literal "{}") and
> reset its goods' brand_id — the gate stops NEW junk, it does not clean the
> existing row.
> **PRICE FLOOR DECIDED (user 2026-08-13): `LITEMALL_GOODS_PRICE_FLOOR=5.00`**
> — 429 of 2,286 anchor SKUs off-saled (19%), leaving 1,857; clears the
> sub-€1 tail. Post-reprice the live feed shows **502 of 2,701** anchor SKUs
> under €5 (18.6%) — the projection holds. **NOW UNBLOCKED** (margin has
> landed; floor code is deployed) — still NOT set. ⚠ Two traps, both hit:
> (a) the floor must not precede the margin — at 1.25× prices €5 off-sales
> **808** anchor SKUs instead of 429 while looking correct; (b) the prod
> compose block had **no passthrough** for `LITEMALL_GOODS_PRICE_FLOOR`, so
> setting it in `.env.prod` would have left the floor at 0 while every surface
> said it was configured (the Wave-18 `LITEMALL_GOODS_AUTH_*` failure mode).
> Passthrough added in `4d1bdf289` and verified inside the container.
> Sequence: margin ✔ → cycle ✔ → verify ✔ → floor → cycle → narrow →
> prove a category flips back.
> **BLOCKED-ON-USER (1–2 ANSWERED above; Phase 2 sourcing needs none of
> the rest, Phase 3/4 do):** ~~(1) anchor~~ ✔ ~~(2) margin~~ ✔,
> (3) any real €0 checkout evidence, (4) GSC coverage numbers
> (indexed vs discovered), (5) VAT number + GPSR responsible person +
> jurisdiction, (6) PayPal Business application started (slow clock the
> user does not control — start it in parallel with everything).
> **Wave-26 CONTRACT (worktrees code to THIS):**
> - **Flyway:** prod applied through **V60**. Wave 26 claims **V61**
>   (goods-management scope) ONLY if EU-warehouse capture needs a column
>   on `litemall_cj_product`; check `flyway_schema_history` immediately
>   before first boot. Every other worktree expects NONE.
> - **goods-management — EU sourcing truth (Phase 1, the GATE):** extend
>   the CJ sync/enrichment intake with `countryCode` + `verifiedWarehouse`
>   filtering, config-driven (`litemall.cj.eu-warehouse.*`, env-backed,
>   DEFAULT OFF = today's behavior). FIRST deliverable is a REPORT, not a
>   catalog change: per-L1 EU-warehouse survival counts logged from the
>   existing rotation. ⚠ Rides the EXISTING sync rotation — NO new CJ call
>   loops (shared daily API-points quota, errno 16900500 on exhaustion).
>   If Home & Garden survival is thin, the anchor changes and Phases 2–5
>   must NOT have started.
> - **goods-management — price floor + margin (Phase 2):** config price
>   floor (`litemall.goods.price-floor`, env-backed, decimal, default 0 =
>   off) applied at promote/reprice — sub-floor goods are OFF-SALED with a
>   reason (reversible), never silently repriced. The anchor margin change
>   uses the EXISTING Wave-14 per-category override (`GET /insight/
>   categories/{id}/simulate` → `PUT /categories/{id}/margin`, bounds
>   1.05–3.0) — SIMULATE FIRST and record the numbers; no new machinery.
> - **goods-management — narrowing (Phase 2):** off-sale the non-anchor L1
>   subtrees through the EXISTING retirement/off-sale path so sitemap,
>   meta-catalog.csv and the OCS index follow via the nightly cycle.
>   Reversible; `is_on_sale` is preserved by the nightly promote since
>   Wave 14. Expect a large one-off off-sale batch — admin-gated, staged.
> - **goods-management — content (Phase 3):** kill the description ==
>   title duplication on the SURVIVING catalog (extend the Wave-13 brief
>   sanitizer fallback chain); ~30 in-band anchor SKUs get genuinely
>   rewritten copy (CJ supplier text ranks nowhere).
> - **gateway-api (Phase 2/4):** nav/category surfaces must degrade
>   honestly when 13 L1s go off-sale (no empty category tiles, no dead
>   links); footer legal gains VAT + GPSR responsible person + honest
>   delivery windows once (5) lands; returns/shipping copy matched to what
>   CJ can actually do from EU warehouses (needs Phase 1's answer).
> - **No gateway-admin half is required** — margin overrides, retirement
>   approval and brand curation panels all exist.
> - errno envelope; money plain decimals; € formatter everywhere;
>   nothing customer-visible flips without an admin-gated, reversible step.
> **Phase gates (nothing downstream starts until the gate above passes):**
> P0 user decisions → P1 EU-warehouse survival report (GATE: anchor
> confirmed against real EU stock) → P2 price floor + margin + narrowing
> (GATE: catalog reprices and off-sales cleanly, feed + sitemap + index
> regenerate) → P3 content (fills the 2–6 week indexing wait that cannot
> be compressed) → P4 trust/legal → P5 Merchant Center registration (the
> valid feed has been live and unused since 2026-08-10), then free
> listings, then paid Shopping on 5–10 best-margin in-band anchor SKUs.
> Meta/TikTok only once conversion data exists.
> **Acceptance (dev, through :9000/:8090 unless noted):** EU-warehouse
> survival logged per L1 with real counts and the filter OFF by default;
> price floor off-sales exactly the sub-floor set and NOTHING else, with
> the flip reversible; simulate → apply on the anchor margin moves stored
> retail with marginPct coherent and deal/coupon floors still honored
> (they are cost-based — verify, don't edit); non-anchor off-sale shrinks
> sitemap + feed + index on the next cycle while the rows survive in
> MySQL; zero description == title rows among surviving anchor goods;
> storefront shows no empty/dead category surfaces; module tests green
> with real "Tests run:" counts.
>
> **USER-SIDE PREREQUISITES:** Stripe **LIVE keys are deployed in prod
> (2026-07-31)** — real card payments enabled; live-mode e2e purchase +
> webhook still to be user-verified; `CJ_CATALOG_*` (goods-management
> catalog/enrichment creds)
> is LIVE; order-side CJ is ARMED since 2026-08-09 (real placement,
> sandbox=false, MANUAL admin approval gate — see Wave-23 STATUS; the old
> "key EMPTY / placement blocked" state is history). Everything must degrade honestly: typed errors,
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

### Worktree: `order` — idle (Wave 24.1 order half DONE)
- **No active assignment.**
- **History — Wave 24.1: courier upgrade-delta freight.** Branch commit
  `95954a08f` (2026-08-10; module tests 278/0, 13 new). Charged CJ
  freight = flat ladder (incl. FREE_MIN) + `CjFreightQuoteService
  .upgradeDelta` — max(0, selected − default) over the POST-FX cached
  options; the ONE implementation behind both
  `CheckoutSummaryService.resolveFreight` and the submit pricing.
  `GET /srv/cart/checkout` gained optional `cjLogisticName` (4-arg
  overload keeps old callers byte-identical); submit reads the existing
  command field and persists the full charged amount in freight_price
  (FREE_MIN cart + upgrade = delta only). Absent/unknown pick, CJ
  outage, or unpriceable lines ⇒ 0.00 (today's charge, never an error).
  `POST /srv/order/freight-quote` options each carry an ADDITIVE
  `upgradeDelta` (0.00 = "Included"; null = unpriceable, show no label)
  for the chooser labels — raw CJ costs remain unsurfaced. ⚠ Dev has no
  CJ creds ⇒ live options are empty and every live delta is 0 — the
  delta math is proven at the unit seam (Wave-24 precedent); first
  nonzero-delta verification happens in prod smoke after deploy.
- **History — Wave 24: CJ freight USD→EUR fx at the quote seam.**
  MERGED to master `116aee40e` + pushed (2026-08-09; branch commit
  `1f5078d2e`; module tests 261/0 on the merged tree, 5 new in
  `CjFreightQuoteServiceFxTest`). `litemall.order.fx-usd-eur` (env
  `LITEMALL_FX_USD_EUR`, explicit yml placeholder, default 1.0
  identity) multiplies every CJ freightCalculate amount inside
  `CjFreightQuoteService` — 2dp HALF_UP in the cache loader (cached
  values pre-converted; `quote()` inherits); non-positive/missing rate
  ⇒ WARN + identity (fail-safe). Prod compose passthrough added on the
  order container (dormant at 1.0). **AUDIT CORRECTION (user-approved
  2026-08-09):** customer-charged CJ freight is the `litemall_system`
  flat rule (`litemall_express_freight_value`/`_min`), NOT the CJ
  quote — the V52 chooser shows no prices by design and CJ USD only
  feeds the cheapest-line fallback, so the fx seam is future-proofing
  with no customer-visible effect today; acceptance proven at the
  unit-test seam (dev has no CJ creds ⇒ no live quotes to halve).
  ⚠ MAIN's deploy-day conversion script must decide on the two
  `litemall_system` freight values — they are the amounts actually
  charged as CJ-cart freight.
- **History — Wave 23 (backend): admin-gated CJ placement + admin
  order-paid notify mail.** MERGED to master `3ed350725` + pushed
  (2026-08-08; branch commit `5a3bad2e7`; module tests 256/0). V59
  `cj_placement_approved_time/_by` (all 10 LitemallOrderMapper.xml sites;
  Flyway floor 59); `CjPlacementMode` fail-safe (only the literal `auto`
  disables the gate) gating BOTH `place()` (neutralizes the pay-path
  fast placement) and an approved-only sweep query;
  `GET /srv/private/admin/order/cj-placement/pending` (cjReady = local
  resolveVid, NO CJ calls on request paths; holdReason for
  rejected-park / missing cj_vid / open aftersale / EU-without-IOSS) +
  `POST /{id}/cj-placement/approve` (CAS, idempotent, typed
  [NOT_FOUND]/[NOT_CJ]/[NOT_PAID]/[ALREADY_PLACED] 422s, timeline hop);
  admin-notify mail "New paid order <sn> — $<amount>" — ⚠ env
  `LITEMALL_CUSTOMERMAIL_ADMIN_NOTIFY` binds via an EXPLICIT yml
  placeholder (`admin-notify-email` does not relax-bind); notice NOT
  gated on buyer email. Dev acceptance green end-to-end (order 114:
  paid → held → pending cjReady:true → approved by admin 1 via :18080 →
  sweep visited ONLY the approved order (1 of 18) → typed retryable park
  on dummy-cred CJ auth failure → MailHog got both mails; refusals
  verbatim; V59 applied at boot). Dev trick: dummy CJ_EMAIL+CJ_API_KEY
  make the sweep attempt observable. Prod deploy + arming = MAIN session
  (set CJ_API_KEY + CJ_EMAIL + placement-mode + notify TOGETHER; verify
  orders 7/10/11 pending; order 9 (202) structurally unplaceable).
- **History — Wave 18 (mini): scope facts on coupon redeem.** SHIPPED +
  DEPLOYED 2026-08-06 (`9b4faa292`, merge `bc75d67b5`): redeem passes
  cart goodsIds/categoryIds; module 189/0.
- **History — Wave 10: rich order-confirmation email once an order is
  paid.** (Merged + deployed 2026-07-26, `77c55e027`; activation done —
  Brevo SMTP live since 2026-08-02. Spec in git history.)

### Worktree: `goods-management` — idle (Wave 26 Phase 2 code SHIPPED + DEPLOYED)
- **No active assignment.** Phase 2's code half (deliverables 2, 4, 5) merged
  to master and deployed to prod 2026-08-14 (`4d1bdf289`), together with the
  Wave 25.1 work Task 0 asked for. What remains of Wave 26 is OPERATIONAL and
  belongs to the MAIN session against prod data, in this order: set the €5.00
  floor → one nightly cycle → verify the off-sale count → narrow to the anchor
  (deliverable 3) → prove a category flips back on. Then Phase 3 (content
  de-duplication) is the next worktree-sized task.
- **History — Task 0 (Wave 25.1) and Phase 2 code, original spec below.**
- **Task 0 (HISTORICAL, DONE): LAND WAVE 25.1.**
  Verified by MAIN 2026-08-13: 25.1 is WRITTEN BUT NOT SHIPPED. The
  worktree holds **8 modified files + 2 untracked test files, all
  uncommitted**, last touched 2026-08-10 04:48 (abandoned, no live
  session); `fix/goods-management` is **7 commits behind master and 0
  ahead** — nothing merged. The code looks complete at both seams
  (`CjDetailEnrichmentService` writes `variant_image` into
  variants_json incl. a JSON-array-string tolerant normalizer;
  `CjProductToNativeAdapter` lands it on `product.url` with main-photo
  fallback) plus further uncommitted refinement of the supplier-junk
  gate beyond committed `4ee7154fb`. MAIN deliberately did NOT commit
  it: unauthored, tests never run. **You:** review the diff, RUN the
  tests (real "Tests run:" counts — incl. the 2 untracked
  variant-image tests), commit, `git merge master`, then merge to
  master as usual. If any of it is wrong, fix it — do not discard it.
  Only then start Phase 2.
- **Task — Wave 26 Phase 2: anchor the catalogue.** Code to the FROZEN
  spec `litemall-goods-management/docs/spec-wave26-phase2-anchor.md`
  (read it in full; it is the contract, this is only the index) and to
  the Wave-26 block above. Read `docs/spec-wave26-eu-sourcing.md`
  FIRST — its Phase-1a measurements rewrote deliverable 4.
  **NO Flyway migration** (V61 belongs to Phase 1b; prod applied
  through V60 — check `flyway_schema_history` before first boot
  anyway). Anchor = `Home, Garden & Furniture` **+** `Home
  Improvement` as ONE cluster. Deliverables:
  1. Margin **1.25 → 2.5** on BOTH anchor L1 roots via the EXISTING
     Wave-14 override (`/insight/categories/{id}/simulate` then
     `PUT .../margin`) — SIMULATE FIRST, record before/after in the
     PR. Guards are ratio/cost-based: VERIFY they need no change,
     do not edit them.
  2. Price floor `litemall.goods.price-floor` (env
     `LITEMALL_GOODS_PRICE_FLOOR`, EXPLICIT yml placeholder, decimal,
     **default 0 = OFF**). Sub-floor goods **OFF-SALE with a reason**,
     NEVER a silent reprice (that would break the cost×margin
     invariant every guard relies on). Report dry-run counts for
     floors 1/2/5/10 — the floor VALUE is a user decision, not yours.
  3. Off-sale the non-anchor L1 subtrees through the EXISTING
     retirement path — staged, logged, admin-gated, reversible.
  4. **Acquisition replaces filtering** (Phase 1a killed the filter
     plan: 0.30% storewide DE share would leave ~40 products). Thread
     `countryCode` (**ONE code, 4-char max** — reject comma lists in
     OUR validation before they reach CJ) + `minPrice`/`maxPrice`
     through `CJProductClient` per `CatalogTarget`. ⚠ Those bounds are
     on CJ **cost**, not our retail: at margin 2.5 a €25–80 retail
     band is €10–32 of cost. Absent config ⇒ byte-identical requests
     to today (test it).
  5. **Investigate before fixing:** Phase 1a measured CJ rejecting
     **~17% of `/product/list` calls on QPS even at a 3s pace**, and
     `CjSnapshotSyncService` paces at 3s. If those rejections are
     swallowed, every nightly catalog run is silently dropping pages
     (and it, not `CatalogTarget.limit` alone, may be the real inflow
     ceiling). Measure the rejection rate per run and report it;
     "no loss found" is a valid, valuable result. Fix with bounded
     retry + backoff only if confirmed.
- **Governing rule — REVERSIBILITY (user intent 2026-08-13: run one
  anchor now, take the other categories back later):** narrowing is an
  `is_on_sale` FLIP, never a delete; rows stay in MySQL; the CJ sync
  keeps mirroring ALL 14 L1s (pipeline broad, storefront narrow — do
  NOT prune `catalog-targets`); Wave-14's is_on_sale-preserving promote
  is load-bearing — verify it, don't assume it. Acceptance must PROVE a
  spot-check category flips back ON and reappears in the index.
- **Acceptance:** as written in the Phase-2 spec (all five deliverables
  + module tests green with real "Tests run:" counts — ⚠ the root pom
  skips `mvn test` and surefire misses `@Nested`).
- **History — Wave 25.1 (per-variant image backfill + supplier-junk
  validation), original task spec below — see Task 0 for its real state.**
- **Task (HISTORICAL) — Wave 25.1: capture CJ per-variant images so the PDP photo
  follows the selected variant.** The SPA half is ALREADY LIVE + DORMANT
  in prod (`ba934d8e4`: photo follows selectedSku.url when it differs
  from the main photo; distinct urls join gallery+lightbox; tiles get
  thumbnails) — it lights up product-by-product as THIS backfill lands.
  Verified facts (2026-08-10): `CJProductVariantData` maps NO image
  field (CJ's variant payload carries one — verify CJ's exact JSON key
  against a real response before naming the DTO field);
  `CjDetailEnrichmentService` writes variants_json with
  variant_sell_price/variant_price/stock only; every
  `litemall_goods_product.url` equals the main photo today.
  1. Map the CJ variant image in the DTO; persist it into
     variants_json (`variant_image`, only when non-blank).
  2. Promote writes `litemall_goods_product.url` from variant_image
     when non-blank; main-photo fallback stays; NEVER blank an
     existing url.
  3. Backfill rides the EXISTING enrichment rotation + on-demand path —
     NO new CJ call loops (⚠ shared daily API points quota). NO new
     endpoint (detail already serves products[].url). NO migration
     (variants_json is schema-free; url column exists).
  4. Verify the image host is covered by the `/_cdn` edge rewrite
     (cf/oss-cf hosts are; anything else must fall back to main photo,
     not serve mixed-content).
  5. **Supplier-junk validation (prod finding 2026-08-10):** CJ's
     detail payload delivers the LITERAL STRING "{}" for
     supplierName/supplierId on some products, and the
     AttributionProvider upserts it — prod row 1046002 has name AND
     external_id "{}" (linked to goods 10014602), and
     UNIQUE(source, external_id) funnels EVERY such product onto that
     one garbage row (cross-supplier mis-attribution if ever enabled;
     curation gate keeps it invisible today). Fix: validate BOTH
     fields before upsert — treat "{}", "", "null", whitespace, and
     non-name junk as ABSENT (no row, no link; honest degradation).
     Add the negative-path tests. Cleanup of the existing junk row
     (delete 1046002 + reset its goods' brand_id) = MAIN session at
     deploy; your acceptance just proves no NEW junk rows appear.
- **Acceptance (dev, through :9000/:8090):** unit tests prove DTO
  mapping + variants_json write + promote url landing (mocked CJ
  payload, real counts); if dev CJ creds are available, ONE on-demand
  enrich shows a real product's SKUs with distinct urls and the PDP
  photo switching per variant; degrade paths: blank/absent image ⇒
  main-photo url unchanged. If dev stays credless, the live check
  moves to prod smoke post-deploy (Wave-24.1 delta precedent).
- **History — Wave 25 (attribution + feed quality) SHIPPED + DEPLOYED, spec below.**
- **Status 2026-08-10: Wave-25 backend MERGED to master `d9584114d` +
  pushed; dev acceptance PASSED (incl. both peer halves' cross-half
  acceptance against this V60 dev service — the gateway-api raise about
  raw disabled names on /srv/brand is resolved by the display_enabled
  gating in this merge). Deploy = MAIN session (V60 at boot + reindex;
  run POST /srv/private/admin/search/catalog-hygiene on prod after —
  the 23 Chinese-named goods + unit glyphs).** V60 applied at dev boot (source pre-existed from V23 — V60
  re-baselines 'local'→'manual' instead of adding it; ALSO adds
  litemall_cj_product supplier_id/supplier_name — required, the nightly list
  upsert would erase in-memory-only capture — and flips goods.unit default
  件→''; both deviations user-approved 2026-08-10). Live dev evidence: full
  promote 9,644/0 created the seeded supplier row as (cj-supplier, kind 1,
  display_enabled 0) + linked brand_id with NO public render; SQL-simulated
  rename+enable flipped PDP brand {kind:1} + /srv/brand/list|detail (+kind,
  +goodsCount) + store goods list; a manual kind-0 assignment SURVIVED the
  full re-promote (manual-wins); all 9,638 "件" units normalized via promote;
  feed 14 cols — 0 "Trovemo", supplier rows blank brand +
  identifier_exists=false, manual brand exported, google_product_category
  filled from the L1 map; reindex 9,642 + search smoke green. Tests: module
  334 run / 2 pre-existing live-OCS failures (same two as Wave 24);
  FlywayMigrationTest 4/4 at floor 60. Gotchas: admin machine-token secret is
  env-only in this dev stack — admin endpoints verified 401-routed, the
  rename+enable click-through stays USER-SIDE via gateway-admin; dev
  description==title mostly remains (fallback needs detail_html — only 103
  dev rows enriched; prod coverage is far higher); catalog-hygiene endpoint
  (Chinese-name pass) is unit-tested, prod run = MAIN post-merge.
- **Task — Wave 25 backend: supplier/brand attribution + Merchant-feed
  quality.** Code to the Wave-25 CONTRACT above (V60 spec, guard rails,
  probe-driven display gate). Deliverables in order:
  1. **Coverage probe FIRST**: during the enrichment rotation, log the
     %-populated of CJ `supplierName`/`supplierId` (fields already
     parsed in the detail DTO, never persisted). No extra CJ calls —
     ride the existing rotation (⚠ daily API points quota, see audit).
  2. **V60** exactly per CONTRACT (check `flyway_schema_history`
     immediately before first boot — prod applied through V59, Wave 24
     shipped with NONE).
  3. `AttributionProvider` seam + CJ-supplier impl #1: upsert brand
     rows keyed (source, external_id), kind=1, display_enabled=0;
     promote links `goods.brand_id` via the existing resolveBrandId
     seam extended to (source, externalId, name); providers NEVER
     overwrite manual assignments.
  4. **Feed quality** (same single artifact /srv/goods/meta-catalog.csv):
     drop the hardcoded brand "Trovemo" (brand column ONLY from kind=0
     display-enabled rows); add `google_product_category` via a static
     CJ-L1 → Google-taxonomy map; add `identifier_exists=false` for
     unbranded rows; description falls back to the Wave-13 brief
     sanitizer instead of duplicating the title. Keep RFC-4180 + the
     13-column base intact (extra columns appended are legal for both
     Meta and Google — verify header order stays backward-compatible).
  5. **Catalog hygiene**: rename the 23 Chinese-named on-sale goods
     (translate; off-sale with reason if untranslatable) and normalize
     Chinese `litemall_goods.unit` glyphs ("件" → "pc"/blank).
  6. **Public brand read must respect the curation gate** (RAISED by the
     gateway-api half 2026-08-10): `/srv/brand/list|detail` EXCLUDE
     rows with display_enabled=0 — otherwise raw CJ supplier legal
     names leak through public JSON even though the SPA never renders
     them.
  7. **Per-variant image capture** (commissioned via the PDP session
     2026-08-09; user confirms at plan approval): CJ's variant payload
     carries an image the DTO never mapped — map it in
     `CJProductVariantData`, persist per-variant image into
     `variants_json` at enrichment, and at promote write it to
     `litemall_goods_product.url` ONLY when non-blank (main-photo
     fallback stays; today every SKU url == main photo). Backfill
     rides the existing enrichment rotation + on-demand path — NO new
     CJ call loops (points quota), NO new endpoint (detail already
     serves `products[].url`). The SPA half is ALREADY DONE + MERGED
     (`ba934d8e4`, inert) — it lights up product-by-product as this
     backfill lands.
- **Acceptance (dev, through :9000/:8090):** probe % logged; V60
  applied at boot; an enriched good gets a brand row (cj-supplier,
  kind=1, display_enabled=0) + brand_id linked with NO public render;
  after admin enable the public read exposes it; feed row for a
  supplier-attributed good = blank brand + identifier_exists=false +
  real google_product_category; a manual kind=0 enabled brand lands in
  the feed brand column; zero Chinese-named/unit on-sale goods left in
  dev sample; module tests green with real "Tests run:" counts.
- **History — done (Wave 24 half MERGED to master `31636c79c`)**
- **Status 2026-08-09:** fx at the single CjPricing intake (all four
  raw-USD call sites) SHIPPED (branch `3e4619c24`, suite 318 run / 2
  pre-existing live-OCS failures). Live dev acceptance PASSED (EUR
  label on meta+feed, fx=0.5 boot, margin ratios, identity default);
  the CJ-enrichment leg was blocked by the account's DAILY API POINTS
  exhaustion (errno 16900500 — quota shared with prod nightly syncs).
  `LITEMALL_GOODS_CURRENCY` binding pre-existed — verified, no change.
- **Task (HISTORICAL) — Wave 24: land CJ costs in EUR + env-flippable
  currency label.** Code to the Wave-24 CONTRACT above.
  1. Config `litemall.goods.fx-usd-eur` (env `LITEMALL_FX_USD_EUR`,
     default 1.0, EXPLICIT yml placeholder). Multiply CJ USD amounts by
     fx AT COST LANDING — both seams: `CjSnapshotSyncService.toRow()`
     and the per-variant path in `CjDetailEnrichmentService` — BEFORE
     `CjPricing` runs, so `litemall_goods[_product].cost` and every
     derived retail persist EUR. `CjPricing`, margin guard, insight,
     deal floors are ratio/cost-based and must need ZERO changes —
     verify, don't edit.
  2. Verify `LITEMALL_GOODS_CURRENCY` env-binds
     `litemall.goods.currency` (meta + JSON-LD + meta-catalog.csv all
     read it); add an explicit placeholder if relaxed binding fails
     (the admin-notify-email lesson).
  3. Confirm the Wave-14 margin-override reprice paths stay coherent
     post-flip (they reprice FROM stored cost — automatic once cost is
     EUR); document any exception found instead of patching around it.
  NO migration this wave (prod at V59; the storewide conversion of
  EXISTING rows is a deploy-day script owned by the MAIN session).
- **Acceptance (dev, through :9000/:8090):** with fx=0.5 set, a dev
  sync/enrichment batch lands cost and retail at exactly half their
  prior values with marginPct unchanged (20%) in the insight endpoints;
  with `LITEMALL_GOODS_CURRENCY=EUR` set, `/srv/goods/meta/{id}` serves
  currency EUR and meta-catalog.csv rows read "x.xx EUR"; fx unset ⇒
  identity; module tests green with real "Tests run:" counts.
- **History — Wave 22 (backend): search demand analytics.** SHIPPED +
  DEPLOYED 2026-08-08 (`66fc6d327`, prod V57): nightly 04:45 rollup into
  `litemall_search_stat_daily`, result_count on history writes, insight
  search-stats endpoints, demand-derived trending (curated keywords
  preserved). Status block above.
- **History — Wave 19 (backend): promo candidate intelligence + Wave 20
  content half.** SHIPPED + DEPLOYED 2026-08-08 (W19 `35cd49ced` V53
  scorers/insight/coupon_flag; W20 `77aa8784a`+`f1bf46cd6` V54 page
  category/templates/clone/palette v1.1; W21 groupon_flag `4f04a7295`).
  Specs in the CONTRACT blocks above / git history.
- **History — Wave 18: coupon margin-guard basis endpoint.** SHIPPED +
  DEPLOYED (2026-08-06, in `3168148e7`): `POST /srv/private/admin/
  insight/margin-basis`; FROZEN spec
  `litemall-goods-management/docs/handoff-coupon-margin-basis.md`.
  2026-08-08 prod incident: promotion's compose block was missing
  `LITEMALL_GOODS_AUTH_*` — guard failed closed on every save; fixed
  `046ad1f82`, deployed, user-confirmed. Prod click-through CLOSED.
- **History — Behavioral targeting Phase 0: COMPLETE, MERGED + DEPLOYED
  to trovemo.com (2026-08-05).** All three halves live: V49 event log +
  `POST /srv/track/{collect,consent}` ingest (`ac990d3ab`), order
  purchase/refund listeners (same merge), gateway-api edge+SPA
  (`7ca8d4ca7` — also carried the checkout step-flow). Prod schema at
  V49 (applied out-of-order after V50, clean); live-verified on
  trovemo.com: consent-less batches drop with zero cookies, granted
  consent mints Secure+HttpOnly lm_vid/lm_sid, GET 401s, event rows
  land with CF-IPCountry geo. Contract: `doc/behavioral-events.md`.
  Data ramps only as visitors accept the banner (strict prior
  consent). Next phases (segments/recommendations reading the log)
  are not yet commissioned.
- **History — Wave 14 (backend): inventory governance.** (Merged +
  deployed 2026-07-30, master `61992575c`; V46 applied in prod; dev
  acceptance green end-to-end incl. governor overage sizing, executor
  off-sale flip, margin override reprice at all sites, live-deal price
  lock, auto-deal kill-switch. Spec in git history.)
- **History:** Wave 13 SEO backend SHIPPED (`b4ca2d557`); Wave 12
  inventory intelligence SHIPPED (`68ebcbb38`); Wave 11 banners
  SHIPPED (`308082607`).
- **Wave 14.1 meta catalogue feed: SHIPPED + DEPLOYED** (2026-07-30,
  `c5fdae86f`; live feed validated).

### Worktree: `gateway-api` — idle (Waves 24.1 + 25 halves SHIPPED + DEPLOYED)
- **No active assignment.** Launch with FRESH=1 only after a new wave is
  commissioned and this block is rewritten.
- **History — Wave 24.1 (checkout money honesty SPA), original spec below.**
- **Task — Wave 24.1 storefront half.** Code to the Wave-24.1 CONTRACT
  above: CouponStrip passes `goodsId`; checkout coupon cell renders
  verbose unusable coupons greyed with server reasons; courier chooser
  shows "+€x.xx"/"Included" deltas and sends `cjLogisticName` on the
  preview call (effect deps) so the total tracks the pick live.
- **Status:** the Wave-25 attribution half is DONE + DEPLOYED
  (`65ab8c75b`, deploy `f41c3bda8`, fail-closed live-verified 2026-08-10;
  jest 118/118). RAISED for goods-management V60: public /srv/brand JSON
  must EXCLUDE display_enabled=0 rows (raw supplier legal names leak).
- **History — Wave 25 (Sold-by storefront surfaces)**
- ⚠ **Worktree-sharing rule:** another session (PDP/variant work) may be
  active in THIS worktree. NEVER stash, checkout, or reset files you did
  not author — that already destroyed a peer's in-flight edit once
  (2026-08-09). If the tree is dirty with foreign changes, leave them,
  scope your `git add` to your own files, and raise conflicts through
  the MAIN session. The variant-image SPA pre-wiring is DONE + MERGED
  (`ba934d8e4`) — do NOT redo or touch `variantDisplay.ts` beyond your
  own task's needs.
- **Task — Wave 25 storefront: honest attribution render.** Code to
  the Wave-25 CONTRACT above. PDP gains an attribution row when the
  product's brand row is display-enabled: kind=1 → "Sold by <name>" +
  "More from this store" linking the EXISTING brand page
  (`BrandDetail.tsx` / `/brand/:id` — currently orphaned, verify it
  still works and restyle to the current storefront look); kind=0 →
  "Brand: <name>" linking the same page. brand_id==0 or not-enabled ⇒
  NO row (never fake attribution; a raw CJ legal-entity name must
  never render). Check what the detail payload exposes for brand
  (extend rendering only — any payload gap is goods-management's,
  raise it against the CONTRACT, don't work around). `/brands` index
  page: verify, restyle minimally, badge Store vs Brand.
- **Acceptance (dev, through :9000/:8090):** PDP shows no row for
  unattributed goods; after enabling a supplier row in admin the PDP
  shows "Sold by" + the store page lists that supplier's goods; a
  manual kind=0 brand renders "Brand:"; € formatting untouched
  (formatMoney everywhere new); jest + touched-file checks green with
  real counts.
- **History — done (Wave 24 half + variant tiles SHIPPED, flip live)**
- **Status 2026-08-09:** € sweep `259573d7f` (jest 106/106, live e2e
  all-green, unicode-minus `−${x}` leak sites caught) + Amazon-style
  variant grid tiles `e4cc1f980`+`5a27edfd7` all merged and DEPLOYED
  with the EUR flip (`c6f7fa3bf`). ⚠ Matomo currency = SITE SETTING
  (user-side); DisputePanel `*Usd` field names are store-currency.
- **Task (HISTORICAL) — Wave 24 (€ storefront display)**
- **Task — Wave 24: € everywhere the customer sees money.** Code to the
  Wave-24 CONTRACT above. ONE shared formatter module (e.g.
  `app/shared/util/money.ts`, "€12.34", 2dp) replacing EVERY $-money
  display site in the storefront SPA (13 files carry toFixed money
  sites; also sweep literal "$" renders — e.g. the Wave-18 percent
  coupon "up to $C" string, and the NEW "US $" sites introduced by the
  PDP Amazon-parity commit `bbb2048aa` already sitting on
  fix/gateway-api and not yet on master). Matomo ecommerce events + Meta Pixel
  events (`ecommerce.ts` seam) carry currency "EUR". NO edge/Java work;
  NO backend calls change (amounts arrive as plain decimals as always).
- **Acceptance (dev, through :9000/:8090):** grep shows no remaining
  hardcoded $-money renders in `app/`; PDP, search hits, cart,
  checkout (incl. freight options + coupon lines), order list/detail,
  coupons center, deals and groupon surfaces all render €; with consent
  granted the Matomo dev console shows EUR on the ecommerce events;
  jest + existing checkout/coupon e2e regression-green with real
  counts.
- **History — Waves 19/20/21 (storefront).** All SHIPPED + DEPLOYED
  2026-08-08: W19 coupon badge/facet (`3917c884f`), W20 groupon-strip +
  /page/:id og-meta (`53123bcb0`), W21 group-buy landing
  /groupon/:id + PDP entry + pinkId checkout + groupon_flag badge
  (`01d7b6957`, jest 61/61). Specs in the CONTRACT blocks above.
- **History — Wave 18 (storefront): coupon center + register-gift +
  percent rendering.** SHIPPED + DEPLOYED 2026-08-06 (`72bf228cf`,
  merge `c9b0848a6`): public `/coupons` center, register-gift trigger
  after password+Google signup, percent "N% off (up to $C)" rendering;
  24/24 stackless + 17/17 unit.
- **History — Phase-0 edge+SPA build detail (2026-08-04):** PublicPaths
  TRACK_POST + VisitorIdentityFilter (consent-gated HttpOnly
  lm_vid/lm_sid, rolling session, spoof-strip), firstParty.ts emitter
  wired at all client call sites, banner shows whenever tracking is
  possible (first-party needs no config; DNT still hides), /cookies
  policy discloses the first-party cookies. Edge tests 54/54; headless
  e2e: pre-consent zero cookies/rows, accept -> minted identity +
  page_view/view_item/search rows, stitching + denial expiry
  curl-verified through :8090. Shipped together with the checkout
  step-flow.
- **History — Behavioral targeting Phase 0, edge + emitter half:
  SHIPPED + DEPLOYED** (2026-08-05, master `f04986fb8`, prod V49,
  live-verified). Original task spec below for reference. The backend
  half is DONE (goods-management branch `e4b7ffb5a`, V49 applied on dev,
  live-verified): `POST /srv/track/{collect,consent}` is served by
  goods-management through the existing `/srv/**` catch-all. **Code to the
  committed contract `doc/behavioral-events.md`** (frozen vocabulary, cookie
  names/attributes, header semantics, batch shape) — NOT to this summary.
  1. **Edge (Java):** add `TRACK_POST = /srv/track/**` (POST-only) to
     `PublicPaths` — the second sanctioned anonymous POST after the Stripe
     webhook (user-approved exception 2026-08-04; mitigations in the doc).
     New WebFilter (pattern: `IdentityForwardingFilter`): ALWAYS strip
     inbound `X-Visitor-Id`/`X-Session-Id`; when cookie `lm_consent=granted`
     mint `lm_vid` (13-month) / `lm_sid` (30-min rolling) HttpOnly cookies if
     absent and forward them as those headers on `/srv/**`; when `denied`,
     expire the identity cookies. STRICT prior consent (user decision): no
     cookies, no events, any region, until grant.
  2. **SPA (React):** `app/shared/tracking/firstParty.ts` emitter — buffer,
     5 s / 20-event flush via axios, `navigator.sendBeacon` (Blob,
     application/json) on pagehide/visibilitychange-hidden; whole emitter
     try/catch fail-silent; gate on the EXISTING `consent.ts` store and
     mirror the choice into the `lm_consent` cookie (+ POST
     `/srv/track/consent` on every choice change). Wire client vocabulary:
     existing `ecommerce.ts` call sites (view_item, add_to_cart,
     begin_checkout) + new call sites (page_view via the MatomoTracker
     route hook, view_category, search, click_result, remove_from_cart).
     Client NEVER generates visitor ids and NEVER emits purchase/refund
     (server-side, already done in litemall-order on the same branch).
- **Acceptance (dev, through :9000/:8090):** pre-consent browse ⇒ zero
  cookies, zero rows; grant ⇒ consent row + cookies minted + buffered-then
  -flushed events land in `litemall_user_event` with visitor/session ids;
  login ⇒ stitching row appears (backend does it — just verify); deny ⇒
  cookies expired, no further events; page close mid-buffer ⇒ beacon
  delivers; Matomo/Pixel Wave-15 regression untouched; edge policy tests
  (`EdgeAuthorizationPolicyTest`) extended for TRACK_POST; module + webapp
  tests green with real "Tests run:" counts.

- **Wave 16 STATUS: MERGED to master `1a157a78e` (2026-07-31).** Guest
  checkout (per-checkout shadow accounts — a repeated email NEVER opens an
  earlier guest's session; real-account email ⇒ 706 sign-in prompt; claim =
  authenticated `/auth/guest/claim`, policy-tested), Google Sign-In
  (`LITEMALL_GOOGLE_CLIENT_ID` env-gated; server-side tokeninfo verify,
  707/708 typed; links google_sub → verified email → guest upgrade), teal
  AuthShell on login/register/reset, country dial-code phone input (static
  ITU map + Intl.DisplayNames, E.164), env-gated Places autocomplete
  (`LITEMALL_PLACES_API_KEY`). **V47 applied via the flyway-OWNING services
  (order/goods) — the edge keeps flyway disabled; prod deploy must rebuild+
  recreate the order container BEFORE the gateway.** Dev acceptance: guest
  e2e 13/13 (order 107 wallet-paid by a fresh guest, claimed, DB-verified);
  GIS loads only when configured; Wave-15 regression 17/17.
- **Wave 15 STATUS: MERGED to master `e8adec3fe` (2026-07-31).** All four
  items done: promo-code box (auto-select on redeem), Matomo ecommerce
  events + env-gated Meta Pixel (`LITEMALL_META_PIXEL_ID`; pending-state
  event buffers flush only on a stored grant), checkout email capture
  (required block when account email missing; partial `/auth/profile`
  persist pre-submit), binding legal copy (seller identity in
  `static/seller.ts`; 30-day returns on ALL surfaces incl. FAQ + PDP
  badge; `LegalPlaceholder` DELETED; launch-blocker doc closed). Dev
  e2e 17/17 + legal 5/5 (order 106: coupon discount + wallet pay +
  email landed). Headless gotcha: React controlled inputs need
  NATIVE-SETTER fills + in-page DOM clicks — `page.type`/hit-test
  clicks silently no-op.
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

### Worktree: `gateway-admin` — done (Wave 25 half MERGED + ACCEPTED)
- **Status 2026-08-10:** brand/store curation surface SHIPPED — MERGED
  to master `3ee441669` + pushed (jest 110/110, headless UI 12/12).
  Extended the surviving Brands panel: Kind badge (Store/Brand),
  Source, Visible toggle (full-row /brand/update), goods count; form
  provider block + visibility checkbox + name-only validation on
  provider rows. All new fields render tolerantly pre-V60. CROSS-HALF
  ACCEPTANCE PASSED 10/10 vs the V60 dev backend (admin Hide/Enable
  flips the storefront Sold-by attribution live; rename persists
  end-to-end; state restored). Gotchas: pre-V60 backend 402s unknown
  JSON fields — the SPA sends V60 fields only when the row/user
  carried them; dev row 1046003 is SHARED with the goods-management
  session (state flips mid-test — scripts must be state-agnostic).
- **Task (HISTORICAL) — Wave 25 admin (SMALL half): brand/store curation.** Code to
  the Wave-25 CONTRACT above. Find the existing admin brand surface
  (legacy litemall had brand CRUD — verify what survived the Wave-4
  decommission); ensure an admin can: list brand rows w/ source, kind,
  display_enabled, linked-goods count; RENAME (the curation act — raw
  CJ legal names → customer-worthy store names); toggle
  display_enabled; badge Store (kind=1) vs Brand (kind=0). No create
  flow for provider rows (they arrive via enrichment); manual create
  keeps working. € money display (Wave-24 formatter) on any money.
- **Acceptance (dev, through :18080):** list shows a cj-supplier row
  disabled by default; rename + enable persists and the storefront
  PDP row appears; disable hides it again; jest green with real
  counts; existing panels regression-green.
- **History — done (Wave 24 half MERGED to master `d3b72d1a4`)**
- **Status 2026-08-09:** € formatter across admin money surfaces
  SHIPPED (`ae3ffd01d`, jest 100/100, headless 11/11). CJ balance tile
  deliberately stays "<amt> USD" (external wallet). ⚠ The admin
  container must NOT be rebuilt until the prod flip (it would render €
  on USD data).
- **Task (HISTORICAL) — Wave 24: $→€ across admin money surfaces.** Code to the
  Wave-24 CONTRACT above. Same shared-formatter approach as the
  storefront half (do NOT read its branch): one money formatter module,
  swapped in across insight panels (margins, potential profit, deal/
  promo candidates), orders (incl. the Wave-23 CJ pending tab amounts),
  coupons, deals, dashboards. Mixed-currency history is ACCEPTED —
  pre-flip orders render as plain numbers with the € symbol; no
  per-row currency logic.
- **Acceptance (dev, through :18080):** admin panels named above render
  €, no remaining hardcoded $-money renders in the admin `app/`; jest
  green with real counts; existing panel e2e regression-green.
- **History — Wave 23 (admin UI): CJ approval surfaces.** DONE 2026-08-08
  (`df86b817b` + handoff `1042e63bf`): Orders "Pending CJ approval" tab
  (count badge via limit-1 probe; holdReason verbatim; honest error
  banner when the endpoint is down) + detail "Approve for CJ fulfilment"
  (inline real-money confirm; refusals verbatim; stamp who+when from the
  approve response; "Approved — awaiting CJ placement" never claims
  placed without cjOrderId; 202 refund never approvable). Jest 100/100.
  LIVE DEV ACCEPTANCE 21/21 headless through :18080 against order@master
  (badge 16→15→14; [NOT_PAID]/[NOT_CJ] 422s verbatim; idempotent
  ALREADY_APPROVED; dev orders 110-112 approved — harmless, dummy CJ
  creds). ⚠ Known gap (committed third ask in
  docs/handoff-order-admin-cj.md): admin /order/detail does NOT project
  cj_placement_approved_time/_by — the stamp survives approve-time
  render but not a reload (falls back to the approve button; harmless
  via idempotency). One-line order-side toRow() projection fixes it;
  the SPA already reads both fields tolerantly.
- **History — Wave 22 (admin UI): search analytics + RFM delivery
  surfaces.** SHIPPED + DEPLOYED 2026-08-08 (`76e47cd13`): Search
  analytics panel (top/zero-result queries w/ CTR, totals, Refresh
  trending), Deliver-to-segment dialog (RFM inputs → preview →
  deliver), performance card + deliveries history. Status block above.
- **History — Waves 19/20 (admin UI).** SHIPPED + DEPLOYED 2026-08-08:
  W19 Promo Suggestions panel w/ prefill create + consume
  (`13060e260`, jest 37/37); W20 page category/template filters +
  "New from template" + Postiz source picker (`8efc7197d`, jest
  57/57). Specs in the CONTRACT blocks above.
- **History — Wave 18 (admin UI): coupon scoping + percent + guard
  surfacing.** SHIPPED + DEPLOYED 2026-08-06 (`4271d5281`, merge
  `9fbe80120`): scope selector (killed the goodsType:0 hardcode),
  percent fields, guard rejections verbatim (`guardError` in envelope
  data, HTTP 400), uncosted warning; jest 19/19 + headless 16/16.
- **History — Wave 17 (admin UI): "Social Publishing (Postiz)" panel —
  product choice happens HERE.** Code to the Wave-17 CONTRACT above;
  do NOT read the promotion branch.
  1. New sidebar panel, HIDDEN unless `GET
     /srv/private/admin/promotion/postiz/status` reports enabled.
  2. Category selector → product picker reusing the existing insight
     goods list (`/srv/private/admin/insight/goods/list?categoryId=`):
     picture, name, price, margin, deal badge, "posted N days ago"
     badge (from preview warnings); multi-select capped at 25.
  3. Channel checkboxes from `GET .../postiz/channels` (dynamic —
     whatever is connected in Postiz appears; `supported:false` rows
     rendered disabled with their reason).
  4. Start time + interval controls → Preview (rendered post cards
     per channel from `/preview`, incl. warnings) → Publish →
     per-product/per-channel result table (errors shown verbatim).
     History tab over `/log`.
- **Acceptance:** panel hidden when status says disabled; with dev
  Postiz up: pick a category, select 3 products + the Facebook
  channel, set start+interval, preview matches the contract shapes,
  publish shows ok rows with postizPostIds and the posts appear in
  Postiz's calendar; a failing channel shows its error in the table;
  history lists the batch; existing admin panels regression-green.
- **History:** Wave 14 governance UI SHIPPED (`61992575c` /
  `02ed9eea8`); Wave 12 insight UI SHIPPED (`fbb29812e`); Wave 8
  branding SHIPPED (`72446568f`).

### Worktree: `platform`
- **No Wave-8 assignment.** The Wave-7 stack is merged and DEPLOYED (prod compose,
  TLS via Caddy DNS-01 behind Cloudflare, CI). Do not start work here without
  a new instruction.
- **Informational:** the caddy image is a custom build
  (`docker/caddy/Dockerfile`, cloudflare DNS module); `CADDY_ACME_DNS` is
  env-injected — an empty value must keep staging bootable (see Caddyfile
  comment). CI's gitleaks job was RED on master per
  `docs/handoff-secrets-wave7.md` — fix belongs here if picked up later.

### Worktree: `promotion` — idle (Wave 24.1 half SHIPPED + DEPLOYED)
- **No active assignment.** Launch with FRESH=1 only after a new wave is
  commissioned and this block is rewritten.
- **History — Wave 24.1 (scoped coupon list + reasons), original spec below.**
- **Task — Wave 24.1: scope-filtered public coupon list + verbose
  selectlist.** Code to the Wave-24.1 CONTRACT above. (1)
  `GET /srv/coupon/list?goodsId=` returns ONLY coupons matching that
  goods — whole-catalog included, goods-scope containment, category
  scope ancestor-expanded per Wave-18 semantics, categories derived
  server-side from the goodsId; response shape unchanged; omitted
  param = today's list. (2) selectlist `verbose=true` → `{usable,
  unusable:[{...coupon, reason: threshold|scope|expired|exhausted,
  minGap?}]}`; WITHOUT the param the legacy bare array stays
  byte-identical (order's facade + old SPA callers untouched). Do NOT
  touch `SocialDealAutoPoster`, the Mautic listener, or the campaign
  endpoints.
- **Acceptance (dev, through :9000/:8090):** goods-scoped coupon
  appears ONLY on its goods' list call and an L1-scoped coupon appears
  for a leaf-category goods under that L1 (ancestor proof); bare
  selectlist byte-identical before/after (capture both); verbose
  returns a threshold miss with the exact minGap; module tests green
  with real "Tests run:" counts.
- **History — idle (Wave 22 SHIPPED).**
- **History — Wave 22 (backend): RFM-targeted coupon delivery.**
  SHIPPED + DEPLOYED 2026-08-08 (`7eb513d1d`, prod V58):
  `litemall_coupon_delivery`, deliver-to-segment w/ preview (audience
  cap 10k = errno 772), performance + deliveries endpoints. Status
  block above.
- **History — Waves 20/21 (backend).** SHIPPED + DEPLOYED 2026-08-08:
  W20 Postiz DIY-page publishing (`066b8e270`, V55, errnos 764/765/
  766); W21 pink attach/release CAS + memberPinkIds on group events +
  765 groupon gate DELETED (`f65baa1e9`, 126/0). Specs in the
  CONTRACT blocks above.
- **History — Wave 18 (backend): scoped + percent + profit-guarded
  coupons.** SHIPPED + DEPLOYED 2026-08-06 (`0ae6423c7`+`aadff8434`,
  merge `1d02deeab`, prod V51; guard X-User-Id fix `3168148e7`;
  2026-08-08 prod compose fix `046ad1f82` added the missing
  `LITEMALL_GOODS_AUTH_*` env pair — user-confirmed working). Original
  spec kept below for reference.
- **Spec (Wave 18, HISTORICAL):**
  1. **V51**: `discount_type` (0 flat / 1 percent, default 0) +
     `discount_cap` decimal(10,2) NULL on `litemall_coupon`; percent
     rate rides the existing `discount` column (validate 1–90).
  2. **Ancestor-aware scope matching**: expand cart leaf category ids
     up the `litemall_category` chain before `matchesGoods`; derive
     cart category ids server-side from `goodsIds` whenever the
     caller omits `categoryIds` (usable/selectlist/redeem alike).
  3. **Percent math server-side**: usable/selectlist return the
     computed effective discount for the passed amount (cap applied);
     redeem re-computes vs `orderSubtotal`; list/read expose
     `discountType`/`discountCap`.
  4. **Margin guard** on coupon create AND update (admin issue path):
     call goods-management `POST /srv/private/admin/insight/
     margin-basis` per the frozen handoff spec (machine token +
     `X-User-Roles: ROLE_ADMIN`); hard-block per the guard formula;
     floor config `litemall.promotion.coupon.margin-floor` (env
     `LITEMALL_PROMOTION_COUPON_MARGIN_FLOOR`, default 1.05); FAIL
     CLOSED when goods-management is unreachable; rejection messages
     state the computed maximum discount/rate.
  5. **Register-gifts**: `POST /srv/promotion/coupon/register-gifts`
     (machine + `X-User-Id`) — grants all active `TYPE_REGISTER`
     coupons, idempotent via per-user limit.
  6. **Redeem scope re-check**: accept optional `goodsIds`/
     `categoryIds` in the redeem body and re-check `matchesGoods`
     when present (order passes them — see `order` mini-task).
  7. **Tests**: guard bounds (flat + percent + null-ratio reject +
     fail-closed), ancestor matching (L1 coupon matches leaf-cart),
     derived-category parity, percent computation with cap,
     register-gift idempotency. Mind the "Tests run:" gotcha.
- **Acceptance (dev, through :18080/:9000):** L1-scoped percent
  coupon created within guard limits → claim → appears in checkout
  picker ONLY with a qualifying CJ product in cart → discounted
  total charged and DB-verified ≥ cost×floor; over-generous coupon
  rejected with the stated max; guard rejects an all-uncosted scope
  with the typed errno; register-gift lands on a fresh signup;
  existing coupon/checkout e2e regression-green; V51 applied
  cleanly; module tests green with real "Tests run:" counts.
  **Dev-DB fact (verified 2026-08-06):** only 4 costed on-sale goods
  exist on dev (ids 10008302–10008305; cost capture runs in prod) —
  before guard happy-path acceptance either run
  `POST /srv/private/admin/search/cj-enrich` batches to land costs or
  scope test coupons to those 4. margin-basis SQL semantics are
  already live-verified against dev data (whole catalog
  maxCostRatio 0.8003; Women's Clothing L1 subtree 1301/1 costed
  0.8001; bogus ids excluded).
- **History — Wave 17 (backend): Postiz publishing client + the five
  CONTRACT endpoints (see Wave-17 CONTRACT above).**
  1. `PostizClient` for the public API: bare-key `Authorization`
     header (no `Bearer`); always sends `shortLink:false` +
     `tags:[]`; explicit UTC ISO `date` + `type:"schedule"`;
     surfaces Postiz's structured 400s (`{provider, name, error}`)
     instead of wrapping them.
  2. Endpoints `status`, `channels`, `preview`, `publish`, `log`
     under `/srv/private/admin/promotion/postiz/`. Composer: content
     = sanitizer-safe HTML (`<p>/<strong>/<a>`) with product name,
     price (live flash-deal price when a swap is active, else
     retail), canonical slugged link
     `https://trovemo.com/product/<id>-<slug>` (Wave-13 slug rules);
     image = absolutized picUrl; products whose image path lacks a
     valid extension are SKIPPED with a per-product warning (no
     upload-from-url fallback in v1). Settings map: facebook `{}`,
     instagram `{post_type:"post"}`, x
     `{who_can_reply_post:"everyone"}`, empty-settings providers
     (threads, mastodon, bluesky, telegram, nostr, vk) `{}`; all
     others ⇒ `supported:false` in `/channels`.
  3. Scheduling: post i ⇒ `startTime + i×intervalMinutes`; batch cap
     25 per publish (Postiz ships `API_LIMIT=30`/h) — larger
     selections rejected with a clear typed error.
  4. **Migration V50** — V49 is CLAIMED by behavioral-targeting
     Phase 0 (`fix/goods-management`, committed 2026-08-04, not yet
     merged); check `flyway_schema_history` immediately before first
     boot (prod applied through V48):
     `litemall_postiz_post` (goods_id, category_id, integration_id,
     channel identifier, postiz_post_id, schedule_time, status,
     error, add/update/deleted) — feeds `/log` + the dedup warning.
  5. Goods data resolved through the existing catalog seam
     (`SocialCatalogAdapter` / goods facade) from explicit goodsIds
     only — no category browsing re-implementation.
  6. **Tests:** composer output (content/link/image
     skip/settings/schedule spread), disabled-env errno, batch cap,
     client payload shape (mandatory keys). Mind the "Tests run:"
     gotcha.
- **Acceptance:** with dev Postiz up (`docker compose up -d` in
  `~/postiz_dir/postiz-app`; API key from Settings → Public API) and
  a Facebook page connected, through `:18080`: `/channels` lists the
  FB integration; `/preview` of 3 products shows slugged links,
  `/_cdn` images, spread UTC times, zero side effects; `/publish`
  returns postizPostIds and the posts appear scheduled in Postiz's
  calendar (≥1 actually publishes to the page); a forced-invalid
  case surfaces the per-channel 400 honestly; env unset ⇒ typed
  errno on every endpoint; existing campaign/seckill/social panels
  regression-green; V49 applied cleanly; module tests green with
  real "Tests run" counts.
- **History:** Wave 12 campaign scheduler SHIPPED (`69d0aeb9d`,
  merged to master, prod live 2026-07-30).
