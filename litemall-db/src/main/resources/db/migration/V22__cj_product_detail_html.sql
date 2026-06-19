-- =============================================================================
-- V22 — full rich product description for the DB-served CJ detail page.
--
-- litemall_cj_product.description holds a SHORT brief (CJ list `remark`, or the
-- title as fallback) and feeds the OCS `description`/search-result `brief` — kept
-- short like local goods' brief. The CJ `product/query` detail returns a separate
-- FULL HTML description that the detail page renders (mirroring local goods.detail
-- vs goods.brief). The enrichment pass discarded it, so the DB-served detail page
-- showed only the brief. This column stores that full description; enrichment
-- fills it, and CjGoodsDetailService serves it as goods.detail.
--
-- Undo: db/undo/U22__undo_cj_product_detail_html.sql
-- =============================================================================

ALTER TABLE `litemall_cj_product`
  ADD COLUMN `detail_html` text DEFAULT NULL COMMENT 'full CJ product description (HTML) for the DB-served detail page; brief stays in description';
