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

**Recommendation: €5.00** — removes 429 of 2,286 anchor SKUs (19%), leaving
1,857, which is far more than a curated storefront needs, and clears the
sub-€1 tail (a €0.25 item against €6.93 flat freight is a guaranteed loss and a
Merchant Center review risk). €10 removes a third of the anchor; defensible if
you only ever intend to sell ad-eligible SKUs, but it also deletes the organic
basket-builders. **The value is the user's call.**

Set `LITEMALL_GOODS_PRICE_FLOOR` (default 0 = OFF) and recreate the container.
**Order matters: apply the margin FIRST and let one nightly cycle land**, or the
floor will judge anchor goods at their old 1.25 prices and off-sale ~2× too many.

**Rollback:** set the floor back to 0; goods return on the next cycle (rows and
data were never deleted).

## Step 3 — narrowing to the anchor

Off-sale the non-anchor L1 subtrees via the existing retirement/off-sale path —
staged and logged, never a bulk UPDATE. Expect ~12k of ~15k rows.

**Reversibility is the governing rule** (user intent 2026-08-13: run one anchor
now, take the other categories back later):

- `is_on_sale` FLIP only — never a delete. Rows stay in `litemall_goods`,
  `litemall_goods_product`, `litemall_cj_product`.
- **Do NOT prune `catalog-targets`** — the CJ sync keeps mirroring all 14 L1s.
  Pipeline broad, storefront narrow.
- Wave 14's is_on_sale-preserving promote is load-bearing: verify a full nightly
  cycle does not resurrect off-saled goods.
- Sitemap, `meta-catalog.csv` and the OCS index shrink on the next nightly cycle.

**Verify:** spot-check one category flips back ON and reappears in the index on
the following cycle. That check is the whole reversibility guarantee — do not
skip it.

**Watch for:** the storefront must not render empty category tiles or dead links
once 13 L1s go quiet (gateway-api half, raise separately). And the feed/sitemap
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
