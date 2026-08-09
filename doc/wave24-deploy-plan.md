# Wave 24 — EUR flip: deploy-day plan (MAIN session)

Status: DRAFT prepared 2026-08-09 while the worktree halves are in flight.
Owner: main session. Executes ONLY after all four Wave-24 halves are merged
to master. **FX rate (USER-APPROVED 2026-08-09): the live market USD→EUR
rate looked up on flip day** — state the number in the deploy log, use the
SAME value for the conversion SQL and `LITEMALL_FX_USD_EUR`.
STAGE 1 DONE 2026-08-09: order + goods-management deployed DORMANT (envs
present at pre-flip defaults, behavior verified unchanged). Remaining =
gateway-api € sweep merge, then steps below with BOTH SPA containers.

## Ordering (single staged pass)

1. **Freeze**: no admin coupon/deal creation during the window (tell user).
2. **Env** (`.env.prod`, backup first as `.env.prod.bak-wave24`):
   `LITEMALL_FX_USD_EUR=<rate>`, `LITEMALL_GOODS_CURRENCY=EUR`,
   `LITEMALL_ORDER_STRIPE_CURRENCY=eur` — all three TOGETHER.
3. **Conversion transaction** (SQL below) — run BEFORE recreating
   containers so no window serves half-flipped data with EUR labels.
4. **Rebuild + recreate**: goods-management, order, gateway-api,
   gateway-admin (order before gateway-api — flyway rides order even
   though this wave has no migration; keep the discipline).
5. **Full reindex** (prices live in the OCS index), then smoke. No new
   index fields ⇒ no searcher restart needed (that rule is for NEW
   fields), but verify one search hit shows the converted price.
6. **Cloudflare**: purge /meta-catalog.csv (cached artifact; must not
   serve "x.xx USD" rows after the flip).
7. **Smoke**: PDP € + JSON-LD priceCurrency EUR + feed "x.xx EUR" + a
   staged checkout where Stripe PaymentIntent currency=eur.

## Conversion SQL (draft — re-derive numbers on deploy day)

All statements in ONE transaction. `@fx` = the same rate as the env.
NEVER touch order history tables (litemall_order, litemall_order_goods
keep their original-currency amounts — accepted mixed history).

```sql
SET @fx = <rate>;
START TRANSACTION;
-- catalog (12.8k goods; covers live flash-deal swapped prices too,
-- since an active swap IS litemall_goods_product.price)
UPDATE litemall_goods SET
  retail_price  = ROUND(retail_price  * @fx, 2),
  counter_price = ROUND(counter_price * @fx, 2),
  cost          = ROUND(cost          * @fx, 2)
WHERE deleted = 0;
UPDATE litemall_goods_product SET
  price = ROUND(price * @fx, 2),
  cost  = ROUND(cost  * @fx, 2)
WHERE deleted = 0;
-- promo money (skip percent-type coupon `discount` — it's a rate)
UPDATE litemall_coupon SET
  min = ROUND(min * @fx, 2),
  discount     = IF(discount_type = 0, ROUND(discount * @fx, 2), discount),
  discount_cap = ROUND(discount_cap * @fx, 2)
WHERE deleted = 0;
-- group-buy campaigns (column names verified at deploy)
UPDATE litemall_combination SET
  combination_price = ROUND(combination_price * @fx, 2),
  original_price    = ROUND(original_price    * @fx, 2)
WHERE deleted = 0;
COMMIT;
```

## Deploy-day checklist of fiddly items

- **Flash-deal swap JSON**: active deals store pre-swap prices in
  `original_sku_prices` JSON — pure SQL can't convert it. Either
  (a) end active deals pre-flip and let AutoDailyDealTask re-propose
  in EUR next morning (SIMPLEST — auto deals are cap-12 daily anyway),
  or (b) python-convert the JSON in-container. Default: (a).
- **Flat freight config** (order-worktree finding 2026-08-09): the
  customer-charged freight includes the `litemall_system` flat rule —
  convert `litemall_express_freight_value` / `litemall_express_freight_min`
  ×fx in the same transaction (system-table money settings; verify
  exact key names at deploy: `SELECT * FROM litemall_system WHERE
  key_name LIKE '%freight%'`).
- **Wallets**: check nonzero balances
  (litemall_user_bill trail — do NOT rewrite history; if any live
  balance exists, post an adjustment entry ×fx per user).
- **Deal candidates / promo candidates suggestion JSON** (V45/V53):
  suggestions carry money — STALE after the flip. Cheapest: delete
  rows with status='proposed' for today; the nightly scorers re-propose
  in EUR (04:30/04:45). Decided rows are history — leave.
- **Sweep for other live money**: `SELECT table_name, column_name FROM
  information_schema.columns WHERE table_schema='litemall' AND
  data_type='decimal'` and tick each table off as converted /
  history-excluded / rate-not-money before running the transaction.
- **Matomo/analytics**: past revenue was USD-denominated; note the
  discontinuity date in the Matomo annotation (user-visible honesty,
  no data rewrite).
- **CJ freight + placement are UNAFFECTED supplier-side** (CJ charges
  the CJ wallet in USD; only our customer-facing conversion changes).

## Post-deploy user-side

1. Stripe Dashboard: enable Klarna / SEPA / iDEAL / Bancontact /
   MobilePay (Payment Element picks them up, no rebuild).
2. Stripe Tax: registrations + activation check with a staged purchase.
3. One real-card live purchase end-to-end (never formally verified).
