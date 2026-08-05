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

### Worktree: `order` — Wave 18 mini-task
- **Task — Wave 18 (mini): scope facts on coupon redeem.** Extend the
  promotion Feign redeem call (`PromotionServiceFeignClient` +
  `CouponRedemption` facade) to pass the cart's `goodsIds` +
  `categoryIds` (both already resolved at the call site,
  `LitemallOrderServiceImpl` coupon block) so promotion re-checks
  goods scope at consumption (Wave-18 CONTRACT item 6 — the fields
  are OPTIONAL, so this deploys safely in either order relative to
  promotion). One DTO/facade change + `LitemallOrderPlaceCouponPathTest`
  extension; nothing else in the money path moves.
- **History — Wave 10: rich order-confirmation email once an order is
  paid.** (Merged + deployed 2026-07-26, `77c55e027`; activation done —
  Brevo SMTP live since 2026-08-02. Spec in git history.)

### Worktree: `goods-management` — Wave 18 margin-basis DONE
- **Task — Wave 18: coupon margin-guard basis endpoint.** DONE on
  `fix/goods-management` (2026-08-06): `POST /srv/private/admin/insight/
  margin-basis` (`MarginBasisService` + `InsightMapper.selectMarginBasis`,
  subtree expansion at query time, whole-catalog when scope empty; unit
  tests 4/4, insight regression 17/17). FROZEN consumer spec:
  `litemall-goods-management/docs/handoff-coupon-margin-basis.md`. Live
  dev verification through the gateways happens at merge (run-from-MAIN
  rule).
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
  `c5fdae86f`; live feed validated). No new assignment — do not start
  work here without a new instruction.

### Worktree: `gateway-api` — behavioral Phase 0 SHIPPED; NEXT: Wave 18 coupon storefront
- **Task — Wave 18 (storefront): coupon center + register-gift +
  percent rendering.** Code to the Wave-18 CONTRACTS above. Scope:
  `litemall-gateway-api/` SPA only; NO selectlist change (promotion
  derives cart categories server-side); NO migration.
  1. Public **`/coupons` coupon-center page**: claimable coupons from
     `GET /srv/coupon/list` (already public + paged), claim via
     `/srv/coupon/receive`, claimed state + "view my coupons" link;
     scoped coupons deep-link "shop eligible items" → `/category/<id>`
     (category scope) or `/search` (all); entry links from the header
     account menu + the existing PDP `CouponStrip` "see all".
  2. **Register-gift trigger**: after successful registration (and
     Google-signup provisioning), fire-and-forget
     `POST /srv/promotion/coupon/register-gifts` through the edge —
     fail-silent, never blocks signup.
  3. **Percent rendering**: `ICoupon` gains `discountType`/
     `discountCap`; CouponStrip / user Coupons / checkout picker /
     order breakdown render "N% off (up to $C)" vs "$D off" —
     checkout keeps using the server-computed effective discount.
- **Acceptance (dev, :9000/:8090):** fresh signup holds the
  register-gift coupon; `/coupons` lists + claims and deep-links a
  category-scoped coupon to its landing; a percent coupon shows the
  right label everywhere and the paid total matches the server
  discount; existing checkout/coupon e2e regression-green (headless:
  native-setter fills + in-page DOM clicks).
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

### Worktree: `gateway-admin`
- **Branch:** `fix/gateway-admin` — FIRST: `git merge master`. ·
  **Scope:** `litemall-gateway-admin/` only. NO migration.
- **Task — Wave 18 (admin UI): coupon scoping + percent + guard
  surfacing.** Code to the Wave-18 CONTRACTS above; do NOT read the
  promotion branch.
  1. `CouponForm`: scope selector — All / **Category** (L1 roots
     picker fed by `/srv/private/admin/insight/categories`, show
     avgMarginPct per root) / **Products** (picker reusing the
     insight goods list `/srv/private/admin/insight/goods/list
     ?categoryId=` with picture/price/margin, multi-select cap 500);
     sends `goodsType`/`goodsValue` (kill the `:21` hardcode). Plus
     discount-type radio (flat / percent), rate + optional cap
     fields with client-side 1–90 bounds.
  2. Margin-guard rejections surfaced VERBATIM (they contain the
     computed maximum discount/rate); `uncostedCount` warning shown
     non-blocking on success.
  3. `CouponList`: type (flat/percent) + scope (All/Category/
     Products) columns.
- **Acceptance:** create an L1-scoped 10% coupon → saved; an
  over-generous one → inline guard message with the stated max; a
  product-list coupon picks via the insight picker; existing coupon
  CRUD + issued-users panels regression-green; webapp tests green
  with real counts.
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

### Worktree: `promotion`
- **Branch:** `fix/promotion` — FIRST: `git merge master` (Wave 17 is
  merged; the branch must equal master before starting). · **Scope:**
  `litemall-promotion-service/` + the single Wave-18 **V51** migration
  in litemall-db (shared-module discipline: hand-edit entity + mapper
  XML together, `mvn install`, restart every dependent; check
  `flyway_schema_history` immediately before first boot). Do NOT
  touch Postiz, `SocialDealAutoPoster`, the Mautic listener, or the
  campaign endpoints.
- **Task — Wave 18 (backend): scoped + percent + profit-guarded
  coupons.** Code to the Wave-18 CONTRACTS above.
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
