-- V32__cj_sourcing_request.sql
--
-- Local record of a CJ Dropshipping product-sourcing request created through the
-- admin surface (goods-management, POST /srv/private/admin/cj/sourcing). CJ OWNS
-- the sourcing lifecycle; this table is our side of the anticorruption boundary
-- so the admin list survives restarts and renders without a CJ round-trip.
--
--   cj_sourcing_id -> CJ's id returned by product/sourcing/create; the key used
--                     for product/sourcing/query status refreshes. Unique when
--                     present (rows without one recorded a failed create).
--   source_status / source_status_str / cj_product_id / cj_variant_sku
--                  -> CJ projection, refreshed on demand via sourcing/query.

CREATE TABLE IF NOT EXISTS `litemall_cj_sourcing_request` (
    `id`                INT NOT NULL AUTO_INCREMENT,
    `cj_sourcing_id`    VARCHAR(100) NULL COMMENT 'CJ sourcing id from product/sourcing/create; query key for status refresh',
    `cj_pid`            VARCHAR(100) NULL COMMENT 'CJ product id the request was created from, when sourced from our catalog',
    `product_name`      VARCHAR(200) NOT NULL COMMENT 'sent to CJ (required by their API)',
    `product_image`     VARCHAR(500) NOT NULL COMMENT 'sent to CJ (required by their API)',
    `product_url`       VARCHAR(500) NULL,
    `remark`            VARCHAR(200) NULL,
    `price`             DECIMAL(10,2) NULL COMMENT 'target price in USD, optional',
    `source_status`     VARCHAR(64) NULL COMMENT 'CJ sourceStatus code as last synced',
    `source_status_str` VARCHAR(200) NULL COMMENT 'CJ human-readable status as last synced',
    `cj_product_id`     VARCHAR(100) NULL COMMENT 'CJ product id once sourcing succeeds',
    `cj_variant_sku`    VARCHAR(100) NULL COMMENT 'CJ variant sku once sourcing succeeds',
    `add_time`          DATETIME NULL,
    `update_time`       DATETIME NULL,
    `deleted`           TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cj_sourcing_id` (`cj_sourcing_id`),
    KEY `idx_cj_sourcing_status` (`source_status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT 'CJ Dropshipping sourcing-request projection (V32)';
