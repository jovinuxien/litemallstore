# Spec — seasonal candidacy as an indexed signal (all four seasons)

**STATUS: BUILT + MERGED (2026-08-26, `dcff54c67`). NOT YET DEPLOYED.**
Approved and implemented as specified; the deviations are recorded in §14. Commissioned 2026-08-26 in the
`goods-management` worktree. This is the contract a build codes to; it supersedes the looser
proposal in the OCS review of the same day.

## Why

The live season page ("Autumns Deal", `litemall_page` id 5, active) carries ONE rail:
`mode=byIds` with **24 hardcoded goods ids — the validator's maximum**. A person picked the
ceiling by hand and the list is frozen. A product arriving tomorrow with better margin, real
stock and German warehouse cover cannot enter it; one that sells out cannot leave. The palette's
other modes (`deals`, `hot`, `new`) resolve against the index, but none of them means
*seasonal*.

## Decisions already taken (do not relitigate)

| # | Decision | Who | Date |
|---|---|---|---|
| 1 | Seasonal fitness becomes a **scored property written into the search index**, not a curated list | user | 2026-08-26 |
| 2 | It covers **all four seasons continuously**, not just the running one — so next season's page is populated before it is activated | user | 2026-08-26 |
| 3 | Global gates are identical for every season; only **weights** are per-season, so adding a season needs no code edit | user | 2026-08-26 |
| 4 | A qualifying product **publishes automatically**, bounded by a cap + env kill-switch, with permanent admin dismissal (auto-with-veto) | user accepted recommendation | 2026-08-26 |
| 5 | The rail is **resolved server-side** in goods-management for v1, so today's storefront renders it with no cross-module dependency | user accepted recommendation | 2026-08-26 |

## 1. The index field — `seasons`, multi-valued

`ProductDocument` gains `seasons` as a **`List<String>`**, mirroring the existing
`category_names`/`category_ids` multi-valued fields (proven mechanics, not invented ones).

A product carries e.g. `["autumn","winter"]` — a wool throw legitimately belongs to both. One
field covers N seasons; adding a season is a data row, never a schema change.

Membership only. **The score is NOT indexed.** A single scalar cannot honestly hold a product's
score for two different seasons, and the page filters to one season anyway. Ranking within a
season uses the index's existing sorts, exactly as the deals page ranks `deal_flag=1` results.
The per-season score lives in the candidate table, where the admin screen reads it.

⚠ **Mapping-materialisation risk, and its mitigation.** The flag family emits explicit `0`/`1`
because OCS resolves filter/sort fields against the index MAPPING, which only materialises once
some document has held the field. An **empty array may behave like an absent field**. Emitting a
`"none"` sentinel would guarantee materialisation but pollutes the facet with a junk entry.
Mitigation instead: **run the scorer BEFORE the full reindex at deploy**, so the first index
already contains real members. Verify the filter returns rows before declaring the deploy done.

## 2. Season definitions — data, not code

**New table `litemall_season_rule`**, one row per season:

| Column | Meaning |
|---|---|
| `season_key` | `autumn` \| `winter` \| `spring` \| `summer` — and anything else later |
| `name` | Display name |
| `window_start_md` / `window_end_md` | Recurring month-day window (wraps the year end) |
| `terms` | JSON array of matching terms |
| `category_ids` | JSON array of boosted categories |
| `price_min` / `price_max` | Target retail band |
| `weights` | JSON: EU multiplier, freshness bonus ceiling, category boost, demand weight |
| `enabled` | Kill-switch per season |

The migration seeds the four **northern-hemisphere** seasons — correct for a DE/FR/DK/SE market.
Nothing caps the table at four: "Black Friday" or "Garden season" is another row.

Rules are editable at runtime through the admin endpoints in §7. If weights were only editable
by redeploy, "configuration" would be a lie.

## 3. Global gates — identical for every season, never tunable per season

An item must pass **all** of these to be eligible for any season:

1. **Profitability** — `retail × 0.85 ≥ cost × 1.05` (the existing deal floor).
   ⚠ **Uncosted goods FAIL CLOSED.** `marginPct` is `null` — never `0` — when cost has not been
   captured. Null means ineligible, never "passes". This is the Wave-18 coupon-guard lesson:
   the same ambiguity there had to become an explicit typed refusal.
2. **Availability** — on sale, with stock on hand.
3. **Price band** — read from `litemall.goods.price-floor` and `…price-ceiling`, **never a
   hardcoded €5**. The floor is env-configurable and has already been changed once in
   production. Match the existing comparison (`retail < floor` is below), so a product at
   exactly the floor stays eligible, as it does everywhere else.
4. **Lifecycle** — not retired. ⚠ Retirement executes as an `is_on_sale` flip, so gate 2 already
   covers this. Kept as belt-and-braces; no code may depend on it catching anything alone.

## 4. Per-season weights

Each season is described purely in configuration:

- **Terms & categories** — matching terms plus boost factors for seasonal categories.
- **Price band** — winter coats are not priced like summer sandals.
- **Warehouse valuation** — a multiplier for German warehouse stock, higher where the season
  carries a delivery deadline (December gifting). ⚠ A **boost, never a filter**: `eu_flag=0`
  means "not known to hold EU stock", not "none".
- **Freshness** — expressed as a **bonus in `[1.0, 1.25]`, not a decay toward zero.** The score
  is multiplicative, so any factor that can reach 0 zeroes the whole score. As a bonus, a
  cold-start item is never penalised, only un-boosted, and the non-zero floor is structural
  rather than a config value someone can accidentally set to 0.

## 5. Discovery and scoring

Reuses the index rather than inventing matching:

- **Discovery** — for each enabled season, run its terms through OCS, take the hits, then apply
  the global gates using DB cost/stock context. The scorer reads the index to find candidates
  and writes a signal back into it.
- **Base formula** — the existing deal-scorer curve: `marginPct × ln(1 + stock) × social`.
- **Seasonal overlay** — multiply by the per-season weights.
- **Tiers** — `hot` / `featured` / `watch`, same threshold family as `DealCandidateScorer`.
- ⚠ **Bound the sweep.** Terms × 4 seasons × hits can pull a large set. Apply a per-season
  candidate cap and **log what was dropped** — the project's no-silent-caps rule.

## 6. Persistence

**New table `litemall_season_candidate`**: `(season_key, goods_id, day, score, tier, reasons,
status, config_snapshot)`, shape mirroring `litemall_deal_candidate`.

- **UNIQUE (season_key, goods_id, day)** — the atomic guardrail.
  ⚠ A constraint alone does not make parallel execution safe; it makes collisions *loud*. A
  plain INSERT throws and can take down the batch. Pair it with
  `INSERT … ON DUPLICATE KEY UPDATE` that **skips any row whose status an admin has decided**.
  The constraint is the guardrail; the upsert is what makes it safe.
- **State preservation** — a re-run never overwrites or alters an admin decision.
- **Audit trail** — the config version hash, **plus a snapshot of the effective weights on the
  row**. A hash alone is a fingerprint with nothing to resolve it against; the snapshot survives
  even if a rule row is later edited or deleted.
- `status`: `auto` (published by the scorer) | `dismissed` (admin veto, permanent) |
  `proposed` (scored but below the auto threshold or over cap).

The index write includes a product in `seasons` when it holds a row with status `auto` for that
season. `dismissed` is excluded permanently.

## 7. Admin surface

Under the existing `/srv/private/admin/insight` routing (errno envelope; money plain decimals;
margin fields null, never 0, when cost is uncaptured):

- `GET /insight/season-candidates?season=&status=&day=` → list with score, tier, reasons, cost,
  margin, stock, status.
- `POST /insight/season-candidates/{goodsId}/dismiss` body `{season, day?}` — permanent veto.
- `POST /insight/season-candidates/{goodsId}/restore` body `{season, day?}` — undo a veto.
- `GET /insight/season-rules` → the rule rows.
- `PUT /insight/season-rules/{key}` — edit weights/terms/band, bounded validation.

**No gateway-admin work is in scope.** These endpoints are the contract; the admin UI is a
follow-up raise for that worktree.

## 8. Rendering — server-side resolution (v1)

`PageService.toPageView` is the single assembly point for every page payload, and is where the
rail resolves.

- The palette gains `season` to `GOODS_LIST_MODES`; a `season` rail requires `seasonKey`.
- Stored as `mode=season`; **served as `mode=byIds` + resolved `goodsIds` + `resolvedFrom:
  "season"`**, so today's storefront renders it unchanged. The marker keeps the payload honest
  about the rewrite rather than hiding it.
- Cap configurable, default 24 (matching today's page).
- **Empty resolution ⇒ the rail is ABSENT**, never an empty grid — matching the season degrade
  rule already implemented in the storefront's `season.ts`.
- ⚠ The existing hand-picked `byIds` Autumn page is untouched and keeps working. Switching it is
  an admin act.

**Stated follow-up, not a silent compromise:** raise a contract for gateway-api to render
`mode=season` natively by querying `seasons=<key>`, so the storefront can sort and paginate the
season itself. v1 does not preclude it.

## 9. Schedule and configuration

- Nightly full pass at **04:35** — after the 04:30 promo scorer, before the 04:45 stats rollup.
- Plus an **arrival hook** on promote (the `NEW_ARRIVAL` channel precedent), so a fresh product
  does not wait a day. Wave 19 showed an arrival-only scorer is not enough on its own, hence both.
- ⚠ No CJ calls on any request path; the scorer uses local tables and the index only.

Config under `litemall.seasons.*`, all env-backed with explicit yml placeholders (the
`LITEMALL_GOODS_PRICE_FLOOR` passthrough lesson — verify the env actually binds *inside the
container*):

| Key | Default | Purpose |
|---|---|---|
| `enabled` | true | Master kill-switch |
| `auto-publish-enabled` | true | Auto-with-veto off ⇒ everything stays `proposed` |
| `auto-tier` | `featured` | Minimum tier that auto-publishes |
| `per-season-cap` | 24 | Max auto-published per season |
| `cron` | `0 35 4 * * *` | Nightly pass |

## 10. Migration

**V64** — `litemall_season_rule` + `litemall_season_candidate` + the four seeded rules.
⚠ Check `flyway_schema_history` **immediately before first boot**: V63 is the highest in the
repo (Wave 27), and prod has been recorded at V60 — confirm, never assume.

## 11. Deploy sequence

Order matters; two steps here have cost a deploy before.

1. Indexer config: add `seasons` field + facet **and BOTH dynamic-field regexes** — the step
   that has bitten every previous flag. Recreate the indexer.
2. Deploy goods-management (V64 applies at boot).
3. **Run the scorer** so index members exist (see the materialisation risk in §1).
4. **Full reindex.**
5. **Restart the searcher AFTER the reindex** — a new field does not resolve until it restarts.
6. Verify `/srv/search?seasons=autumn` returns rows and the season page resolves.

## 12. Acceptance (dev, through :9000/:8090)

- V64 applies clean; the four seasons are seeded.
- A product that fails ANY global gate never appears — including an **uncosted** one (the
  fail-closed case gets its own test).
- A product scoring into two seasons carries both keys in `seasons`.
- `/srv/search?seasons=<key>` returns exactly the auto-published set for that season.
- An admin dismissal removes a product from the index on the next write and it is **never
  re-proposed**.
- A re-run does not alter any admin-decided row (proven by re-running).
- `auto-publish-enabled=false` ⇒ everything stays `proposed` and the page rail is absent.
- A `season` rail with no members renders as absent, not an empty grid.
- The existing `byIds` Autumn page is byte-identical before and after.
- Module tests green with real "Tests run:" counts (⚠ the root pom skips `mvn test` and surefire
  misses `@Nested`).

## 13. Out of scope

- gateway-admin UI for candidates and rules (endpoints are the contract; raise it).
- gateway-api native `mode=season` rendering (§8 follow-up).
- Southern-hemisphere windows.
- Indexing a per-season score.

## 14. As built — deviations from this plan

Three details changed while building, all in the direction of removing a failure mode:

1. **The cap moved to READ time.** The plan implied a publish step that capped as it wrote. That
   has a re-run bug: rows already at `auto` do not re-enter the count, so a second run on the same
   day publishes past the cap. `selectPublishedGoodsIds(season, cap)` ranks and caps on read, which
   is idempotent however often the scorer runs. `SeasonSignalResolver` snapshots it on a 60s TTL,
   the `EuStockSignalResolver` pattern.
2. **A veto is carried forward explicitly.** The upsert guard only protects the row for the SAME
   day, so a dismissal would have quietly expired on the next run. The scorer now checks
   `countDismissed(season, goods)` across all days and re-writes the veto onto the new day's row.
   Without this, "permanent" would have lasted until midnight.
3. **Weights are clamped on parse** to `[1.0, 3.0]`. An admin editing a weight to 0 would otherwise
   have zeroed every product in that season, because the score is multiplicative — the same reason
   freshness is a bonus rather than a decay.

Everything else shipped as written. Tests: module 574 run / 0 failures / 8 skipped;
litemall-db 24/0 with V64 applied on a real MySQL container.
