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
> and Wave 5 (affiliate program: brokerage engine V39, invite capture,
> affiliate portal + admin-edge auth split) are fully merged to master;
> their specs live in git history.
>
> **Wave 6 (2026-07-16) — social promotion + tracking & customer/scheduled
> email.** Approved design:
> `doc/social-email-marketing-plan-2026-07-16.pdf` (audit + locked
> decisions; approved by the user 2026-07-16 — implement directly, no
> per-worktree re-approval unless deviating). Locked: posting platforms =
> Meta (FB Page + Instagram) AND TikTok; tracking = deploy Matomo + SPA
> tracker; posting = manual admin composer + OPT-IN auto-post on deal
> activation; email = in-house transactional (CustomerMailSender + outbox
> with `send_at`) + Mautic for scheduled/drip campaigns. The promotion
> service's Phase-3 ACLs (`acl/matomo`, `acl/mautic`, contract doc
> `litemall-promotion-service/docs/phase3-marketing-stack-integration.md`)
> are already coded/tested/disabled — this wave deploys their backends and
> turns them on; do NOT rewrite them.
> **USER-SIDE PREREQUISITES (may land mid-wave): Meta business-app tokens,
> TikTok Content-Posting approval, real SMTP creds. Everything must build,
> boot, and be verifiable WITHOUT them** (adapters disabled by default,
> failures → failed ledger rows, dev email via MailHog).
> **Shared contracts:** UTM = `utm_source=facebook|instagram|tiktok`,
> `utm_medium=social`, `utm_campaign=<slug>` (link-builder shared with the
> affiliate `?invite=` links). Mail config namespace =
> `litemall.customer-mail.*` (enabled:false, from, host/port/user/pass via
> ENV — core config-precedence trap: core profile yml outranks service yml).
> Template keys: `order-confirmation`, `shipped`, `refund-approved`,
> `pickup-code`, `password-reset`.
>
> **Dependency order:** `promotion` (marketing infra + social vertical),
> `order` (transactional mail), and `gateway-api` (tracker + reset adapter)
> are independent — start all three together. `gateway-admin` panels build
> against promotion's + order's committed `docs/` handoffs. `goods-management`
> has a SEPARATE pending assignment (CJ-deals fix) — see its block.
>
> **Cross-cutting landmines (apply to every block):**
> - **Flyway:** V39 used (brokerage); **V40 is EARMARKED for
>   goods-management's CJ-deals SKU-charge fix — do NOT take it.** Wave-6
>   migrations (promotion social_post, order mail_outbox) claim V41+ after
>   checking `flyway_schema_history` immediately before first boot.
>   `out-of-order: true` is permanent. Never `flyway repair`.
> - **litemall-db is shared and hand-maintained:** never regenerate; hand-edit
>   entities + mapper XMLs together (the `now_money` silent failures). After
>   editing: `mvn install` litemall-db, restart EVERY dependent, verify the
>   nested `BOOT-INF/lib` copy in running exec jars; concurrent `-am` builds
>   overwrite `~/.m2`.
> - **litemall-core is shared too** (order edits it this wave): same
>   install/restart-all-dependents discipline; enable core-read config via
>   ENV VARS (the kdniao precedence lesson).
> - **svcsecurity is deny-by-default:** no new anonymous customer paths
>   expected this wave; admin/social + admin/mail prefixes ride the
>   machine-token relay like the rest of `/srv/private/**`.
> - Verify live through the gateways (`:9000`/`:9001`→`:8090`, `:18080`) —
>   machine-token ~10-min TTL makes direct service curls flaky.

### Worktree: `goods-management`
- **Branch:** `fix/goods-management` — no Wave-6 assignment, but carries the
  PENDING approved CJ-deals work (design `doc/cj-deals-strategy-2026-07-16.pdf`,
  committed `304c925b8`): the flash-deal checkout SKU-charge fix (**V40
  earmarked**), CJ flash deals (floor at cost, promote-path guards), organic
  suggestSellPrice anchors. That assignment predates Wave 6 and proceeds
  independently of it.

### Worktree: `promotion`
- **Branch:** `fix/promotion` — FIRST: `git merge master` (branch tip is
  Wave-2 vintage). · **Scope:** `litemall-promotion-service/` + the
  `litemall_social_post` litemall-db additions it owns +
  `docker-compose/docker-compose.marketing.yml` (it OWNS that file — no one
  else edits it this wave). Un-parked for Wave 6. Existing module
  conventions: ports in `application/ports`, ACLs in `infrastructure/acl/**`
  (domain never imports a client), StatsSourceConfiguration @Primary
  pattern, core JacksonConfig voids spring.jackson yaml (ISO T bodies),
  Kafka down = 60s blocking mutations, admin endpoints need X-User-Id AND
  X-User-Roles:ROLE_ADMIN.
- **Task A — marketing infra (land FIRST, small):**
  `docker-compose/docker-compose.marketing.yml` with Matomo + its MariaDB,
  Mautic + its DB, and MailHog (dev SMTP, 1025/8025) — env-configured
  creds/ports, dedicated volumes, OPT-IN (not started by `dc-local.sh`;
  document in `README-DOCKER-COMPOSE.md`). Matomo site "litemall
  storefront"; emit the tracker URL + site id + auth token as env for the
  other worktrees (record actual values in the handoff).
- **Task B — social posting vertical:**
  1. **Migration** `V<next>__social_post.sql` (V41+ — V40 is earmarked, check
     history first): `litemall_social_post` (goods_id, platform enum
     `meta_fb|meta_ig|tiktok`, caption text, media_url, link_url, status
     `draft|posted|failed`, external_post_id varchar, error varchar(511),
     posted_by varchar — admin id or `'auto'`, add/update_time, deleted).
     litemall-db mapper trio (CjSourcingRequestMapper pattern): insert,
     paged list w/ status+platform filters, guarded status transitions.
  2. **`SocialPublishPort`** (`application/ports`) + adapters:
     `infrastructure/acl/meta/` — Graph API, ONE app: FB Page photo/feed
     post + IG business media-container→publish (page token, ig-user-id via
     env; long-lived-token renewal documented in the ADR);
     `infrastructure/acl/tiktok/` — Content Posting API, VIDEO required
     (source = the goods' video URL; Wave-3 `/srv/goods/videos` surfaces
     which goods have one). Both disabled by default
     (`litemall.promotion.social.{meta,tiktok}.enabled:false`), fail-soft:
     any transport/API error → row status `failed` + error message, log
     WARN, NEVER a 5xx (Mautic adapter conventions exactly). No new
     RestTemplate bean if one exists — follow the module's Feign/OkHttp
     precedent.
  3. **Admin endpoints** `/srv/private/admin/social/`:
     `GET compose-preview?goodsId=` (templated caption: name, price, deal
     price when active, share URL from the shared UTM link-builder +
     candidate images/video + per-platform availability incl. TikTok
     video-gate + enabled flags), `POST post` {goodsId, caption, mediaUrl,
     platforms[]} → one ledger row per platform (posts what it can,
     per-platform result in the response envelope), `GET list?page=`,
     `POST {id}/retry` (failed rows only — guarded).
  4. **Auto-post on deals (OPT-IN):**
     `litemall.promotion.social.auto-post-deals:false`. v1 = @Scheduled poll
     (1 min) detecting flash-deal activations in the deals tables (NO
     cross-module edit; state-change detection must be restart-safe — derive
     from deal state + an existing `litemall_social_post` row, not from
     in-memory memory), then templated post to all ENABLED platforms, rows
     `posted_by='auto'`, dedupe = at most one auto row per (goods, platform,
     deal activation).
  5. **Mautic enablement (Wave-6 Phase D):** contact upsert gains EMAIL +
     nickname (today it sends only the litemall user id — small
     MauticDeliveryAdapter/DTO edit; skip contacts with NULL email,
     documented); config notes for enabling `mautic.enabled` + pointing
     `litemall.promotion.stats.source` to composite once Matomo has data
     (both remain default-off in committed yml).
  6. **Handoffs under `docs/`:** `handoff-social-composer.md` (envelopes,
     platform enum, error strings, video-gate rule — for gateway-admin),
     `handoff-matomo-tracker.md` (tracker URL/site-id env contract, UTM
     convention — for gateway-api), `adr-social-publishing.md` (token
     renewal, fail-soft, auto-post dedupe, TikTok video constraint).
- **Acceptance:**
  - `mvn -q -o -pl litemall-promotion-service -am compile` clean; boots with
    everything disabled (zero behavior change); migration applies.
  - Marketing compose stack starts; Matomo UI reachable; MailHog reachable;
    none started by default local bring-up.
  - compose-preview returns caption+media+availability (TikTok greyed for a
    video-less goods); post with adapters DISABLED → rows `failed` with a
    clear "disabled" error, response envelope says so, no 5xx; with dummy
    creds → failed + API error captured; retry re-fires only failed rows.
    (Real-token posting verified live IF creds arrive mid-wave; otherwise
    the ADR records the manual verification steps.)
  - Auto-post: enable flag + activate a flash deal → exactly ONE auto row
    per enabled platform; restart mid-window → no duplicate; deactivating/
    re-activating a deal → new row (documented semantics).
  - Mautic (container up, enabled): campaign evaluation → segment visible in
    Mautic, contacts carry real emails; NULL-email users skipped without
    failing the batch. Kafka/campaign/coupon/groupon regression untouched.

### Worktree: `order`
- **Branch:** `fix/order` — FIRST: `git merge master`. · **Scope:**
  `litemall-order/` + the `litemall_mail_outbox` migration + mapper it owns
  + **litemall-core `mail` package** (it owns this shared-module edit — own
  the install/restart-all consequences; NotifyService/notify package stays
  untouched). Schedulers/listeners in `application/internal` as before.
- **Task — transactional customer email with scheduled outbox (Wave-6
  Phase C):**
  1. **Migration** `V<next>__mail_outbox.sql` (V41+ — V40 earmarked):
     `litemall_mail_outbox` (recipient varchar(127), subject varchar(255),
     body MEDIUMTEXT, template_key varchar(63), status
     `pending|sent|failed`, attempts int default 0, send_at datetime,
     last_error varchar(511), add/update_time, deleted; index on
     (status, send_at)). Hand-written litemall-db mapper trio: insert,
     `findSendable(now, limit)` (pending AND send_at<=now, id asc), guarded
     markSent/markFailed/incrementAttempts, paged admin list.
  2. **litemall-core `core/mail` package:** `CustomerMailSender` interface +
     `SmtpCustomerMailSender` (JavaMailSender bean built from
     `litemall.customer-mail.*`; enabled:false default → a no-op logging
     impl is the @ConditionalOnMissing default so every core dependent
     still boots unchanged) + `MailTemplates` (English, plain-text v1, the
     5 shared template keys; DB is de-Chinesed).
  3. **Enqueue listeners** (`application/internal`, AFTER_COMMIT + small
     executor + catch-all WARN — the auto-print/brokerage pattern; NEVER
     block payment): paid → order-confirmation; shipped → shipped(+shipSn/
     channel); aftersale approved → refund-approved; write-off/pickup pay →
     pickup-code. Recipient = buyer's email; NULL/blank → silent skip (no
     row). Rows default `send_at=now` — future `send_at` = the scheduled-
     send seam (exposed for reuse, e.g. third-party mail).
  4. **Outbox sweep** `@Scheduled` (CJ-sweep pattern): `findSendable` batch
     (limit ~50) → per row try send → markSent / incrementAttempts (cap 5 →
     failed + last_error). One row's failure never aborts the batch.
  5. **Admin** `/srv/private/admin/mail/{list,resend}`: list pages the
     outbox w/ status filter; resend = failed→pending reset (guarded),
     attempts zeroed.
  6. **Handoff** `docs/handoff-mail-outbox-admin.md` (envelopes for
     gateway-admin; config-key table; note that gateway-api's
     ResetMailSender uses the same `litemall.customer-mail.*` keys but its
     OWN JavaMailSender — no code dependency on core needed edge-side).
- **Acceptance:**
  - `mvn -q -o -pl litemall-order -am compile` clean; boots on defaults
    (mail disabled — byte-identical behavior, listeners skip); migration
    applies; FlywayMigrationTest green.
  - With MailHog (`docker run -p 1025:1025 -p 8025:8025 mailhog/mailhog` or
    promotion's marketing compose) + enabled via env: pay as an
    email-bearing user → confirmation visible in MailHog within one sweep;
    email-less user → no row, no error; ship/refund/pickup each produce
    their template. SMTP down → pay still 2xx, row cycles to failed after
    5 attempts with last_error; resend after recovery delivers. A row
    hand-inserted with future send_at goes out ONLY after that time
    (scheduled-send proof).
  - Wave-5 brokerage/wallet/pay/aftersale e2e regression unchanged; grep —
    application/domain import no adapter/mail-impl packages.

### Worktree: `gateway-api`
- **Branch:** `fix/gateway-api` — FIRST: `git merge master`. · **Scope:**
  `litemall-gateway-api/` only (edge + customer SPA). NO migration.
- **Task A — Matomo tracker + UTM (consumes promotion's
  `docs/handoff-matomo-tracker.md`; env contract is stable enough to start):**
  1. SPA: Matomo tracker bootstrap driven by build/runtime config (tracker
     URL + site id; ABSENT ⇒ no script injected, byte-identical behavior);
     track SPA route changes as page views; product detail fires a page view
     carrying the goods id as a custom dimension; honor Do-Not-Track; no
     tracking of auth pages' form contents (page URL only).
  2. Shared link-builder util (SPA-side) implementing the UTM convention
     from the Wave-6 header; ensure `utm_*` params (like `?invite=`) survive
     landing → routing without breaking any route matching (regression:
     `?invite=` stash still works when both are present).
- **Task B — real reset mail:** implement `ResetMailSender` over spring
  `JavaMailSender` using the SAME `litemall.customer-mail.*` env keys (add
  spring-boot-starter-mail to this module if absent); keep the no-op logging
  impl as the disabled default (`litemall.auth.reset-mail.enabled:false`
  still the outer gate; anti-enumeration semantics unchanged).
- **Acceptance:** compile + SPA build. Tracker configured → Matomo shows a
  storefront visit, a `utm_source=facebook` campaign hit, and a
  product-detail view with the goods dimension; unconfigured → no matomo
  script tag in the served HTML/bundle behavior. Reset flow with MailHog +
  both flags enabled → mail arrives, token round-trip works; disabled →
  701 + SPA hides the tab (regression). `?invite=` + `utm_*` combined on a
  product URL → both captured, routing normal.

### Worktree: `gateway-admin`
- **Branch:** `fix/gateway-admin` — FIRST: `git merge master`. · **Scope:**
  `litemall-gateway-admin/` (edge + admin SPA). Backend gaps = handoff
  notes, never cross-module edits. Webpack gotcha: delete
  `target/checksums.csv.old` if the jar ships without `static/index.html`.
- **Task A — routes:** `/srv/private/admin/social/**` → the promotion
  service (match the target the existing coupon/groupon admin routes use),
  BEFORE the `/srv/**` catch-all, in BOTH `routes:` blocks; confirm
  `/srv/private/admin/mail/**` rides the existing explicit order admin
  route (add if prefixes are enumerated). Machine-token relay on both
  (verify via :18080 curls).
- **Task B — SPA (against promotion's `handoff-social-composer.md` +
  order's `handoff-mail-outbox-admin.md`):**
  1. **Promote composer:** "Promote" button on the goods list/detail →
     dialog from compose-preview (caption editor prefilled, media picker,
     platform checkboxes — TikTok disabled with a tooltip when the goods has
     no video or the platform is disabled, preview pane, post → per-platform
     result toasts from the response envelope).
  2. **Social posts page:** paged list (platform + status chips, error
     tooltip on failed, posted_by incl. `auto` badge), Retry button on
     failed rows, external link-out when external_post_id exists. Route in
     admin-routes.tsx — no NotAvailable fallthrough.
  3. **Mail outbox page:** paged list (status filter, template_key,
     recipient, attempts, last_error tooltip, send_at), Resend on failed
     rows.
  4. **Campaign link-out:** on the existing promotion-campaign admin page,
     a "Segment in Mautic" link-out (env-configured Mautic base URL; hidden
     when unset).
- **Acceptance:**
  - Compile clean; admin SPA prod build green (checksum gotcha).
  - Through :18080: composer round-trips against a goods with images (post
    with adapters disabled → failed rows + honest toasts — the UI contract
    holds without platform creds); TikTok gate visible on a video-less
    goods; retry works; posts list paginates and filters.
  - Mail outbox lists rows produced by order's e2e; resend flips failed →
    pending and the sweep delivers (MailHog).
  - admin123/mall123 + affiliate portal + Wave-4/5 admin surfaces regress
    clean; no admin view routes to NotAvailable for a live backend.
