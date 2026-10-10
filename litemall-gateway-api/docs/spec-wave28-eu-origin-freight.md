# Wave 28 — EU origin on the buying surfaces, EU freight where stock allows

**Status:** contract. gateway-api's display half is built against this doc (2026-08-19);
the goods-management half (§3.1) is BUILT 2026-09-06 on `fix/goods-management` — see the
as-built note under §3.1. The order half (§3.2) is NOT started and must be commissioned in
its own worktree. Code to THIS document, not to any branch.

**Origin:** user ask, 2026-08-18 — *"mention the EU country selling the product to
appear on the checkout instead of Trovemo every time as it is now, and freight
company has to be those from EU to start with"*.

---

## 1. What was measured before writing this

All of the following was verified in code on 2026-08-18/19, not assumed.

| Claim | Evidence |
|---|---|
| "Trovemo" as shipper/seller is a PDP hardcode, not a checkout one | `modules/product/Detail.tsx:478-483` — two static `<dd>Trovemo</dd>` trust rows. FIXED in this wave. |
| Checkout never names Trovemo per item | `Checkout.tsx:1309` says "Some items ship via **CJ Dropshipping**"; line items (`GoodsLineCard`, `Checkout.tsx:1542`) carry no origin at all |
| An honest per-SKU origin already exists on the PDP | `shared/util/euStock.ts` ← `data.euStock` ← goods-management `LitemallGoodsServiceApiImpl.attachEuStock` ← V61 `litemall_cj_product.eu_stock_num` / `warehouse_countries` |
| Every courier CJ offers is China-origin **by construction** | `spring.cjdropship.api.from-country-code` defaults to `"CN"`; `application.yml:76` is its ONLY definition in the repo (prod does not override). `CjDropshipOrderFacadeImpl:230` sends it as `startCountryCode` on every `freightCalculate`. |
| The SPA cannot filter EU carriers itself | `CjLogisticsOption` carries `logisticName` / `logisticPrice` / `logisticAging` only — no origin. Name-matching carrier strings would be a guess. |
| CJ already accepts a per-call origin | `CjFreightCalculateRequest.startCountryCode` exists and is populated; only the VALUE is pinned |
| The order side already reasons about warehouse country | `CjStockFacadeImpl.pickWarehouseStock:82-95` prefers stock in `fromCountryCode` over stock anywhere |

## 2. ⚠ The constraint that shapes the design

Wave 26 Phase 1a measured **DE as CJ's only EU warehouse, holding 0.30% of CJ
supply**. The 2026-08-18 reading: of 40 relevance-ordered catalogue products **0**
carry DE stock; only the 30 *newest* arrivals do (16/30).

**Therefore `from-country-code: DE` must never become a global flip.** CJ answers
`freightCalculate` with no lines for a product it cannot ship from the requested
origin, and `fetchLogisticsOptions` already logs exactly that case
(`CjDropshipOrderFacadeImpl:236`). A global DE would empty the delivery-option
chooser for almost the entire live catalogue — an unbuyable store.

Origin is therefore **per shipment, derived from the measurement**, and EU freight
becomes the norm as the Wave-26 DE acquisition targets fill the catalogue, not on
the day this ships.

## 3. Contract

### 3.1 goods-management — expose the measurement where order can read it

The measurement already exists per `cj_pid`. It needs a read path the order service
can use for a cart, not just a PDP.

```
GET /srv/goods/origin?ids=1,2,3   (PUBLIC read — same trust level as the PDP's
->  {list: [{goodsId, originCountry}]}   euStock key, which is already public)
```

Public, not machine-token, and batched. Rationale: the PDP already serves this exact
measurement anonymously via `/srv/goods/detail`, so a machine-token variant would be
guarding data that is already out. Batched because the checkout needs one call for
the whole cart — the SPA has the precedent at `Checkout.tsx:545`, which collects
`goodsIds` the same way for the coupon selectlist. Requires a `PublicPaths` entry
(read-only GET; the deny-by-default rule stands for everything else).

- `originCountry` = the EU warehouse country code (`"DE"`) when
  `eu_stock_num > 0`, else **the key is omitted for that goods**.
- A goods with no measurement is **absent from `list`** — never `"CN"`, never
  `null` standing in for "we think China". `NULL` (never probed) and `0` (probed,
  none) collapse here exactly as they do on the PDP, and for the same reason: the
  two are indistinguishable to a customer-facing claim.

**As built (goods-management, 2026-09-06):** `LitemallGoodsController.origin` →
`WarehouseOriginService` (the PDP's `euStock` badge reads the same predicate, so the two
surfaces cannot disagree). Response is the errno envelope, `data.list` as above. `ids` is
whitespace-tolerant, deduplicated, junk tokens dropped; empty ⇒ `{list:[]}`; more than 100
distinct ids ⇒ errno 402 typed refusal. `originCountry` = the first configured EU warehouse
country (`spring.cjdropship.eu-warehouse-countries`, DE) present in the row's measured
`warehouse_countries`; a reading with units but no configured EU country yields NO row. A
lookup failure on one id drops that row only. No `PublicPaths` change was needed: both the
service's `public-paths` and the edge's GET-only `CATALOG_GET` already cover `/srv/goods/**`.

### 3.2 order — per-shipment origin, and say which origin was quoted

**Freight origin.** `CjDropshipOrderFacadeImpl` stops reading one global
`fromCountryCode` for quoting. Per shipment:

- every CJ line in the shipment has measured EU stock ⇒ quote `startCountryCode=DE`
- otherwise ⇒ quote `startCountryCode=CN`, exactly today's behaviour
- DE quote returns **zero** options ⇒ **re-quote from CN and use that**. A stale or
  optimistic measurement must degrade to a shippable cart, never to an empty
  chooser. Log the fallback — silent fallback would hide the measurement rotting.

`spring.cjdropship.api.from-country-code` stays as the placement/stock default so
nothing else moves. Add `litemall.order.cj.eu-origin-enabled`
(env `LITEMALL_ORDER_CJ_EU_ORIGIN_ENABLED`, **default false** = today's behaviour,
explicit yml placeholder — the Wave-26 `LITEMALL_GOODS_PRICE_FLOOR` passthrough
lesson: verify the env inside the container, not just in `.env.prod`).

**Payload additions** (both additive; absent ⇒ the SPA degrades to today's render):

- `POST /srv/order/freight-quote` — each `cj.options[]` entry gains
  `originCountry` (the origin the line was actually quoted from), and the quote
  gains a top-level `cj.originCountry`.

The cart payload deliberately does **not** change: the per-line badge reads the
public goods endpoint above instead. That keeps the display half shippable without
an order-service release, and keeps one owner for the measurement.

`originCountry` describes **where the line was quoted from**, which after the
fallback rule above is a fact about the quote, not a restatement of the
measurement. That distinction is the point: the customer sees the origin that
priced their shipping.

### 3.3 gateway-api — display only (BUILT, this wave)

- PDP: hardcoded `Ships from → Trovemo` / `Sold by → Trovemo` **deleted**.
  "Ships from" renders only for a measured product; seller attribution stays in
  Wave-25's `SoldByRow`. **No backend dependency — shipped independently.**
- Checkout: per-line origin badge from `originCountry`; the blanket "ships via CJ
  Dropshipping" alert gives way to per-item truth.
- Courier chooser: EU-origin options labelled as such.
- Every field is optional in the render path. Against a pre-Wave-28 backend the
  checkout looks exactly as it does today (the Wave-24.1 `upgradeDelta` precedent).

## 4. Non-goals

- **No delivery-day promise anywhere.** We measure that stock exists in an EU
  warehouse; CJ still picks the fulfilling warehouse at order time. A day count
  would be a number nobody measured (Wave-26 rule, restated because it is the
  easiest thing to get wrong under commercial pressure).
- No migration in any module — V61 already stores the measurement.
- No change to placement (`createOrder`) origin. Quoting and placing can disagree
  today; making them agree is a follow-up, not this wave.

## 5. Acceptance

- gateway-api: PDP shows no "Ships from" row for an unmeasured product and names
  the country for a measured one; checkout renders per-line origin when the field
  is present and is byte-identical to today when it is absent; jest green.
- order: a cart of measured-EU lines quotes from DE and the options carry
  `originCountry: "DE"`; a mixed cart quotes CN; a DE quote returning nothing
  falls back to CN with a log line and a non-empty chooser; flag off ⇒ requests
  byte-identical to today.
- goods-management: batch origin endpoint returns rows only for measured goods.
