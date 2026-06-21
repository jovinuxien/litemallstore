-- V23__cj_native_linkage.sql
--
-- Land CJ Dropshipping products inside the native goods catalog instead of the
-- parallel litemall_cj_product snapshot table, so OCS indexes a single source
-- (litemall_goods) and customer checkout can recover the CJ identifiers straight
-- off the rows.
--
-- Identity contract (see CLAUDE design notes):
--   CJ product id (pid, UUID) -> litemall_goods.cj_pid
--   CJ variant id  (vid, UUID) -> litemall_goods_product.cj_vid   <-- order-placement key
--   source discriminator       -> 'local' (hand-authored) vs 'cj' (synced)
--
-- All new columns are nullable / defaulted so existing inserts (which never name
-- them) keep working unchanged. MySQL treats NULLs as distinct in a UNIQUE index,
-- so every local row (cj_pid / cj_category_id NULL) is exempt from the uniqueness
-- guard while CJ rows are deduplicated by their UUID.

-- --- goods: origin + CJ product linkage -------------------------------------
ALTER TABLE litemall_goods
    ADD COLUMN source  VARCHAR(32) NOT NULL DEFAULT 'local'
        COMMENT 'origin of the goods row: local | cj',
    ADD COLUMN cj_pid  VARCHAR(64) NULL
        COMMENT 'CJ Dropshipping product id (UUID) when source=cj';

-- one CJ product maps to exactly one goods row; local rows (cj_pid NULL) are exempt
CREATE UNIQUE INDEX uk_goods_source_cjpid ON litemall_goods (source, cj_pid);

-- CJ image galleries routinely exceed the original varchar(1023); widen so the full
-- gallery survives the merge onto the native detail page (JSON-array type handler unaffected).
ALTER TABLE litemall_goods MODIFY COLUMN gallery TEXT;

-- --- goods product (SKU): CJ variant linkage --------------------------------
ALTER TABLE litemall_goods_product
    ADD COLUMN cj_vid  VARCHAR(64) NULL
        COMMENT 'CJ Dropshipping variant id (UUID); the key litemall-order replays to CJ createOrder when the parent goods row is source=cj';

CREATE INDEX idx_goods_product_cjvid ON litemall_goods_product (cj_vid);

-- --- category: origin + CJ category linkage (idempotent hierarchy upsert) ----
ALTER TABLE litemall_category
    ADD COLUMN source          VARCHAR(32) NOT NULL DEFAULT 'local'
        COMMENT 'origin of the category: local | cj',
    ADD COLUMN cj_category_id  VARCHAR(64) NULL
        COMMENT 'CJ category id when source=cj, for idempotent category-tree upsert';

CREATE UNIQUE INDEX uk_category_cjid ON litemall_category (cj_category_id);

-- --- brand: origin discriminator --------------------------------------------
ALTER TABLE litemall_brand
    ADD COLUMN source  VARCHAR(32) NOT NULL DEFAULT 'local'
        COMMENT 'origin of the brand/supplier: local | cj';
