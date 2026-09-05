# Spec — seasonal candidacy as an indexed signal (all four seasons)

**STATUS: SHIPPED + DEPLOYED to trovemo.com (2026-08-27, prod schema V64).**
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
| `hot-quantile` | 0.10 | Share of a run's scored set that reads `hot` (§17) |
| `featured-quantile` | 0.35 | Share that reads `featured` or better, always ≥ hot (§17) |

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

## 15. Deploy record (2026-08-27)

Sequence run exactly as §11: indexer recreate → goods-management (V64 applied, success=1, four
seasons seeded, env verified INSIDE the container) → scorer → full reindex 4,239 → searcher
restart → verify.

**Live acceptance:** `?seasons=autumn|winter|spring|summer` each return exactly **24** — the
read-time cap doing its job — and the field materialised, which was the risk §1 flagged. Scorer:
autumn 1007 scanned / 1005 scored / 980 dropped by cap, with real gate rejections (1 UNCOSTED,
1 UNAVAILABLE); spring 1324/1320, summer 951/948, winter 963/961. The hand-picked Autumn page is
byte-identical (`mode=byIds`, 24 ids, no `resolvedFrom`), and the brand-facet gating from earlier
in the day still holds.

### ⚠ A bug the first production run caught

The first run reported `scanned 0` for every season while the searches behind it reported
thousands of matches. **OCS document ids are STRINGS** — `toGoodsListItem` puts
`document.getId()` straight into the item, so `goodsList[].id` is `"10010060"`, not `10010060` —
and the extractor accepted only `Number`, silently discarding every hit. Fixed in `55e4d1ebc`,
pinned by a test using the real string form.

The `considered X of Y hits` log line, written only to satisfy the no-silent-caps rule, is what
made it diagnosable in one look. A bare `scanned 0` would have read as "nothing matched" —
entirely plausible on a narrowed catalogue — and sent the investigation to the terms instead of
the parsing.

### ⚠ Two known limitations, neither blocking

1. **The tiers do not discriminate.** 1003 of 1005 autumn candidates score `hot`: the thresholds
   (hot ≥ 100, featured ≥ 70) were inherited from the deal scorer and are far too low for this
   curve, whose live scores span roughly 0–1126. The mechanism is unharmed because the read-time
   cap ranks by SCORE and takes the top 24 — the tier label is simply not earning its keep, and
   `auto-tier: featured` is not really the bar the cap is. Recalibrating needs a judgement about
   what counts as "hot" and now has a real distribution to use.
2. **`seasons` is not passed through onto search hits.** The filter works (that is what the rail
   needs) but a hit carries no `seasons` key, so a card cannot badge which season it came from —
   the positive-only passthrough every previous flag needed. No consumer exists yet; the rail
   resolves server-side from the database.

**Nothing is customer-visible yet, by design:** the Autumn page still uses its hand-picked
`byIds` rail. Switching it to `{"mode":"season","seasonKey":"autumn"}` is an admin edit.

## 16. Autumn page switched to the season rail (2026-08-27)

Applied through `POST /srv/private/admin/page/update` so it went through `PageConfigValidator`
rather than editing the row behind it. Both rich-text blocks preserved; only the goods rail
changed, from `mode=byIds` + 24 ids to `{"mode":"season","seasonKey":"autumn"}`.

Reversible: the previous config is at `/root/autumn-page-backup-2026-08-27.json` on the VPS.

Live: `/srv/page/season` serves the rail as `mode=byIds` + 24 resolved ids +
`resolvedFrom: "season"` + the original `seasonKey`, and all 24 products resolve **anonymously**
through `POST /srv/goods/batch` — the failure mode that once left every curated `byIds` rail
empty for logged-out shoppers. ⚠ That endpoint takes a BARE ARRAY body, not `{"ids":[...]}`;
the wrong shape answers errno 402, which reads like a permissions problem.

### ⚠ The terms are now the weak link, and they are data

Measured on the live 24: **20 contain an autumn term, 4 arrived through relaxed relevance**
(All-Season Sofa Cover, Digital Cable Organizer, Peeping Sticker Wall Decal, Tiered Wooden
Storage). Worse than the 4 misses, some *matches* are seasonally wrong — "Class A Cartoon-Printed
**Summer Cooling** Air-Conditioning Blanket" matched on `blanket`, and generic terms like `lamp`
pull ordinary desk lamps.

The mechanism is behaving exactly as specified; the seeded term list is doing the damage. That is
the design working — terms are configuration, so tightening them is
`PUT /srv/private/admin/insight/season-rules/autumn` and a re-run, with no deploy. Options, in
increasing order of intervention: drop the generic terms (`lamp`, `warm`), add the boosted
`category_ids` (currently empty), or require the term to actually appear in the title instead of
trusting relevance.

## 17. Follow-ups shipped (2026-09-04): term-anchored discovery, quantile tiers, `seasons` on hits

The three limits §15–16 recorded, closed in one goods-management change. No migration, no index
field change, no reindex, no searcher restart: the container is the whole deploy.

### 17.1 Discovery is anchored on the title, and exclusions ride the term list

`SeasonScoringService.discover` used to take every hit the index returned for a term. The index is
tuned for shoppers — it matches descriptions and category names, tolerates typos, and falls back to
relaxed and n-gram strategies when the exact query finds nothing — which is right for a search box
and wrong for a curator. Three rules now stand between a hit and the candidate set, each counted
and logged (`discardedRelaxed` / `discardedOffTitle` / `discardedExcluded`, also returned by
`POST /season-candidates/run` under `discovery`):

1. **A relaxed result set contributes nothing.** `SearchService` already reports `relaxed=true`
   when `meta.query_stage > 0`; the loop now reads it and skips the whole set — the exact query
   found nothing, so every hit is a guess. This is how "All-Season Sofa Cover" got in.
2. **The term must be in the title**, case-insensitive, at a word boundary, plural-tolerant
   (`blanket` matches "Blankets", not "blanketed"; `rug` does not match "drug"; `fall` does not
   match "waterfall"). A hit with no title is unverifiable and is dropped — fail closed.
3. **Exclusion terms**: an entry starting with `-` in the same `terms` JSON array vetoes any title
   containing it. `"-summer"` on autumn drops the "Summer Cooling Air-Conditioning Blanket" that
   matched on `blanket`. Exclusions are never searched for. Same column, same PUT — no migration and
   no new admin field. `SeasonTerms` is the pure class behind all three; `SeasonTermsTest` pins them.

⚠ The discard counts are HITS, not products: a product can be discarded under two terms.

### 17.2 Tiers are quantiles of the run's own curve

`hot ≥ 100 / featured ≥ 70` put 1003 of 1005 autumn candidates in `hot`. Absolute thresholds would
be wrong again after the next repricing (the score is margin-weighted and the anchor margin has
already moved 1.25 → 2.5). Now, per season per run: the top `hot-quantile` (10%) of scored
candidates are `hot`, the top `featured-quantile` (35%) are `featured` or better, the rest `watch`.
Rounding is up (a tiny run still has a hot row), ties at a cut are included, a zero score is always
`watch`, and an inverted pair collapses to `featured == hot` rather than an empty featured band. The
run logs and returns the cut scores (`tierCuts.hot` / `.featured`) so an operator can see where the
bar fell. Both knobs are env-backed (`LITEMALL_SEASONS_HOT_QUANTILE`, `…_FEATURED_QUANTILE`) with
explicit yml placeholders and prod compose passthrough.

**What this changes about publishing:** `auto-tier: featured` now means "the top 35% of what
scored", and the 24-cap still applies at read. Before, effectively everything published and the
cap alone chose. Nothing customer-visible moves unless a season scores fewer than ~69 candidates
(24 / 0.35), in which case fewer than 24 publish — by design, and visible in the run summary.
Scoring is now two passes (score everything, then tier), so no row is written before the cuts exist.

### 17.3 `seasons` rides search hits

`toGoodsListItem` emits `seasons: ["autumn", …]` on a hit when the field is non-empty — the same
positive-only rule as `coupon_flag` / `groupon_flag` / `eu_flag`; absent (pre-reindex) and empty
both mean no key. A bare single value is normalised to a one-element list. Read-time only.
**RAISED for gateway-api:** a season badge on `ProductCard` from `hit.seasons` (outside the overlay
priority chain, as the EU chip is — a season is context, not an offer).

### 17.4 Recommended autumn terms (data — applied on prod through the PUT, not by this deploy)

Measured on the first live rail: `warm` and `lamp` pull ordinary desk lamps and anything "warm
white"; `fall` matches "Anti-Fall"; the seed list has no exclusions at all. Proposed:

```json
PUT /srv/private/admin/insight/season-rules/autumn
{"terms":"[\"autumn\",\"fall\",\"cosy\",\"cozy\",\"blanket\",\"throw\",\"candle\",\"harvest\",\"pumpkin\",\"halloween\",\"knit\",\"wool\",\"rug\",\"curtain\",\"-summer\",\"-cooling\",\"-christmas\",\"-xmas\",\"-anti-fall\",\"-beach\"]"}
```

Then `POST /srv/private/admin/insight/season-candidates/run` and re-measure the live 24 through
`/srv/page/season` + `POST /srv/goods/batch` (BARE ARRAY body): the acceptance bar is 24 of 24
titles carrying an autumn term, the cooling blanket gone, and the run summary showing tier cuts
that split the set. Reversible: PUT the previous list back (it is in V64) and re-run.

Winter/spring/summer have the same weakness (`gift`, `storage`, `fan`, `outdoor` are generic) and
the same fix; tune them before their page is activated, not after.


### 17.5 Deployed 2026-09-05 — and what the first live run measured

Merged to master `3e784648d` (suite on the merged tree 596 run / 0 failures / 8 skipped) and
deployed as the goods-management container alone (built on the VPS from that commit, healthy in
~20 s, `SeasonTerms` present in the running jar with `LitemallProductIndexingService` as the positive
control, both quantile knobs visible inside the container). No migration, no reindex, no searcher
restart — as §17 said.

The §17.4 autumn list was then applied through `docker-compose/season-tune.sh terms autumn
season-terms-autumn-2026-09.json` (backup of all four rules first:
`/root/season-rules-backup-20260905T001924Z.json` on the VPS; `season-tune.sh restore autumn <backup>`
puts the seed list back) and the scorer run once by hand.

**Before → after, measured on the live rail through `/srv/page/season` + `POST /srv/goods/batch`:**

| | before | after |
|---|---|---|
| rail items with an autumn term in the title (new list) | 14 / 24 | **24 / 24** |
| items hit by an exclusion | 1 (Christmas icicle lights) | 0 |
| off-season passengers (all-season sofa cover, coffee mug, cable organiser, bedside lamps) | 6 | 0 |
| search hits carrying `seasons` | 0 / 20 | **20 / 20** |

Run summary (all four seasons score on every run): autumn scanned 429, scored 428, published 150
(the top 35%), 126 dropped by the cap, **discovery discarded 914 off-title hits + 44 excluded hits,
0 relaxed sets**, tier cuts hot 773.3 / featured 631.67 — the set splits, which it never did under
the absolute thresholds. Spring 679/679/238, summer 420/416/146, winter 518/517/181, each with
hundreds of off-title discards and cuts in the 590–720 band. Rejections stay marginal (1 UNCOSTED,
4 UNAVAILABLE, 1 OUT_OF_BAND). Storefront smoke 200 across shell, sitemap, robots, PDP, search,
season page; live `?seasons=` totals autumn 24 / winter 25 / spring 24 / summer 24.

Observed, recorded not hidden:
- The 200-hit scan limit is now the binding bound on broad terms: `wool` had 1,250 hits and 200 were
  considered, `fall` 552 → 200 (both logged). The candidate set is therefore "the top 200 by relevance
  per term that also carry the term in the title", not the full population. Fine for a 24-item rail;
  raise `LITEMALL_SEASONS_SCAN_LIMIT` before asking the field to be exhaustive.
- Three "Air-conditioning Blanket" products are on the rail — a summer article under a title that
  does not contain `cooling` or `summer`. `-air-conditioning` is the one-line data fix if the curator
  wants them out; not applied here because it was not in the approved list.
- Winter returns 25 members through search where the other seasons return 24: one row more than
  the cap, from an earlier run's membership the later run did not unpublish. Cosmetic on search;
  the page resolver still caps at 24.
- Winter/spring/summer still run on their seed terms (`gift`, `storage`, `fan`, `outdoor`) — tune
  each before its page is activated, exactly as §17.4 says.
