# Wave 26 Phase 1 — EU-warehouse sourcing truth (FROZEN SPEC)

Status: **Phase 1a probe built** (`cj-eu-warehouse-probe.sh`, repo root) —
Phase 1b (this spec) is the goods-management half, assigned once Wave 25.1 lands.

Phase 1 is the Wave-26 **gate**: it decides whether *Home, Garden & Furniture*
can anchor a DE/FR/DK/SE storefront. Nothing in Phases 2–5 may start before its
answer. Phase 1 changes NO catalogue state — its deliverable is a report.

---

## Verified CJ API facts (2026-08-13 — do not re-derive)

Source: CJ API 2.0 docs + this module's DTOs. Verified against the code, not assumed.

1. **`/product/list` supports the filters the strategy advice assumed** —
   `countryCode` ("filter products with inventory in specified countries"),
   `verifiedWarehouse` (1 = verified, 2 = unverified), `startInventory` /
   `endInventory`, `deliveryTime` (24 | 48 | 72 hours), `minPrice` / `maxPrice`,
   `productType`, `supplierId`, `orderBy` (createAt | listedNum), `sort`.
   **We pass none of them.** `CJProductClient` sends only `pageNum`, `pageSize`,
   `categoryId` (`CJProductClient.java:89-93`).
2. **Category ids exist ONLY at leaf level.** `CJCategoryDataResponse` gives
   `categoryFirstName` and `categorySecondName` as *names with no id*; only
   `CategoryThird` carries `categoryId`. So `/product/list?categoryId=` can only
   be filtered by leaf, and any per-L1 figure is an aggregate over leaves —
   never a single call. ~540 leaves exist.
3. **`data.total` is the full match count** (`CJProductData.total`), so a survival
   count costs ONE `pageSize=1` call — never page through rows to count.
4. **Warehouse country is already fetched and thrown away — this is the free
   seam.** `CjDetailEnrichmentService.stockOf(vid)` (~:289) calls
   `getInventory(vid)`, receives `List<CJInventoryData>` where every element
   carries `countryCode`, `areaId`, `areaEn` and `storageNum`, and collapses the
   lot to `sum(storageNum)`. **Capturing the per-country split at this line costs
   ZERO additional CJ calls** — the quota is already being spent.
5. `/product/list` and the inventory endpoints are **separately rate-limited**
   (`CJProductClient` and `CJProductInventoryClient` hold their own
   resilience4j limiters). CJ's daily API POINTS budget is shared account-wide
   with the prod nightly syncs — exhaustion answers errno `16900500`.
6. CJ inventory endpoint 3.3 queries **by pid** and returns product-level and
   variant-level arrays including `verifiedWarehouse`. We only implement 3.1
   (by vid). Not needed for Phase 1 — noted so nobody adds a per-variant loop
   when a per-product call would do.

## What is NOT available

`litemall_cj_product` has **no warehouse or country column**, and
`attributes_json` holds only `{Material, Weight, Unit}`. Nothing in the local
schema can answer the survival question today.

---

## Phase 1a — CJ supply survival (RUN 2026-08-13 — see RESULTS below)

`cj-eu-warehouse-probe.sh` at repo root. Read-only; auth + getCategory +
`1 + N` paced `/product/list` calls per sampled leaf (default `COUNTRIES=DE,US`,
`LEAVES_PER_L1=6`). Reports per L1: `CJ TOTAL` and one independent column per
country, with the sampled-leaf denominator visible. Creds are prod-only.

⚠ The design in the first draft of this spec was WRONG and the script has been
corrected: `countryCode` takes ONE code (no comma lists, 4-char max), country
columns cannot be summed, and `verifiedWarehouse=1` is far too strict to use as
the default filter. See "Probe gotchas" at the end.

This measures **CJ's supply**, not our catalogue. Both numbers are needed and
they answer different questions:

| Question | Answered by |
|---|---|
| Can this category be *sourced* from EU warehouses at all? | 1a (supply) |
| How much of our existing 15k catalogue survives an EU filter? | 1b (below) |

## Phase 1b — our catalogue's survival (goods-management)

1. **Capture at the free seam.** In `CjDetailEnrichmentService`, keep the
   per-country breakdown that `stockOf` currently discards: total stock
   (unchanged behaviour), EU stock (configured country set), and the distinct
   warehouse country codes seen. Fall-back semantics are UNCHANGED — an empty
   inventory response still yields `config.getDefaultStock()` and must NOT be
   recorded as "0 EU stock" (absent ≠ zero; that distinction is the whole report).
2. **V61** (goods-management scope; prod applied through **V60** — check
   `flyway_schema_history` immediately before first boot):
   `litemall_cj_product` gains `eu_stock_num` INT NULL and
   `warehouse_countries` VARCHAR(255) NULL. Both NULL = "not yet probed", which
   is distinct from 0 = "probed, no EU stock". Hand-edit entity + mapper XML
   together (shared-module discipline; ⚠ the recurring missing-mapper-XML gotcha).
3. **Coverage probe log**, mirroring the Wave-25 supplier coverage probe in the
   same class: per batch and cumulative, the %-populated of `eu_stock_num`.
   The report is only as good as enrichment coverage, and coverage grows with the
   rotation — say so in the numbers rather than implying a census.
4. **Report endpoint** `GET /srv/private/admin/insight/eu-sourcing` →
   `{countries:[...], categories:[{categoryId, name, onSaleCount, probedCount,
   euStockedCount, euSurvivalPct, avgEuStock}]}`, sorted `euSurvivalPct` desc.
   `euSurvivalPct` is computed over `probedCount`, **never** over `onSaleCount`,
   and `probedCount` ships in the payload so the denominator is always visible.
   `null` (never 0) where nothing is probed yet. Errno envelope as everywhere.
5. **Sync filtering stays OFF.** Config `litemall.cj.eu-warehouse.*`
   (env-backed, `enabled` DEFAULT FALSE = today's behaviour) may thread
   `countryCode` + `verifiedWarehouse=1` through `CJProductClient`, but flipping
   it on is a Phase-2 decision made *after* the report is read. Phase 1 must not
   change what the catalogue contains.

## Landmines

- **No new CJ call loops.** Ride the existing enrichment rotation (shared daily
  points quota). A standalone sweep over 15k products would exhaust it and take
  the prod nightly syncs down with it.
- Never rebuild what is live: head injection, JSON-LD, sitemap, robots.txt,
  funnel instrumentation, EU payment plumbing (Waves 13/22/24/25).
- `andLogicalDeleted()` is inverted across the domain — bind the literal.
- Absent ≠ zero, everywhere in this wave.

## Acceptance (dev, through :9000/:8090)

- V61 applied at boot; `eu_stock_num` / `warehouse_countries` NULL for unprobed
  rows and populated for rows the rotation has since touched.
- An enrichment batch logs coverage % with real counts; total-stock behaviour is
  byte-identical to before (the fallback path especially).
- `/insight/eu-sourcing` returns per-L1 survival with `probedCount` denominators
  and `null` — not 0 — for categories with no probed rows.
- `enabled=false` (default) leaves `/product/list` calls byte-identical; flipping
  it on adds exactly `countryCode` + `verifiedWarehouse` and nothing else.
- Module tests green with real "Tests run:" counts (⚠ root pom skips `mvn test`
  and surefire misses `@Nested` — read the count, don't trust "BUILD SUCCESS").

---

# Phase 1a RESULTS (measured 2026-08-13, prod CJ account)

Raw rows: `data-wave26-cj-warehouse-survival.csv` (84 leaves sampled of 540,
6 per L1; 14 rows still carry an incomplete cell after one gap-fill pass and are
excluded from their category's totals — the LVS column shows the real denominator).

## Where CJ's stock sits

| L1 | leaves | CJ total | DE | DE% | US% |
|---|---:|---:|---:|---:|---:|
| Home Improvement | 4 | 7,612 | 394 | **5.18%** | 56.92% |
| Computer & Office | 6 | 2,670 | 65 | 2.43% | 6.74% |
| Home, Garden & Furniture | 5 | 9,610 | 91 | 0.95% | 2.59% |
| Sports & Outdoors | 6 | 5,676 | 21 | 0.37% | 1.80% |
| Health, Beauty & Hair | 5 | 4,328 | 5 | 0.12% | 6.38% |
| Toys / Electronics / Phones / Bags / Jewelry / Clothing / Pets / Autos | — | — | ≤5 | ≤0.05% | — |
| **ALL SAMPLED** | | **201,089** | **594** | **0.30%** | 3.03% |

Verified DE pockets (each checked against its own leaf total):
Garden Tools 365/5,529 · Office & School Supplies 60/2,254 · Curtains 51/733 ·
Tools Storage 26/845 · Diamond Painting 20/3,117 · Kitchen Knives 18/2,842 ·
Musical Instruments 17/360.

## Findings

1. **Germany is CJ's only EU warehouse.** ES/CZ/IT/NL/BE/PL/SE/DK/AT and "EU"
   all return 0 on a leaf holding 13,056 products. GB (34 there) is post-Brexit
   and does not serve EU customers without customs.
2. **EU stock is not a property of a category — it is a property of a few
   hundred individual SKUs.** 25 of 84 sampled leaves have any DE stock; most
   are single digits. Catalogue-wide, the crude extrapolation is **~3,800
   DE-stocked products** across all 540 leaves (84/540 sample, leaf sizes vary
   widely — treat as an order of magnitude, not a count).
3. **You cannot filter the existing 15k catalogue into an EU-stocked version of
   itself.** At a 0.30% storewide DE share it would leave roughly 40 products.
   EU sourcing has to be a deliberate *acquisition* of DE-stocked SKUs, not a
   filter over what we already mirror.
4. **Home Improvement leads on DE density but is concentrated in ONE leaf**
   (Garden Tools = 365 of its 394). Home & Garden's 91 is spread across more
   leaves. Treated as one merchandising cluster, home/garden/tools holds most of
   the DE stock that exists.
5. This **converges with the price analysis**: Hardware (≈ Home Improvement) had
   both the highest €25–80 in-band share (23.7%) and the highest median price
   (€15.70) of any category in the live feed. Best price band and best EU stock
   are the same cluster.

## Consequences for the wave

- The gate does NOT invalidate the anchor — it narrows it. The anchor cluster
  should be **home / garden / tools**, and Home Improvement deserves equal
  billing with Home & Garden rather than being the "optional second".
- Phase 2's narrowing stands, but its sourcing step changes: use
  `/product/list` with `countryCode=DE` + `minPrice`/`maxPrice` to ACQUIRE
  in-band DE-stocked SKUs, rather than filtering the existing catalogue.
- Phase 1b (our own catalogue's DE survival) is now a *smaller* question — the
  expected answer is "almost none" — but still worth building, because it is the
  only way to know which existing SKUs can carry a fast-delivery promise.
- An honest delivery promise cannot be storewide. It is per-SKU, and only ~3,800
  candidates exist account-wide.

## Probe gotchas (encoded in the script)

- `countryCode` takes ONE code, max 4 chars; comma lists are rejected. Country
  columns are independent queries and MUST NOT be summed.
- `verifiedWarehouse=1` is drastically stricter than it reads (DE 92 -> 3); off
  by default.
- CJ rejects ~17% of calls with "QPS limit is 1 time/1second" even at a 3s pace.
  An empty cell drops its whole leaf from the aggregate and BIASES the shares —
  Home Improvement read 0.00% before gap-fill and 5.18% after. The script now
  retries (RETRIES/RETRY_PAUSE); any run must report its incomplete-row count.
- ⚠ `IFS=$'\t' read` COLLAPSES consecutive tabs (tab is IFS whitespace), so an
  empty field shifts every later value left. This corrupted the first gap-fill
  pass and produced plausible-but-wrong DE figures. Use a non-whitespace
  delimiter when fields may be empty.
