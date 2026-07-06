-- =============================================================================
-- V19 — CJ Dropshipping product snapshot (the DB-backed store the OCS index is
-- built from). CJ catalog products are fetched (rate-limited) into Redis, then
-- normalized and persisted here as the system of record; OCS indexing reads
-- these rows exactly like local goods read litemall_goods.
--
-- The primary key is the RAW CJ pid (a UUID) — NOT the cj_<pid> OCS id — so the
-- pid can be used directly to fetch CJ product detail and to reference the
-- product when placing a CJ order. The cj_ prefix is applied only at index time.
-- This is a dedicated table (NOT litemall_goods) because CJ ids are UUIDs and
-- must never be shoehorned into the int-keyed local schema.
--
-- Undo: db/undo/U19__undo_cj_product_snapshot.sql
-- =============================================================================

CREATE TABLE IF NOT EXISTS `litemall_cj_product` (
  `pid`            varchar(64)  NOT NULL                COMMENT 'CJ raw product id (UUID); used directly for detail fetch + order placement',
  `source`         varchar(32)  NOT NULL DEFAULT 'cj_dropshipping' COMMENT 'document origin tag',
  `title`          varchar(512) NOT NULL DEFAULT ''     COMMENT 'English product name (productNameEn)',
  `price`          decimal(10,2)         DEFAULT NULL   COMMENT 'retail price (CJ wholesale x usd-to-cny x margin), local basis',
  `discount_price` decimal(10,2)         DEFAULT NULL   COMMENT 'discount price (currently unused for CJ)',
  `description`    text                                 COMMENT 'cleaned description/remark',
  `image_url`      varchar(1024)         DEFAULT NULL   COMMENT 'productImage URL',
  `brand`          varchar(128)          DEFAULT NULL   COMMENT 'brand (sparse for CJ)',
  `category_names` text                                 COMMENT 'mapped local category names root->leaf (JSON array)',
  `category_ids`   varchar(512)          DEFAULT NULL   COMMENT 'mapped local category ids root->leaf (JSON array)',
  `variants_json`  text                                 COMMENT 'per-SKU variant maps (variant_price/stock) as JSON',
  `attributes_json` text                                COMMENT 'curated attribute name->value pairs as JSON',
  `add_time`       datetime              DEFAULT NULL,
  `update_time`    datetime              DEFAULT NULL,
  `deleted`        tinyint(1)   NOT NULL DEFAULT '0'    COMMENT 'soft-delete flag; set when the product disappears upstream',
  PRIMARY KEY (`pid`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CJ Dropshipping product snapshot (OCS index source)';
