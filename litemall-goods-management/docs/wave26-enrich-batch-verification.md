# Verifying the raised CJ enrichment batch (100 → 400)

Set up 2026-08-14. Scheduled cloud routine `trig_011Tu82KCYnQQUywzzHab4zo`, one-shot
at **2026-08-15 05:00 UTC** (07:00 CEST).

## The constraint that shaped this

A cloud routine has **no SSH and no database access**, so it cannot read the line
that would answer the question directly:

```
CJ detail+inventory enrichment: N enriched, N failed (batch requested 400, due N)
```

That line is in `/app/logs/log.log` inside the prod goods-management container and
needs a session on this machine. ⚠ It is NOT in `docker logs` by morning — the
json-file driver rotates at 10 MB × 3 and a nightly run overruns that easily.

## ⚠ The trap: meta-catalog.csv cannot answer this

The obvious public signal is the duplicate-description count in
`https://trovemo.com/meta-catalog.csv`. It is **useless for this question**, because
the feed is rebuilt inside `CjCatalogRefreshTask.refreshCjCatalog` at **03:00**, and
enrichment runs at **03:30** — the feed is regenerated BEFORE the run it would be
reporting on. A check the next morning reads pre-enrichment data and concludes
"nothing happened". The first feed reflecting a given night's enrichment is the
following day's.

`GET /srv/goods/meta/{id}` is the right probe instead: it reads live tables
(5-minute TTL cache) and reflects enrichment within minutes.

## Probe design

45 products, drawn by replaying the enrichment queue's exact `ORDER BY`
(`selectForEnrichment`: on-sale first, never-enriched, views, listed_num,
enriched_time, add_time) and keeping only ones whose description repeated the title.

| group | queue ranks | meaning |
|---|---|---|
| A | 1–100 | processed even at the OLD batch of 100 |
| B | 150–395 | processed ONLY if the batch is really 400 — **the discriminator** |
| C | 500–880 | **control** — processed at neither size |

Baseline measured 2026-08-14 20:05 UTC, before the run:

| group | duplicate | distinct |
|---|---:|---:|
| A | 6 | 9 |
| B | 9 | 6 |
| C | 14 | 1 |

Verdict rule: B falling against a static C is the evidence. A alone is weak, because
a separate **on-demand** path enriches whatever customers view, independent of the
nightly batch — which is exactly why A and B were already partly distinct at
baseline while the low-traffic control was 14/15 duplicated. If C also improves, the
discriminator is void and the result is inconclusive.

Expect some enriched products to stay duplicated: their supplier detail body is
images with no prose (29 of the 708 known duplicates are that case). A large drop is
the signal, not a clean zero.
