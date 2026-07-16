# CLAUDE.md — litemall Per-Worktree Tasks

## Per-worktree tasks

> Active fix branches launched via `open-fix-worktrees.sh`. Each Claude session
> opens in `../litemall-wt/<short>` on branch `fix/<short>` and is told to read
> the matching `### Worktree: <short>` block below as its sole task. Edit the
> Task / Acceptance lines to redirect a worktree.
>
> **History:** Wave 2 (coupons, groupon, aftersale, engagement), Wave 3
> (CJ API parity: sourcing/videos/warehouse, createOrderV2 lifecycle,
> tracking, admin panels), Wave 4 (legacy wx-api/admin-api decommission +
> crmeb verticals: freight templates, stores + pickup write-off, article CMS
> + DIY pages, CSV export, printer/express seams, account self-service), and
> the goods deals wave (flash-deal lifecycle + related items, 2026-07-16) are
> fully merged to master; their specs live in git history.
>
> **Wave 5 (2026-07-16) — affiliate program.** Approved design:
> `doc/affiliate-program-feasibility-plan-2026-07-16.pdf` (feasibility audit
> + locked decisions; plan approved by the user 2026-07-16 — implement
> directly, no per-worktree re-approval needed unless deviating). Affiliates
> are `litemall_user` rows with `is_promoter=1` — NEVER `litemall_admin`
> rows. Locked decisions: commission FROZEN at pay → invalidated on approved
> aftersale → auto-unfrozen into `brokerage_price` after configurable
> freeze-days; promoters are ADMIN-GRANTED only; flat global single-level
> rate; attribution = invite code at REGISTRATION (permanent `spread_uid`
> binding). Explicitly v2 — do NOT build: cookie/click attribution,
> multi-level commission, per-goods rates, self-serve signup. Schema mostly
> EXISTS: V7 `litemall_user_brokerage_record` is a complete frozen/valid/
> invalid ledger but has NO Java mapper; `litemall_user` already carries
> `spread_uid/spread_time/is_promoter/spread_count/pay_count/brokerage_price/
> path`; `litemall_user_extract` is fully wired (order's `/srv/wallet/
> extract`). The wave's only migration is order's config-row seed.
> **Shared contract (so worktrees start in parallel): invite code =
> `"A" + base36(uid) uppercase`** (e.g. uid 7 → `A7`, uid 42 → `A16`);
> decode by stripping the leading `A` and parsing base36 — no DB column.
>
> **Dependency order:** `order` owns the engine + the `docs/` handoff specs —
> start first. `gateway-api` (invite capture) and `gateway-admin` Task A
> (edge auth split) are independent — start all three together; gateway-admin
> SPA panels build against order's committed handoffs (`docs/` pattern).
> `promotion` and `goods-management` are parked this wave.
>
> **Cross-cutting landmines (apply to every block):**
> - **Flyway:** one migration this wave (order's config seed) on the shared
>   dev DB. V38 is the last KNOWN version; check `flyway_schema_history`
>   immediately before first boot and claim the next free number (V32 was
>   stolen mid-flight once). `out-of-order: true` is permanent. Never
>   `flyway repair`. Whichever service boots first applies shared migrations.
> - **litemall-db is shared and hand-maintained:** never regenerate; hand-edit
>   entities + mapper XMLs together (the `now_money`/`OrderMapper.xml` silent
>   failures). After editing: `mvn install` litemall-db, restart EVERY
>   dependent, verify the nested `BOOT-INF/lib` copy in running exec jars;
>   concurrent `-am` builds overwrite `~/.m2`.
> - **svcsecurity is deny-by-default:** any new anonymous customer path must
>   be added to the owning service's `litemall.svcsecurity.public-paths`
>   (this wave: none expected — `/srv/private/affiliate/**` is authenticated
>   edge-side and relayed with the machine token like the admin prefix).
> - Verify live through the gateways (`:9000`/`:9001`→`:8090`, `:18080`) —
>   machine-token ~10-min TTL makes direct service curls flaky.

### Worktree: `promotion`
- **Branch:** `fix/promotion` — **parked, no Wave-5 assignment.** Wave-2
  coupon/group-buy revive is merged (`73f12660c`). Recorded follow-up for a
  future wave: register-gift coupon (`assignForRegister` parity) now that
  gateway-api's `/auth/register` exists — via a user-registered event or an
  internal grant endpoint.

### Worktree: `goods-management`
- **Branch:** `fix/goods-management` — **parked, no Wave-5 assignment.**
  Wave-4 parity/content tasks and the deals wave (flash-deal price-swap
  lifecycle + co-occurrence related items) are merged to master
  (`d9dad23dc`); specs live in git history.

### Worktree: `order`
- **Branch:** `fix/order` — FIRST: `git merge master` (the branch tip is the
  already-merged Wave-4 work). · **Scope:** `litemall-order/` plus the
  litemall-db edits it owns (ONE config-seed migration + brokerage mapper
  trio + `LitemallUserMapper` credit/debit). Admin controllers live in
  `interfaces/rest/` (no `admin/` subpackage); orchestrator =
  `application/LitemallOrderOrchestratorService`; schedulers + internal
  services in `application/internal`.
- **Task — brokerage engine (Wave-5 Phase 1):**
  1. **Migration** `V<next>__brokerage_config.sql` (check
     `flyway_schema_history` first — V38 known used): INSERT INTO
     `litemall_system` the rows `litemall_brokerage_enabled` `'false'`,
     `litemall_brokerage_rate` `'5'`, `litemall_brokerage_freeze_days` `'7'`,
     `litemall_brokerage_min_extract` `'10'`. NO new tables — V7 already
     created `litemall_user_brokerage_record`. (Rows MUST exist:
     `updateConfig` silently no-ops unknown keys.)
  2. **litemall-db hand-edits:** NEW `LitemallUserBrokerageRecord` domain +
     slim mapper interface + XML (CjSourcingRequestMapper pattern, no Example
     classes): insert, paged-by-user (`add_time desc`), sumByUserAndStatus,
     findUnfreezable(now), guarded `WHERE status=0` transitions (→1 unfreeze,
     →-1 invalidate). `LitemallUserMapper.xml`: atomic `creditBrokerage`/
     `debitBrokerage` guarded UPDATEs (`brokerage_price = brokerage_price ±`
     with `>=` guard on debit); verify `spread_*`/`is_promoter`/
     `brokerage_price` appear in EVERY result map (the `now_money`
     silent-drop landmine). `mvn install` litemall-db + restart dependents.
  3. **`BrokerageService`** (`application/internal`):
     `@TransactionalEventListener(AFTER_COMMIT)` on `LitemallOrderPaidEvent`
     → SMALL dedicated executor → catch-all WARN (the Wave-4 auto-print
     listener pattern; payment must never block; paid event fires at most
     once via the 0-row-update guard). If enabled AND buyer has `spread_uid`
     → live promoter (`is_promoter=1`, not deleted): write FROZEN ledger row
     — `link_id=orderSn`, `link_type='order'`, `pm=1`,
     `price = goodsPrice × rate%` (HALF_UP, 2dp), `balance` = snapshot,
     `freeze_time=now`, `unfreeze_time=now + freezeDays`. Read config via
     `LitemallSystemConfigService` per event (core SystemConfig static cache
     is per-JVM/stale — don't use it).
  4. **Aftersale clawback:** in the `approveAftersale` orchestrator path —
     matching row (`link_id=orderSn AND status=0`) → status −1 via guarded
     UPDATE. Already-unfrozen rows stay valid (documented in the ADR — money
     may be withdrawn; that is why the freeze window exists).
  5. **Unfreeze sweep** `@Scheduled` (the CJ 5-min sweep pattern):
     findUnfreezable → ONE TX PER ROW: guarded status 0→1 +
     `creditBrokerage`; 0-row update = lost race → skip, never retry-credit.
  6. **Withdrawals:** `LitemallExtractRequestCommand` gains
     `source: wallet|brokerage` (absent = wallet — back-compat). brokerage:
     below min-extract → 422; guarded `debitBrokerage` (insufficient → 422);
     write `pm=0` ledger row `link_type='extract'`. NEW admin
     `/srv/private/admin/extract/{list,approve,reject}` over
     `litemall_user_extract` (approve only from status 0 — guarded UPDATE;
     reject = refund `brokerage_price` + `fail_msg` + `fail_time`).
  7. **Affiliate API** `/srv/private/affiliate/**` (identity = forwarded
     `X-User-Id`, ALWAYS self-scoped — no id params anywhere, no IDOR
     surface): `GET dashboard` {available=`brokerage_price`, frozenSum,
     lifetimeEarned (status=1 pm=1), thisMonth, spreadCount, referredOrders},
     `GET records?page=` (paged ledger), `GET team?page=` (users WHERE
     `spread_uid`=me: MASKED nickname, add_time, pay_count), `GET links`
     (invite code per the shared contract + canonical URLs
     `http://localhost:9000/register?invite=<code>` and
     `/product/<goodsId>?invite=<code>`), `POST extract` +
     `GET extract/history`. New errno constants 660-family beside the
     existing ones — don't re-mint inline literals.
  8. **Handoffs under `docs/`:** `handoff-affiliate-portal.md` (EVERY
     endpoint envelope + errno table + what the edge must forward),
     `handoff-register-invite.md` (code format + resolve rules — mirrors the
     shared contract above), `adr-brokerage-lifecycle.md` (frozen/invalid/
     valid semantics, unfrozen-then-refunded deviation, kill switch).
- **Acceptance:**
  - `mvn -q -o -pl litemall-order -am compile` clean; boots; migration
    applies; `FlywayMigrationTest` green (no new tables — untouched).
  - E2e (SQL-seed promoter A `is_promoter=1`, user B `spread_uid=A`, rate 5,
    enabled true): B pays an order → EXACTLY ONE frozen row, price = 5% of
    goodsPrice; force `unfreeze_time` past → sweep credits A's
    `brokerage_price`, row → status 1, second sweep pass is a no-op;
    aftersale approved pre-maturity → row −1, balance untouched; kill switch
    `'false'` → pay writes no row; buyer without referrer → no row.
  - Extract: brokerage extract ≥ min → balance debited + `pm=0` row + pending
    in admin list; reject → balance restored + fail_msg; below-min /
    insufficient → 422, nothing changes; wallet-source extract regression
    unchanged.
  - Affiliate endpoints return sums matching manual SQL (verify direct with
    machine token + X-User-Id; gateway path lands with gateway-admin).
  - Regression: pay/aftersale/wallet/CJ e2e unchanged; grep — no adapter
    imports from application/domain.

### Worktree: `gateway-api`
- **Branch:** `fix/gateway-api` — FIRST: `git merge master`. · **Scope:**
  `litemall-gateway-api/` only (edge + customer SPA). NO migration this
  wave; the tiny litemall-db exposure below is verification-only.
- **Task — invite capture at registration (Wave-5; small, independent —
  start immediately; contract = the shared invite-code format above +
  order's `docs/handoff-register-invite.md` once committed):**
  1. `POST /auth/register` gains optional `inviteCode`: decode per contract;
     referrer must exist, `is_promoter=1`, not deleted. Valid ⇒ set
     `spread_uid`, `spread_time=now`,
     `path = referrer.path + referrer.id + "/"`, increment referrer
     `spread_count`. `''`/absent/malformed/unknown/non-promoter ⇒ register
     NORMALLY with `spread_uid=0` — an invite problem never fails or delays
     registration and surfaces no error. Verify `LitemallUser` +
     `LitemallUserMapper.xml` actually round-trip `spread_*`/`path` through
     the insert/update used here (result-map landmine; hand-edit + own the
     install/restart consequences if not).
  2. SPA: an App-level effect stashes `?invite=` from ANY landing route into
     sessionStorage; Register.tsx shows a dismissible "Invited by a friend"
     chip when present and sends `inviteCode` in the existing register thunk;
     clear the stash after successful registration.
- **Acceptance:** compile + SPA build. Curl: register with a valid code →
  row shows spread_uid/spread_time/path AND referrer `spread_count` +1;
  malformed/unknown/non-promoter code → errno 0 and `spread_uid=0`. Browser
  e2e: land on `/product/<id>?invite=A1` → browse → register → SQL proves
  the binding. Regression: register 704/705 paths, login/refresh/logout,
  seed-user login unchanged.

### Worktree: `gateway-admin`
- **Branch:** `fix/gateway-admin` — FIRST: `git merge master`. · **Scope:**
  `litemall-gateway-admin/` (edge + admin SPA). Backend gaps become handoff
  notes, never edits to other modules. Webpack gotcha: `mvn package` re-runs
  webpack — delete `target/checksums.csv.old` if the jar ships without
  `static/index.html`.
- **Task A — edge auth split (independent — start immediately; THE
  security-critical piece of the wave):**
  1. `AuthoritiesConstants.AFFILIATE = "ROLE_AFFILIATE"`.
  2. **`AdminJwtAuthenticationManager` must derive authorities FROM the JWT
     `roles` claim** instead of unconditionally granting ROLE_ADMIN — an
     affiliate token must NEVER map to ADMIN. Regression: existing admin
     tokens (claim `[ROLE_ADMIN]`) behave byte-identically.
  3. `POST /auth/affiliate/login|refresh|logout`: authenticate against
     `litemall_user` (same BCryptPasswordEncoder family as
     AdminCredentialsService/gateway-api), REQUIRE `is_promoter=1` else 403
     "not an affiliate"; issue the admin-edge JWT with `typ=affiliate`,
     `uid=<litemall_user.id>`, `roles = new String[]{AFFILIATE}`
     (**String[] not List** — the documented `JwtService.addClaim`
     serialization trap); refresh over `litemall_user_refresh_token` with
     `login_type='affiliate'` (V15 pattern — slim edge service over the same
     table if the customer one isn't reachable from this module).
  4. `SecurityConfig`: `.pathMatchers("/srv/private/affiliate/**")
     .hasAuthority(AFFILIATE)` — ADMIN deliberately NOT allowed there
     (symmetry); every existing ADMIN matcher untouched.
     `AdminAuditLogFilter`: skip `typ=affiliate` tokens (it writes admin ids
     into `litemall_log`).
- **Task B — routes:** `/srv/private/affiliate/**` → `lb://order-service-app`
  BEFORE the `/srv/**` catch-all, in BOTH `routes:` blocks (keep in sync);
  confirm `/srv/private/admin/extract/**` rides the existing explicit order
  admin route (add it if prefixes are enumerated). Machine-token relay must
  fire on the affiliate prefix exactly as on the admin one (verify via
  :18080 curls).
- **Task C — SPA (against order's `docs/handoff-affiliate-portal.md`):**
  1. Login page gains an **Affiliate** tab → `/auth/affiliate/login` thunk;
     persist the role; role-keyed router: ROLE_AFFILIATE lands on
     `/affiliate/dashboard` and can reach ONLY `/affiliate/**` — Dashboard,
     My Links (+ client-side QR, copy buttons), Earnings (ledger with
     frozen/valid/invalid chips), Team, Withdraw (request + history);
     `/admin/**` navigation redirects away; `menu.config.ts` gains a role
     filter (admin menu unchanged for admins). Client gating is UX ONLY —
     SecurityConfig is the boundary.
  2. Admin pages: **Promoters** (user search + `is_promoter` toggle via a
     NEW edge controller over `LitemallUserService` —
     EdgeAdminProfileController pattern, `AdminEdge.blocking()`; link to a
     per-affiliate ledger view), **Extract approval console** (pending list
     → approve / reject-with-reason → toasts), **Sys/ConfigBrokerage** form
     for the 4 `litemall_brokerage_*` keys (rows seeded by order's
     migration; add a `brokerage` group to EdgeAdminConfigController — keep
     its key-validation guard).
- **Acceptance:**
  - Compile clean; admin SPA prod build green (checksum gotcha).
  - Privilege-separation curl proofs through :18080: affiliate token on ANY
    `/srv/private/admin/**` → 403; admin token on
    `/srv/private/affiliate/**` → 403; non-promoter customer on affiliate
    login → 403; deleted user → 401/403, never NPE. admin123/mall123 login +
    the Wave-4 admin surfaces regress clean.
  - Browser e2e: grant promoter via the Promoters page → affiliate logs in
    on the Affiliate tab → dashboard sums match SQL; ledger/team/links
    render; QR/copy works; withdraw request appears in the admin console;
    reject restores the balance with the reason visible affiliate-side;
    affiliate deep-link to `/admin/orders` redirects.
  - Config form round-trips `litemall_brokerage_rate`; a rate change alters
    the NEXT commission row (proves per-event config read).
