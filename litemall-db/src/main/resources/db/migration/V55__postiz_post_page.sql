-- =============================================================================
-- V55 — Postiz ledger gains a DIY-page source (Wave 20, promotion scope)
--
-- The Wave-17 ledger was goods-only. Wave 20 lets the admin publish a DIY
-- promo page (litemall_page) to social channels: one post per publish,
-- ledgered per channel with page_id set and goods_id NULL — so goods_id
-- loses its NOT NULL. Exactly one of (goods_id, page_id) is set per row;
-- enforced by the writer (promotion-service), not the schema.
--
-- NOTE: V54 (litemall_page category/template, goods-management scope) lives
-- on another branch — flyway out-of-order:true is permanent, the gap is fine.
-- =============================================================================

ALTER TABLE `litemall_postiz_post`
  MODIFY COLUMN `goods_id` int(11) DEFAULT NULL COMMENT 'goods being promoted (NULL for DIY-page posts)',
  ADD COLUMN `page_id` int(11) DEFAULT NULL COMMENT 'DIY promo page being promoted (litemall_page id; NULL for goods posts)' AFTER `goods_id`,
  ADD KEY `idx_page_id` (`page_id`);
