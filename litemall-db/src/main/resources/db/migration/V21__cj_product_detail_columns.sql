-- =============================================================================
-- V21 — enrich the CJ product snapshot for full-detail + real inventory.
--
-- The CJ catalog list only carries shallow fields. A separate paced enrichment
-- pass fetches CJ product detail (real per-variant prices + options) and per-vid
-- inventory, and lands them in litemall_cj_product so (a) OCS facets/scoring act
-- on real CJ data and (b) the CJ product-detail page is served from the DB (no
-- live CJ call). variants_json / attributes_json / discount_price already exist
-- (V19) and are reused — enriched, not added. This migration adds:
--   * images_json    — the CJ product gallery (productImageSet) for the detail page
--   * enriched_time  — when detail+inventory were last fetched; NULL = not yet
--                      enriched, so the enrichment job can run incrementally and
--                      resumably (oldest/NULL first), respecting the CJ daily quota.
--
-- Undo: db/undo/U21__undo_cj_product_detail_columns.sql
-- =============================================================================

ALTER TABLE `litemall_cj_product`
  ADD COLUMN `images_json`   text     DEFAULT NULL COMMENT 'CJ product gallery image URLs (JSON array) for the DB-served detail page',
  ADD COLUMN `enriched_time` datetime DEFAULT NULL COMMENT 'last detail+inventory enrichment time; NULL = not yet enriched (incremental/resumable)';

CREATE INDEX `idx_cj_enriched_time` ON `litemall_cj_product` (`enriched_time`);
