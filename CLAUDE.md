# CLAUDE.md — litemall Per-Worktree Tasks

## Per-worktree tasks

> Active fix branches launched via `open-fix-worktrees.sh`. Each Claude session
> opens in `../litemall-wt/<short>` on branch `fix/<short>` and is told to read
> the matching `### Worktree: <short>` block below as its sole task. Edit the
> Task / Acceptance lines to redirect a worktree.
>
> **History:** Wave 2 (coupons, groupon, aftersale, engagement) and Wave 3
> (CJ API parity: sourcing/videos/warehouse, stock-at-submit, createOrderV2
> lifecycle, tracking, admin panels) are fully merged to master; their specs
> live in git history.
>
> **Wave 4 (2026-07-13) — legacy decommission + crmeb vertical adoption.**
> Full design doc: `doc/wave4-plan-2026-07-13.pdf` (parity audit + six
> adversarially-verified vertical designs; every task below already folds in
> the review corrections). Goals: (a) close every remaining upstream-litemall
> gap so `litemall-wx-api`/`litemall-admin-api` can be DELETED (keep
> `litemall-core` — order uses its NotifyService — and `litemall-db`);
> (b) adopt the selected crmeb verticals: freight templates, physical stores +
> pickup write-off (核销), article CMS + DIY page builder (phase-1), order CSV
> export, Yly receipt printer + OnePass express as disabled-by-default DDD
> seams. Deferred by decision: brokerage/distribution, user tags/groups,
> WeChat everything, SMS flows, login kaptcha.
>
> **Dependency order:** `order`, `goods-management`, and gateway-api's
> account-self-service task are independent — start all three first. Gateway
> SPA work builds against committed handoff specs (`docs/` pattern as before).
> `promotion` is parked this wave.
>
> **Cross-cutting landmines (apply to every block):**
> - **Flyway:** up to 4 new migrations across 3 worktrees on the shared dev
>   DB. V33 is the last KNOWN version; check `flyway_schema_history`
>   immediately before first boot and claim the next free number (V32 was
>   stolen mid-flight once). Never `flyway repair`. Whichever service boots
>   first applies shared migrations.
> - **litemall-db is shared and hand-maintained:** never regenerate; hand-edit
>   entities + mapper XMLs together (the `now_money`/`OrderMapper.xml` silent
>   failures). After editing: `mvn install` litemall-db, restart EVERY
>   dependent, verify the nested `BOOT-INF/lib` copy in running exec jars;
>   concurrent `-am` builds overwrite `~/.m2`.
> - **svcsecurity is deny-by-default:** every new anonymous customer path
>   (`/srv/region/**`, `/srv/storage/fetch/**`, `/srv/article/**`,
>   `/srv/page/**`, `/srv/store/**`) must be added to the owning service's
>   `litemall.svcsecurity.public-paths`.
> - Verify live through the gateways (`:9000`/`:9001`→`:8090`, `:18080`) —
>   machine-token ~10-min TTL makes direct service curls flaky.
>
> **Decommission checklist (from main, after Wave 4 merges):** remove the
> `customer-wx-api` route (→ :8084) from gateway-api; drop `litemall-wx-api` +
> `litemall-admin-api` from the root pom; optionally delete the empty
> `litemall-wallet-service` placeholder (wallet lives in order).

### Worktree: `promotion`
- **Branch:** `fix/promotion` — **parked, no Wave-4 assignment.** Wave-2
  coupon/group-buy revive is merged (`73f12660c`). Recorded follow-up for a
  future wave: register-gift coupon (`assignForRegister` parity) once
  gateway-api's `/auth/register` lands — via a user-registered event or an
  internal grant endpoint.

### Worktree: `goods-management`
- **Branch:** `fix/goods-management` · **Scope:** `litemall-goods-management/`
  plus ADDITIVE litemall-db edits (precedent: V32/CjSourcingRequest) — own the
  install/restart consequences. Gateway/SPA wiring stays with the gateways.
- **Task A — small parity items (zero migrations; every item reuses an
  existing table):**
  1. **Region cascade:** `GET /srv/region/list?pid=` (pid=0 → 31 provinces) +
     `GET /srv/region/clist` (3-level nested tree, legacy RegionVo shape —
     mirror, don't import). In-memory lazily-built tree over
     `LitemallRegionService` (instance-scoped double-checked build; DB down at
     first hit → clean 502 payload, no poisoned cache). Add `/srv/region/**`
     to public-paths.
  2. **Customer upload + public fetch:** `POST /srv/storage/upload`
     (X-User-Id required → errno 501; 5 MB cap
     `litemall.customer-storage.max-size-bytes`; image whitelist verified by
     MAGIC BYTES, not Content-Type) + `GET /srv/storage/fetch/{key:.+}`
     (WxStorageController port incl. `../` guard; the `:.+` matters or
     extensions truncate). NOTE: no fetch route has EVER existed —
     `litemall.storage.local.address` points at a nonexistent admin path;
     re-point it to the public gateway URL (`http://localhost:8090/srv/storage/fetch`).
     Existing stored URLs are already dead — no back-compat route. Add
     `/srv/storage/fetch/**` to public-paths; upload deliberately NOT public
     (machine token + X-User-Id is the enforcement).
  3. **Topic admin CRUD:** `/srv/private/admin/topic/{list,create,read,update,
     delete,batch-delete}` — AdminBrandController shapes; litemall-db
     `LitemallTopicService` already has the full surface incl. `deleteByIds`.
  4. **Search history:** record on `GET /srv/search?q=` when UserContext has a
     user (try/catch soft-fail — a history-write failure never fails search;
     consecutive-dupe dedupe needs a NEW `latest row by add_time desc,
     deleted=0` query — `queryByUid` is distinct-unordered, unusable);
     `POST /srv/search/clearhistory`; admin `GET /srv/private/admin/history/list`.
  5. **Comment reply:** `POST /srv/private/admin/comment/reply
     {commentId, content}` sets `admin_content` once; re-reply → errno **622**
     (ORDER_REPLY_EXIST — not 620). Customer comment DTO already emits
     adminContent (verified — no read-side work).
- **Task B — Article CMS + DIY pages (content subdomain, crmeb parity).**
  One migration `V<next>__content_article_page.sql`: `litemall_article_category`
  (declared deviation — crmeb reuses its shared category tree),
  `litemall_article` (sanitized MEDIUMTEXT content, atomic
  `view_count=view_count+1` statement, status published|hidden, is_hot/
  is_banner, related goods_id), `litemall_page` (position home|custom, palette
  JSON ≤64KB, status draft|active, **single active home enforced IN SCHEMA via
  generated-column UNIQUE index** — `FOR UPDATE` on zero rows serializes
  nothing). litemall-db: 3 hand-written domain classes + slim mappers + XML
  (CjSourcingRequestMapper pattern, no Example classes). Customer:
  `/srv/article/{list,detail,categories}` (detail = anonymous GET-with-UPDATE,
  accepted, crmeb-same), `/srv/page/home` (none active → errno 642 → SPA
  legacy-home fallback), `/srv/page/{id:\d+}` (active only); both prefixes →
  public-paths. Admin: article + category CRUD (delete refused while
  referenced), page list/read/create(draft)/update(revalidates)/activate
  (@Transactional home swap → 641 conflicts)/deactivate/delete (refuses active
  home), `GET /srv/private/admin/page/palette` (machine-readable schema drives
  the structured editor). PageConfigValidator = pure domain service: palette-v1
  (unknown type / missing required config / >30 components / >64KB → errno 640
  naming the component) + rich-text sanitization via jsoup **custom Safelist**
  (add org.jsoup:jsoup to pom — not on classpath; clean-and-store semantics —
  `Jsoup.clean` never rejects). Errno 640–643 constants beside
  GOODS_NAME_EXIST=611 (631/632 are inline literals elsewhere — don't
  re-mint). Palette v1: ordered `components[]` — banner, image-row, goods-list
  (byIds|byCategory|hot|new), coupon-strip, seckill-strip, article-strip,
  rich-text — storing IDs/queries, NOT crmeb's stale product snapshots; strips
  resolve CLIENT-side (promotion down ⇒ strips hidden, never a broken home).
  No active home seeded — legacy home stays until an admin activates; seed a
  draft "Default Home". Handoffs under `docs/`: `spec-page-palette-v1.md`
  (normative: schema + PER-ENDPOINT ENVELOPES — goods `/list`={errno,data},
  promotion strips = BARE ARRAY with NO limit param → renderer slices,
  `/srv/goods/batch` = raw Map; degrade rule R1 "no resolvable data ⇒ skip,
  never an error"; hot/new modes serve LOCAL-only goods — divergence from the
  OCS-boosted legacy home documented) + `handoff-content-endpoints.md`.
- **Task C — freight tempId surface (consumes order's handoff; NO
  migration).** `litemall_goods.temp_id` already exists (V2, DEFAULT 0 =
  unbound). After order's litemall-db entity/mapper edit merges: round-trip
  `tempId` through `GoodsAllinone` create/update/detail and include it in
  `/srv/goods/goodsdetail` (order's facade cherry-picks JsonNode fields —
  non-breaking). Not an OCS field. Grey/ignore for `source='cj'` goods.
- **Acceptance:**
  - `mvn -q -o -pl litemall-goods-management -am compile` clean; boots;
    migration applies wherever the first post-install boot happens.
  - Region: `:8090/srv/region/list?pid=0` → 31 provinces; clist → tree;
    second call → no SQL. Upload: customer JWT + jpg → `{key,url}`; url
    fetches anonymously; no X-User-Id → 501 nothing stored; 6 MB / .exe →
    clean 4xx. Topic CRUD + batch-delete round-trip to customer list.
    History: logged-in search → row; `/srv/search/index` returns it;
    clearhistory empties; anonymous writes nothing; admin list pages it.
    Reply → customer list shows adminContent; second reply → 622 unchanged.
  - Articles: create → customer list; `view_count` +1 exactly per curl hit;
    hidden → 643 customer-side, still in admin list; `<script>` GONE on read.
    Pages: every palette type validates; unknown type / byIds without
    goodsIds → 640 naming component, no row; activate home → `/srv/page/home`
    serves it; second activation demotes the first (SQL shows exactly one
    active home — schema-enforced); delete active → 641; none active → 642.
  - `tempId` round-trips through the admin goods form into
    `/srv/goods/goodsdetail`.
  - Regression: `/srv/topic/*`, `/srv/issue/*`, `/srv/goods/index`, admin
    storage vertical, `/srv/search` + facets, `/srv/comment/post` unchanged.

### Worktree: `order`
- **Branch:** `fix/order` · **Scope:** `litemall-order/` plus litemall-db
  edits it owns (2 migrations + entity/mapper hand-edits). Admin controllers
  live in `interfaces/rest/` (NO `admin/` subpackage exists in order); the
  orchestrator is `application/LitemallOrderOrchestratorService` (`internal/`
  holds the schedulers).
- **Task A — freight templates (crmeb parity, REUSING existing schema).**
  V8 already created `litemall_shipping_templates{,_region,_free}` and V2
  already put `temp_id`/`weight`/`volume` on the goods tables — do NOT create
  parallel tables. One ALTER migration adds `country_code varchar(4) DEFAULT
  '*'` + `province_name varchar(63)` to region/free rows (addresses store
  free-text international names — match case-insensitive/trimmed on
  `litemall_address.province`; the Chinese `litemall_region` tree is
  address-decoupled); update litemall-db `FlywayMigrationTest` (asserts these
  table names). Hand-map `tempId` into `LitemallGoods.java` +
  `LitemallGoodsMapper.xml` (0 = unbound); new hand-written FreightTemplate*
  mappers (CjDisputeMapper pattern). `FreightCalculationService` in
  `application/internal` = single authority for quote AND submit (replace the
  flat block at `LitemallOrderServiceImpl` ~263; hoist
  `goodsFacade.batchGetGoods` out of the coupon-only branch so freight shares
  one fetch). Ladder: global `litemall_express_freight_min` free rule
  outermost → template groups (appoint 0/1/2, crmeb first/continue formula
  RoundingMode.UP, region specificity (country,province)→(country,NULL)→('*'),
  free-rule OR semantics) → flat `litemall_express_freight_value`. DOCUMENTED
  DEVIATION: crmeb ships appoint=1-unmatched FREE, we flat-charge. Combine
  mode `max` default (`freight.template.combine-mode`, crmeb-exact `sum`
  optional — sum silently raises multi-template carts);
  `freight.template.enabled:false` bypass; CJ carts skip templates entirely
  (informational CJ quote block untouched); templates Caffeine 5 min; quote
  NEVER 5xxs for freight reasons (worst case flat + `source:"SYSTEM_FLAT"`).
  `POST /srv/order/freight-quote` gains optional `addressId` (X-User-Id
  `required=false` — anonymous callers must not 400; owner-scope → errno 605)
  + `breakdown[]`. Admin `LitemallAdminFreightController`
  (`/srv/private/admin/freight`): template list/detail/create/update/delete
  (422 while a live goods references it)/set-default/selectlist (BARE
  array)/`GET /preview` dry-run. Handoffs under `docs/`: freight ADR
  (deviation + combine-mode + is_postage/seckill/bargain temp_id explicitly
  ignored), goods `tempId` binding contract, gateway-admin endpoint list,
  gateway-api quote contract.
- **Task B — pickup + stores + write-off (crmeb 核销 parity).** Migration:
  `litemall_store` (name/intro/phone/address/detailed_address/logo/lat/lng/
  business_hours/is_show/deleted) + `litemall_order` columns
  `delivery_type`(default 'express')/`store_id`/`verify_code varchar(12)`
  UNIQUE/`verify_time`/`verified_by`; hand-edit `LitemallOrder` entity +
  `LitemallOrderMapper.xml` + Example (verify-code lookup) AND new
  `LitemallStore` entity/mapper/XML. Submit: command gains
  deliveryType/storeId/pickupName/pickupMobile; pickup ⇒ freight 0, addressId
  optional, address column stores ONLY `"PICKUP: <store.name>"`
  (varchar(127)!); ALL pickup 422s (CJ-in-cart, store missing/hidden, blank
  name/mobile) in the orchestrator PRE-CHECK zone or as typed rethrown
  exceptions — NEVER inside @Transactional placeOrder (rollback-only-502
  landmine). Verify code generated inside markOrderPaid's TX (10-digit
  SecureRandom, dup-key retry; deviation from crmeb-at-create documented:
  unpaid orders never carry a redeemable code). Write-off THROUGH the
  @Transactional orchestrator (approveAftersale pattern — a bare service call
  has no TX): preview `GET /srv/private/admin/order/writeoff?verifyCode=` +
  commit `POST` → `LitemallOrderStatus.PAID.canTransitionTo` gains DELIVERED
  (real guards = handle-option gate + `WHERE order_status=201` conditional
  UPDATE; fix any test asserting the old graph), timeline hop `writeoff`,
  `verified_by = "admin:<X-User-Id>"`, `LitemallOrderDeliveredEvent(orderId,
  false)`; unknown/verified/wrong-state → three distinct 422s; double-scan
  loses cleanly on 0 rows. `shipOrder` 422s pickup orders. Store CRUD
  `/srv/private/admin/store/**` + customer `GET /srv/store/list|detail`
  (add `/srv/store/**` to public-paths). Order LIST DTO gains `deliveryType`
  (My-Orders tab relabeling). Config `litemall.order.pickup-enabled`
  kill-switch. Defaults taken: reuse PAID (no READY_FOR_PICKUP), no code
  expiry this wave (AutoConfirm gap flagged in ADR), staff RBAC deferred
  (crmeb binds wx-customer uids — different identity universe). Handoffs:
  gateway-api pickup spec (submit body, 422 strings, detail/list DTO fields,
  required route line), gateway-admin store/write-off spec, ADR.
- **Task C — CSV export + admin extras.** All queries as hand-written
  interface+XML in **litemall-db `db.dao`** (OrderMapper/StatMapper
  precedent — NO module-local mapper pattern exists; order's @MapperScan is
  litemall-db's). Export `GET /srv/private/admin/order/export`: streamed CSV
  (UTF-8 BOM, RFC-4180 quoting, Content-Disposition), same filters as admin
  list + NEW start/end on both, keyset pagination id-asc with a LEAN
  projection (not 10k aggregate hydrations), cap
  `litemall.order.export.max-rows:10000` → `# TRUNCATED` trailer (deviation
  from crmeb's xlsx-temp-file two-hop documented). Offline mark-paid
  `POST /srv/private/admin/order/{orderId}/pay`: pre-check OUTSIDE the TX;
  allowed only from CREATED; `pay_id = "OFFLINE:" + (reference|"admin:"+ts)`
  (fits the tender convention → refund-parity resolves automatically);
  timeline hop `admin_offline_pay`; **CJ orders: the in-TX createOrderV2
  replay FIRES as for a normal pay** (live-fire — ADR
  `docs/adr-offline-mark-paid.md`; SPA confirm dialog must name it; sandbox
  flag exists). `GET /srv/private/admin/order/stat/channel?start=&end=` →
  `{bySource[], byTender[]}` (tender = pay_id prefix, null → UNPAID; literal
  `stat/` segment avoids `/{orderId}` collision — cj/balance trick; closes
  the recorded `/srv/order/admin/stat` follow-up). Aftersale
  `POST batch-approve|batch-reject {ids}`: loop the orchestrator per id, ONE
  TX EACH (a bad id must not roll back siblings), response
  `{succeeded[], failed[{id,errmsg}]}`. Handoff
  `docs/handoff-admin-order-export-stat.md`.
- **Task D — printer + express seams + local tracking (disabled by default;
  NO migration).** Ports beside the existing facades in
  `infrastructure/services/acl/facades/` (module convention — NOT
  domain/ports): `ReceiptPrinterPort` (contract: `print()` always safe to
  call; `enabled()` gates only the admin surface — else the no-op
  verification path dies) + `ExpressQueryPort`. Adapters:
  `infrastructure/acl/printer/{LoggingReceiptPrinterAdapter, yly/*}` (Yly on
  OkHttp DIRECTLY — form-encoded; do NOT introduce a first RestTemplate bean;
  OAuth token Caffeine ~30d; lazy addprinter; `origin_id=orderSn` fixes
  crmeb's hardcoded "order111" → vendor-side exactly-once; reprints
  `orderSn+"-R"+epoch`, per-order cooldown noted in ADR) and
  `infrastructure/acl/express/{noop, kdniao, onepass}` (kdniao wraps
  litemall-core's ExpressService bean — NOTE config-precedence trap: core's
  profile yml `litemall.express.*` outranks order's yml → enable via env
  vars; Chinese vendor names make a ship-dialog vendor dropdown a
  prerequisite → **OnePass is the practical provider**: `v2/expr/query`
  form-encoded, `Bearer-` token, verified shape). Provider-selection
  @Configuration (promotion StatsSourceConfiguration pattern); fail fast on
  provider=yly with incomplete creds. Auto-print:
  `@TransactionalEventListener(AFTER_COMMIT)` on `LitemallOrderPaidEvent` →
  SMALL DEDICATED executor → catch-all WARN (payment never blocked; paid
  event fires at most once via the 0-row-update guard). Tracking: rename
  `CjTrackingService`→`OrderTrackingService` (2 controllers + 4 test files);
  local branch consults the port — disabled → payload +
  `note:"tracking provider disabled"`; enabled+miss → `note:"tracking
  temporarily unavailable"`; CJ branch untouched (payload byte-identical —
  global NON_NULL omits the null note); `TrackingDtoResponse` gains nullable
  `note`. New admin: `POST .../order/{id}/print-receipt` (errno 641
  disabled / 642 failed; paid orders only) + `GET .../order/fulfillment/config`
  (flags only, no secrets). Config `litemall.order.printer.*` (provider
  none|yly, auto-print, business-name, env creds, 3s/6s timeouts; English
  receipt template — DB is de-Chinesed) + `litemall.order.express.*`
  (provider none|kdniao|onepass, cache-minutes 30 incl. negatives). OnePass
  SMS explicitly out of scope (future SmsSender in litemall-core; token
  client written reusably). Handoffs: receipt-print spec for gateway-admin;
  AMEND `handoff-gateway-admin-cj-tracking.md` (note field, local-events
  behavior, strike the no-local-tracking caveat, SPA loading-state note);
  ADR.
- **Acceptance:**
  - `mvn -q -o -pl litemall-order -am compile` clean; boots on defaults
    (printer/express `none`); both migrations apply; `FlywayMigrationTest`
    green.
  - Freight: quote with a Swedish addressId + qty 3 of a bound goods →
    first + 2×continue, `source:TEMPLATE`; submit charges the IDENTICAL
    amount (quote and submit always agree); subtotal ≥ freight-min → 0
    (regression); unbound/no-default → old flat value with
    `source:SYSTEM_FLAT` in breakdown; appoint=2 free rule honored; template
    delete while referenced → 422; foreign addressId → 605; CJ cart quote
    unchanged, CJ down never 5xxs.
  - Pickup: store via :18080 appears on `GET :9000/srv/store/list`; is_show
    off hides + 422s submit; pickup submit (local goods, no addressId) → 201
    freight 0; CJ line + pickup → 422 pre-TX no row; pay → 10-digit code
    visible only to owner; preview→commit write-off → status 401 + timeline
    `writeoff` hop; replay/random/wrong-state → three distinct 422s, no state
    change; ship on pickup → 422; express/CJ/coupon/aftersale e2e unchanged.
  - Export: file starts EF BB BF, filters honored, embedded comma stays one
    field, over-cap ends `# TRUNCATED`. Offline pay: unpaid → PAID with
    `OFFLINE:` tender + timeline hop; PAID → 422; CJ order marked paid
    reaches CJ. stat/channel sums match a manual GROUP BY. Batch approve:
    good+bogus id → good refunds (wallet ledger verified), bogus in failed[].
  - Printer disabled: pay → 2xx + one rendered-receipt log line; reprint →
    641. Enabled: Yly accepts, no added pay latency, no double auto-print;
    dead endpoint → pay still 2xx, WARN, reprint 642, no hang >6s.
  - Tracking: shipped local order → owner gets events:[] + note (provider
    off) / populated events cached on second call (provider on); foreign JWT
    → 404; unshipped unchanged; CJ tracking byte-identical. Grep:
    application+domain import no adapter packages. All handoffs + ADRs
    committed under `docs/`.

### Worktree: `gateway-api`
- **Branch:** `fix/gateway-api` · **Scope:** `litemall-gateway-api/` (edge +
  customer SPA) plus the account-self-service litemall-db migration it owns
  (precedent: V15 + `db.auth.JwtService` landed with the original auth work).
  First: land/rebase any unmerged close-out commits on this branch.
- **Task A — account self-service (INDEPENDENT — start immediately; the #1
  legacy-deletion blocker: no register endpoint exists, single seed user).**
  Migration `V<next>__user_email_reset_token.sql`: `litemall_user.email
  varchar(127) NULL` + `litemall_user_reset_token` (SHA-256 `token_hash`
  UNIQUE, 30-min expiry, single-use — V15 refresh-token pattern). NO username
  index (it already exists). Hand-edit `LitemallUser` +
  `LitemallUserMapper.xml` (EVERY result map — insertSelective means a missed
  XML edit silently drops email; acceptance asserts round-trip) + new
  reset-token mapper. Extend `gatewayapi/auth` (all handlers
  `Mono.fromCallable` on boundedElastic like login; share ONE
  BCryptPasswordEncoder bean — refactor the inline `new`):
  `POST /auth/register` {username, password, nickname?, email?, mobile?} —
  policy 8–72 bytes ≠ username; dup → 704 (pre-check + DuplicateKeyException
  mapping — race closed by the existing unique index); mobile dedupe with
  `''` treated as ABSENT → 705; auto-login returning login's exact
  `{token, refreshToken, userInfo}` (the SPA thunk already expects it; make
  userInfo.nickName return the real nickname on BOTH register and login).
  `POST /auth/reset` {oldPassword, newPassword} — the guaranteed
  zero-dependency path: BCrypt matches → re-encode →
  `refreshTokens.revokeAllForUser`; wrong old → 700. Optional email flow
  behind `litemall.auth.reset-mail.enabled:false` — `ResetMailSender` port
  with no-op default (core NotifyService has a FIXED sendTo, can't mail
  customers); disabled → 701 (SPA hides the tab); enabled → always errno-0
  regardless of email existence (anti-enumeration), fire-and-forget send,
  token single-use, invalid/expired → 703. `POST /auth/profile` partial
  update + `GET /auth/me` (replaces the dead `/srv/user/index` stub;
  deleted-user token → 501 unlogin, not NPE). SPA: Register.tsx fields +
  704/705 inline (thunk exists); new ResetPassword.tsx (change tab always;
  forgot tab hidden on 701); Profile.tsx un-stubbed → `/auth/me|profile`,
  avatar file input → `/srv/storage/upload` (URL-field fallback behind
  isMissingEndpoint until goods-management lands); `/reset` route; UserCenter
  link. Handoff `docs/handoff-auth-account.md` (envelopes, errno table
  700/701/703/704/705, policy, revoke-on-change, no wallet/loyalty
  provisioning needed — verified lazy, register-coupon deferral, rate-limit
  follow-up). JWT ephemeral-keypair gotcha: e2e against one gateway process
  or configure PEM keys.
- **Task B — checkout + fulfillment SPA (consumes order handoffs):**
  1. **Route (required):** `/srv/store/**` → `lb://order-service-app` (else
     the `/srv/**` catch-all sends it to goods-management → 404).
  2. **Freight:** send selected `addressId` in the freight-quote body;
     re-quote on address change; render `breakdown[]`; confirmation shows the
     freight line.
  3. **Pickup:** delivery-type toggle; pickup swaps address picker for a
     store picker (hours/phone), collects name+mobile, freight 0; 422s inline
     like coupon errors; order detail pickup card (store info + big verify
     code + client-side QR while PAID); My-Orders relabels via
     `deliveryType`.
  4. **Tracking (net-new — NO customer tracking view exists):** order-detail
     section fetching `GET /srv/order/{id}/tracking` — carrier + events
     timeline + `note` states; loading state for the first uncached call.
- **Task C — content + engagement SPA (consumes goods-management handoffs;
  no new routes — catch-all covers `/srv/article|page|region|storage`):**
  1. **DIY home:** try `GET /srv/page/home`; errno 642 or failure → legacy
     home fallback (pixel-identical when no page is active). PageRenderer +
     registry for the 7 palette types (R1 skip-empty; hidden skipping;
     BARE-ARRAY promotion strips with renderer-side limit slice); `/page/:id`
     route.
  2. **Articles:** list (category tabs) + detail (server-sanitized HTML,
     related-goods link).
  3. **Region cascade** on AddressEdit (OPTIONAL — free-text fallback for
     non-CN; region data is China-only).
  4. **ImageUploader** shared component → ReviewForm / Feedback / aftersale
     forms; Reviews render `adminContent` as "Seller response".
- **Acceptance:**
  - Compile clean; SPA builds; migration applies.
  - Register via curl → errno 0 + working token on `/srv/order/list`;
    duplicate → 704 no second row; register WITH email → `/auth/me` returns
    it (mapper round-trip proof); new user `/srv/wallet/balance` → 0
    (loyalty-zero checked against the loyalty port directly — no gateway
    route exists); reset → old refresh token 401s, only new password logs in,
    wrong old → 700; default config reset/request → 701 and SPA hides the
    tab; forged X-User-Id stripped (curl proof). Browser e2e: register →
    land logged-in → place an order as the SECOND user. Seed user123
    login/refresh/logout regression.
  - Pickup e2e through :9000 → code + QR on order detail. Freight breakdown
    renders and re-quotes on address change. Tracking section shows events
    (provider on) and the disabled note (provider off).
  - Active DIY home renders (banner + goods + strips); promotion stopped →
    strips absent, no error UI; no active home → legacy home; articles
    visible; review-with-image round-trips; seller response renders.
  - No isMissingEndpoint guard remains for a live endpoint.

### Worktree: `gateway-admin`
- **Branch:** `fix/gateway-admin` · **Scope:** `litemall-gateway-admin/`
  (edge + admin SPA). Backend gaps become handoff notes, never edits to other
  modules. Webpack gotcha: `mvn package` re-runs webpack — delete
  `target/checksums.csv.old` if the jar ships without `static/index.html`.
- **Task A — edge verticals (served by the gateway itself; SecurityConfig
  already gates `/srv/private/admin/**`; smoke-test controller-vs-catch-all
  precedence on first boot — proven for `/notice`):**
  1. **EdgeAdminConfigController** `GET/POST /srv/private/admin/config/
     {mall,express,order}` over `litemall_system` via
     `LitemallSystemConfigService` (mall reader is misnamed `listMail`).
     POST: prefix guard AND validate posted keys against the existing rows —
     `updateConfig` silently no-ops unknown keys. No `wx` group. Runtime
     consumption by services = handoff note ONLY
     (`docs/handoff-system-config-runtime.md` — freight min/value → order
     quote; order_unpaid/unconfirm → schedulers; core SystemConfig static
     cache is per-JVM).
  2. **EdgeAdminProfileController:** `POST /srv/private/admin/profile/password`
     ({old,new}, `BCryptPasswordEncoder.matches` vs `litemall_admin.password`
     — same encoder as AdminCredentialsService; wrong old → 605) + notice
     inbox `nnotice/lsnotice/catnotice/bcatnotice/rmnotice/brmnotice` via
     `LitemallNoticeAdminService`, ALL scoped to
     `AdminEdge.adminId(authentication)` (fixes legacy's stubbed adminId=0);
     wrap MyBatis in `AdminEdge.blocking()`. Authoring stays in
     EdgeAdminNoticeController.
- **Task B — routes:** `/srv/private/admin/freight/**` and
  `/srv/private/admin/store/**` → `lb://order-service-app`, BEFORE the
  `/srv/**` catch-all, in BOTH route blocks (two `routes:` sections — keep in
  sync). Everything else rides existing routes (order/aftersale explicit;
  article/page/topic/history/comment via the goods catch-all). Machine-token
  relay fires on all (verify via :18080 curls).
- **Task C — admin SPA (against the committed handoff specs):**
  1. **FreightTemplates** module: list, editor (region rows: country +
     optional province + first/first-price + continue/continue-price; free
     rules), set-default, preview widget; `tempId` dropdown in
     `Goods/GoodsForm.tsx` from selectlist (BARE array), greyed for CJ goods.
  2. **Stores** CRUD + show toggle; **Write-off console**: scanner-friendly
     code input → preview card → confirm → toast (three distinct 422
     messages); `deliveryType=pickup` filter chip on the order list; pickup/
     verify block on admin order detail.
  3. **Article/** (list + editor with category select, status toggle, storage
     upload; category tab) and **Page/** (list with position/status badges;
     STRUCTURED editor generated from `GET /palette` — add/remove/reorder/
     hide per-component forms, NOT drag-drop; Activate confirm explaining the
     home swap; draft preview rendered client-side in the admin SPA). Route
     both in admin-routes.tsx — no NotAvailable fallthrough.
  4. **Order surfaces:** Export CSV button (JWT fetch → blob, current
     filters); "Mark paid (offline)" on unpaid order detail with a confirm
     dialog that NAMES the CJ replay; Dashboard channel/tender card from
     `stat/channel`; aftersale batch checkboxes (render the partial-success
     envelope). **Tracking panel:** widen the Wave-3 CJ panel (gated
     `source==='cj'`) to any order with `shipSn`; render `note` states.
     **Print receipt** button per `GET .../fulfillment/config`; toast
     641/642.
  5. **Bundle pages:** Topic pages (clone Brand), HistoryList (clone
     FootprintList), Comment reply dialog (show existing adminContent),
     Sys/Config{Mall,Express,Order} forms, Profile (password change + notice
     inbox + header bell from nnotice).
- **Acceptance:**
  - Compile clean; admin SPA prod build green (checksum gotcha).
  - Live through :18080: config GET/POST round-trips (`litemall_express_*`
    visible; wx-key or misspelled key → rejected, row untouched); password
    change (wrong old → 605; new works, old dies); nnotice drops after
    catnotice, second admin unaffected.
  - Freight template created → appears in goods-form dropdown; preview
    returns a computed price; a template edit changes a live quote.
  - Write-off console completes a pickup order; order list filters pickup;
    a shipped order's tracking panel shows events (or the disabled note);
    print button behaves per config (641/642 toasts).
  - Article/page created → renders customer-side; structured-editor edits
    reflect on the customer home after reload.
  - Export downloads and opens with correct columns; offline mark-paid works
    with the CJ-naming confirm; dashboard card sums match; batch aftersale
    shows partial success.
  - No admin view routes to NotAvailable for a live backend.
