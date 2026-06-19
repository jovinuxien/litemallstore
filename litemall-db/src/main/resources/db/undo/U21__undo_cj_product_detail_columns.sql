-- Undo V21 — drop the CJ snapshot detail/enrichment columns
DROP INDEX `idx_cj_enriched_time` ON `litemall_cj_product`;
ALTER TABLE `litemall_cj_product`
  DROP COLUMN `images_json`,
  DROP COLUMN `enriched_time`;
