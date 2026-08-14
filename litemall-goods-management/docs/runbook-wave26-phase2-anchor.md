# Wave 26 Phase 2 — deliverables 1 & 3 RUNBOOK (deploy-time, MAIN session)

Deliverables 5, 4 and 2 are code and are merged. **1 (margin 2.5) and 3
(narrowing) are operational actions against a live catalogue, not worktree code**
— the spec deliberately built both on existing machinery, so there is nothing to
implement, only to run.

## Why these were NOT executed on dev

Measured 2026-08-13, dev DB:

| L1 | on sale | costed |
|---|---:|---:|
| Home, Garden & Furniture | 546 | **0** |
| Home Improvement | 520 | **0** |
| (whole dev catalogue) | 9,642 | 4 |

`CjProductPromotionService.repriceForCategory` returns early when
`cost == null || cost.signum() <= 0`. With zero costed goods in the anchor
cluster, applying the margin override on dev reprices **exactly nothing** — the
simulate endpoint would report `goodsCount: 0`. Running it would demonstrate
nothing and could be mistaken for a passing acceptance later, which is worse than
not running it. Dev prices are also stale pre-EUR-flip (Wave 24 converted prod
only), so dev cannot stand in for prod here at all.

Both steps therefore belong to the MAIN session at deploy, against prod data.
A worktree must not touch the prod VPS or its DB.

---

## Step 1 — anchor margin 1.25 → 2.5

⚠ **Category ids are per-database.** Dev's anchor roots are `1036143`
(Home, Garden & Furniture) and `1036495` (Home Improvement); prod's will differ.
Resolve them on the target DB first:

```sql
SELECT id, name FROM litemall_category
WHERE name IN ('Home, Garden & Furniture','Home Improvement')
  AND pid = 0 AND deleted = 0;
```

For EACH resolved L1 id, **simulate before applying** and record the output:

```
GET  /srv/private/admin/insight/categories/{id}/simulate?margin=2.5
PUT  /srv/private/admin/insight/categories/{id}/margin   {"margin": 2.5}
```

Bounds are 1.05–3.0, so 2.5 is in range. Repricing lands at the next nightly
promote, from stored cost — no bulk UPDATE, no separate reprice job.

**Expected, from the live feed (2,286 anchor SKUs, 2026-08-12):** median
€9.13 → €18.26; €25–80 band 17.5% → 24.0% (≈549 SKUs). If the live simulate
diverges materially from that, STOP — it means cost data moved and the margin
decision should be re-checked before applying.

**Verify:** `goodsCount` non-zero and roughly matching the on-sale anchor count
(a near-zero count means costs are missing and the override will do nothing);
`marginPct` coherent after the nightly cycle; deal floors, coupon guard and
margin-basis still pass — they are ratio/cost-based and must need no change.

**Rollback:** `DELETE /srv/private/admin/insight/categories/{id}/margin` restores
the global 1.25; prices revert on the next nightly promote.

## Step 2 — price floor (value decision FIRST)

Dry run computed from the LIVE prod feed (15,258 rows; anchor = Google
`Home & Garden` + `Hardware`, 2,286 rows). Counts are goods that would be
**off-saled**:

| floor | whole store today | anchor today | whole store after 2.5× | **anchor after 2.5×** |
|---:|---:|---:|---:|---:|
| €1 | 1,056 (6.9%) | 151 | 938 (6.1%) | **33** |
| €2 | 2,420 (15.9%) | 321 | 2,250 (14.7%) | **151** |
| €5 | 5,877 (38.5%) | 808 | 5,498 (36.0%) | **429** |
| €10 | 10,011 (65.6%) | 1,199 | 9,620 (63.0%) | **808** |

Read the **"anchor after 2.5×"** column — it is the only one that matters once
narrowing has run, and the floor must be evaluated against post-reprice prices.
Anchor after 2.5×: min €0.06, p10 €3.18, median €18.26.

**DECIDED (user, 2026-08-13): `LITEMALL_GOODS_PRICE_FLOOR=5.00`** — removes 429
of 2,286 anchor SKUs (19%), leaving 1,857, and clears the sub-€1 tail (a €0.25
item against €6.93 flat freight is a guaranteed loss and a Merchant Center review
risk).

⚠ **NOT YET SET IN PROD, and it must not be set before the margin lands.** As of
2026-08-13 the 2.5× override has not been applied, so a floor of €5 today would
judge anchor goods at their OLD 1.25× prices and off-sale **808 anchor SKUs
instead of 429** (5,877 storewide instead of 5,498) — nearly double, while
looking exactly like a correct run. Apply the margin, let ONE nightly cycle land
the reprice, verify the new prices, THEN set the floor.

Set `LITEMALL_GOODS_PRICE_FLOOR` (default 0 = OFF) and recreate the container.
**Order matters: apply the margin FIRST and let one nightly cycle land**, or the
floor will judge anchor goods at their old 1.25 prices and off-sale ~2× too many.

**Rollback:** set the floor back to 0; goods return on the next cycle (rows and
data were never deleted).

## Step 3 — narrowing to the anchor

**EXECUTED ON PROD 2026-08-14.** 9,078 goods off-saled, 2,200 kept
(Home, Garden & Furniture 1,444 + Home Improvement 756). Executor summary:
`due 9078, executed 9078, skippedLiveDeal 0, goodsMissing 0, lostRace 0`.

⚠ **The path this step assumed did not exist.** "Off-sale the non-anchor L1
subtrees via the existing retirement path" was not runnable: retirement
candidates are only ever created by `RetireCandidateScorer` (CJ-unavailability
streak) or `RetirementGovernor` (weakest-first against the catalogue target),
both catalogue-wide and neither taking a category; the approve endpoint needs a
pre-existing `proposed` row per goods id; and no bulk off-sale endpoint existed
anywhere. The missing SELECTION step was built as `CatalogNarrowingService`
(commit `da6dbafb2`) — the flip itself still rides the existing
`RetirementExecutor`, so a narrowed good is indistinguishable downstream.

Commands (all via `docker-compose/wave26-anchor.sh`, on the VPS):

```
./wave26-anchor.sh narrow-preview   # READ-ONLY per-L1 split
./wave26-anchor.sh narrow-dry       # READ-ONLY real walk + counts
./wave26-anchor.sh narrow-apply     # stages approved rows (nothing customer-visible yet)
./wave26-anchor.sh narrow-execute   # the flip: off-sale + per-goods reindex
./wave26-anchor.sh narrow-restore <L1 id>   # put one category back
```

`narrow-apply` and `narrow-execute` are deliberately separate: staging is
inspectable and reversible before anything reaches the storefront.

Expect ~9k of ~11k rows (not the ~12k of ~15k this runbook first estimated —
the €5 floor had already removed 6,120 by the time narrowing ran).

**Reversibility is the governing rule** (user intent 2026-08-13: run one anchor
now, take the other categories back later):

- `is_on_sale` FLIP only — never a delete. Rows stay in `litemall_goods`,
  `litemall_goods_product`, `litemall_cj_product`.
- **Do NOT prune `catalog-targets`** — the CJ sync keeps mirroring all 14 L1s.
  Pipeline broad, storefront narrow.
- Wave 14's is_on_sale-preserving promote is load-bearing: verify a full nightly
  cycle does not resurrect off-saled goods.
- Sitemap, `meta-catalog.csv` and the OCS index shrink on the next nightly cycle.

**Verify:** spot-check one category flips back ON and reappears in the index.
That check is the whole reversibility guarantee — do not skip it.

**DONE 2026-08-14, and it found a real bug.** Restoring Phones & Accessories
(1036575) put back exactly its 132 goods — on-sale 2,200 → 2,332, index 2,332
docs, other 8,946 narrowed rows correctly untouched. But an immediate re-narrow
reported `staged 0, alreadyDecided 132`: the unique key is goods_id + day, and
`restored` was being read as a standing decision, so reversibility worked in only
ONE direction until midnight. Fixed in `4560b5d4d` — `restored` is a policy
reversal, not a decision to keep goods on sale. The honest counts are what
surfaced it; a silent success would have left a category stuck on sale.

**Watch for:** the storefront must not render empty category tiles or dead links
once the other L1s go quiet. This was assigned to a gateway-api half that was
never built, so it was fixed server-side instead (`9db298ccf`):
`/srv/catalog/all` and `/first-categories` now drop roots with no on-sale goods,
using counts they already computed for ordering. ⚠ Those counts are memoized for
5 minutes (`CatalogGoodsCountService.TTL_MS`) — right after a narrowing the nav
still shows the old categories. That is the cache, not a failure; re-check after
five minutes before investigating. And the feed/sitemap
shrinking by ~80% is expected, not an incident — tell whoever watches Search
Console before it happens.

---

## Order of operations

1. Resolve prod anchor category ids.
2. Simulate margin 2.5 on both, record, apply.
3. Let ONE nightly cycle land the reprice.
4. Decide the floor value from the post-reprice reality, set it, recreate.
5. Let one cycle land, verify the off-sale count matches the dry run.
6. Narrow to the anchor, staged.
7. Prove a category flips back.

Steps 2, 4 and 6 each change what customers see and are individually reversible.
Doing 4 before 3 completes, or 6 before 2, produces wrong counts — not damage,
but a misleading picture that invites the wrong next decision.
