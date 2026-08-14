# Verification brief — did the Wave 26 anchor reprice land?

Self-contained instructions for an automated run. Assume ZERO prior context and
**no access to the production server** (no SSH, no database). Verify only through
the PUBLIC product feed. Finish with a clear verdict.

## Background

On **2026-08-13 12:12 UTC** a margin override of **2.5** (up from the global
1.25) was applied to two anchor categories on the live store trovemo.com:
`Home, Garden & Furniture` (id 1036143) and `Home Improvement` (id 1036495).

Repricing is applied by the **nightly promote** (03:00 sync → 03:30 enrich),
which also regenerates the public feed. Prices in those two categories should now
be roughly **DOUBLE**; everything else should be unchanged.

Full context: `runbook-wave26-phase2-anchor.md` in this same directory.

## Step 1 — fetch the feed

```bash
curl -s -A 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36' \
  https://trovemo.com/meta-catalog.csv -o feed.csv
```

⚠ A **browser User-Agent is mandatory** — Cloudflare silently 403s
python-urllib. Confirm the file is a real CSV with thousands of rows before
trusting anything computed from it.

## Step 2 — measure

Parse with Python's `csv` module. The `price` column holds strings like
`"12.34 EUR"` — split on whitespace and take `[0]`.

Split rows into two sets by `google_product_category`:

- **ANCHOR** — starts with `Home & Garden` or `Hardware`
- **CONTROL** — everything else

For each set report: row count, median, mean, and the share priced €25–80.

## Step 3 — compare against this pre-change baseline (measured 2026-08-12)

| set | rows | median | in €25–80 |
|---|---:|---:|---:|
| ANCHOR | 2,286 | €9.13 | 17.5% |
| CONTROL | 12,972 | €6.63 | 7.4% |

Expected if the reprice landed: **ANCHOR median ≈ €18** (roughly double) and the
€25–80 share rising toward ~24%. **CONTROL should be essentially unchanged** —
that is the control, and it is what distinguishes a real reprice from a
site-wide change or a measurement error.

## Step 4 — verdict

State plainly one of:

- **LANDED** — anchor median roughly doubled, control unchanged.
- **NOT LANDED** — anchor median still ≈ €9.13.
- **INCONCLUSIVE** — anything else, including the cases below.

Honesty requirements, in order of how easily each one fools you:

1. **Cloudflare may serve a CACHED feed.** If the numbers are *identical* to the
   baseline — same row count and same median — that is more likely a stale
   cached copy than a failed reprice. Say so; do not report NOT LANDED.
2. **If CONTROL also moved**, something broader changed. Do not report LANDED;
   report INCONCLUSIVE and describe what moved.
3. **The catalogue grows daily**, so row counts will not match the baseline
   exactly. A modest count difference is normal and is not evidence either way.
4. Report the numbers you measured, not the numbers you expected.

## Step 5 — leave a durable record

Write your findings to `docs/wave26-reprice-verification-<UTC-date>.md` in the
repo (create it), including the measured table, the verdict, and anything
surprising. Commit it on a branch and push. Do NOT merge to master, and do NOT
change any other file — a verification run has no business editing the system it
is verifying.

## What happens next (do NOT attempt any of it)

If LANDED, a human then sets `LITEMALL_GOODS_PRICE_FLOOR=5.00` and recreates the
goods-management container. That step needs production access you do not have.
Simply note in your report whether the floor step is now unblocked.
