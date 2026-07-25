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
> **Wave 9 is MERGED to master** (goods-management `c78118743`, gateway-api
> `d085062c7`) — deploy to the VPS is PENDING and belongs to the main
> session. Spec + cross-module contract live in git history (`abc2d4b72`).
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
>
> **DEV → PROD:** worktrees merge to master as always. **Deployment to the
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
- **No Wave-9 assignment.** The Wave-8 CJ fulfilment/payment walk is merged
  and deployed (`b6906aac2` + retained-placement `272176272`). Do not start
  work here without a new instruction.

### Worktree: `goods-management`
- **No Wave-9.1 assignment.** The Wave-9 search contract (app-side
  highlighter + typed suggest, `c78118743`) is merged to master; deploy
  pending with the main session. Do not start work here without a new
  instruction.

### Worktree: `gateway-api`
- **Branch:** `fix/gateway-api` — FIRST: `git merge master`. · **Scope:**
  `litemall-gateway-api/` (edge + customer SPA). NO migration. No new
  anonymous service paths — social config rides the EXISTING
  `/auth/site-config`.
- **Task — Wave 9.1: storefront trust surfaces (social links, help center,
  customer-service FAQ).**
  1. **Config-driven social links in the footer.** Extend the site-config
     seam: `litemall.social.{facebook,instagram,tiktok,youtube,x}-url`
     `@Value` bindings in `SiteConfigController` (env-overridable,
     `LITEMALL_SOCIAL_*`), fields in the `siteConfig.ts` interface, then
     turn the decorative glyphs (`Layout.tsx:496-501`) into real anchors:
     `target="_blank" rel="noopener noreferrer"`, proper `aria-label`s.
     RENDER ONLY icons whose URL is non-empty. Committed yml default for
     facebook: `https://www.facebook.com/trovemo`; all others default
     EMPTY (the pages don't exist yet — a hidden icon, not a dead link).
     Add the `bi-tiktok` glyph so it's ready. Activation later = set env +
     recreate, NO rebuild — prove this in dev.
  2. **Help Center (`/help`, Help.tsx).** Grow the 5-entry FAQ into a
     structured self-service hub: topic sections (Orders & Delivery ·
     Payments & Pricing · Returns & Refunds · Account & Security · Coupons
     & Deals), a client-side FAQ filter box, and a guided "still stuck?"
     flow — self-serve deep links first (order status/timeline in
     `/user/...` account pages, `/returns` policy, `/cookies` preferences,
     password reset), then escalation card: `support@trovemo.com` (mailto)
     + `/user/feedback`. EVERY answer must state only TRUE store behaviour
     (card via Stripe + wallet; 7-day return window per `Returns.tsx`;
     dropshipping delivery windows stated honestly; no invented policies,
     no invented channels). Keep the existing no-i18n plain-JSX convention.
  3. **Shared FAQ source.** Factor the Q&A content into one data module
     (e.g. `app/modules/static/faqData.ts`) consumed by BOTH `/help`
     (full hub) and `/service` (top questions) so they can't drift.
  4. **Customer Service (`/service`, CustomerService.tsx).** DELETE the
     fake phone `+1 (800) 000-0000` (never show unreal contact channels —
     same honesty rule as payments). Keep hours + `support@trovemo.com`
     mailto; add "Top questions" (from the shared FAQ, linking into
     `/help` sections/anchors), the social row, and the `/user/feedback`
     link for signed-in users.
  5. **Cleanup:** delete the orphaned
     `app/views/userViews/pages/ContactUs.tsx` (unrouted, `alert()`
     submit, "Twitter, Inc." address). Verify the footer "Let us help
     you" column links (`/help`, `/service`, `/returns`) stay coherent.
- **Acceptance:** SPA build clean; footer facebook icon opens
  `facebook.com/trovemo` in a new tab; instagram/tiktok/youtube/x icons
  ABSENT from the DOM until their env var is set — then appear after
  restart with NO rebuild (demonstrate once in dev); no fake phone
  anywhere (repo-wide grep for `800) 000`); `/help` filter narrows
  entries, every internal link resolves (no 404 through `:9000`);
  `/service` shows top questions + escalation; ContactUs.tsx gone;
  anonymous browse/search/PDP, login, cart, checkout regression-green
  through `:9000`.

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
