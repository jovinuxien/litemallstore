-- =============================================================================
-- V40 — SKU-level flash-deal swap state + CJ suggested-retail anchor
--
-- CJ deals strategy (doc/cj-deals-strategy-2026-07-16.pdf).
--
-- original_sku_prices: every checkout amount reads litemall_goods_product.price
-- (cart-add snapshots it, submit re-stamps it), so the V38 goods-row swap was
-- display-only. The lifecycle scheduler now swaps every SKU row proportionally
-- and records {productId: {"o": original, "s": swapped}} here so unwind can
-- restore per-SKU with an admin-wins guard.
--
-- suggest_price: CJ's detail API returns suggestSellPrice (an MSRP-style
-- suggestion) that enrichment previously discarded. Persisted (converted with
-- usdToCny only — it is already a retail suggestion, applying our margin would
-- double-mark it) it becomes the organic counter_price anchor: CJ goods we sell
-- below CJ's suggested retail show an honest discount and reach Today's Deals.
-- =============================================================================

ALTER TABLE `litemall_seckill`
  ADD COLUMN `original_sku_prices` text COMMENT 'JSON {productId:{"o":original,"s":swapped}} captured at swap-on; drives per-SKU restore';

ALTER TABLE `litemall_cj_product`
  ADD COLUMN `suggest_price` decimal(10,2) DEFAULT NULL COMMENT 'CJ suggestSellPrice lower bound x usdToCny; organic counter_price anchor when above retail';
