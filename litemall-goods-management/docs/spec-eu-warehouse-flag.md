# `eu_flag` — EU-warehouse visibility in search (Wave 27)

**Status:** goods-management half BUILT. gateway-api half NOT started — it codes
to this document.

## 1. What was measured, before anything was built

Live on prod, 2026-08-18:

| Sample | Carrying a measured EU-stock reading |
|---|---|
| 40 products, default relevance order | **0** |
| 30 **newest arrivals** | **16 — all DE** (230 units ×9, 20 units ×7) |

The Wave-26 DE acquisition targets (`country-code: DE` on the anchor leaves) are
working: what arrives now is largely German-warehoused. The standing catalogue
mostly predates them. Treat the 53% as directional — those 16 fall into two
arrival batches with identical unit counts.

The authoritative split is `GET /srv/private/admin/insight/eu-sourcing`, which
computes survival over the **probed** count, never over on-sale, and returns
`null` rather than `0` where nothing has been probed.

## 2. Why not the brand section

The brand section answers *who sells this*. Wave 25 was explicit that a supplier
must never render as a consumer brand, and provider-created rows stay
`display_enabled = 0` until an admin curates them — so most goods have no brand
row at all. Warehouse location is a **per-SKU, per-measurement stock property**
that changes without the seller changing. Filing it under brand would
misattribute and go stale.

`eu_flag` instead joins `deal_flag`, `coupon_flag` and `groupon_flag` — three
existing instances of exactly this pattern.

## 3. What the flag means

`eu_flag = 1` — the last inventory probe of this product found stock in an EU
warehouse (V61 `eu_stock_num > 0`).

`eu_flag = 0` — **not known to hold EU stock.** This lumps together "probed,
found none" and "never probed". Those are different facts, and the flag cannot
tell them apart. That is fine for a *filter* (both are products we cannot
honestly advertise as EU-stocked) and would be a lie as a statement about the
catalogue — which is why the per-category report keeps them separate.

Local (non-CJ) goods have no pid and are always 0.

**A reading is a measurement with a timestamp, not a promise.** It can be stale
by the time a customer orders, and EU stock does not by itself guarantee EU
dispatch for a given order. Customer-facing copy must read as a measurement —
"in stock in Germany at last check" — never as a delivery guarantee.

## 4. Implementation

| Piece | Where |
|---|---|
| `selectEuStockedPids` | `LitemallCjProductMapper` + XML (litemall-db, hand-edited together) |
| `EuStockSignalResolver` | 60s TTL snapshot of EU-stocked pids; fail-soft |
| `ProductDocument.euFlag` | `@JsonProperty("eu_flag")`, always-emit 0/1 |
| indexing wiring | `LitemallProductIndexingService`, keyed on `cj_pid` |
| hit passthrough | `SearchService` — positive-only, like the other badges |
| index field + facet | `docker-compose/application.indexer-service.yml`: `eu_flag` (Result + Filter) **and both dynamic-field regex whitelists** |

The query returns **pid strings only**. `queryAllLive()` would drag every
`variants_json` and `detail_html` through memory to answer a yes/no question.

No migration: V61 already stores the reading. No filter-side code either —
`SearchService` forwards all candidate filters and OCS acts on those matching a
configured facet field, so `?eu_flag=1` works once the index config ships.

## 5. Deploy discipline (MAIN session)

The Wave-19/21 lesson applies verbatim, and skipping it is what makes a new flag
look broken:

1. Recreate the **indexer** (it owns the field/facet config).
2. Deploy goods-management.
3. **Full reindex.**
4. **Restart the searcher AFTER the reindex** — new-field resolution happens at
   searcher start; restarting first leaves `eu_flag=1` returning nothing.

Coverage grows with the enrichment rotation, so the flag's reach increases night
over night rather than being complete on day one.

## 6. gateway-api half (NOT started)

- "Ships from the EU" badge on cards, riding `eu_flag: 1` in the hit exactly as
  the coupon and group-buy badges do. Absent field ⇒ no badge, never a negative.
- A filter toggle passing `eu_flag=1` to `/srv/search`.
- Copy must state the measurement, not a delivery promise (§3).
- The PDP badge already exists (`euStock` on the detail payload, present only
  when a real measurement found units > 0).
