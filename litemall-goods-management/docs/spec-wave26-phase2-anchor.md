# Wave 26 Phase 2 — Anchor the catalogue (FROZEN SPEC)

Scope: **goods-management only**. **NO Flyway migration** (V61 belongs to Phase
1b; prod applied through V60 — check `flyway_schema_history` before first boot
regardless). Assigned once Wave 25.1 lands; do not code in that worktree before.

Prerequisite: Phase 1a is DONE (`spec-wave26-eu-sourcing.md`). Its result
rewrote this phase's sourcing step — read it before starting.

Anchor (user decision 2026-08-13): the **home/garden/tools cluster** =
`Home, Garden & Furniture` **+** `Home Improvement`, treated as ONE anchor.
Margin for the anchor: **2.5** (from the global 1.25).

---

## Governing rule: everything here is reversible

The user's stated intent is to run one anchor now and **take the other
categories back later**. Every step below must honour that literally:

- Narrowing is an `is_on_sale` **flip**, never a delete. Rows stay in
  `litemall_goods` / `litemall_goods_product` / `litemall_cj_product`.
- The CJ sync keeps mirroring **all 14 L1s** — only the storefront narrows.
  Do NOT prune `catalog-targets`; the pipeline stays broad, the store narrow.
- Wave 14 made the nightly promote **preserve `is_on_sale`**; that behaviour is
  load-bearing here and must not regress. Verify it, don't assume it.
- Restoring a category = flipping the flag back; sitemap, meta-catalog.csv and
  the OCS index follow on the next nightly cycle with no code change.
- Any step that cannot be undone by a flag flip must be called out in the PR.

---

## 1. Anchor margin: 1.25 → 2.5

No new machinery. Use the EXISTING Wave-14 per-category override:
`GET /insight/categories/{id}/simulate?margin=2.5` then
`PUT /insight/categories/{id}/margin {margin: 2.5}` (bounds 1.05–3.0).

- Apply to BOTH anchor L1 roots. Global default stays 1.25 for anything else
  still on sale (moot after step 3, but leave it correct).
- **SIMULATE FIRST and record the before/after numbers in the PR** —
  `goodsCount`, `avgPriceNow`/`avgPriceAt`, `potentialProfitNow`/`At`.
- Repricing happens at the nightly reprice from **stored cost**, so it is
  automatically coherent with the Wave-24 EUR landing. Margin guard, deal
  floors and coupon guards are ratio/cost-based — **verify they need no change,
  do not edit them**.
- Expected shape (modelled on the live feed, 2,286 anchor SKUs): median
  €9.13 → €18.26; €25–80 band 17.5% → 24.0% (≈549 SKUs). If the live result
  diverges materially from that, STOP and report — it means cost data moved.

## 2. Price floor

New config `litemall.goods.price-floor` (env `LITEMALL_GOODS_PRICE_FLOOR`,
explicit yml placeholder — the admin-notify-email relax-binding lesson;
decimal; **default 0 = OFF**, dev-safe).

Applied at the promote/reprice path: a good whose post-margin retail lands
below the floor is **OFF-SALED with a reason**, never silently repriced (a
silent reprice would break the cost×margin invariant every other guard relies
on). Reversible like any off-sale.

Context: 1,056 live SKUs price under €1 today; at 2.5× they are still under €2.
Recommended starting floor **€5.00** — but the VALUE IS A USER DECISION, so
ship the mechanism defaulted OFF and report what each candidate floor would
off-sale (a dry-run count per floor: 1, 2, 5, 10) before anything flips.

## 3. Narrowing to the anchor

Off-sale the non-anchor L1 subtrees through the EXISTING retirement/off-sale
path — admin-gated and staged, not a bulk UPDATE.

- Expect a large one-off batch (~12k of ~15k rows). Stage it and log counts.
- Sitemap, `meta-catalog.csv` and the OCS index shrink on the next nightly
  cycle; no separate work.
- **The 23-ish CJK-named / unit-glyph hygiene rules still apply** to what
  survives (Wave 25 machinery — reuse, don't rewrite).
- gateway-api must not show empty category tiles or dead links afterwards.
  That is a gateway-api half; raise it against the Wave-26 contract rather
  than reaching into the storefront from here.

## 4. Acquisition — the step Phase 1a rewrote

**The old plan ("filter the catalogue to EU stock") is DEAD**: at a 0.30%
storewide DE share it would leave roughly 40 products. EU-stocked SKUs must be
**acquired** instead.

Thread the filters we already pay for but never send through
`CJProductClient.getProductList` (today it sends only `pageNum`, `pageSize`,
`categoryId`) and expose them per `CatalogTarget`:

- `countryCode` — **ONE code, max 4 chars**; CJ rejects comma lists outright.
  DE is the only EU warehouse that exists (Phase 1a).
- `minPrice` / `maxPrice` — source directly into the €25–80 band instead of
  filtering after the fact. NOTE these bound CJ **cost**, not our retail;
  at margin 2.5 the retail band €25–80 corresponds to cost €10–32.
- `verifiedWarehouse` — available, but Phase 1a measured it as drastically
  stricter than it reads (DE 92 → 3 on one leaf). Leave it OFF unless a
  deliberate decision says otherwise.
- Absent config ⇒ **byte-identical requests to today**. This is the
  degrade-honest default and must be covered by a test.

New anchor catalog-targets source DE-stocked, in-band SKUs for the two anchor
L1s. Densest known leaves (Phase 1a, verified): Garden Tools 365/5,529,
Office & School Supplies 60/2,254, Curtains 51/733, Tools Storage 26/845,
Diamond Painting 20/3,117, Kitchen Knives 18/2,842.

⚠ Only ~3,800 DE-stocked products exist account-wide (extrapolated). An
honest fast-delivery promise is **per-SKU**, never storewide. Do not build a
storewide delivery claim on this.

## 5. Investigate: is the nightly sync silently lossy?

Phase 1a measured CJ rejecting **~17% of `/product/list` calls** with
"QPS limit is 1 time/1second" **even at a 3-second pace**. `CjSnapshotSyncService`
paces at 3s (`refresh-pace`) and the catalog run issues hundreds of calls.

If those rejections are swallowed, every nightly catalog run has been silently
dropping pages — which would also explain inflow never matching the configured
target. **Verify first, then fix if confirmed**: log rejection counts per run,
and add bounded retry with backoff (mirror `RETRIES`/`RETRY_PAUSE` in
`cj-eu-warehouse-probe.sh`). Report the measured rejection rate either way —
"no loss found" is a valid and valuable result.

---

## Acceptance (dev, through :9000/:8090)

- Simulate → apply on both anchor L1s moves stored retail with `marginPct`
  coherent; deal/coupon/margin guards still pass unchanged (proven, not assumed).
- Price floor: with the floor OFF, promote is byte-identical to today; with a
  floor set, exactly the sub-floor set off-sales and nothing else, and the flip
  is reversible. Dry-run counts reported for floors 1/2/5/10.
- Narrowing: non-anchor L1s off-sale in a staged, logged batch; the rows survive
  in MySQL; a spot-check category flips back ON cleanly and reappears in the
  index on the next cycle. `is_on_sale` survives a full nightly promote.
- Acquisition: with no filter config, CJ requests are byte-identical to today;
  with `countryCode=DE` + price bounds set, requests carry exactly those params
  and a dev run lands DE-stocked in-band goods. A comma-separated `countryCode`
  is rejected by OUR validation before it reaches CJ.
- Sync loss: rejection rate per run measured and reported with real counts.
- Module tests green with real "Tests run:" counts (⚠ root pom skips `mvn test`
  and surefire misses `@Nested` — read the count, never trust "BUILD SUCCESS").

## Landmines

- No new CJ call loops; ride the existing rotation (shared daily points quota,
  errno 16900500 on exhaustion).
- `andLogicalDeleted()` is inverted across 33 domain classes — bind the literal.
- litemall-db is shared and hand-maintained: entity + mapper XML together,
  `mvn install`, restart every dependent (⚠ recurring missing-mapper-XML gotcha).
- Never rebuild a jar under a running JVM.
- Absent ≠ zero, everywhere in this wave.
- Deployment is the MAIN session's job after merge. Do not touch the prod VPS
  or its DB from a worktree.
