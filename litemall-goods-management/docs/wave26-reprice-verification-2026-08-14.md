# Wave 26 — anchor reprice verification, 2026-08-14

Run against the live public feed `https://trovemo.com/meta-catalog.csv` per
`wave26-verify-reprice-brief.md`. Fetched 08:37 CEST with a browser User-Agent
(Cloudflare 403s python-urllib); 17,398 rows, real CSV, 14 columns.

## Verdict — **LANDED**

The 2.5 margin override applied 2026-08-13 12:12 UTC to the two anchor L1s has
been carried into live prices by the overnight promote.

| set | rows | median | in €25–80 | baseline (2026-08-12) |
|---|---:|---:|---:|---|
| **ANCHOR** (Google `Home & Garden` + `Hardware`) | 2,701 | **€16.90** | **23.5%** (634) | 2,286 / €9.13 / 17.5% |
| **CONTROL** (all other categories) | 14,697 | **€6.63** | 7.3% (1,076) | 12,972 / €6.63 / 7.4% |

Anchor mean €67.95 (long right tail); control mean €13.18.

### Why this is a real reprice and not a measurement artefact

- **The control did not move.** Median €6.63 → €6.63 exactly, band 7.4% → 7.3%.
  A site-wide change or a stale cached feed would have moved both sets or
  neither; only the two overridden categories moved.
- The feed is **not** a cached pre-change copy: row counts grew (15,258 →
  17,398) and the anchor numbers differ from the baseline.
- Anchor median rose **1.85×**, slightly under the modelled 2.0×. Expected: the
  window added 415 anchor rows (2,286 → 2,701) priced at the new margin only if
  costed, and uncosted goods do not reprice at all
  (`repriceForCategory` returns early on a null/zero cost).
- The €25–80 band landed at **23.5%** against a predicted 24.0%.

### Split within the anchor

| set | rows | median | p25 | p75 | in €25–80 | under €5 |
|---|---:|---:|---:|---:|---:|---:|
| Home & Garden | 1,895 | €13.18 | €5.10 | €47.60 | 18.8% | 452 |
| Hardware | 806 | **€28.90** | €12.00 | €62.72 | **34.5%** | 50 |

Hardware is now the strongest half of the cluster — a third of it sits in the
€25–80 band, and its median alone clears a €15–40 CAC. This supports the
2026-08-13 decision to promote Home Improvement to equal billing with Home,
Garden & Furniture rather than treat it as an optional second category.

## Floor step — unblocked, with a correction

502 of 2,701 anchor SKUs (18.6%) are still priced under €5, tracking the
runbook's dry-run projection of 429 of 2,286 (19%) closely. The €5.00 floor
decision holds at post-reprice prices.

⚠ **The floor could not have been set before today regardless of sequencing:**
`litemall.goods.price-floor` shipped in `556abad5f`, which was committed on
`fix/goods-management` and never merged to master or deployed. The image running
in production (built 2026-08-10 01:26 UTC) has no code that reads
`LITEMALL_GOODS_PRICE_FLOOR`; setting the env var would have looked correct and
done nothing. Merged to master and deployed 2026-08-14.

## Notes

- No production data was modified by this verification.
