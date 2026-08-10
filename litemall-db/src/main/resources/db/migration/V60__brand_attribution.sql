-- Wave 25: supplier/brand attribution + feed quality groundwork.
--
-- litemall_brand becomes the shared home for consumer brands (kind=0) AND
-- supplier stores (kind=1), written by AttributionProvider implementations
-- keyed on (source, external_id). `source` ALREADY EXISTS (added by
-- V23__cj_native_linkage with default 'local') — this migration re-baselines
-- its default to 'manual' and normalizes existing values instead of adding it.
--
-- Display-curation gate: provider-created rows land display_enabled=0 —
-- captured and linked but never rendered until an admin renames the raw
-- legal-entity name to something customer-worthy and enables the row.
-- Manual (admin-created) rows default enabled.

ALTER TABLE litemall_brand
    MODIFY COLUMN source varchar(32) NOT NULL DEFAULT 'manual'
        COMMENT 'origin of the brand/store row: manual | cj-supplier | future provider names',
    ADD COLUMN external_id varchar(63) NULL
        COMMENT 'provider-side id (e.g. CJ supplierId); NULL for manual rows',
    ADD COLUMN kind tinyint NOT NULL DEFAULT 0
        COMMENT '0 = consumer brand, 1 = supplier store (renders as "Sold by", never "Brand")',
    ADD COLUMN display_enabled tinyint NOT NULL DEFAULT 0
        COMMENT 'admin curation gate: 0 = captured but hidden, 1 = publicly rendered',
    ADD UNIQUE KEY uk_brand_source_external (source, external_id);

-- Existing rows are legacy manual seeds: normalize source and keep them visible.
UPDATE litemall_brand SET source = 'manual' WHERE source = 'local';
UPDATE litemall_brand SET display_enabled = 1 WHERE source = 'manual';

-- Durable supplier capture on the CJ snapshot (detail enrichment writes these;
-- the nightly list upsert leaves them untouched). Without dedicated columns the
-- nightly promoteBatch re-read would lose the attribution.
ALTER TABLE litemall_cj_product
    ADD COLUMN supplier_id varchar(63) NULL
        COMMENT 'CJ supplierId from product detail; sparse (~half of items)',
    ADD COLUMN supplier_name varchar(255) NULL
        COMMENT 'raw CJ supplier legal-entity name; never rendered without admin curation';

-- Catalog hygiene: stop stamping the Chinese default unit glyph from the DB side.
ALTER TABLE litemall_goods ALTER COLUMN unit SET DEFAULT '';
