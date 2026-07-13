-- V34: internationalize the freight-template region rows (Wave 4, order worktree).
-- Addresses store free-text international province names (V29 made litemall_address
-- international); the Chinese litemall_region tree is address-decoupled. Region and
-- free rows therefore match on (country_code, province_name) instead of the legacy
-- province-JSON blob. country_code '*' = wildcard (any country); province_name NULL
-- = whole-country row. The legacy `province` JSON column is retained untouched.

ALTER TABLE `litemall_shipping_templates`
  ADD COLUMN `is_default` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'default template for goods with temp_id=0 (at most one row set)' AFTER `appoint`;

ALTER TABLE `litemall_shipping_templates_region`
  ADD COLUMN `country_code`  varchar(4)  NOT NULL DEFAULT '*' COMMENT 'ISO country code this row applies to, * = any' AFTER `temp_id`,
  ADD COLUMN `province_name` varchar(63) DEFAULT NULL COMMENT 'free-text province/state name (case-insensitive match against litemall_address.province), NULL = whole country' AFTER `country_code`;

ALTER TABLE `litemall_shipping_templates_free`
  ADD COLUMN `country_code`  varchar(4)  NOT NULL DEFAULT '*' COMMENT 'ISO country code this row applies to, * = any' AFTER `temp_id`,
  ADD COLUMN `province_name` varchar(63) DEFAULT NULL COMMENT 'free-text province/state name (case-insensitive match against litemall_address.province), NULL = whole country' AFTER `country_code`;

CREATE INDEX `idx_temp_country` ON `litemall_shipping_templates_region` (`temp_id`, `country_code`);
CREATE INDEX `idx_temp_country_free` ON `litemall_shipping_templates_free` (`temp_id`, `country_code`);
