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

## Phase 1a — CJ supply survival (built, awaiting a run)

`cj-eu-warehouse-probe.sh` at repo root. Read-only; auth + getCategory + two
paced `/product/list` calls per sampled leaf. Samples `LEAVES_PER_L1` (default 8)
evenly-spread leaves per L1 and reports, per L1, `CJ TOTAL` vs `EU STOCK`
(`countryCode=DE,FR,ES,CZ,PL,IT` + `verifiedWarehouse=1`) with the sampled-leaf
denominator visible. Creds are prod-only ⇒ **user-fired**.

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
