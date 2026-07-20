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
> **Wave 8 (2026-07-20) — CJ COMMERCE COMPLETENESS + TROVEMO BRANDING.**
> The store is live and sells ONLY the CJ dropshipping catalog (the 239
> legacy goods are off sale). Three things stand between "live" and
> "credible": (1) the CJ order fulfilment + payment path has never been
> walked end to end since the Wave-7 rewrite — walk it, fix what's broken;
> (2) the PDP customer-reviews section is EMPTY for every CJ good (the
> Reviews UI works and calls `/srv/comment/list`; the data side serves only
> local `litemall_comment` rows, of which dev has 7) — ingest CJ product
> reviews; (3) both SPAs still wear stock branding — the storefront header
> is the literal text "litemall" and the admin header is a JHipster PNG.
> Brand assets live in **`doc/brand/`**: `logo-wordmark.svg` (trovemo
> wordmark; NOTE white background + dark text — adapt for dark headers) and
> `logo-open-trove.svg` (square parcel mark → favicon source). Palette:
> `#F09000` / `#FFC24B` / `#C86F00` / `#23272E`.
>
> **DEV → PROD:** worktrees merge to master as always. **Deployment to the
> VPS is done by the MAIN session after merge** (docker build + recreate +
> reindex where needed) — do NOT touch the production VPS or its DB from a
> worktree. Verify in dev through the gateways.
>
> **USER-SIDE PREREQUISITES still pending:** `STRIPE_*` and `CJ_API_KEY` are
> EMPTY in prod `.env.prod` — live card pay and live CJ placement stay
> blocked by design. Everything must degrade honestly: typed errors,
> retryable states (an order paid today must be placeable at CJ tomorrow
> when the key arrives), never a fake success, never a 5xx.
>
> **Cross-cutting landmines (apply to every block):**
> - **Flyway:** V43 is the last used (Wave-7 `order`). **V40 remains
>   EARMARKED** for goods-management's parked CJ-deals SKU-charge fix — do NOT
>   take it. Wave-8 migrations claim **V44+** after checking
>   `flyway_schema_history` immediately before first boot. `out-of-order: true`
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

### Worktree: `order`
- **Branch:** `fix/order` — FIRST: `git merge master`. · **Scope:**
  `litemall-order/` (+ migrations **V44+** only if truly needed).
- **Task — walk the CJ order fulfilment + payment path end to end; fix every
  gap.** The chain: add CJ good → `GET /srv/cart/checkout` (server totals) →
  submit → pay (WALLET works today; Stripe disabled ⇒ typed 402-style error,
  never fake success) → `placeForPaidOrder` (AFTER_COMMIT + outbox, moved out
  of the money TX in Wave-7) → CJ `createOrderV2` (incl. IOSS fields,
  `CjDropshipOrderFacadeImpl`) → CJ status sync scheduler → tracking surfaced
  on `GET /srv/order/{id}` (timeline events). Walk it in dev with the CJ ACL
  in BOTH states:
  - **CJ enabled (dev key):** a wallet-paid CJ order must reach CJ placement
    and progress via status sync; tracking visible to the customer. ⚠ A real
    CJ placement can cost money — use the cheapest variant, flag before
    placing, and prefer whatever CJ sandbox/test mode exists.
  - **CJ disabled (prod's current state, `CJ_API_KEY` empty):** payment still
    settles, order lands in an honest retryable fulfilment state, the outbox
    RETAINS the placement job and retries once the key appears — an order
    paid today must NOT be stranded or marked fulfilled. Refund/cancel of an
    unplaced CJ order must work; of a placed one must not lie about CJ-side
    state.
- **Acceptance:** dev e2e demo of the enabled path (or sandbox equivalent);
  disabled-path degradation exactly as above, verified by flipping the env;
  timeline shows fulfilment events; `litemall-prod.sh smoke-checkout` money
  path unbroken (note: it needs the one-legacy-good re-activation dance, the
  script prints it); Wave-7 adversarial checks (junk paymentIntent, replay,
  tampered amount) still dead.

### Worktree: `goods-management`
- **Branch:** `fix/goods-management` — FIRST: `git merge master`. · **Scope:**
  `litemall-goods-management/` (+ migrations **V44+**; **V40 stays EARMARKED**
  — do not take it). The parked CJ-deals work stays parked.
- **Task — CJ product reviews: the PDP reviews section is EMPTY for every CJ
  good.** `/srv/comment/list` (type 0) reads only local `litemall_comment`
  (7 rows in dev, ~0 in prod). CJ's API exposes product comments/reviews for
  its catalog — ingest and serve them for CJ goods through the SAME endpoint
  the SPA already calls (`Reviews.tsx` renders fine; it receives `total:0`).
  Design constraints:
  - Follow the module's **demand-driven enrichment precedent**
    (`CjDetailEnrichmentService`) — fetch on first view, persist/cache, serve
    from local store afterwards; NO bulk pre-import of 9k goods.
  - **Fail-soft:** CJ ACL disabled/unreachable ⇒ clean empty list, never 5xx
    (prod currently has no CJ key — the PDP must still render).
  - Keep `POST /srv/comment/post` (signed-in customer reviews) working; local
    + CJ reviews merge in one list, newest-first or rating-weighted — your
    call, document it.
  - Mind `cj_<pid>` → native id resolution (`EngagementGoodsResolver`
    precedent) and the machine-token/admin path distinctions.
- **Acceptance:** in dev with the CJ key: PDP of a popular CJ good shows real
  reviews (count, stars, dates, bodies); second view served locally (no CJ
  re-hit — show the log); CJ ACL off ⇒ clean empty state; a posted customer
  review appears alongside CJ ones; search/reindex unaffected; migration (if
  any) is V44+ and applies cleanly.

### Worktree: `gateway-api`
- **Branch:** `fix/gateway-api` — FIRST: `git merge master`. · **Scope:**
  `litemall-gateway-api/` (edge + customer SPA). NO migration.
- **Task — Trovemo branding on the storefront.** The header brand is the
  literal text "litemall" (`app/Layout.tsx:122-124`, dark navbar). Replace
  with the wordmark `doc/brand/logo-wordmark.svg` — it ships white-background
  + dark-text, so derive a header variant (transparent background,
  light/white text, KEEP the orange open-"o" + spark exactly as designed).
  Favicon + touch icons from `doc/brand/logo-open-trove.svg` (supersedes the
  current favicon); wire real sizes (16/32/180/512 + manifest if present).
  Sweep the SPA for remaining **user-visible** "litemall" strings (footer,
  page titles, empty states, mails routed through site-config, legal pages) —
  internal identifiers, package names and API paths are NOT in scope. Reviews
  section: verify it renders goods-management's CJ reviews once that worktree
  merges (coordinate via master), and that the signed-out/empty state looks
  intentional.
- **Acceptance:** SPA build clean; header wordmark crisp on desktop + mobile
  widths (no white box on the dark navbar); tab icon = open-trove mark;
  a case-insensitive sweep of user-visible surfaces finds no "litemall";
  PDP reviews render CJ data post-merge; anonymous browse/search/PDP, login,
  cart, checkout all regression-green through `:9000`.

### Worktree: `gateway-admin`
- **Branch:** `fix/gateway-admin` — FIRST: `git merge master`. · **Scope:**
  `litemall-gateway-admin/` (edge + admin SPA). NO migration.
- **Task — Trovemo branding on the admin console.** The admin header still
  renders the JHipster logo (`shared/layout/header/header-components.tsx:10`
  → `content/images/logo-jhipster.png`). Replace with the Trovemo wordmark
  (`doc/brand/logo-wordmark.svg`, adapt background/contrast to the header),
  favicon/tab icon from `doc/brand/logo-open-trove.svg` (supersedes the
  `c96c73d6f` favicon), and sweep the admin SPA for user-visible "litemall" /
  "JHipster" remnants (sign-in page, sidebar, navbar, page titles, about
  strings). Internal identifiers are NOT in scope.
- **Acceptance:** admin SPA builds AND the jar actually contains it
  (**checksum dirty-skip gotcha: `rm target/checksums.csv.old`** if webpack
  is skipped); sign-in page + console show Trovemo marks; tab icon updated;
  no JHipster/litemall visible anywhere; login + dashboard + goods/orders
  panels regression-green through `:18080`.

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
