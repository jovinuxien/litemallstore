# ADR — Flash deals via price swap on the goods row (Deals wave, Phase B)

Status: accepted · 2026-07-16 · owner: `litemall-goods-management`

## Decision

Time-boxed flash deals are realized by **swapping the deal price into
`litemall_goods.retail_price` for the duration of the deal window**, driven by a
60-second scheduler inside goods-management. No new purchase path, no order-service
changes, no checkout branching: cart, quote, submit, freight, coupons and the Phase-A
discount signals (`discount_pct` / `deal_flag`) all see the deal price because it IS
the retail price while the deal is live.

Storage **reuses the V9 `litemall_seckill` table** (precedent: Wave-4 freight reused
the V8 shipping-template tables — never create parallel tables). V38 adds exactly two
lifecycle columns:

- `original_retail_price` — `goods.retail_price` captured at swap-on, restored at
  swap-off;
- `price_swapped` — 1 while the deal price is live on the goods row. This column is
  the single source of truth for "deal is live"; `status`/window express only intent.

## Key choices (and deviations)

- **Deviation from crmeb:** crmeb runs seckill as a separate purchase path (dedicated
  product snapshot, slot-based "time" model, its own stock reservation via
  promotion's `/seckill/join`). We deliberately bypass all of it — a separate path
  would need order-service awareness (price authority, stock, refund parity) for a
  marketing feature. The price swap gets deal pricing with **zero** order changes.
  promotion-service's legacy `/seckill` read endpoints keep working unmodified: they
  read this same table and V38 only ADDS columns. The crmeb-era NOT-NULL columns
  (`time`, `quota`, `cost`, …) are zero-filled at create; we use window-based deals
  (`start_time`/`stop_time`), not the hour-slot model.
- **Single lifecycle writer:** `FlashDealLifecycleTask` (60s `fixedDelay` tick, config
  `litemall.deals.tick-ms`, kill switch `litemall.deals.lifecycle-enabled`) is the
  ONLY code that flips `price_swapped`/`original_retail_price` and touches the goods
  row. Admin create/update/delete author intent only; disabling or deleting a live
  deal is allowed and unwinds on the next tick (≤60s). One tick's failure never kills
  the schedule (catch-all around the tick body).
- **Admin-edit-wins unwind guard:** unwind restores `original_retail_price` ONLY if
  the goods' current retail still equals the deal price. If an admin hand-edited the
  price mid-deal, we leave it and WARN — a restore would silently destroy the newer
  human decision.
- **`counter_price` lift:** at swap-on, if the goods had no (or a weaker)
  strike-through, the pre-deal retail becomes `counter_price` so the storefront shows
  a truthful anchor. For the common `counter == retail` case the goods row ends
  byte-identical after unwind.
- **Live deals are immutable in price/start:** editing `dealPrice` or `startTime`
  while `price_swapped=1` is refused (errno 651) — the swapped goods row carries the
  current price and a mid-flight change would desynchronize charged vs displayed.
  Extending `stopTime`, changing `stock`, or disabling is allowed. Overlapping
  enabled windows per goods are refused at authoring (651).
- **CJ goods are refused (errno 652):** CJ price sync / demand-driven enrichment
  rewrites CJ goods rows and would fight the swap. `GET /srv/goods/deal`
  short-circuits `cj_*` ids to `data:null` for the same reason.
- **Claimed counts are read-only order integration:** the tick recomputes
  `sales` from paid-or-later order lines (`order_status >= 201`,
  `pay_time` inside the window) — no order-service hook. A capped deal
  (`stock > 0`) whose claimed count reaches the cap unwinds early, exactly like an
  expiry. `stock <= 0` means uncapped (no claimed bar, no early unwind).
- **Index-time urgency, not query-time decay:** `deal_urgency` (0–100 gaussian in
  hours-to-end, `DealMath.urgencyOf`) is computed at index time and multiplied into
  the searcher's ln2p scoring stack. Capability spike 2026-07-15: OCS `SCRIPT_CODE`
  scoring takes no params, so a query-time decay can't anchor its origin at "now".
  The scheduler reindexes a live deal when urgency drifts ≥2 from the last pushed
  value (and on every lifecycle transition / claimed change), keeping the index
  fresh within a tick. `MISSING: 0` under ln2p is a uniform ~0.69 multiplier —
  non-deal ranking is unchanged (verified against the §23 nDCG baseline).
- **Queryable deal fields ride EVERY document** (`deal_active` 1/0, `deal_end_epoch`
  with a 2100-01-01 sentinel for no-deal docs so "ending soon" ascending keeps live
  deals first, `deal_urgency` 0): found live 2026-07-16 — OCS resolves
  filter/sort/score fields against the index MAPPING, so a conditionally-emitted
  field is absent from any full reindex taken while no deal is live, and the
  Limited-time filter / ending-soon sort / urgency scoring silently no-op until the
  next searcher restart after a deal activates. Only Result-only `deal_claimed_pct`
  stays conditional (live + capped deals). The customer DTO maps the fields to
  `dealActive`/`dealEndEpoch`/`dealClaimedPct` only when `deal_active=1`, so the
  sentinel never reaches clients.
- **Epoch conversion is server-zone:** DB datetimes are naive server-local values
  (MySQL `NOW()`); service and MySQL share a host/zone in every environment we run,
  so `DealMath.toEpochMilli` uses `ZoneId.systemDefault()`.
- **@EnableScheduling side effect (intentional):** `SchedulingConfiguration` is the
  module's first `@EnableScheduling`. `CjCatalogRefreshTask`'s documented 03:00/03:30
  nightly crons were silently inert before it; they become live with Phase B, which
  matches their documented intent.

## Errnos (beside 640–643, GoodsServiceResponseCode)

| errno | meaning |
|-------|---------|
| 650 | `DEAL_INVALID` — validation failure; errmsg names the reason (price ≥ retail, bad window, missing goods…) |
| 651 | `DEAL_CONFLICT` — overlapping enabled window, or live-immutable field edit |
| 652 | `DEAL_CJ_UNSUPPORTED` — flash deals refuse `source='cj'` goods |

## Consequences

- A deal is visible to EVERYTHING that reads the goods row — including admin goods
  forms. The admin list/read views surface `live` + `originalRetailPrice` so
  operators can tell a swapped price from a hand-set one.
- Restart safety: state lives in the DB (`price_swapped`), so a restart just
  resumes the tick; the in-memory last-indexed-urgency map re-pushes one reindex
  per live deal.
- No code expiry/timezone follow-ups this wave; AutoConfirm-style sweeps are not
  needed because expiry IS the tick.
