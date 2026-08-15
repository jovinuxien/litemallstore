-- Wave 26 Phase 1b — capture which CJ products hold EU (German) warehouse stock.
--
-- CJ already returns a per-country inventory breakdown on the call enrichment ALREADY makes
-- (CjDetailEnrichmentService.stockOf -> getInventory(vid) -> List<CJInventoryData>, each carrying
-- countryCode/areaEn/storageNum); today it is collapsed to sum(storageNum) and the split thrown
-- away. Keeping it costs ZERO additional CJ calls — the daily points are already being spent.
--
-- ⚠ NULL vs 0 is the whole point of this migration. NULL = "never probed"; 0 = "probed, and this
-- product has no EU stock". Collapsing them would make the survival report a lie: an unprobed
-- catalogue would read as a catalogue with no EU stock. Coverage grows with the enrichment
-- rotation, so every number derived from these columns must show its probed denominator.
ALTER TABLE `litemall_cj_product`
    ADD COLUMN `eu_stock_num` INT NULL DEFAULT NULL
        COMMENT 'Units held in EU warehouses at last probe. NULL = never probed (NOT zero).',
    ADD COLUMN `warehouse_countries` VARCHAR(255) NULL DEFAULT NULL
        COMMENT 'Distinct warehouse country codes seen at last probe, comma-separated (e.g. "CN,DE"). NULL = never probed.';

-- The survival report filters on "has EU stock" and joins to goods; without this it degrades to a
-- full scan of the snapshot table (~200k rows) on every admin page load.
CREATE INDEX `idx_cj_product_eu_stock` ON `litemall_cj_product` (`eu_stock_num`);
