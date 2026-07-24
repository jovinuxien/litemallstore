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
> Split: **goods-management** owns the two backend contract additions
> (highlight pass-through, typed suggest); **gateway-api** owns ALL SPA
> surfacing. **Cross-module contract — fixed here; implement to it, do NOT
> read the other worktree's in-flight code:**
> - Suggest entries become objects
>   `{text, type: "keyword"|"category"|"curated", categoryId?}`
>   (`categoryId` only when `type:"category"`). Plain-string entries remain
>   legal; the SPA must accept both shapes.
> - Search hits gain an OPTIONAL `highlight` map `{field: snippet}` with
>   matches wrapped in `<em>` only. Absent map/field ⇒ render plain. SPA
>   sanitizes: `em`/`mark` tags only, everything else escaped.
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
- **Branch:** `fix/goods-management` — FIRST: `git merge master`. · **Scope:**
  `litemall-goods-management/` only. NO migration expected (runtime contract
  work); if one is truly needed it's **V45+** (**V40 stays EARMARKED** — do
  not take it). The parked CJ-deals work stays parked.
- **Task — Wave 9, backend half: enrich the search contract so the SPA can
  surface what OCS already does.** Implement to the Wave-9 cross-module
  contract above; gateway-api consumes it post-merge.
  1. **Highlighting:** make `OcsSearchClient` request highlighting (OCS
     supports it — `search_products.sh` already passes `highlight=true`) and
     have `SearchService` pass a per-hit `highlight` map through per the
     contract (`<em>`-wrapped matches only). Highlight off/failed ⇒ hits
     unchanged, fail-soft, never a 5xx.
  2. **Typed suggest:** upgrade `/srv/search/suggest` entries to
     `{text, type, categoryId?}` per the contract. Classify category
     suggestions (suggest harvests `category_names`) and resolve name → id
     server-side (category service / `EngagementGoodsResolver` resolution
     precedent). Merge the curated `helper` keywords into the SAME suggest
     response as `type:"curated"` so the SPA has ONE autocomplete source;
     keep `GET /srv/search/helper` itself working (compat).
  3. Leave `/srv/search/index`, `POST /clearhistory`, category landing and
     zero-result logging as-is — gateway-api consumes them unchanged.
- **Acceptance:** via the `:9000` gateway (or `:8093` direct with a machine
  token): a category-ish prefix returns a `type:"category"` entry whose
  `categoryId` opens the right `/srv/search/category/{id}`; a title-word
  query returns hits with `<em>`-wrapped snippets in `highlight`; OCS
  down/highlight failure ⇒ plain hits, no 5xx; old-shape consumers survive
  (plain-string suggest entries still legal per contract); reindex + search
  regression green; module tests actually RUN (read the "Tests run:" count).

### Worktree: `gateway-api`
- **Branch:** `fix/gateway-api` — FIRST: `git merge master`. · **Scope:**
  `litemall-gateway-api/` (edge + customer SPA). NO migration.
- **Task — Wave 9, frontend half: surface the dormant search features.**
  Items 1-4 are pure SPA work against endpoints/fields that are live TODAY —
  do them first, in order; 5-6 depend on goods-management's contract work
  (coordinate via master, implement to the Wave-9 contract, tolerate the old
  shapes until it lands).
  1. **Search-box dropdown when focused + empty:** "Recent searches"
     (per-user) + "Trending" chips from `GET /srv/search/index`; a clear
     button wired to **POST** `/srv/search/clearhistory` (POST, not GET);
     anonymous users get trending only (index returns no history + the
     clear path is unlogin-guarded).
  2. **"Did you mean / similar results" banner:** read `queryStrategy` /
     `relaxed` from the search response; when relaxed, render "No exact
     matches for 'X' — showing similar results" above the grid.
  3. **Backend-driven sorting:** drive the sort dropdown from response
     `sortOptions` — `searchSlice.ts:60-91` already parses it into redux
     and nothing reads it; revive that path. Keep the hardcoded
     `SORT_ITEMS` (Search.tsx:46) only as fallback when the field is
     absent.
  4. **Zero-results page:** replace the dead-end empty grid in
     `Search.tsx` with an intentional state: "no results for 'X'" +
     trending keywords + popular categories (data: `/srv/search/index` and
     the existing category tree).
  5. **Highlight snippets** in `ProductHit.tsx` from the per-hit
     `highlight` map — sanitize per contract (allow `em`/`mark` only,
     escape everything else); absent map ⇒ plain title, no layout shift.
  6. **Category deep-links from autocomplete:** suggestion entries with
     `type:"category"` navigate to the existing `/category/:id` landing
     (breadcrumb + scoped facets) instead of `/search?q=`; `curated`
     entries render visually distinct; plain strings keep today's
     behaviour.
- **Acceptance:** SPA build clean; dropdown correct for anonymous AND
  logged-in users, history actually clears (POST) and dedupes visually;
  a misspelled query shows the relaxed banner; sort dropdown reflects
  server `sortOptions`; zero-results shows alternatives, not an empty
  grid; post-merge: snippets render with NO raw-HTML injection (search for
  `<script>alert(1)</script>` to prove it) and a category suggestion lands
  on `/category/:id` with breadcrumb; anonymous browse/search/PDP, login,
  cart, checkout all regression-green through `:9000`.

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
