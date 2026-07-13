-- V35: physical stores + in-store pickup / write-off (核销) support (Wave 4, order worktree).
-- New litemall_store table (crmeb system_store parity, trimmed) and pickup columns on
-- litemall_order. verify_code is generated at pay time (deviation from crmeb-at-create:
-- unpaid orders never carry a redeemable code) and is UNIQUE for O(1) scan lookup.

CREATE TABLE IF NOT EXISTS `litemall_store` (
  `id`               int NOT NULL AUTO_INCREMENT,
  `name`             varchar(63)  NOT NULL DEFAULT '' COMMENT 'store display name',
  `intro`            varchar(255) NOT NULL DEFAULT '' COMMENT 'short introduction',
  `phone`            varchar(25)  NOT NULL DEFAULT '' COMMENT 'contact phone',
  `address`          varchar(127) NOT NULL DEFAULT '' COMMENT 'region-level address (city/district)',
  `detailed_address` varchar(255) NOT NULL DEFAULT '' COMMENT 'street-level address',
  `logo`             varchar(255) NOT NULL DEFAULT '' COMMENT 'logo/storefront image URL',
  `latitude`         varchar(16)  DEFAULT NULL COMMENT 'latitude',
  `longitude`        varchar(16)  DEFAULT NULL COMMENT 'longitude',
  `business_hours`   varchar(127) NOT NULL DEFAULT '' COMMENT 'e.g. Mon-Sun 09:00-18:00',
  `is_show`          tinyint(1)   NOT NULL DEFAULT '1' COMMENT 'visible to customers',
  `add_time`         datetime DEFAULT NULL,
  `update_time`      datetime DEFAULT NULL,
  `deleted`          tinyint(1)   NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='physical pickup stores';

ALTER TABLE `litemall_order`
  ADD COLUMN `delivery_type` varchar(15) NOT NULL DEFAULT 'express' COMMENT 'express | pickup' AFTER `cj_order_status`,
  ADD COLUMN `store_id`      int DEFAULT NULL COMMENT 'pickup store id (delivery_type=pickup)' AFTER `delivery_type`,
  ADD COLUMN `verify_code`   varchar(12) DEFAULT NULL COMMENT 'pickup write-off code, generated at pay time' AFTER `store_id`,
  ADD COLUMN `verify_time`   datetime DEFAULT NULL COMMENT 'write-off timestamp' AFTER `verify_code`,
  ADD COLUMN `verified_by`   varchar(63) DEFAULT NULL COMMENT 'who wrote the order off, e.g. admin:<id>' AFTER `verify_time`;

CREATE UNIQUE INDEX `uk_order_verify_code` ON `litemall_order` (`verify_code`);
