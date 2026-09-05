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
> Sequence: margin ✔ → cycle ✔ → verify ✔ → floor ✔ → narrow ✔ → flip-back
> proven ✔ — **PHASE 2 COMPLETE ON PROD 2026-08-14.**
> **FLOOR EXECUTED 2026-08-14 07:07 UTC** (`LITEMALL_GOODS_PRICE_FLOOR=5.00`,
> backup `.env.prod.bak-wave26-floor`). It did NOT wait for the nightly:
> `CjCatalogRefreshTask` schedules a startup refresh 10 min after ready, and the
> promote inside it is where the floor acts. Result: on-sale **17,398 → 11,278**
> (6,120 off-saled vs 6,206 predicted — the gap is goods that repriced across €5
> during the run); sub-€5 on sale 6,206 → 945; refresh summary `105 new / 5367
> updated / 0 removed; promoted 31509 (failed 0); reindexed 11278 docs`.
> ⚠ `docker logs` showed only 325 floor lines because the json-file driver
> rotates at 10m×3 — the container's own logback file had 10,313. Never measure a
> long prod run from `docker logs`; use the DB or /app/logs.
> **NARROWING EXECUTED 2026-08-14** — on-sale **11,278 → 2,200** (9,078 off-saled;
> executor `due 9078, executed 9078, skippedLiveDeal 0, goodsMissing 0,
> lostRace 0`); search index 2,200 docs; nav down to the two anchor categories.
> ⚠ **The narrowing path the spec assumed DID NOT EXIST** and had to be built
> (`da6dbafb2` `CatalogNarrowingService` + restore; `9db298ccf` empty-category nav
> filter; `4560b5d4d` restored-rows-are-re-narrowable). Retirement candidates are
> only ever created by the scorer (unavailability streak) or the governor
> (weakest-first vs the catalogue target) — neither takes a category, approve needs
> a pre-existing `proposed` row, and no bulk off-sale endpoint existed anywhere.
> The FLIP still rides the existing `RetirementExecutor`, so narrowed goods are
> indistinguishable downstream. Module suite 390 run / 0 failures / 8 skipped.
> **REVERSIBILITY PROVEN** on prod: restoring Phones & Accessories put back exactly
> its 132 goods (2,200 → 2,332, index 2,332) touching none of the other 8,946 —
> restore is driven by the narrowing AUDIT ROWS, never by category, so
> floor/hygiene/retirement off-sales are never resurrected. That spot-check also
> CAUGHT a real bug (fixed `4560b5d4d`): `restored` counted as a standing decision,
> so a same-day re-narrow staged nothing — reversibility worked one way only.
> Ops: `docker-compose/wave26-anchor.sh narrow-preview|narrow-dry|narrow-apply|
> narrow-execute|narrow-restore <L1 id>` (read-only by default, typed confirms).
> ⚠ The category nav is memoized 5 min (`CatalogGoodsCountService.TTL_MS`) — right
> after a narrowing it still lists the old categories; that is the cache, not a
> failure. The spot-check category was re-narrowed after the fix deployed (dry run
> `staged 132, alreadyDecided 0` — the exact case that failed before — then
> `due 132, executed 132`), so the store is anchor-only: **2,200 on sale, 2,200
> indexed**, and the full restore→re-narrow round trip is proven in both
> directions on production.
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
> **PHASE 3 EXECUTED 2026-08-14 (content de-duplication).** Measured on the
> narrowed catalogue, NOT the pre-narrowing feed: 737 of 2,202 rows (33.5%) had
> description == title, incl. 215 of the 636 in-band SKUs. ⚠ **The spec's
> prescription — "extend the Wave-13 brief sanitizer fallback chain" — reaches
> only 45 of them.** The real split: 659 rows have NO detail body at all, 29 have
> image-only markup, 7 have detail that just repeats the name. It is a CONTENT
> gap, not a fallback gap.
> Two real fixes shipped (`96d7f9964`, suite 400/0/8 skipped):
> 1. **The guard was defeating itself.** `GoodsMetaService.descriptionOf` compared
>    a CLEANED brief against a RAW name while the feed titles from `clean(name)`,
>    so a name with doubled spaces or entity soup ("13A W  2 Charger USB  Outlets")
>    was ≠ its own cleaned brief; the brief was returned and the feed emitted both
>    as identical strings. Feed dups 737 → **708** on the next rebuild.
> 2. **The enrichment queue had no idea what is on sale.** `selectForEnrichment`
>    filtered only `cp.deleted = 0`, so with the mirror broad (all 14 L1s) and the
>    storefront narrow, ~93% of every batch — and of the shared daily CJ points
>    budget — went to products we no longer sell, and the 659 could never be
>    reached. Now on-sale ranks first (PRIORITISES, never filters, so a restored
>    category is not stale; `coalesce` because the goods join is a LEFT join).
>    Live proof: a batch of 10 enriched 11 on-sale rows vs 2 off-sale (pre-change a
>    random batch would hit ~1); on-sale-with-empty-detail 1,038 → 1,027.
> **ENRICH BATCH RAISED 100 → 400 (user decision 2026-08-14)** — convergence
> ~11 nights → **~3 nights**. ⚠ CJ daily points are shared ACCOUNT-WIDE with ORDER
> PLACEMENT (catalog + order use different key pairs on the SAME account — verified
> on prod). The raise ships WITH the guard that makes it safe: `enrichBatch` now
> abandons the batch on quota exhaustion (errno 16900500 / "api points" / "quota"
> in the message) or after 5 consecutive failures, instead of firing hundreds of
> futile calls against a dead quota; a QPS rejection deliberately does NOT count
> (transient). The result carries `stoppedEarly` and the endpoint returns it, so a
> batch that gave up can never read as a drained queue.
> **`LITEMALL_CJ_ENRICH_BATCH_SIZE`** (yml placeholder + compose passthrough, default
> 400, **0 disables enrichment**) is the FIRST knob to turn down if paid orders start
> parking on CJ errors — container recreate, no rebuild.
> Trade-off accepted: off-sale mirror rows now rarely enrich until the storefront is
> current.
> The residual 708 are all provably content gaps (verified row by row) — they
> resolve as enrichment reaches them. The spec's "~30 in-band SKUs get genuinely
> rewritten copy" is NOT done: that is human/editorial work, not a code change.
>
> **MORNING-AFTER (2026-08-15) — three findings, all from looking at prod:**
> 1. **NARROWING LEAKS.** On-sale climbed 2,200 → 2,668 overnight. NOT narrowed goods
>    returning (all still off sale) — **428 new goods from the 03:00 sync, 328 outside
>    the anchor**, landing on sale because the promote INSERT branch always does. The
>    homepage was advertising Women's Clothing / Jewelry / Bags again. Fixed
>    `18583700c`: `litemall.goods.anchor-category-ids` (env
>    `LITEMALL_GOODS_ANCHOR_CATEGORY_IDS`, compose passthrough, empty = off) off-sales
>    NEW non-anchor goods at promote — **NEW GOODS ONLY**, because a standing rule
>    would undo `/insight/narrow/restore`. Prod set to `1036143,1036495`; the 362 that
>    had leaked were off-saled; catalogue back to anchor-only (1,518 + 788 = 2,306,
>    index 2,306, banners back to 2 after their 10-min TTL).
>    ⚠ **Acceptance lesson: proving a category flips BACK is not proving narrowing
>    HOLDS across a nightly cycle.** Test the cycle, not the toggle.
> 2. **The enrich abort ate its own batch.** First 400-run: `119 enriched, 5 failed
>    (batch requested 400, due 400) — STOPPED EARLY: 5 consecutive failures (last: no
>    CJ detail for pid ...)`. The raise WORKED; the guard then discarded ~276 because
>    five consecutive rows were products CJ has no detail for — a DATA GAP, not a
>    fault. `isDataGap()` now excludes those (neither increments nor resets, so a real
>    fault streak interrupted by one still trips). Quota exhaustion never occurred;
>    order placement unaffected.
> 3. **⚠ PROD INCIDENT: goods-management was OOM-KILLED 03:30:02** (restarts=1),
>    mid-enrichment. `/app/logs` is a **tmpfs and its pages are charged to the
>    container's memory cgroup**; logback ships `org.linlinjava.litemall` at DEBUG and
>    rotates DAILY with no size cap, so it wrote **611 MB in under 2 h** against a
>    1.5 GiB limit — JVM at 90.7% before the heavier batch tipped it. Truncating the
>    file dropped the container to 51.4%, proving the log WAS the memory. Guards
>    (`15aebe4f6`, container-recreate only): **tmpfs `size=256m`** +
>    `LOGGING_LEVEL_ORG_LINLINJAVA_LITEMALL=INFO`. Evidence preserved at
>    `/root/wave26-enrich-evidence-2026-08-15.log`.
>    **FIXED PROPERLY (`64c3285b5` + `3053a6324`, deployed):** logback-spring.xml now
>    uses `SizeAndTimeBasedRollingPolicy` (20 MB/file, 3 gzipped archives, totalSizeCap
>    100 MB log + 20 MB error ⇒ ~120 MB worst case, deliberately BELOW the 256 MB tmpfs
>    cap so a logback regression is caught by its own limit rather than the tmpfs wall),
>    `cleanHistoryOnStart`, `debug="false"`, and **profile-scoped levels** (prod INFO,
>    everything else keeps DEBUG).
>    ⚠ **The XML alone was NOT enough, and this is the reusable lesson: Spring's
>    `logging.level.*` OVERRIDES logback-spring.xml, and Spring resolves the MOST
>    SPECIFIC logger key.** `application.yml` (and litemall-db's own) set
>    `org.linlinjava.litemall.db: DEBUG` = every SQL statement, ~14k lines/minute —
>    which beat both the XML and the package-level env. Proven live: after deploying the
>    XML fix the log still had **14,091 DEBUG lines in 40 seconds**, all
>    `o.l.l.d.d.L.*` mappers. Countered at the same specificity in
>    `application-prod.yml` (+ `LOGGING_LEVEL_ORG_LINLINJAVA_LITEMALL_DB` env knob) ⇒
>    **0 DEBUG lines, log 12 KB vs 2.7 MB, memory 43.65% vs 90.7%.**
>    `LogbackConfigTest` (5) pins all of it, including a real JoranConfigurator parse so
>    a typo fails in the suite instead of at boot.
> ⚠ **Cloud routines CANNOT verify trovemo.com** — beyond having no SSH, the cloud
> egress proxy BLOCKS the domain (`EGRESS_BLOCKED`, confirmed via curl and WebFetch).
> The routine returned INCONCLUSIVE with zero data. Verify prod from the MAIN session.
>
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

### Worktree: `order` — ACTIVE: order lifecycle end-to-end fix (plan approved 2026-09-05)
- **Task — order lifecycle E2E (user-commissioned 2026-09-05, all six decisions
  approved).** Code to `litemall-order/docs/plan-order-lifecycle-e2e.md` (the
  contract: findings F1–F18, packages A→B→C, raises D). Decisions: D1 late
  payment on a cancelled order = automatic Stripe refund (+hop +customer mail);
  D2 CJ-cancelled paid order = automatic customer mail, NO money movement; D3
  stalled placements park after 24 h; D4 customer "withdraw refund request"
  action; D5 no automatic CJ dispute on local refund (separate decision); D6
  prod sets `CJ_OPS_MAIL` — MAIN-session deploy step (worktrees never touch the
  VPS). Package A (payment money-safety) ships and merges FIRST, alone.
- **Acceptance:** as written in the plan §4 — per package, module tests green
  with real "Tests run:" counts (baseline 298 / 0), dev acceptance through
  :9000/:8090/:18080, and the first real prod EUR order after deploy is
  USER-SIDE.
- **Status 2026-09-05 — PACKAGE A BUILT (payment money-safety, F1/F2).** Module
  suite **342 run / 0 failures** (+44). Design: `adr-stripe-payments.md` §11.
  What changed: the PaymentIntent id is now RECORDED AT MINT (CREATED-guarded;
  a re-mint settles a succeeded previous intent / refuses on processing /
  cancels a pending one at Stripe); the unpaid sweep is now
  `UnpaidOrderTaskScheduler` (task rows only) + `UnpaidOrderReconciler`
  (SUCCEEDED ⇒ settle via `settleVerifiedPspPayment`, PROCESSING ⇒ defer 60
  min, UNAVAILABLE ⇒ defer 5 min, PENDING ⇒ cancel AT STRIPE first, then the
  order); stray charges (webhook OR `/actions/pay` on a cancelled/already-paid
  order) are REFUNDED under `refund-order-<id>-<intent>` + hop
  `payment_refunded_stray` + customer mail `payment-refunded` (core
  MailTemplates/MailHtmlTemplates — shared-module rebuild discipline applies);
  a refused refund commits a `payment_stray_unrefunded` hop + ops mail; a
  transient verify failure throws `LitemallPaymentTemporarilyUnavailableException`
  → webhook answers **503** (claim rolled back, Stripe redelivers). Knobs
  `litemall.order.unpaid-reconcile.{processing,unavailable}-defer-minutes`
  (env `LITEMALL_ORDER_UNPAID_*_DEFER_MINUTES`). NO migration.
  ⚠ The orchestrator is injected `@Lazy` into the reconciler (scheduler →
  reconciler → orchestrator → scheduler cycle) — dev boot on :18085 proved the
  context starts (17 s, Flyway validated 65, schedulers up). ⚠ `@Transactional
  (noRollbackFor = LitemallPaymentGatewayException)` on `createPaymentIntent`
  is load-bearing: the "already paid" refusal must NOT roll back the settlement
  it just made. ⚠ `settleVerifiedPspPayment` must never touch the task table
  (the sweep holds `FOR UPDATE SKIP LOCKED` on it) — the sweep deletes its own
  rows. ⚠ Mockito here has no `verifyNoInteractions`; `argThat` lambdas in a
  second `when()` see null. Live Stripe test-mode probe =
  `StripePaymentGatewayAdapterLiveIT` (opt-in via `STRIPE_TEST_SECRET_KEY`; no
  test key exists in the repo). RAISED for gateway-api: `StripeCardForm`
  treats a `processing` intent (SEPA) as "not completed" and `return_url:
  /orders` never reconciles a redirect return — the backend now survives both,
  the SPA copy is still wrong. Deploy: order container + every litemall-core
  dependent (template classes changed); D6 `CJ_OPS_MAIL` env at the same time.
  **Package A MERGED to master `c9ec31257` + pushed 2026-09-05.**
- **Status 2026-09-05 — PACKAGES B + C BUILT (fulfilment visibility + closure).**
  Suite **387 run / 0 failures** (+45). NO migration. B: new
  `CjFulfilmentIncidentService` (@Transactional — the AFTER_COMMIT mail listener
  drops events published outside a tx, the shipped-mail lesson) owns every
  "CJ went wrong" signal, with STATE ON THE TIMELINE (no schema): retryable
  placement failures write ONE "deferred" hop on the first failure (whichever
  path), warn ops after `litemall.order.cj.stall-warn-minutes` (60), PARK under
  the new `PLACEMENT_STALLED` sentinel after `stall-park-hours` (24); a placed
  order CJ refuses to confirm/pay-from-balance (empty CJ balance) gets a
  `cj_stall` hop + ops mail at most every `lifecycle-alert-hours` (24) — the
  facade now returns `CjCallOutcome` with CJ's reason; CJ CANCELLED after payment
  publishes `LitemallCjFulfilmentCancelledEvent` → customer mail
  `fulfilment-cancelled` (D2) + ops mail, no money moves. Pending list now
  INCLUDES parked rows regardless of the approval stamp (+`parked`,
  `parkReason` = CJ's words from the last `cj_placement_failed` hop);
  `POST /srv/private/admin/order/{id}/cj-placement/requeue` (CAS clears either
  sentinel, keeps the approval) replaces the SQL-in-an-email; approve refuses
  `[AFTERSALE_OPEN]` / `[PARKED]`; `place()` holds on an open aftersale; blank
  CJ tracking stays NULL and `/tracking` answers `TRACKING_PENDING` for a
  shipped order; sync predicate excludes 401/402; a CJ ship during a refund
  review leaves a hop; the poller ships as operator `system`. C: `delivered`
  mail on `LitemallOrderDeliveredEvent` (return window from
  `litemall.order.return-window-days`, 30); auto-confirm window = system
  setting `litemall_order_unconfirm` when set, yml fallback otherwise (was a
  hidden 15-day code default vs the panel's 7); customer
  `POST /srv/order/{id}/actions/refund/withdraw` (202 → PAID|SHIPPED, D4) +
  `handleOption.withdrawRefund`; `canBeCanceled` now delegates to the
  dispatcher, `getStatusesForShowType` deleted, dead paid-at-creation branch
  removed, stale offline-pay javadoc corrected. Contracts:
  `docs/handoff-gateway-admin-cj-requeue.md`, `docs/handoff-gateway-api-lifecycle.md`.
  Dev boot on :18085 verified after EACH package (15–17 s, Flyway 65 validated).
  ⚠ Gotchas: a Java record component named `ok` forbids a static factory
  `ok()`; `LitemallCjRetryableException` wraps its message (match with
  `contains`); `getOrderForUser` reads `findById` + filters, not
  `findByIdAndUserId`. **RAISED (not built here):** gateway-api — SEPA
  `processing` copy + redirect `return_url` reconciliation + withdraw button +
  202 on the Refunds page + TRACKING_PENDING + timeline; gateway-admin — parked
  rows + Requeue button + `[AFTERSALE_OPEN]`/`[PARKED]`; goods-management —
  reviews have no purchase check (`POST /srv/comment/post` accepts any user,
  never marks `order_goods.comment`, so "Unrated" never clears). NOT done:
  F15 (refund after CJ paid opens no CJ dispute — D5, separate decision), F16
  Refunds-page visibility (SPA), F17 (goods-management).
- **Status 2026-08-27 — UNPAID-ORDER SWEEP LOOP: cancelled orders no longer
  retried forever.** Branch commit `01a7bc77e`; module tests 298 run / 0
  failures (was 291, +7 new). Found while verifying the mail deploy: prod had
  been logging `IllegalStateException: Order status cannot transition from
  CANCELED to SYSTEM_CANCELED` **every 60 s since 2026-07-23** — one row
  (order 8, cancelled by the customer 50 s after placing it) at roughly
  **50,000 failed attempts over 35 days**, with no end.
  THREE defects compounded, and the fix touches all three:
  1. **Nothing retired the task on cancel.** `unpaidOrderTaskScheduler.cancel()`
     was called from exactly two sites, BOTH on the pay path;
     `handlePostCancellation` was an empty stub. ⚠ It cannot live in
     `LitemallOrderServiceImpl` — `UnpaidOrderTaskScheduler` depends on that
     service, so injecting it back is a cycle. That is almost certainly why the
     cancel path never got it. It now lives in the orchestrator.
  2. **The graceful-skip branch was dead code.** `autoCancelOrder` validated on
     the aggregate (`autoCancel()` throws on non-CREATED) BEFORE its own CAS, so
     the CAS's `updated == 0` branch — commented *"That is not an error for the
     sweep — just skip it"* — could never run. CAS now runs FIRST; safe because
     it proves the row was CREATED, so the later `autoCancel()` cannot throw
     (`persistStatusHistory` writes history only, never the order row).
  3. **A missing order looped identically** (`NoSuchElementException` → retry).
     Now retired with an INFO.
  ⚠ **The sweep keeps rows "for retry" BY DESIGN** (transient failures). That is
  correct only while every permanent failure is made non-throwing at the source —
  otherwise it is an infinite loop. No retry cap was added: that needs a schema
  column for a problem these three fixes close at source.
  **Production self-heals** on the first sweep after deploy (a graceful skip is
  followed by the row delete) — NO DB surgery was done or needed.
  **DEPLOYED to trovemo.com 2026-08-27** (VPS tree detached at `4932c068f`,
  order image rebuilt 01:45 UTC, ONLY the order container recreated, healthy).
  Live proof: **zero** `cannot transition from CANCELED` throws since recreate
  (was ~1/minute for 35 days), one INFO `Skipping auto-cancel of order 8: no
  longer in CREATED state`, `litemall_unpaid_order_task` now **empty** — the row
  retired itself with no DB surgery — and order 8 still reads status 102
  (CANCELED), i.e. the skip did NOT rewrite the customer's own cancellation.
  Storefront smoke 200s; `/srv/order/list` 401 (routed + auth-gated).
  ⚠ Deploy notes: prod disk was 25 G, under the script's own 30 G threshold, but
  the whole 8.6 G of builder cache was from the same day (nothing prunable) and a
  SINGLE-service build fits easily — the threshold guards a full 9-service
  reactor pass. ⚠ Launch long VPS builds with `setsid nohup … &` and poll: a
  `timeout`-wrapped ssh returning 143 kills only the ssh, not the build. ⚠ The
  build log is full of `[ERROR] <s> [webpack.Progress]` lines — that is webpack
  writing progress to stderr, NOT failures; grep `^ERROR:|BUILD FAILURE|failed to
  solve` instead.
  Incidental finding, not fixed: `LitemallOrderStatusQuery.canBeCanceled` claims
  CREATED||PAID, but the dispatcher uses `LitemallOrderHandleOption`, which gives
  PAID *refund* and not cancel. The two disagree on paper; the helper is unused
  by the cancel path.
- **Status 2026-08-26 — CUSTOMER MAIL DESIGN: every lifecycle mail wears the
  storefront design.** Branch commit `66f0a1296`; module tests 291 run / 0
  failures (was 279, +12 new). User-commissioned outside any wave.
  Audit of the lifecycle found the real gap was NOT inside the two HTML
  templates: of the four customer mails, only order-confirmation and shipped
  had HTML at all — **pickup-code and refund-approved arrived as raw plain
  text**, so half the lifecycle looked like a different company. Both now have
  HTML twins built from the same shell, wired through a shared `safeHtml()`
  that degrades to plain-text-only on any throwable (the contract
  `renderConfirmationHtml` already had). Plain-text bodies are UNTOUCHED —
  they stay the multipart fallback and the admin panel's view.
  `MailHtmlTemplates` is now token-driven: the storefront's own `--lm-*`
  values as constants (band `#0a5d65` — the site header is primary-DARK, the
  mails were using primary; `#0e7c86` CTA, `#e3f2f3` soft, `#1f9d6b` success
  for discounts, 10px card radius, the Amazon Ember stack) plus shared
  builders. No style literal at a call site again — that is how the first two
  templates drifted from the site and from each other.
  ⚠ Three of the fixes are CORRECTNESS, not taste: every text node must name
  its `font-family` (Outlook's Word engine does not inherit it from `<body>` —
  headings were rendering in **Times**); each template opens with a hidden
  preheader (else the inbox preview shows whatever copy fell first); the shell
  declares `color-scheme: light` (Apple Mail / Outlook dark mode was free to
  invert the card and the brand band). A test asserts the font invariant over
  every sized node in all four templates, so a new call site cannot regress it.
  Verified by rendering all six variants (4 templates + 2 degrade paths) to
  HTML and screenshotting them headless — puppeteer-core + `/usr/bin/google-chrome`,
  `NODE_PATH` pointed at the repo's node_modules, JDK 21 for the render harness
  (`java` on PATH is 11 and cannot read core's class files). ⚠ puppeteer
  `fullPage` stitches a SHORT page twice — the doubled screenshot is an
  artifact, check the .html before believing it.
  **DEPLOYED to trovemo.com 2026-08-26** — and ⚠ **nothing had to be built**:
  another session ran a full `docker compose build` ~2 h after this merge and
  swept the commit into the order image (created 13:31 UTC, container healthy,
  storefront smoke 200s). A worktree merge CAN reach production without its
  author deploying it — check the running artifact before rebuilding. Verified
  at the byte level (the "verify the nested BOOT-INF/lib copy" rule, done
  properly): `docker cp` the running `/app/app.jar`, read
  `BOOT-INF/lib/litemall-core-0.1.0.jar` with python `zipfile` (⚠ the VPS has
  NO `unzip`), download it, and run the render harness against THAT jar — all
  six renders came back byte-identical (`cmp`) to the local build.
  ⚠ **The mail pipeline has NEVER fired in prod:** `litemall_mail_outbox` is
  EMPTY (0 rows ever) and the last paid order was **2026-08-02**, before mail
  was switched on. Config is live and correct (`CUSTOMERMAIL_ENABLED=true`,
  Brevo :587, from noreply@, admin notify contact@; the 60 s sweep polls and
  finds 0). The new mails are deployed but UNEXERCISED — first real proof is
  the next paid order. NO migration (schema stays V60), no reindex.
  `promotion-service` (image 2026-08-10, **unhealthy 2 weeks**) and
  `loyalty-service` (2026-07-19) still carry an old litemall-core; left alone
  deliberately — only litemall-order references the changed classes, and their
  staleness pre-dates this work.
  ⚠ **Pre-existing prod noise spotted while verifying, NOT from this change:**
  order logs `IllegalStateException: Order status cannot transition from
  CANCELED to SYSTEM_CANCELED` **451 times in 7 h** (a scheduled auto-cancel
  retrying already-cancelled orders), plus one `AccessDeniedException:
  /app/storage`. Neither investigated — they deserve their own task.
  **RAISED, not touched (both outside this worktree's scope):**
  1. `MailTemplates.passwordReset` is **dead code** — gateway-api's
     `SmtpResetMailSender` sends its own hardcoded body, and the two disagree
     (core's template says "open this link"; the live mail sends a 6-char
     CODE). The reset mail is the one customer mail still arriving as raw
     plain text, and restyling core's version would change nothing a customer
     sees. Fix belongs to the gateway-api worktree, which deliberately takes
     no code dependency on litemall-core.
  2. The confirmation CTA points at `/order/:id`, a `CustomerProtectedRoute` —
     Wave-16 guest buyers (password-less shadow accounts) are bounced to a
     login wall from their own confirmation mail. The honest fix is a
     tokenized guest order view = gateway-api work, not a template change.
- **Status 2026-08-18 — MAIL CURRENCY HONESTY: order mails render EUR, not
  dollars.** MERGED to master `ec481fad8` + pushed (branch commit
  `f05238693`; module tests 279 run / 0 failures, +1 new). User-commissioned
  outside any wave. The Wave-24 EUR flip swept both SPAs but NOT
  litemall-order's mail bodies — a customer charged €54.31 received a
  confirmation reading "$54.31", and every admin paid-order notice + CJ ops
  alert said "$" too. Fixed at the 4 render sites
  (`CustomerMailEnqueueListener` money/moneyNonZero/admin-subject fallback,
  `CjPlacementService.money`) behind a per-class `CURRENCY_SYMBOL`.
  ⚠ **litemall-core is touched:** `SmtpCustomerMailSender` built
  `JavaMailSenderImpl` with NO `setDefaultEncoding`, so the PLAIN-TEXT sends
  (`SimpleMailMessage` — admin paid-notice, refund-approved, pickup code)
  shipped a non-ASCII symbol with no declared charset and JavaMail fell back
  to the platform default (mojibake risk); now UTF-8, matching what the HTML
  path already forced via `MimeMessageHelper`. **DEPLOY = shared-module
  discipline:** `mvn install` litemall-core, rebuild + restart EVERY
  dependent, verify the nested `BOOT-INF/lib` copy. NO migration (schema
  stays V60), no reindex. Amounts stay plain decimals — the symbol is
  presentation only, exactly as both SPAs treat it.
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

### Worktree: `goods-management` — season follow-ups SHIPPED + DEPLOYED + autumn terms tuned (2026-09-05)
- **RAISED by order 2026-09-05 (lifecycle audit F17):** `POST /srv/comment/post`
  has no purchase check (any authenticated user, any goods, unlimited) and never
  marks `litemall_order_goods.comment` / `litemall_order.comments`, so the
  storefront's "Unrated" tab never clears. Needs an order-goods linkage
  (goodsId + orderId from the buyer's own delivered order) before it is a review.
- **Status 2026-09-04 — SEASON FOLLOW-UPS BUILT: term-anchored discovery + quantile tiers +
  `seasons` on hits.** Built as `54912135c`; **MERGED to master `3e784648d` + DEPLOYED to
  trovemo.com 2026-09-05** (goods-management container only, built on the VPS from that commit;
  suite on the merged tree **596 run / 0 failures / 8 skipped**; the merge also carries the peer's
  SEO bulk-apply, so that is live too). **Autumn terms tuned on prod the same session** via
  `docker-compose/season-tune.sh terms autumn season-terms-autumn-2026-09.json` (rules backup
  `/root/season-rules-backup-20260905T001924Z.json` on the VPS; `restore` reverses it) + one manual
  scorer run. Measured live, before → after: rail items on-term **14/24 → 24/24**, off-season
  passengers 6 → 0, search hits carrying `seasons` **0/20 → 20/20**; autumn discovery discarded 914
  off-title + 44 excluded hits, 0 relaxed sets; tier cuts hot 773.3 / featured 631.67 (the set
  splits now). Full record spec §17.5. ⚠ The 200-hit scan limit is now the binding bound on broad
  terms (`wool` 1,250 hits → 200 considered, logged). ⚠ Three "Air-conditioning Blanket" items
  survive (title has neither `cooling` nor `summer`) — `-air-conditioning` is the one-line data fix
  if wanted. Winter/spring/summer still on seed terms. ⚠ The MAIN checkout's index shows my three
  files as staged reverse-changes (phantom from the ref move; its working CLAUDE.md is byte-identical
  to old master) — `git checkout HEAD -- CLAUDE.md docker-compose/season-tune.sh
  docker-compose/season-terms-autumn-2026-09.json` there clears it; touching MAIN was denied here. Spec §17 of
  `litemall-goods-management/docs/spec-seasonal-candidacy.md` is the record. Closes all three
  limits the 2026-08-27 deploy recorded:
  1. `SeasonScoringService.discover` used to keep EVERY hit for a term. The index matches
     descriptions/categories, tolerates typos and falls back to relaxed strategies — right for a
     shopper, wrong for a curator ("All-Season Sofa Cover" arrived relaxed; "Summer Cooling
     Blanket" matched `blanket` exactly). Now: a `relaxed=true` result set contributes NOTHING
     (the flag was already on the response, never read); the term must be IN THE TITLE at a word
     boundary, plural-tolerant, title-less hits dropped (fail closed); and **`-term` entries in
     the same `terms` JSON are exclusions** (`"-summer"`), so no migration and the existing PUT
     edits them. Discards are counted, logged and returned by `POST /season-candidates/run`
     (`discovery.discardedRelaxed/OffTitle/Excluded` — HITS, not products). Pure class
     `SeasonTerms`.
  2. Tiers were absolute (hot ≥ 100 / featured ≥ 70 ⇒ 1003 of 1005 hot). Now QUANTILES of each
     run's own curve: top 10% hot, top 35% featured-or-better, rest watch
     (`LITEMALL_SEASONS_HOT_QUANTILE` / `_FEATURED_QUANTILE`, yml placeholders + prod compose
     passthrough). Round-up, ties included, zero score = watch, inverted pair collapses. Run
     returns `tierCuts`. Scoring is two-pass now (score all, then tier). ⚠ `auto-tier: featured`
     therefore means "top 35%": a season scoring under ~69 candidates publishes fewer than 24 —
     by design, visible in the summary. The three sibling scorers (deal/coupon/groupon) keep
     their own absolute constants, untouched.
  3. `toGoodsListItem` emits `seasons: [...]` when non-empty (positive-only, like the flags; bare
     single value normalised to a list). Read-time only. **RAISED for gateway-api:** season badge
     on ProductCard, outside the overlay chain.
  **Deploy = goods-management container only** — no migration, no index field change, no
  reindex, no searcher restart. AFTER deploy, MAIN applies the recommended autumn term list
  (spec §17.4 has the exact PUT: drop `warm`/`lamp`, add `halloween`/`wool`, exclusions
  `-summer -cooling -christmas -xmas -anti-fall -beach`), runs the scorer, and re-measures the
  live 24 (bar: 24/24 on-term, cooling blanket gone, tier cuts that split). Winter/spring/summer
  seeds have the same generic-term weakness (`gift`, `storage`, `fan`, `outdoor`) — tune before
  activation. ⚠ Test-data gotcha met while building: a 40 retail with cost ≥ 33 fails the 15%
  season-markdown gate (`40×0.85 < cost×1.05`) — synthetic rows must clear it or they never reach
  the tier pass.
- **Status 2026-08-27 — SEASONAL CANDIDACY LIVE (prod schema V64).** Full contract + deploy
  record + known limits: `litemall-goods-management/docs/spec-seasonal-candidacy.md`.
  `seasons` is a MULTI-VALUED index field scored nightly (04:35) for EVERY season, so the winter
  page is populated before anyone activates it. `?seasons=<key>` returns exactly 24 per season
  (the read-time cap). **The Autumn page now runs on the season rail** — switched through the
  validated admin API, previous config backed up at `/root/autumn-page-backup-2026-08-27.json`.
  ⚠ **OCS document ids are STRINGS** (`goodsList[].id` = `"10010060"`); a Number-only extractor
  discarded every hit and the first prod run reported `scanned 0` against thousands of matches
  (fixed `55e4d1ebc`). ⚠ `POST /srv/goods/batch` takes a BARE ARRAY, not `{"ids":[…]}` — the
  wrong shape answers errno 402, which reads like permissions. ⚠ Run the scorer BEFORE the
  reindex; restart the searcher AFTER it. ⚠ Known limits, recorded not hidden: the tiers do not
  discriminate (1003/1005 score `hot` — thresholds inherited from the deal scorer, far too low
  for this curve) and `seasons` is not passed through onto search hits. ⚠ The seeded TERMS are
  the weak link: 20 of the live 24 contain an autumn term, and some matches are seasonally wrong
  ("Summer Cooling ... Blanket" matched `blanket`). Terms are data — tune via
  `PUT /insight/season-rules/{key}`, no deploy.
- **Previously: brand-facet leak SHIPPED + DEPLOYED**
- **NEXT TASK (planned, NOT started): seasonal candidacy as an indexed signal.** FROZEN
  contract: `litemall-goods-management/docs/spec-seasonal-candidacy.md` (commit
  `0c9f74ed4`) — read it in full; the block below is only an index.
  User commissioned 2026-08-26 after an OCS ecosystem review. **Origin:** the live
  season page ("Autumns Deal", `litemall_page` id 5, active) carries ONE rail —
  `mode=byIds` with **24 hardcoded ids, the validator's maximum**. The list is frozen:
  a better new arrival cannot enter, a sold-out product cannot leave.
  **The user's generalisation is the design:** score EVERY season continuously, not
  just the running one. Hence the index field is **`seasons`, MULTI-VALUED**
  (mirroring `category_names`) — a single `season_flag` could not say WHICH season,
  would force a rescore+reindex every time the year turned, and could never prepare
  next season in advance. Membership is indexed; **the score deliberately is not** (one
  scalar cannot hold two seasons' scores; ranking uses existing sorts, as the deals
  page does).
  Decisions TAKEN (do not relitigate): auto-publish with per-season cap + minimum tier
  + env kill-switch + permanent admin veto; server-side rail resolution in
  `PageService.toPageView` (stored `mode=season` → served `mode=byIds` + `goodsIds` +
  `resolvedFrom`) so TODAY's storefront renders it with NO gateway-api dependency.
  Three load-bearing corrections to the user's criteria are in the spec:
  **uncosted goods FAIL CLOSED** (`marginPct` is null, never 0 — the Wave-18 lesson);
  the `UNIQUE (season, goods, day)` key makes collisions LOUD, not safe, so it needs an
  upsert that skips admin-decided rows or a plain INSERT kills the batch; freshness is
  a **bonus in [1.0, 1.25], not a decay toward zero** (the formula is multiplicative,
  so any factor reaching 0 zeroes the score). Also: read the price floor from config,
  never hardcode €5 (env-configurable, already changed once in prod); snapshot the
  effective weights beside the config hash (a hash alone resolves to nothing); bound
  the OCS sweep with a LOGGED cap.
  ⚠ **Migration V64** — check `flyway_schema_history` immediately before first boot
  (V63 is the repo's highest, Wave 27; prod recorded at V60 — confirm, never assume).
  ⚠ **Deploy order is load-bearing:** indexer config (`seasons` field + facet + **BOTH**
  dynamic-field regexes) → recreate indexer → goods-management → **run the scorer** (so
  index members exist before the mapping materialises — an empty array may behave like
  an absent field) → **full reindex** → **searcher restart AFTER it**.
  Out of scope: gateway-admin UI (endpoints are the contract, raise it) and gateway-api
  native `mode=season` (a STATED follow-up, not a silent compromise).
- **Status 2026-08-26 — OCS ecosystem review.** 16 capabilities built on the index, 2 of
  them dark (`SearchTrendingService`, `/srv/search/helper`). Consumer map:
  **OCS is a two-party system** — goods-management owns/serves it, gateway-api renders
  it, and NOTHING else touches the index. gateway-admin reads the DATABASE (its
  search-analytics panel consumes rolled-up stats), so no admin screen can show what the
  index thinks of a product. promotion and order never touch OCS; the relationship runs
  the other way (goods-management reads THEIR tables at index time to compute
  `coupon_flag`/`groupon_flag`). Related-goods is DB-backed, NOT OCS. Stack =
  elasticsearch + indexer + searcher.
- **Previously: brand-facet leak SHIPPED + DEPLOYED**
- **No active assignment.**
- **Status 2026-08-26 — SEARCH BRAND FACET: raw CJ supplier legal names no longer
  indexed.** MERGED to master `a44540e69`; module suite 515 run / 0 failures / 8
  skipped. User-commissioned 2026-08-24 (plan approved 2026-08-26; decisions: index
  `brand` for **kind=0 only**, trending work is **spec only**).
  Measured live before the fix: the SPA's `Brand` facet offered *Yiwu Ruijia Auto
  Supplies Co., Ltd.* / *Sichuan Micro-entrepreneur E-commerce Co., Ltd.* / *Shenzhen
  Kagu Technology Co., Ltd.*, and `q="co., ltd"` autocompleted eight supplier names
  (the suggest index sources `brand`).
  Root cause: Wave-25's curation gate was **three separate inline re-implementations**
  (public brand read / PDP / merchant feed) and `LitemallProductIndexingService:127`
  was a fourth site with **no check at all**. The rule now lives in
  `domain/brand/BrandDisplayPolicy` and all four sites call it — `isDisplayable`
  (PDP + public read, which label kind as "Brand" vs "Sold by") vs `isConsumerBrand`
  (feed column + search facet, which carry a BARE brand claim). Both fail closed on
  null.
  **DEPLOYED to trovemo.com 2026-08-26** (image built from `663ac54e9`, container
  recreated healthy in ~30s, 0 boot errors, **reindex 4,214 docs**). Live evidence:
  the `brand` facet on `q=lamp` is now **ABSENT** (was 20+ supplier legal entities);
  `q="co., ltd"` autocomplete returns `[]` (was 8 supplier names) — **the suggest
  index refreshed with the reindex, no container recreate needed**; PDP for goods
  10035068 still serves `{"kind":1,"name":"EVERGREEN SHOP LLC"}`, so the enabled
  supplier "Sold by" path is intact — the two predicates proven distinct in prod;
  feed unchanged at 4,135 rows / 0 branded; smoke 200s across storefront, sitemap,
  robots, PDP, search and the `/srv` reads.
  ⚠ **Built from `663ac54e9`, NOT master's tip.** Master had meanwhile picked up a
  peer's merge `19b3d496d` (gateway-admin SEO title worklist + a CJ credential-leak
  fix + 1,652 lines of goods-management SEO keyword research + prod compose/env
  changes) — theirs to ship, with env I do not own, so it was deliberately kept out
  of this image. `/opt/litemall` is left **detached at 663ac54e9**, which is an
  honest record of what is running; the next deployer checks out master as usual.
  ⚠ **The peer had recreated goods-management minutes earlier from an image built at
  `285c006a5`** — so "a container restarted recently" did NOT mean my fix was live.
  Proven by grepping the running jar for the class WITH A POSITIVE CONTROL
  (`LitemallProductIndexingService` 2 / `SupplierTitle` 2 / `BrandDisplayPolicy` 0).
  ⚠ `pgrep -f "compose.*build goods-management"` **self-matches the polling loop's
  own command line** — it reported "still building" after the image was done.
  **PEER MERGE `19b3d496d` DEPLOYED TOO 2026-08-26** (user asked for it after the
  brand fix landed): goods-management + gateway-admin rebuilt at that commit and
  recreated, both healthy in ~30s, 0 errors. Ships the peer's SEO keyword-research
  backend (`TitleProposer`/`SeoPlatformKeywordClient`/`AdminSeoController`), the
  admin "SEO titles" worklist, and a real **CJ credential-leak fix**
  (`CJAuthenticationClient` was `System.out.println`-ing the supplied AND configured
  CJ email + API key on a mismatch — and goods-management is the one service with
  file logging). Prod check before deploying: **0 occurrences** of the leak marker in
  `/app/logs` or the json-file logs — and `/app/logs` is a tmpfs, so nothing
  historical survives a recreate. No rotation appears necessary.
  Tests at `19b3d496d`: **543 run / 0 failures / 8 skipped** (my 515 + their 28).
  The SEO feature is **DARK BY DEFAULT** and was deployed that way: `SOURCE=file`,
  `ENABLED=false`, and `.env.prod` has **no `LITEMALL_SEO_*` keys at all**, so
  defaults apply. The new `${LITEMALL_SEO_EXPORT_DIR:-./seo-export}` bind mount
  landed read-only and the host dir is EMPTY — the provider warns once and serves
  nothing, which is the designed fail-soft. Turning it on is a USER decision: the
  platform behind it BUYS each uncached seed pay-as-you-go.
  My brand fix re-verified AFTER the rebuild: facet still ABSENT, autocomplete still
  `[]`. NO second reindex needed (indexing logic identical between the two commits).
  Peer SEO admin endpoints answer 401 through the edge (routed + auth-gated), and the
  live admin bundle `main.2d4638f1.js` carries `seo-titles`.
  ⚠ **`/opt/litemall` is SHARED MUTABLE STATE.** A peer ran `git pull --ff-only`
  on it twice DURING this work, moving detached HEAD 663ac54e9 → 19b3d496d →
  179ababb9; my own `git checkout 19b3d496d` was a silent no-op because HEAD was
  already there. Harmless here only because `19b3d496d..179ababb9` has ZERO diff in
  goods-management, gateway-admin and the compose file — checked, not assumed.
  Verify a deploy by IMAGE CONTENT and live behaviour, never by what the checkout
  says now.
  ⚠ **Grepping a jar proves different things for different targets.** Class names sit
  uncompressed in the zip central directory, so `grep -c <ClassName> app.jar` works;
  strings INSIDE a minified JS asset are DEFLATE-compressed, so a 0 there is
  INCONCLUSIVE, not absence. The admin UI had to be verified from the live bundle.
  ⚠ Disk fell 25G → 16G across the two builds (79% used). Prune before the next one.
  ⚠ Pre-existing, NOT from this deploy: `promotion-service` has now been unhealthy
  for ~2.5 weeks. Still untouched; still needs its own look.
  ⚠ **DEPLOY NEEDED A FULL REINDEX** — the fix only changes what future documents
  carry. Afterwards check whether the OCS suggest index still serves the old names
  and recreate the suggest container if it caches. NO migration, no indexer-config
  change, no searcher-restart-for-new-field (no new field).
  ⚠ **The brand facet will go EMPTY after the reindex, by design.** The only
  display-enabled rows with products are 3 suppliers (DVLL 278, dbjjj 251, EVERGREEN
  SHOP LLC 30); the 49 display-enabled consumer brands are 2018 seed rows with
  `goodsCount` 0. Reverses the moment an admin curates a real brand.
  ⚠ **RAISED for gateway-api:** `Search.tsx:410-413` renders `<h3>Brand</h3>`
  UNCONDITIONALLY, so an empty facet leaves a dangling heading — the exact thing
  their own Wave-26 nav-honesty work forbids. Their module; not reached into.
  Part 2 shipped as **design only**: `docs/spec-dynamic-hot-keywords.md`. The
  storefront's sole hot keyword is still the Feb-2018 seed "Gift Pack Early Access"
  (both `defaultKeyword` and the whole hot list). `SearchTrendingService` already
  does the hard part correctly — including a zero-result guard — and is silent ONLY
  because `trending-min-searches: 3` is never met at our traffic. ⚠ Honest finding:
  **no free trending-keyword API exists** (Google Trends' feed is news-shaped; real
  volume data is paid), so external trends can only be an env-gated optional input,
  never the engine. ⚠ MAIN's worktree holds another session's uncommitted SEO
  title/keyword-research work (`KeywordResearchProvider`, `TitleProposer`,
  `AdminSeoController`) — adjacent to this spec; reconcile before building Part 2.
- **History — Wave-27 items.** Both (season backend `bac29f144`, `eu_flag` (season backend `bac29f144`, `eu_flag`
  `9ee2f89d6`) are merged to master; the gateway-admin half shipped separately as
  `a336203a5`, the gateway-api half is still not started. Rewrite this block before
  launching new work here.
- **Topic goodsCount — SHIPPED + DEPLOYED to trovemo.com (2026-08-18, master
  `ead2cb3d5`).** Closed the raise gateway-api logged in `contentAvailability.ts`.
  Deploy: goods-management container ONLY (build + `up -d --no-deps`), no migration,
  no reindex; live `goodsCount` on `/srv/topic/list`, paging byte-identical
  (total 20 / pages 7), smoke 200s across storefront + sitemap.
  ⚠ **The VPS was 5 merged-but-never-deployed commits behind** (`cedf964c7`):
  gateway-admin's season-category fix + pending-approval dashboard + groupon copy,
  and order's EUR mails, are STILL undeployed — this deploy rebuilt only
  goods-management, though its image now compiles against the newer
  litemall-core/litemall-db those commits carry. Their containers are the owning
  sessions' to ship.
  ⚠ **Prod disk was 85% (12 G free)** — below `litemall-prod.sh`'s own 30 G build
  threshold; `docker builder prune -f --filter until=24h` freed 22 G. Check disk
  BEFORE building on this host.
  ⚠ **No prod positive control is possible:** all 20 seed topics hold literally
  EMPTY goods arrays (`JSON_LENGTH = 0`, confirmed by an independently written SQL
  that matched the endpoint 20/20), so every live count is a correct 0. Non-zero is
  proven only by the Testcontainers test until an admin curates a topic.
  ⚠ **Pre-existing, NOT from this deploy:** `promotion-service` has been unhealthy
  8 days — its actuator health check times out at 5 s and a curl inside the
  container hangs. Untouched here; needs its own look.
  **Original detail:** `/srv/topic/list` rows now carry `goodsCount` (live =
  on-sale + not-deleted among the topic's curated ids). Contract:
  `litemall-goods-management/docs/handoff-topic-goods-count.md`. The raise named the
  request count (9 → 1); the bigger half was CORRECTNESS — `TOPIC_PROBE_LIMIT = 8`
  decided the Topics nav entry from the 8 newest topics, so a real topic sorted 9th
  could never light it. The count uses the SAME predicate as `findByIdVO`, which is
  what `/srv/topic/detail` renders with, so nav and page cannot disagree — an
  invariant, pinned by test, not a convention. ⚠ `queryList` selects only
  id/title/subtitle/price/picUrl/readCount, so `goods` was never in the list payload
  — the client could not have computed this. ⚠ Counts attach IN PLACE:
  `ResponseUtil.okList` reads `total` off the PageHelper `Page` it is handed, so
  rebuilding rows into maps would silently collapse `total` to the page size.
  ⚠ NEW FAILURE MODE, handled: `JsonIntegerArrayTypeHandler` throws on a malformed
  `goods` column and fails the WHOLE result set — caught, counts go unmeasured
  (`null`) rather than 500-ing an anonymous endpoint. ⚠ goods-management SERIALIZES
  NULLS despite core's NON_NULL customizer: `JacksonConfig` also declares a raw
  `@Bean ObjectMapper` and that one wins — the same root cause as the known
  "LocalDateTime as arrays" quirk. Clients must test for a number, not for key
  presence. `/srv/topic/detail` has always had that exposure and was deliberately
  left alone. ⚠ Testcontainers gotcha: `LitemallCjLinkageMapper.xml` `<include>`s
  fragments from Brand/CjProduct mappers, and MyBatis parses statements LAZILY — a
  missing `addMapper` surfaces as a failure on first call, not at build time.
  litemall-db 24/0 (9 against real MySQL), goods-management 465 run / 0 failures /
  8 skipped. NO migration; litemall-db entity + mapper XML hand-edited together, so
  every dependent needs a rebuild to pick the field up.
- **History — Wave 27 season collection (backend half BUILT + MERGED).**
- **Wave 27 (2026-08-18) — SEASONAL MERCHANDISING COLLECTION.** User ask: push the
  current season's products in place of "Summer Deals", with a clear admin
  procedure to manage them and select them for promotion. **Backend half BUILT +
  merged: `bac29f144`** (module suite 455 run / 0 failures / 8 skipped; Flyway 4/4
  with **V63** applied clean on a real MySQL container, schema at v63).
  Audit facts (measured live 2026-08-18, prod): "Summer Deals" was NEVER a
  collection — a hardcoded keyword search (`q=summer&source=cj`,
  `GoodsListPage.tsx:27`) with its label hardcoded in `Layout.tsx:416`,
  `CategoryDrawer.tsx:84` and `Home.tsx:294`, and NO admin surface at all. Post
  narrowing it returned **39 products of 3,026**, relevance-relaxed so not even
  reliably on topic (result #2 = "Diving Tube Super Bright Flashlight", which does
  not contain the word). A season is now an ordinary DIY page with
  `category='season'`, inheriting the palette editor, draft/active lifecycle,
  clone flow and Postiz publishing.
  **Wave-27 CONTRACT (the other halves code to
  `litemall-goods-management/docs/spec-season-collection.md`, NOT to this branch):**
  - `GET /srv/page/season` → the active season page (identical PageView shape to
    `/srv/page/home`), or errno 642 "no active season page". Already anonymous —
    `/srv/page/**` was on public-paths.
  - Selection: `category='season' AND status='active' AND deleted=0 AND
    is_template=0`, `ORDER BY update_time DESC, id DESC LIMIT 1`. Deliberate: NO
    single-active invariant (that would need a migration + demote-then-promote),
    so two active season pages resolve **last-activated-wins**, never arbitrarily;
    `is_template=0` stops the seeded template ever going live; **NO scheduling
    columns** — activation is the switch, admin-gated and reversible.
  - **V63** seeds ONE draft template ("Season spotlight"). Its rail is
    `mode=deals`, NOT `byIds`: the validator demands 1-24 REAL ids, so a seeded
    byIds rail could only carry placeholders that either render nothing or
    advertise products nobody chose. Pinned by `PageTemplateSeedTest`.
  - No structural migration was needed for the category itself — `VARCHAR(31)`,
    no DB constraint; `'season'` only had to join `AdminPageController.CATEGORIES`.
  - **gateway-api (NOT started):** header link + home strip + `/summer` route
    become season-driven; on errno 642 the strip AND the nav link are ABSENT —
    never an empty grid or a dead link.
  - **gateway-admin (NOT started):** `'season'` in the page-list category filter;
    "New from template" already exists.
  Until the storefront half ships the endpoint is INERT and `/summer` behaves
  exactly as today.
- **EU-warehouse surfacing — `eu_flag` BUILT (2026-08-18).** Contract:
  `litemall-goods-management/docs/spec-eu-warehouse-flag.md`. Fourth instance of the
  `deal_flag`/`coupon_flag`/`groupon_flag` pattern: `EuStockSignalResolver` (60s TTL
  snapshot of EU-stocked pids, fail-soft, pid strings ONLY — `queryAllLive()` would
  drag every variants_json through memory), `ProductDocument.euFlag` always-emit 0/1,
  keyed on `cj_pid` not goods id, positive-only hit passthrough, `eu_flag` field +
  facet in `application.indexer-service.yml` **and BOTH dynamic-field regexes**.
  NO migration (V61 already stores it); NO filter-side code (SearchService forwards
  all candidates, OCS acts on configured facets). ⚠ **`eu_flag=0` means "not known to
  hold EU stock"** — it lumps "probed, none" with "never probed"; honest for a filter,
  a lie as a catalogue claim. ⚠ A reading is a measurement with a timestamp, NOT a
  delivery promise — customer copy must say "in stock in Germany at last check".
  ⚠ DEPLOY ORDER (Wave-19/21 lesson): indexer recreate → goods-management → full
  reindex → **searcher restart AFTER the reindex**, else `eu_flag=1` returns nothing.
  **gateway-api half NOT started** (badge + filter toggle; PDP badge already exists).
- **EU-warehouse measurement (2026-08-18, prod):** of the 30
  NEWEST arrivals **16 carry measured DE stock** (units 230 x9, 20 x7 — two arrival
  batches, so directional not precise); of 40 relevance-ordered catalogue products
  **0** do. The Wave-26 DE acquisition targets are working; the standing catalogue
  predates them. Authoritative split =
  `GET /srv/private/admin/insight/eu-sourcing` (computes survival over PROBED
  count, not on-sale, and returns null never 0 where unprobed). ⚠ The brand
  section is the WRONG home for this: brand answers *who sells it* (Wave 25 —
  a supplier must never render as a brand) while warehouse stock is a per-SKU,
  per-measurement property. The right seam is an **`eu_flag`** in the search index
  beside `dealFlag`/`couponFlag`/`grouponFlag` in `ProductDocument` (no migration —
  V61 already stores it). A delivery CLAIM must stay per-SKU: the PDP payload
  already omits `euStock` unless a real measurement found units > 0.
- **History — Wave 26 Phase 2.** Phase 2's code half (deliverables 2, 4, 5) merged
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

### Worktree: `gateway-api` — i18n BATCHES 3–7 COMPLETE + MERGED (deploy pending); theme hierarchy SHIPPED
- **No active assignment.** The storefront UI i18n is now COMPLETE (every §5 UI batch
  done; legal pages English by design). Candidate next tasks: i18n phase 2 `lang_key`
  on the user (V65, litemall-db shared-module discipline, order mails in the buyer's
  language); native sv/da review (user-side). Rewrite this block before launching.
- **Status 2026-09-05 — i18n batches 3–7 + slice fallbacks MERGED to master.** Six
  commits (`57ea2abfe` checkout/coupons, `59dd8a42d` orders/cookies, `24d884d39` user
  area/shared forms, `f3571861d` home/content pages, `a59d6a439` help centre/FAQ,
  `70e060428` redux fallbacks): ~380 more string sites, 12 namespaces, **24 lazy sv/da
  chunks**; jest 50 suites / 360 tests, tsc 0, `i18n:check` 0, prod build clean.
  As-built record: `litemall-gateway-api/docs/spec-i18n-foundation.md` §10.
  Every batch was rendered READ-ONLY against production data (built bundle + GET-only
  proxy to trovemo.com, headless Chrome) in sv/da with 0 page errors; signed-in surfaces
  (orders, user area) are proven at unit/type level only.
  ⚠ `faqData.ts` now holds the FAQ STRUCTURE only — wording lives in `help.json` by id
  (`faq.<id>.q/a`, `sections.<id>`, `links.<key>`); edit copy THERE, in all three
  languages, or the parity spec fails. ⚠ The courier "Included" class tests the delta
  NUMBER, never the label. ⚠ `count` is i18next's plural trigger — eight keys needed
  `_one/_other`; a sentence that merely mentions a number uses another variable.
  ⚠ Anchoring an import insert on `import React` glues it into the first line — anchor
  on the full line. Also fixed on the way: the order-confirmation fallback total still
  rendered `$` (missed by the Wave-24 € sweep).
  **Deploy = gateway-api container rebuild only** (no migration, no backend, no env;
  the Ubuntu-mirror gotcha below still applies). sv/da strings are MY drafts.
- **Previously (2026-09-05): theme hierarchy SHIPPED + DEPLOYED, i18n batch 2 DEPLOYED.**
- **DEPLOYED to trovemo.com 2026-09-05 02:32 UTC** from master `d45511aa8`: gateway-api
  container only (mirror-patched Dockerfile copy reused, cached runtime layer, build 2 min),
  healthy in 15 s, 0 errors, smoke 11/11 200s, live bundle `main.740efb7c…`. The SAME
  headless `getComputedStyle` check re-run AGAINST PRODUCTION: 372 elements / 0 Bootstrap
  blue, Help h1 26.4 / h2 18.4 / body 16, Returns 7×16px `rgb(31,42,46)`, cookie Accept
  `rgb(14,124,134)`, Amazon Ember on home/search/docs, header links white, 0 page errors.
- **Status 2026-09-05 — theme hierarchy + document type scale MERGED to master.** ONE
  commit, SPA only. jest 49 suites / 353 tests (was 48/331, +22 in `app/sass/theme.spec.ts`),
  tsc 0, prod build clean. What changed:
  1. **`--lm-*` palette now lives on `:root` in `app/sass/global.scss`** (was only in
     `product-card.scss`, so pages without a card had no tokens and relied on the
     `var(--lm-primary, #0e7c86)` fallbacks). Bootstrap remap beside it: `--bs-primary`
     (+`-rgb`), `--bs-link-color`/`-hover` (+`-rgb`) → teal; `.btn-primary` and
     `.btn-outline-primary` re-declare their `--bs-btn-*` literals (Bootstrap 5.3.8 bakes
     `#0d6efd` into the variant class, so a `:root` variable alone does NOT reach them).
     Global `a { text-decoration: none }` + underline on hover/focus.
  2. The four `font-family` overrides deleted (home.scss, storefront-home.scss,
     search.scss, _cards.scss) — everything reads `--lm-font`; no webfont added.
  3. `.lm-doc` (global.scss) on the 9 document pages (8 static + NotFound): body 1rem/1.6 in
     `--lm-text`, h1 1.65rem, h2 1.15rem with top margin; the `h4`/`h6` heading demotions
     removed; `small text-muted` dropped from body paragraphs, FAQ answers, the delivery
     steps list and the Privacy processor table (kept `small`); muted survives ONLY on
     "Last updated", the support-hours line, the help footnote and the chevron icon. Copy,
     seller identity and i18n keys byte-identical (verified: only className attributes changed).
  **Headless acceptance on the built bundle (real Chrome, `getComputedStyle`):** /help
  /returns /service /cookies /404 → 372 anchors/buttons/icons scanned, **0** with
  `rgb(13,110,253)` in color/background/border; Help h1 26.4px, h2 18.4px > body 16px, icon
  teal; Returns 7 body paragraphs all 16px `rgb(31,42,46)`, "Last updated" 14px, link teal
  with no underline at rest; cookie Accept (`btn btn-primary`) background `rgb(14,124,134)`;
  `.lm-home`, `.lm-isearch`, `.lm-doc` all resolve to the Amazon Ember stack; header links
  still white. 0 page errors. Harness: built bundle served locally + GET-only proxy of
  `/srv`/`/auth` to trovemo.com (no dev stack was up).
  ⚠ Blast radius is storewide by design: the 12 files that use `btn-primary` /
  `text-primary` (CookieBanner, CookiePreferences, ErrorBoundary, Coupons, CouponCenter,
  Groupon, GrouponDetail, TopicDetail, one Checkout button …) turn teal with no edit — that
  was the point. Anything that WANTED Bootstrap blue no longer gets it.
  **Deploy = gateway-api container rebuild only (MAIN)**, no migration, no backend, no env.
  ⚠ Ubuntu-mirror gotcha from the i18n deploy still applies (see the entry below).
- **Previously (2026-09-05):** i18n batch 2 merged AND DEPLOYED — see below.
- **Status 2026-09-05 02:10 UTC — i18n BATCH 2 DEPLOYED to trovemo.com** from master
  `1d5536f7e` (contains `ee51e96ff`): gateway-api container only, healthy in 15 s,
  `Started GatewayApiApplication` clean, 0 errors, smoke 8/8 200s; live bundle
  `main.3c496ef1…`, 14 i18n chunks in the runtime map, 6/6 sv/da chunks verified BY
  CONTENT through the edge (`Lägg i varukorgen`, `Læg i kurv`, `Pris: lägst först`,
  `Sortér efter`, `Skickas från`, `Sendes fra`).
  ⚠ **Two build attempts FAILED, neither because of the code:** the disk guard forced a
  `docker builder prune -af`, which also evicted the shared `runtime` layer
  (`apt-get install curl tini` on `eclipse-temurin:21-jre-jammy`) that every service
  image had reused for weeks — and that night archive.ubuntu.com was crawling (22 s per
  InRelease from the host, 15 min with no bytes inside the sandbox, then exit 100). The
  gate held both times (container untouched). Attempt 3 built from a Dockerfile COPY in
  `/root/Dockerfile.i18n2` (line 107 only: apt sources → de.archive.ubuntu.com, the
  official German mirror, 0.08 s) with compose's own target + tag, 56 s, then
  `up -d --no-deps`. Nothing in the repo tree was changed on the VPS. Follow-up worth a
  commit: make the mirror an `ARG` (or add `Acquire::Retries`) so a mirror outage cannot
  block a deploy. ⚠ The runtime layer is cached again now; the NEXT prune will evict it.
  ⚠ Resolve lazy chunks as `app/<chunkName>.<hash>.js` (name + hash from the runtime
  map), not `<id>.<hash>` — the id form 404s. The base JRE image already ships curl;
  only tini comes from apt. VPS checkout left detached at `1d5536f7e`.
  Next i18n task after this one = batch 3 (cart/checkout + delivery chooser +
  coupon cell; spec §5).
- **Status 2026-09-05 — i18n BATCH 2 BUILT + MERGED to master.** ~90 string sites
  across 23 files (spec §5 estimated ~30): `ProductCard`, `Detail.tsx` + 14 PDP
  sub-components, `Search.tsx` + rail/tree/empty/unavailable states, `euStock.ts`.
  New namespaces `product` + `search` (14 lazy sv/da chunks now); shared EU-warehouse
  phrasing in `common:euStock.*` because batch 3's checkout badges read the same
  helper. jest 48 suites / 331 tests (was 45/320), tsc 0, `i18n:check` 0, prod build
  clean. As-built record: `litemall-gateway-api/docs/spec-i18n-foundation.md` §9.
  **Live render check was done against PRODUCTION DATA, read-only:** no dev stack
  was up, so the built bundle was served locally with a GET-only proxy of
  `/srv`/`/auth`/`/_cdn` to trovemo.com and driven headless — PDP in sv, search +
  zero-results in da/sv, home grid in sv all render translated, 0 page errors.
  ⚠ Backend `sortOptions` labels and dynamic facet headings are SERVER strings:
  known fields map to keys, anything else passes through VERBATIM (the
  `describeError` rule) — never "fix" that into a hardcoded list. ⚠ react-instantsearch
  `translations` props MUST be memoised on `t` (dequal compares functions by
  reference — an inline object remounts the widget and refetches every render).
  ⚠ `count` is i18next's plural trigger, not a free variable — a `{{count}}` key
  without `_one/_other` fails `i18n:check`. ⚠ `pkill -f <pattern>` matches the Bash
  tool's own shell and kills the session — kill by port (`ss -ltnp`) instead.
  Seen live, NOT this batch: cookie banner still English (batch 6); the category
  facet shows 3 raw ids whose leaves are missing from the catalog name map
  (pre-existing, not a language issue). sv/da strings are MY drafts — native review
  still user-side. **Deploy = gateway-api container rebuild only** (no migration, no
  backend change, no reindex); env unchanged (`LITEMALL_I18N_LANGUAGES=en,sv,da` is
  already live).
- **Previous status (2026-09-04) — i18n FOUNDATION PHASE 1 SHIPPED + LIVE.**
- **Status 2026-09-04 — i18n FOUNDATION PHASE 1 MERGED + DEPLOYED to trovemo.com
  with sv/da ENABLED** (master `4fca1ce06`; `.env.prod` `LITEMALL_I18N_LANGUAGES=
  en,sv,da`, backup `.env.prod.bak-i18n-2026-09-04`; container healthy in 13 s; live
  bundle `main.a0b81a9c…` + hashed sv/da chunks verified BY CONTENT; smoke 200s).
  ⚠ **The deploy failed TWICE before it landed, neither time because of i18n:** the
  image build has no root lockfile, so `npm install` floated the storefront onto
  **webpack 5.110.3** (published 2026-08-27, one day after the previous gateway-api
  image) whose new default `minimizer-webpack-plugin` crashes on html-webpack-plugin's
  `index.html` ("reading 'syntax'"). Pinning webpack 5.107.2 in the storefront did NOT
  help — npm hoists `webpack-cli` to the workspace root and it requires the ROOT
  webpack. Fix that held: `webpack.config.prod.js` now sets an explicit JS-only Terser
  minimizer (verified under 5.110.3 AND 5.107.2). ⚠ My first deploy script recreated
  the container from the OLD image after the failed build (harmless, but wrong) —
  the script now stops on `BUILD EXIT != 0`. Full record: spec §8.
  ⚠ The sv/da strings had a second critical pass by ME, not a human native speaker
  (spec §8 lists the idiom fixes). A human pass is still worth having; corrections are
  JSON data + a container rebuild, nothing else.
  Rollback of visibility = `LITEMALL_I18N_LANGUAGES=en` + recreate (no rebuild).
- **Build detail (2026-09-04) — i18n FOUNDATION PHASE 1.** User-commissioned outside any wave ("lay the
  architecture foundation for multi-language like the JHipster announceapp02
  gateway: en default, sv, da"); plan approved same day. FROZEN spec + as-built
  record: `litemall-gateway-api/docs/spec-i18n-foundation.md` (read §0 on
  currency and §7 before touching this). jest 45 suites / 320 tests (was
  40/274), tsc 0, mvn compile clean, `npm run i18n:check` green with a negative
  control, prod build clean with 10 lazy `i18n-{sv,da}-*` chunks.
  What it is: `i18next` + `react-i18next`, JHipster's folder-per-language /
  file-per-feature layout under `app/i18n/locales/`, English BUNDLED and
  registered synchronously (first paint never waits; a missing key falls back to
  English, never a raw key), sv/da lazy-loaded per namespace via webpack
  `import()`. Locale = an observable store (`app/i18n/locale.ts`, the siteConfig
  pattern) readable outside React — `money.ts` reads it. Detection: `?lang=` →
  cookie `lm_lang` → browser (sv/da only) → en. **ENV-GATED like every other
  storefront feature:** `LITEMALL_I18N_LANGUAGES` (default `en`) via
  `/auth/site-config` `i18nLanguages`; the switcher renders only when >1
  language is enabled and a Swedish browser gets English until then. Prod compose
  passthrough is IN (with the feature, the price-floor lesson). Server errors are
  localised BY ERRNO on the client (`describeError`; unknown errno ⇒ server text
  verbatim, the repo's typed-refusal acceptance behaviour preserved).
  ⚠ **Language ≠ currency.** The user asked for kronor; the store CHARGES EUR
  (Wave-24 decision). Phase 1 localises the WRITING of one currency
  (`€1,234.56` / `1 234,56 €` / `1.234,56 €`), never the amount. Real SEK/DKK is a
  money-path wave across four services, and an "≈ 139 kr (charged in EUR)"
  indicative line is an OPEN user option, not built. `moneyParts` (card split) is
  deliberately locale-stable.
  Pilot migrated (every visitor sees these on every route): Layout header/strip/
  footer, CategoryDrawer, Cart + SubmitBar, NotFound, both error-boundary
  fallbacks (plain `t()`, not hooks — a fallback must not depend on what threw),
  login/register/reset, Google button fallback, coupon-reason + courier labels.
  ⚠ sv/da strings are MY DRAFTS — native review is USER-SIDE before enabling
  them in prod. Legal pages stay English in every locale by design.
  ⚠ Gotchas: `i18next-parser --fail-on-update` logs `[write]` but writes nothing,
  and with `sort:true` a hand-ordered file counts as drift — config runs
  `sort:false`/`keepRemoved:true`/`createOldCatalogs:false` so the check fails on
  exactly one thing (a code key some language lacks). Components that render
  `money()` without `useTranslation` update on their next render, not on the
  switch — the reason the remaining ~200 string sites migrate FILE BY FILE
  (batches 2–7 in the spec §5) rather than remounting the route tree (which would
  wipe checkout state). `lang_key` on the user = phase 2 (V65, litemall-db,
  shared-module discipline); URL prefixes/hreflang only if content is ever
  translated.
  Merged + deployed the same day (see status above); batch 2 (PDP + cards + search
  rail) is the next task.
- **Previous status (2026-08-26) — footer audit + EU stock SHIPPED + LIVE.**
- **Status 2026-08-26 — FOOTER AUDIT + EU STOCK: MERGED + LIVE on
  trovemo.com.** Commits `b5e8d7004` (copy/nav) + `0469129b8` (EU stock +
  empty facets), merged `0bba2e553`, pushed. jest 40 suites / 274 tests (was
  35/237), tsc 0 errors, prod build clean. No migration, no backend change,
  no reindex.
  ⚠ **I did NOT build the container — it was already deployed when I went to
  do it.** Another session recreated gateway-api at 13:32 UTC from a master
  that had merged mine underneath (`311db0256`, an admin fix, is a descendant
  of `0bba2e553`), so my code shipped inside their build. **Always check the
  live bundle for your own strings BEFORE building** — the container being
  "Up 3 hours" says nothing about which commit is in it. Verified live by
  content: `main.8859a4fc…` carries "Best sellers" and none of the removed
  copy; the EU chip is in 12 ProductCard chunks; chunk 1643 carries `eu_flag`,
  "In EU stock", "Delivery", "last stock check", plus `entryCount`/`fieldName`
  from facetVisibility. Smoke: 15 routes + robots/sitemap/meta-catalog all
  200, `eu_flag=1` → 921 of 4,239 through the edge, deal_flag → 12, PDP
  JSON-LD intact, container healthy with 0 errors in 3h of log.
  ⚠ **The brand facet went EMPTY live during this session** — the peer's
  reindex (4,214 docs) landed and `brand` is now ABSENT from `filters[]`
  entirely, not merely empty. That is exactly the case `shouldShowFacet`
  hides, and it was hiding nothing before this shipped.
  ⚠ **Disk: I pruned ALL 18 G of build cache** (`docker builder prune -af`,
  the script's own remedy) because the host was at **13 G free against
  `litemall-prod.sh`'s `MIN_DISK_GB=30`** — the guard exists because "a build
  filled the disk to 100% once and OOM-killed half the containers". Host is
  now 33 G free. Cost: the next build on this host is cold. The audit's
  "a few more single-service builds and this needs a real decision" arrived.
  User-commissioned: check the footer links, then track what is missing.
  **All 24 footer links resolve** — no dead links, no 404s, no placeholder
  pages, and the Wave-26 availability gating correctly hides Topics/Articles/
  Group buys. The defects were one level down. Full tracked list (20 numbered
  items, live-verified, with owners): the artifact published 2026-08-26, and
  the six items below are the ones that were mine.
  1. **The footer promised what /help contradicts.** "Fast, tracked delivery /
     on every order, nationwide" (we sell cross-border in the EU, and the FAQ
     already says delivery "can be longer than domestic shipping");
     "Hassle-free refunds & exchanges" (buyer pays return shipping on remorse;
     replacement only for defects); "Customer care, every day" (it is Mon–Fri).
     The blurb still sold a 17k-product general store with "trusted brands".
     ⚠ **The hours disagreed because footer and /service each hardcoded their
     own** — `SUPPORT_HOURS` now sits beside `SUPPORT_EMAIL` in `faqData.ts`,
     which exists precisely to stop /help and /service drifting.
  2. **/service advertised "Online support"** with hours, a headset icon, no
     chat, no phone and no link, directly above the only real channel. That
     page's own comment forbids exactly this; it was written when the fake
     phone number was removed and this row survived it.
  3. **"Today's Deals" named TWO pages** — header strip → `/deals`, drawer →
     `/hot`, and `/hot`'s own heading agreed with the drawer. `/hot` ranks by
     `listed_num`: it is **"Best sellers"** now, at all three sites. ⚠ An
     existing drawer spec asserted the old label — it was pinning the bug.
  4. **"Send feedback"** sat in the help column behind a login wall, three
     lines under the Returns link whose comment forbids that. Signed-in only.
  5. **The cookie policy described the Meta Pixel** while `metaPixelId` is
     null in prod, documenting a tracker that never loads. Config-driven now,
     matching what `CookiePreferences` beside it always did.
  6. **eu_flag badge + filter** (the Wave-27 half that was never started):
     `/srv/search?eu_flag=1` returns **921 of 4,135** live. Chip on cards +
     "In EU stock" toggle under a **Delivery** heading + `?eu_flag=1` deep
     link. ⚠ **The badge deliberately does NOT join the overlay priority
     chain** (deal > discount > coupon > group buy, one badge max) — EU stock
     is a fulfilment fact, not an offer, so it rides the logistics line and
     displaces no deal. ⚠ **Copy states a measurement, never a delivery
     window**: `eu_flag=0` lumps "probed, none" with "never probed", so a
     positive is a fact and a negative is an unknown — there is no inverse
     badge and none should be added. The hit carries **no country** (verified
     live), so the PDP's "Ships from Germany" phrasing cannot be reused.
  **Also fixed, raised by the goods-management session against its own
  change:** the filter rail rendered a heading for any facet the backend
  NAMED, never checking it had values — so after the next full reindex empties
  the brand facet, `<h3>Brand</h3>` would stand over nothing. `shouldShowFacet`
  now answers it for EVERY facet with the nav probe's own policy: pending
  hides, empty hides, **a FAILED probe SHOWS** (an outage must not amputate
  the rail), and a refined facet always shows or a `?brand=` deep link could
  not be cleared. Interval groups count as populated when named — RangeInput
  takes bounds from `facets_stats` and never lists buckets.
  ⚠ **Test gotchas:** this repo has **no jest-dom** — assert `toBeTruthy()`/
  `toBeNull()`, not `toBeInTheDocument()`. A `useSyncExternalStore` mock must
  return a **referentially stable** snapshot or the component re-renders
  forever. `jest.resetModules()` + dynamic `import()` hands the subtree a
  SECOND React instance (null hook dispatcher) — mock the module instead.
  ⚠ **Verify built strings by CHUNK, not main.js**: the badge ships in all 13
  ProductCard chunks and the toggle in the search chunk; main.js has neither.
  **STILL OPEN, needs the user:** VAT number, GPSR responsible person, and a
  return address for `/returns` (the policy explains the window, who pays and
  how refunds are paid, then never says where to send the goods). Deliberately
  NOT written as plausible placeholders.
  **RAISED, not worked around:** three supplier stores are display-enabled but
  never renamed — `dbjjj` (251 products), `DVLL` (278), `EVERGREEN SHOP LLC`
  (30) — so 559 PDPs say "Sold by dbjjj" and the footer's "Shop by brand" link
  exists ONLY because of them (admin rename-or-hide). The single public coupon
  is a test worth 11 cents whose scope matches 0 indexed products. The active
  season page is named "Autumns Deal". All admin-side, no deploy.
- **Status 2026-08-24 — STOREFRONT AUDIT: findings + fixes, DEPLOYED to
  trovemo.com** (master `3b636211d`, bundle `main.519ecc3d…`, gateway-api
  container only; no migration, no backend change, no reindex).
  A full scan of the SPA + edge, static analysis plus live read-only probes.
  Two commits: `b11da7898` (error boundary + dead-code deletion) and
  `3b636211d` (origin gate, stale comments, 404 page, PDP a11y).
  1. **The storefront had NO error boundary at all** — `index.tsx` rendered
     `<Provider><App/></Provider>` and nothing caught a render exception, so one
     throwing component blanked the whole shop. (The admin got one after its DIY
     LocalDateTime crash; the customer-facing surface never did.) An
     `ErrorBoundary` class existed, orphaned in `views/commonViews/boundaryerror`,
     imported by nothing, rendering a bare "Something went wrong."
     Now `RouteErrorBoundary` (wraps Layout's Outlet — a crashed PAGE keeps
     header/search/nav) + `RootErrorBoundary` (outside the router, reload only).
     ⚠ **Both RESET on navigation**: without that a boundary is a TRAP — React
     keeps the fallback mounted, so the first crash makes every later route look
     broken until a hard reload. Neither claims we reported anything (nothing is
     wired); a test pins that.
  2. **41 source files + 6 stylesheets, 2,930 lines, DELETED** — unreachable
     from `app/index.tsx`. Whole parallel implementations (a second account
     area, a second product-detail set, a blog, 404/500 pages, a second cart
     sidebar, `loadingModule.tsx` which was itself unimported and took
     CategoryList/SubCategoryList with it).
     ⚠ **Reachability was computed FROM THE ENTRY POINT, not "is anyone
     importing this"** — the latter keeps dead clusters alive when they
     reference each other; it found 41 where the cruder sweep found 37.
     ⚠ **The payoff was NOT bytes** (webpack already tree-shook it: 716→713 KB).
     It is that **all 73 tsc errors across 20 files lived in that dead code**,
     some referencing modules that no longer exist — so typecheck was
     permanently red and useless as a gate. **`tsc` is now 0 errors and can gate
     CI.**
  3. `GET /srv/goods/origin` **404s** (Wave-28 SPA shipped before its backend
     half). Already fail-open, but re-asked on every cart change. A 404/501 is
     now remembered for the SESSION and short-circuits; a 5xx or dropped
     connection stays retryable (ill ≠ absent). Nothing persisted, so it
     self-heals when the backend ships.
  4. **Seven TODOs asserted endpoints were "not implemented yet"** — orders
     list/detail/ops, favorites, footprint, feedback, reviews, brands, topics.
     ALL SEVEN controllers verified live. Same failure mode that had
     gateway-admin suppressing a working action for four months.
  5. The catch-all 404 was a bare unstyled string. Post-narrowing (17k→3.6k
     on-sale) retired URLs sit in Google's index for weeks, so it is a real
     landing page: it now names the failed path, says the product may be off
     sale, and offers search + home/all-products/deals. It deliberately does NOT
     guess intent from the URL.
  6. PDP gallery thumbnail buttons had **no accessible name** (decorative
     `alt=''` only) — now aria-label + aria-pressed.
  ⚠ **Two of my own audit claims were WRONG and corrected in the commits:** the
  "3 images without alt" was a multi-line-`<img>` grep artifact (only the
  thumbnail buttons were a real defect), and the "live console.log" is inside a
  commented-out block. Also discarded mid-scan: "/deals is empty" (it has 12 —
  I had probed the per-product `/srv/goods/deal`) and "the admin container is
  stale" (its bundle does carry `season`).
  **RAISED for goods-management, NOT worked around:** `/sitemap.xml` carries
  3,718 products + 2 categories + the homepage and **ZERO `/page/*` entries** —
  DIY pages, including the season collection, are invisible to Google. The XML
  is generated by goods-management; the edge only proxies it.
  **NOT done (deliberate):** main.js is 713 KB raw / ~181 KB gzipped, over
  webpack's 244 KB budget — needs a real dependency analysis, not an edit.
  ⚠ **VPS disk is trending down: 24 G → 20 G → 17 G free across two builds**,
  against `litemall-prod.sh`'s own `MIN_DISK_GB=30`. All build cache is <7 days
  old (so pruning forces cold rebuilds). A few more single-service builds and
  this needs a real decision.
  ⚠ **The Autumn Deals season page is STILL NOT LIVE**: prod page id 4 is the
  seeded V63 template edited IN PLACE, so `is_template=1` and
  `selectActiveByCategory` excludes it by design. Fix is admin-side: clone it
  (clone sets `isTemplate(false)`), rename, activate the copy, deactivate id 4.
- **Status 2026-08-22 — WAVE 27 STOREFRONT HALF + THE AUTUMN COLLECTION.**
  Branch commit `bbd69f92c` (jest 219/219, 32 suites, 36 new; module 67/0;
  prod build clean). User-commissioned: "replace Summer Deals with Autumn
  Deals based on goods appropriate for the autumn period, both Home, Garden
  and Home improvement." NOT merged to master yet.
  Coded to the FROZEN spec `litemall-goods-management/docs/spec-season-
  collection.md` §3. Header strip, "☰ All" drawer and home rail now read
  `GET /srv/page/season`; `/summer` redirects to the active season (home when
  none). **Nothing hardcodes a season** — name and products are page data, so
  swapping Autumn for Winter is one admin activation, no deploy.
  New: `shared/util/season.ts` + `useSeason.ts`, `modules/home/SeasonRail.tsx`
  + `Section.tsx` (extracted from Home), `modules/page/SeasonRedirect.tsx`,
  `modules/page/goodsListSource.ts` (the goods-list resolver lifted OUT of
  PageRenderer so the home rail and the season page cannot disagree).
  ⚠ **Two deliberate departures from the Wave-26 `contentAvailability` probe
  next door, both documented in season.ts:** (a) NO fail-open — the link
  target `/page/<id>` exists only inside the payload, so a failed fetch has
  nothing to fail open TO; 642 and a transport error both mean absent;
  (b) NO sessionStorage — caching the payload would keep a DEACTIVATED season
  on screen for the rest of the session.
  ⚠ **FOUND + FIXED, and it would have silently broken the collection:**
  `POST /srv/goods/batch` was public for **GET only**, and `byIds` — the
  goods-list mode the season AND coupon curation procedures tell admins to
  pick — resolves through it. Every curated DIY rail therefore rendered
  **EMPTY for logged-out shoppers** while looking correct to the signed-in
  admin previewing it. Proven on prod 2026-08-22: `GET /srv/goods/detail`
  200, `POST /srv/goods/batch` **401**. `PublicPaths.GOODS_BATCH_POST` is the
  EXACT path, POST only (no prefix, so no future write rides in); exposes
  nothing `GET /srv/goods/**` did not already serve one id at a time;
  goods-management's own public-paths never restricted the method.
  **RAISED for goods-management:** `batchGoods(@RequestBody Set<Integer>)`
  caps nothing, so it is an amplification surface — the cap belongs in that
  handler.
  **Live verification against PRODUCTION (read-only, no VPS access):**
  `/srv/page/season` → `{"errno":642,"errmsg":"no active season page"}` — the
  Wave-27 backend IS deployed and anonymous, and 642 is exactly the shape the
  resolver handles. The POSITIVE path (season activated ⇒ surfaces light up)
  is proven at the jest seam only: dev has no Java stack running and the
  MySQL password is user-held, so no dev season could be activated. First
  live positive check = prod, after deploy + activation.
  **THE AUTUMN COLLECTION IS CURATED AND HANDED OVER, NOT ACTIVATED:**
  `litemall-gateway-api/docs/autumn-deals-collection.md` — 24 goods ids
  validated against the live feed (on sale, in stock, real images), 15 Home &
  Garden / 9 Hardware, ordered so the first 8 are the home rail (4/4 across
  both anchors), with per-pick rationale, the paste-ready byIds config, page
  copy, and the admin procedure. Measured justification: `q=autumn` matches
  **5 products of 3,646** (`summer` matched 18) — a keyword swap would have
  been worse than what it replaced; curated by theme the catalogue holds ~144
  Halloween/harvest, ~96 outdoor lighting, ~31 cosy textiles, ~19 garden
  tidy-up. ⚠ **Do NOT activate the page until the gateway-api container
  carries this build** — on the old container the rail is empty for every
  logged-out shopper. ⚠ Halloween is a deliberate MINORITY (4 of 24) so the
  page stays truthful into November; swap or clone around 1 Nov.
  **Also measured, for whoever plans autumn ads:** draught-sealing and
  weatherproofing — the obvious autumn DIY theme — has **3 products in the
  entire store**. Sourcing gap, not a curation one.
  **RAISED for goods-management (catalogue hygiene, not blocking):** 68 live
  products carry a raw supplier prefix in the title ("Support Pan European："
  with a fullwidth colon); 2 are titled "Halloween …" for no reason, incl.
  ordinary thermal blackout curtains. Both are customer-visible on the PDP
  and in the Meta feed.
- **Status 2026-08-22 — WAVE 28 (peer session's work) MERGED to master
  `8373f2b3f`.** "Ships from: Trovemo" → measured origin. It had been sitting
  committed-but-unmerged on this branch since 2026-08-19 with the block never
  updated; its jest claim (183/183) was RE-RUN and confirmed before merging.
  Spec for its unstarted backend halves: `docs/spec-wave28-eu-origin-
  freight.md`.
- **Still NOT started (both backends already on master):** the `eu_flag`
  search badge + filter toggle (`spec-eu-warehouse-flag.md`; ⚠ deploy order
  is reindex → searcher restart, and `eu_flag=0` means "not known", not
  "none").
- **History — Wave 26 (Phase 2/4 storefront half): "no empty tiles, no dead
  links" after the narrowing.** MERGED to master `40cb2e024` (2026-08-18;
  webapp suite 170 passed / 25 suites, 39 new tests, clean prod build).
  **DEPLOYED to trovemo.com 2026-08-18** — gateway-api container only, no
  migration, no backend change, no reindex. Built from the VERIFIED commit
  (detached checkout of `87478b944`), NOT from master's tip: master had
  meanwhile picked up another session's Wave-27 work incl. a litemall-db
  migration (V63), and baking a peer's unshipped db code into this image was
  not mine to decide. Evidence: image built, container recreated healthy in
  11s, 0 errors in its log, smoke 200s across shell/robots/sitemap/PDP/
  category + every `/srv` surface the probes call; new bundle
  `main.b4f4a08ef3cd9fbf3ae5.js` carries `lm_content_avail` +
  `requestIdleCallback`, lazy chunk 845 carries the new empty-state copy and
  "No subcategories", chunk 923 "No brands to show yet" (⚠ route components
  are code-split — verify by CHUNK content, the main bundle alone proves
  nothing). Final click-through is USER-SIDE (the gate renders client-side).
  ⚠ **VPS repo gotcha found while deploying:** `/opt/litemall`'s local
  `master` ref was stale at `cb791e856` (Wave 18) — deploys had been running
  from detached HEADs after `git pull --ff-only /opt/litemall.git master`,
  so `git checkout master` there SILENTLY REWINDS the tree three months.
  Left fast-forwarded to the pushed tip and clean; check
  `git rev-parse --abbrev-ref HEAD` before trusting a VPS checkout.
  ⚠ Pre-existing, NOT caused by this deploy: `promotion-service` has been
  **unhealthy for 7 days**; disk is at 85% with 28GB of reclaimable build
  cache (`litemall-prod.sh` refuses a full build below 30GB free — currently
  12GB).
  Audit measured on the LIVE narrowed store (dev was never narrowed — it
  CANNOT reproduce this state; verification was fixture-based against captured
  live payloads): the L1 category nav was ALREADY honest (goods-management
  `9db298ccf` filters empty roots out of `/srv/catalog/all`, and the homepage
  tiles / drawer / search rail all read that filtered list; sitemap carries
  exactly the 2 anchor categories — do NOT rebuild any of that). What was
  broken were the CONTENT sections the header strip, "☰ All" drawer and footer
  advertised regardless: `/srv/brand/list` 49 seed brands with **goodsCount 0
  on all 49** and plain-`http` yanxuan images (mixed content, blocked on the
  https page); `/srv/topic/list` 20 seed topics from 2018 with **goods:[] on
  all 20**; `/srv/article/list` total 0; `/srv/groupon/list` total 0. Plus:
  retired L1 departments still resolve by URL (`/category/1005000` → 0 hits)
  and said "No products found. Check the spelling"; `/srv/search/category/{id}`
  lists zero-count children (Cross-Stitch 0) as links into empty pages.
  Fix: `app/shared/util/contentAvailability.ts` is the SINGLE answer to "does
  this section have content", read by the nav AND the page it points at so they
  can never disagree. Data-driven, not a static removal — enabling a brand,
  filling a topic or starting a campaign restores the entry with NO rebuild.
  Failure semantics mirror the backend's own empty-category filter: pending ⇒
  hide (never advertise unconfirmed), FAILED probe ⇒ show (an outage must not
  amputate navigation), unjudgeable payload ⇒ show. One probe per surface per
  session (sessionStorage + in-flight dedup), deferred to `requestIdleCallback`.
  Also: /topics and /brands list only entries with products behind them
  (BrandList's 49 count-probes drop to 0 — the backend has supplied
  `goodsCount` since Wave 25); zero-count subcategory children dropped while
  UNCOUNTED ones survive; the empty state names the real reason only when
  nothing else explains it (a filter that emptied a live category blames the
  filter); blocked `http` images render a placeholder, never a broken img.
  ⚠ **RAISED for goods-management:** a `goodsCount` on `/srv/topic/list` rows
  would collapse the topic probe from 9 requests to 1.
  ⚠ **Test gotcha:** jsdom has no `window.matchMedia`, so react-bootstrap's
  `Offcanvas` (CategoryDrawer) fails inside `useBreakpoint` without a polyfill.
- **History — Wave 26 Phase 1b: EU stock badge on the PDP** (`ce75ab5e7` +
  `c12708982`, merged 2026-08-15; the payload half landed in goods-management
  `56dc33fe9`). Badge stays SILENT unless a product was probed AND has non-zero
  EU stock; it promises no delivery window.
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

### Worktree: `gateway-admin` — SEO title worklist: LIVE DEV ACCEPTANCE PASSED 2026-09-05 (one UI honesty gap found, unfixed)
- **RAISED by order 2026-09-05:** code to
  `litemall-order/docs/handoff-gateway-admin-cj-requeue.md` — parked rows
  (`parked`, `parkReason`) in the pending tab, a **Requeue** action on
  `POST …/cj-placement/requeue`, `[AFTERSALE_OPEN]`/`[PARKED]` approve refusals.
- **Status 2026-09-04 — the worklist is LIVE in prod; this pass closes its open items.**
  The 2026-08-24 block below said "NOT merged, NOT run against a live stack". Both were
  stale when this session opened: the worklist merged as `19b3d496d` and was deployed to
  trovemo.com on 2026-08-26 (goods-management + gateway-admin rebuilt at that commit),
  DARK for keywords — `source=file` with an EMPTY export dir on the VPS, so it runs as
  truncation-only. Its FIRST live use (prod goods 10000844) exposed that Apply said
  "Request failed" on every call including successful ones; master fixed it as
  `10ca91595` (`applyResult.ts` + 5 tests). This branch had 0 commits of its own and was
  52 behind, so it was fast-forwarded (step 4 of the old list = done, nothing lost).
- **Built here (one commit, both halves — the SPA is useless without its endpoint):**
  1. **Honest reindex on apply.** `TitleOptimisationService.apply` wrote MySQL and THEN
     called the indexer; `OcsProductIndexer.upsert` throws on any transport failure and
     the controller caught only IllegalArgumentException, so an indexer outage turned a
     SUCCESSFUL rename into a 500 — the same "worked but said it failed" shape as the
     first live bug, one layer down. Now `apply` returns `Applied{goodsId, title, changed,
     reindexed, reindexError}`; a reindex failure is logged and REPORTED (errno 0 with
     `reindexed:false` + `warning`), never thrown. The SPA shows the caveat verbatim and
     marks the row "saved, not reindexed" instead of inviting a retry of a write that
     already landed.
  2. **Bulk apply.** `POST /srv/private/admin/seo/titles/apply-batch` body
     `{items:[{goodsId,title}]}` → `{applied, failed, results:[{goodsId, ok, title,
     changed, reindexed, error}]}`, one entry per item IN ORDER. Each row goes through
     `apply` on its own: a refused row (blank, >127, unknown goods) is reported IN PLACE
     and the batch carries on — nothing already written can be taken back. Only an empty
     or >100-row batch (`BATCH_LIMIT`) is refused wholesale (errno 660), and then nothing
     was written. Same `/srv/**` catch-all, no gateway route change.
  3. **SPA.** Checkbox per row; the HEADER checkbox selects ONLY `needsReview === false`
     rows whose draft would actually change something (a flagged row can still be ticked
     by hand after reading). "Apply selected (N)" → inline confirm line ("Rename N
     products and reindex them…") → batch → a dismissible banner with the server's
     per-row wording verbatim. Applied rows LEAVE the list on refetch (they are no longer
     over-length), so the banner is the only record of what just happened — it stays
     until dismissed. Refused rows stay ticked with their error in the Actions cell.
     Selection logic lives in `Insight/seoTitleBatch.ts` (testable, promoFormat.ts
     pattern); the component itself now has a testing-library spec
     (`SeoTitleList.spec.tsx`, first component-render spec in the admin suite — the RTK
     hooks are mocked at the module seam, no fetch).
- **Tests (real counts):** goods-management module suite **582 run / 0 failures / 8
  skipped** (+7 `TitleOptimisationServiceTest`, incl. reindex-failure-is-reported and
  batch-reports-refusals-in-place); admin jest **153 passed / 14 suites** (was 130/12:
  +11 `seoTitleBatch.spec.ts`, +12 `SeoTitleList.spec.tsx`);- **Status 2026-09-05 — LIVE DEV ACCEPTANCE PASSED through :18080** (branch == master
  `8c674d368` at the time; NO code change this pass). The "user-held secrets" blocker was
  STALE: `~/.litemall/dev.env` (mode 600) holds MYSQL_PASSWORD, and the authserver /
  gateway-admin secrets are any MATCHING pair (`gateway-admin-dev-secret`, the way CI
  uses `ci`). Built goods-management + gateway-admin from THIS worktree with `-am`
  (reactor, no `~/.m2` install — the m2 litemall-db predated V64); booted eureka +
  authserver (MAIN's July jars — both modules unchanged since 2026-07-17) +
  goods-management :8082 + gateway-admin :18080 (JDK 21, `docker-compose/dc-local.sh`
  for OCS/ES/rabbit/redis). ⚠ The local dev DB was at **V60**; goods-management applied
  V61–V64 at boot (additive) — the "dev applied through V64" note elsewhere refers to a
  different DB. Proven, each through the gateway with the admin JWT:
  1. Worklist renders — 20 rows, header checkbox, blurb "4604 of 9642 live products are
     over" (headless: puppeteer-core + google-chrome, `waitUntil:'load'` — `networkidle0`
     never settles because the admin shell keeps polling; seed `admin-jwt`/`admin-refresh`/
     `admin-role` at `/`, reload, then pushState to `/admin/goods/seo-titles`).
  2. Single Apply on 10000032 with a marker word → MySQL renamed AND
     `/srv/search?q=ACCPROOF7` returned exactly that product with the new name. ⚠ The
     FIRST search, <5 s after the apply, returned `total 0, relaxed:true` — the served ES
     index has `refresh_interval: 5s`; the doc WAS already in the index. Not a bug: never
     assert search freshness inside 5 s of an upsert.
  3. 3-row API batch with one blank draft → HTTP 200, `applied 2 / failed 1`, the refusal
     IN PLACE (`title must not be blank`) and in order; DB confirmed 2 renamed, 1 untouched.
  4. `docker stop ocs_indexer` → Apply → HTTP 200, errno 0, `reindexed:false`, warning
     verbatim ("… No route to host"), MySQL renamed, index doc still the OLD title (the
     warning tells the truth). Same case through the UI → cell "saved, not reindexed" +
     alert "#10000179: Saved, but on-site search still shows the old title: …".
  5. UI batch (header flow: tick 3 → "Apply selected (3)" → confirm → banner) — see the
     finding below.
  All 4 renamed dev goods (10000032/160/177/179) were restored via apply itself; DB and
  ES titles byte-equal to the originals, worklist total back to 4604. `ocs_indexer`
  restarted; dev JVMs stopped afterwards.
- ⚠ **FOUND, NOT FIXED (needs its own approval): the UI batch silently drops a blank
  draft.** `seoTitleBatch.batchItems` filters `title.length === 0` CLIENT-side while the
  confirm line counts every ticked row — the admin confirmed "Rename 3 products and
  reindex them", the banner said **"Applied 2 of 2 titles."** in green, and the blank row
  stayed ticked with a greyed Apply and NO error (screenshot-verified). This contradicts
  the claim above that refused rows show their error in the Actions cell — true only for
  SERVER refusals. Two honest fixes: drop the client filter so the server refuses it in
  place (the documented behaviour; the `batchItems` jest spec pins the filter and must
  change with it), or exclude blank drafts from the confirm count and say so ("1 skipped:
  blank title"). Also "Apply selected (1)" stays ENABLED with only a blank row ticked →
  an empty `items[]` → errno 660 for the admin.
- ⚠ **Build gotcha (dev; matches what `docker/Dockerfile` already does):** `mvn package`
  on gateway-admin FAILS in the `test` phase — the frontend plugin runs
  `npm run webapp:test` = `web-test-runner src/**/*.test.js`, a dead legacy script that
  matches no files. `-Dmaven.test.skip=true` does NOT skip a frontend-plugin execution;
  `-DskipTests` does (the plugin honours it for executions whose arguments contain
  "test"). ⚠ A failed build leaves the PREVIOUS jar in `target/` — I booted a stale
  August jar once; check `BUILD EXIT` and the jar mtime before booting, and grep the jar
  for the feature string (`saved, not reindexed` sat in `static/main.<hash>.js`).
- ⚠ `pkill -f <pattern>` from the Bash tool matches the tool's own `bash -c` line and
  kills the calling shell (exit 144, twice) — kill by pid from an ANCHORED
  `pgrep -f '^/usr/lib/jvm/…'`.
- Deploy is still MAIN's (goods-management + gateway-admin rebuild, no migration).
  USER-SIDE: prod click-through. NEXT worktree task, if approved: the blank-draft batch
  fix above (tiny; SPA + one spec).
ot reindexed" — NOT a 500.
- **Jest gotchas (admin):** `--reporters default <path>` parses the PATH as a second
  reporter ("Could not resolve a module for a custom reporter") — use
  `--testPathPattern`. The global `jest` namespace is NOT typed here even with
  `types:["jest"]`; import `jest` from `@jest/globals` and type mocks as
  `Mock<any>` from `jest-mock` (the hoisted one is v29-shaped: ONE type argument).
  `getByText` matches a controlled `<textarea>`'s text content, so a proposal equal to
  the current title appears twice.
- **Ops note for MAIN (no rebuild):** copying `~/trovemo-seo-export/category-keywords-
  clean.csv` into the VPS `LITEMALL_SEO_EXPORT_DIR` lights up the evidence column
  (28 of 81 categories have terms; the rest need ~$50 of DataForSEO credit). Deploy of
  this pass = goods-management + gateway-admin container rebuild; no migration.
- **Decisions carried forward unchanged from the 2026-08-24 block:** proposals are
  word-boundary truncations of the CURRENT title, never generated from keyword data;
  `HtmlText.truncateAtWord`'s mid-word hard cut is detected and flagged `needsReview`,
  not changed (shared with the feed); `goods.keywords` is left alone (it is a SEARCH
  column); nothing auto-applies — bulk apply is still an administrator's explicit,
  confirmed selection.
- **History — Status 2026-08-24 (original build).** The problem, measured live: 2,571 of
  3,718 on-sale titles exceeded 60 chars (median 85, max 127). Built:
  `application/seo/KeywordResearchProvider` (port), `infrastructure/acl/seo/` (ACL:
  `CsvKeywordResearchProvider` DEFAULT / `SeoPlatformKeywordClient` buys / `No…` off,
  selected by `litemall.seo-research.source` = file|platform|none),
  `TitleProposer` + `TitleOptimisationService` (asks per CATEGORY, never per product —
  the live source bills per seed), `AdminSeoController` (`GET /titles`, `POST
  /titles/apply`, errno 660), SPA `/admin/goods/seo-titles`. Honest limitation: only 43
  of 2,571 over-length titles matched a bought keyword and 18 kept it after the cut —
  the truncation is the value, the keyword is evidence on ~2% of rows. Data:
  `~/trovemo-seo-export/category-keywords-clean.csv` (1,450 rows, 28 categories, UK
  locationCode 2826) bought 2026-08-24 for $0.64; the DataForSEO account ran out of
  credit at seed 31 of 81 (surfaces as 502 wrapping a 402).

### Worktree: `gateway-admin` — history (Wave 27 admin half + pending-approval dashboard SHIPPED)
- **Status 2026-08-18 — PENDING CJ APPROVALS ON THE DASHBOARD, clickable.**
  MERGED to master (branch commit `dd874e657`, fast-forwarded into
  `ec481fad8`'s first parent) + pushed; jest 125/125 (11 suites, +14 new),
  `tsc` clean in `app/`. User-commissioned outside any wave. Paid CJ orders
  wait in the durable placement queue behind the Wave-23 manual gate — money
  already taken, fulfilment not started — but that queue was only visible
  behind a tab, so the dashboard could show healthy revenue while orders sat
  unnoticed. Dashboard gains a "Pending CJ approval" stat tile + an
  "Awaiting your approval" card (up to 5 rows: SN, paid time, total,
  ship-to, items, Ready/Check badge), every row linking to
  `/admin/mall/order/:id` where Approve lives, plus "View all N" and the
  real-money reminder; the card hides itself when nothing is waiting.
  **NO backend work** — order already served `total` + full rows on
  `GET /srv/private/admin/order/cj-placement/pending` and the typed RTK
  query shipped in Wave 23; tile and card share ONE cache entry (same args)
  so they cannot disagree and cost one request. `OrderList`'s tab moved from
  `useState` into the URL (`?tab=pending`, unrecognised ⇒ `all`, switches use
  `replace`) so those links land on the right tab — existing links
  unaffected. ⚠ **A failed fetch renders '—', NEVER '0':** `OrderList`
  coerces the error to 0 for its tab badge (fine decorating a visible tab),
  but on a dashboard KPI a bare "0" asserts nothing is waiting exactly when
  orders are stuck behind a dead endpoint; an errno body is treated the same
  and its message shown verbatim. Logic lives in
  `Dashboard/pendingApproval.ts` so it is testable without rendering (the
  `pageFormat.ts` pattern). ⚠ **jest here needs `--config jest.conf.js`**
  (config sits at the module root, not `src/main/webapp`) — without it babel
  parses `.ts` as plain JS and even pre-existing specs die on `as never`.
  Deploy = admin container rebuild only. USER-SIDE: admin click-through.
- **Status 2026-08-18 — Wave 27 season collection, admin half BUILT.**
  Coded to the FROZEN spec `litemall-goods-management/docs/spec-season-
  collection.md` §"gateway-admin". `'season'` now round-trips end to end:
  `PageCategory` union, `PAGE_CATEGORIES` (feeds the PageEditor select),
  `normalizeCategory`, `categoryTag` ('success'), and the category filters
  on BOTH PageList and PostizPagePublish. jest 112/112 (10 suites, +2 new),
  tsc clean in `app/` (the 6 `NoInfer` errors are pre-existing, all inside
  node_modules/@reduxjs/toolkit). Branch fast-forwarded to master first.
  ⚠ **The literal ask ("'season' in the page-list category filter") was
  not the whole bug.** `normalizeCategory` folded every unrecognised value
  into 'general', and `PageEditor` seeds its category select from that
  helper — so opening ANY season page and saving it silently rewrote
  category season→general and orphaned the row from `GET /srv/page/season`.
  V63 already seeds a real season template, so the path was live, not
  theoretical. Pinned by a regression test that asserts every declared
  category round-trips. Unknown values still fall back to 'general'
  (case-sensitively — the server value is lowercase).
  Both filter option lists now render from `PAGE_CATEGORIES` instead of
  four hardcoded `<option>` literals each; that duplication is why a
  one-category change touched six sites. `postizSource.ts` is a courtesy
  note, NOT a client-side block — season pages are never refused, so the
  spec's step-5 Postiz promotion path works. No backend, no migration, no
  litemall-db touch. **MERGED to master** (`a336203a5`; verified contained
  in master 2026-08-18 — the earlier "NOT MERGED" note was stale).
- **Status 2026-08-18 — stale groupon-refusal copy DELETED** (follow-up to
  the adjacent item Wave 27 flagged; no wave, no backend, no migration).
  The claim sat at FOUR sites, TWO of them admin-visible copy, all
  repeating a refusal Wave 21 retired: `postizSource.ts` (the note + its
  file header), `PostizPagePublish.tsx:128` ("groupon-category pages are
  refused for now") and `PageEditor.tsx:817` ("groupon pages are held
  back"). Backend truth re-verified BEFORE editing:
  `PostizPublishServiceImpl.java:62-63` marks errno 765 RETIRED and `:337`
  composes bespoke groupon post copy ("Team up, unlock the group price") —
  groupon pages publish like any other. The panel was talking admins out of
  a path that works. `pageSourceNotes` now judges status ONLY (draft ⇒ the
  errno-764 warning, which is still real); category is never judged, pinned
  by a test looping `PAGE_CATEGORIES` so a new category cannot reintroduce
  a gate. Deploy = admin container rebuild only.
  ⚠ Lesson: the defect was one `if`, but the same claim had been copied
  into file headers, JSX copy and comments — grep the CLAIM, not the
  symbol. A client-side note that contradicts the server is worse than no
  note: it suppresses the action instead of failing loudly.
- **History — Wave 25 (brand/store curation, MERGED + ACCEPTED)**
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
  creds). The former "known gap" (admin /order/detail not projecting
  cj_placement_approved_time/_by) is CLOSED — verified 2026-08-18 at
  LitemallAdminOrderController.detail() :173-174, landed as `0dcd94892`
  exactly as docs/handoff-order-admin-cj.md's third ask asked; the SPA
  consumes it (OrderDetail.tsx:173-174, adminOrderCjApi.ts:282-283).
  The approval stamp now survives a reload.
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
