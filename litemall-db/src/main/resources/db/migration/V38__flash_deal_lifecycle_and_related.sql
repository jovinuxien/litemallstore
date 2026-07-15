-- =============================================================================
-- V38 — Flash-deal lifecycle state on litemall_seckill + goods co-occurrence
--
-- Deals Phase B/C (goods-management). REUSES the V9 litemall_seckill table
-- (precedent: V8 shipping templates reused for Wave-4 freight — never create
-- parallel tables). The price-swap lifecycle needs two columns of state:
--   original_retail_price — goods.retail_price captured at swap-on, restored
--                           at swap-off (deal ends / disabled / stock sold out)
--   price_swapped         — 1 while the deal price is live on the goods row;
--                           the single source of truth for "deal is live"
-- litemall_goods_related backs the collaborative-filtering "related items"
-- field (Phase C): one row per goods, strongest-first comma-separated ids
-- recomputed by the nightly co-occurrence batch.
-- =============================================================================

ALTER TABLE `litemall_seckill`
  ADD COLUMN `original_retail_price` decimal(10,2) DEFAULT NULL COMMENT 'goods.retail_price captured at swap-on; restored at swap-off',
  ADD COLUMN `price_swapped` tinyint(1) NOT NULL DEFAULT '0' COMMENT '1 while the deal price is live on the goods row';

CREATE TABLE IF NOT EXISTS `litemall_goods_related` (
  `goods_id`    int(11) NOT NULL COMMENT 'goods this row belongs to',
  `related_ids` varchar(255) NOT NULL DEFAULT '' COMMENT 'comma-separated related goods ids, strongest affinity first',
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`goods_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='item-item co-occurrence (co-purchase/co-view) for recommendations';
