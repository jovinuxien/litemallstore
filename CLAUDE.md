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
> **USER-SIDE PREREQUISITES:** Stripe TEST keys are LIVE in prod (card pay
> verified e2e); `CJ_CATALOG_*` (goods-management catalog/enrichment creds)
> is LIVE; order-side `CJ_API_KEY` is still EMPTY — live CJ order placement
> stays blocked by design. Everything must degrade honestly: typed errors,
> retryable states (an order paid today must be placeable at CJ tomorrow
> when the key arrives), never a fake success, never a 5xx.
>
> **Cross-cutting landmines (apply to every block):**
> - **Flyway:** V44 is the last used (Wave-8 goods-management, comment
>   source/external_id). **V40 remains EARMARKED** for goods-management's
>   parked CJ-deals SKU-charge fix — do NOT take it. Wave-9 migrations (none
>   are expected — this wave is contract + SPA work) claim **V45+** after
>   checking `flyway_schema_history` immediately before first boot.
>   `out-of-order: true` is permanent. Never `flyway repair`.
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

### Worktree: `order`
- **Branch:** `fix/order` — FIRST: `git merge master`. · **Scope:**
  `litemall-order/` + `litemall-core` `mail` pkg (shared-core discipline:
  `mvn install` core, restart every dependent) + compose env passthrough.
  **NO migration expected** — V41 already has the outbox with `send_at`;
  if one becomes truly necessary, claim V45+ after checking
  `flyway_schema_history` (V40 stays earmarked).
- **Task — Wave 10: rich order-confirmation email once an order is paid.**
  Backend anchors (verified 2026-07-26): paid choke point =
  `LitemallOrderServiceImpl.markOrderPaid` (:625) → aggregate
  `markAsPaid()` adds `LitemallOrderPaidEvent`; its three callers in
  `LitemallOrderOrchestratorService` (customer pay card+wallet :380,
  Stripe webhook :482, admin offline pay :592) mean event coverage is
  ALREADY complete — do not add new paid paths. Mail side:
  `CustomerMailEnqueueListener` (AFTER_COMMIT + 1-thread executor;
  `buyerEmail()` :127 skips blank), `MailTemplates.orderConfirmation`
  (:27, core), `MailOutboxSweepScheduler`, admin resend at
  `/srv/private/admin/mail`. Proven precedent for loading full order data
  after commit: `OrderPaidReceiptPrintListener` (:91-140, uses
  `orderGoodsRepository.findByOId`).
  1. **Enrich the confirmation body** (`MailTemplates.orderConfirmation` +
     listener): full plain-text order summary — each line item (goodsName,
     specifications, quantity, price; via `LitemallOrderGoodsRepository`),
     money breakdown (goodsPrice, freightPrice, couponPrice, tax,
     actualPrice), delivery block (consignee, mobile, address string — or
     pickup store; the separate pickup-code mail stays), orderSn, payTime.
     Keep plain text (`SimpleMailMessage`; V41 body column is text-only)
     and keep render-at-enqueue (row stores the final subject/body). A DB
     read failure while building the mail must never break the payment
     path — degrade to the current minimal body, never throw.
  2. **Light the pipeline up.** Dev: enable via env
     (`LITEMALL_CUSTOMERMAIL_ENABLED=true`, host/port → MailHog :1025) and
     prove delivery in the MailHog UI (:8025). Prod: add
     `LITEMALL_CUSTOMERMAIL_*` passthrough to the order service in
     `docker-compose.prod.yml` with EMPTY defaults — real SMTP creds are a
     USER-SIDE PREREQUISITE (none exist yet); enabling later must be env +
     container recreate, NO rebuild. Honesty rule: while disabled/blank,
     behaviour stays exactly as today (silent no-op, no fake sends, no
     5xx). Keep the compose warning accurate: the sweeper is
     single-instance-only (duplicate emails if order is scaled out).
  3. **Tests** — none exist today for the listener/sweeper. Add: paid
     event ⇒ outbox row whose body contains items/amounts/address;
     blank-email user ⇒ no row, no error; goods-load failure ⇒ minimal
     body still enqueued; sweeper marks sent/failed. Mind the test-run
     gotcha: read the "Tests run:" count, root pom + `@Nested` both
     produce green no-op builds.
  4. **Out of scope:** gateway-api's password-reset mail (separate
     worktree; its `user`/`pass` key names diverge from core's
     `username`/`password` — leave it, but don't make it worse). No new
     anonymous service paths. Do not touch the production VPS.
- **Acceptance:** dev e2e through `:9000` — register a user WITH an email,
  place an order, pay with a Stripe test card ⇒ confirmation mail lands in
  MailHog showing line items + amounts + delivery + orderSn; wallet-paid
  order covered too (e2e or test); user with no email ⇒ order pays fine,
  no outbox row, no error logged as failure; `litemall.customer-mail.enabled`
  unset ⇒ behaviour identical to master; outbox row `template_key
  order-confirmation` stores the final body; module tests green with real
  "Tests run" counts; no migration (or V45+ justified in the plan);
  checkout regression-green through `:9000`.

### Worktree: `goods-management`
- **Branch:** `fix/goods-management` — FIRST: `git merge master`. ·
  **Scope:** `litemall-goods-management/` (+ `litemall-db` ONLY if a
  schema change is truly justified — then claim **V45+** after checking
  `flyway_schema_history`; V40 stays earmarked; note Wave-10 `order` runs
  in parallel and also expects no migration — first to boot claims V45).
- **Task — Wave 11 (backend): derive homepage banners from CJ imagery,
  matched to the live category mix.** Serve the CONTRACT in the Wave-11
  preamble note.
  1. **Derivation:** for the top-N (config, default ~5) CJ L1 roots by
     on-sale goods count (`CatalogGoodsCountService.countsByRoot()`
     already computes this), pick a hero image from that subtree's
     on-sale CJ goods (newest or best-selling; URL MUST be on
     `cf.`/`oss-cf.cjdropshipping.com` so the `/_cdn` rewrite covers it —
     skip aliyuncs/yanxuan-hosted pics). Build banner entries: `name` =
     L1 name, `url` = hero pic, `link` = `/category/<L1 id>`, `content` =
     short subtitle. NO live CJ API calls on the request path — derive
     from local `litemall_goods` data like `CategoryImageBackfillService`
     does, with a cache/refresh so `/srv/goods/index` latency stays flat.
  2. **Serving:** `/srv/goods/index` `banner` key returns the derived
     banners; manual admin rows (`litemall_ad` position=1, enabled)
     still appear and outrank generated ones. The 3 yanxuan seed rows
     must STOP being served (they are mixed-content broken) — data-level
     deactivation or read-time filtering of non-HTTPS/foreign hosts;
     your plan decides, but the admin ad panel must keep working for
     rows admins create.
  3. **Honesty:** only banner categories with a meaningful number of
     on-sale goods; every `link` must land on a non-empty category page.
  4. **Tests** for derivation (top-N selection, host filter, admin-row
     precedence, empty-catalog fallback). Mind the surefire/@Nested
     "Tests run:" gotcha.
- **Acceptance:** through `:9000`, `/srv/goods/index` returns ≥3 banners,
  every `url` rewritten to `/_cdn/...` at the edge, every `link` a
  `/category/<id>` with on-sale goods, zero `yanxuan.nosdn.127.net` in
  the payload; admin ad CRUD still functional (create a manual row ⇒ it
  appears first); index latency comparable to master (cached derivation);
  module tests green with real "Tests run" counts; no migration (or V45+
  justified in the plan and coordinated).

### Worktree: `gateway-api`
- **Branch:** `fix/gateway-api` — FIRST: `git merge master`. · **Scope:**
  `litemall-gateway-api/` customer SPA only. NO migration, no new
  anonymous service paths, no banner logic at the edge — the banner list
  arrives ready-made per the Wave-11 CONTRACT (code to the contract, not
  to the goods-management branch).
- **Task — Wave 11 (SPA): render + route the CJ category banners.**
  1. **Routing:** in `Home.tsx` (:186-205) treat `banner.link` values
     starting with `/` as internal SPA routes (react-router navigate, no
     full page reload); keep plain `<a>` behaviour for absolute external
     URLs; no more dead `href="#"`.
  2. **Presentation:** the hero images are 1:1 product catalog crops, not
     designed banner art — make them read as banners: fixed hero height
     with `object-fit: cover` (or equivalent), a gradient/scrim overlay
     so the caption is legible, caption = `name` + `content` subtitle +
     a "Shop <category>" CTA. Keep the react-bootstrap `<Carousel>` and
     the existing `storefront-home.scss` conventions; keep a graceful
     empty-state (no broken 340px void if the list is ever empty).
  3. **Regression:** home, category landing (banner click lands on
     `/category/<id>` with facets), anonymous browse/search/PDP, login,
     cart, checkout — green through `:9000`.
- **Acceptance:** SPA build clean; banners render on `/` through `:9000`
  with images served via `/_cdn` (no mixed-content or console errors);
  clicking a banner performs a client-side route to its category landing;
  captions legible on arbitrary product imagery; empty banner list
  degrades cleanly; regression list above green.

### Worktree: `gateway-api` — history (Wave 9.1, SHIPPED)
- **Task — Wave 9.1: storefront trust surfaces (social links, help center,
  customer-service FAQ).** (Merged + deployed 2026-07-25, `3989e2053`.)

### Worktree: `gateway-admin`
- **No Wave-9 assignment.** Wave-8 admin branding is merged and deployed
  (`72446568f`). Do not start work here without a new instruction.
  (Candidate for a later wave: a curated-keywords management panel feeding
  `/srv/search/helper` and hot-keyword curation — NOT commissioned yet.)

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
- **No Wave-8 assignment.** Do not start work here without a new instruction.
