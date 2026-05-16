-- =============================================================================
-- Undo V2 — Removes all columns added to existing tables
-- Run manually: mysql -u litemall -p litemall < U2__undo_enrich_existing_tables.sql
-- WARNING: column data is permanently lost after execution.
-- =============================================================================

ALTER TABLE `litemall_user`
  DROP COLUMN `now_money`,
  DROP COLUMN `brokerage_price`,
  DROP COLUMN `integral`,
  DROP COLUMN `experience`,
  DROP COLUMN `sign_num`,
  DROP COLUMN `spread_uid`,
  DROP COLUMN `spread_time`,
  DROP COLUMN `is_promoter`,
  DROP COLUMN `pay_count`,
  DROP COLUMN `spread_count`,
  DROP COLUMN `card_id`,
  DROP COLUMN `group_id`,
  DROP COLUMN `tag_id`,
  DROP COLUMN `login_type`,
  DROP COLUMN `path`,
  DROP COLUMN `subscribe`,
  DROP COLUMN `subscribe_time`,
  DROP COLUMN `country`;

ALTER TABLE `litemall_address`
  DROP COLUMN `city_id`,
  DROP COLUMN `longitude`,
  DROP COLUMN `latitude`;

ALTER TABLE `litemall_goods`
  DROP COLUMN `vip_price`,
  DROP COLUMN `cost`,
  DROP COLUMN `give_integral`,
  DROP COLUMN `is_seckill`,
  DROP COLUMN `is_bargain`,
  DROP COLUMN `is_benefit`,
  DROP COLUMN `is_best`,
  DROP COLUMN `is_postage`,
  DROP COLUMN `postage`,
  DROP COLUMN `fictitious`,
  DROP COLUMN `browse`,
  DROP COLUMN `bar_code`,
  DROP COLUMN `video_link`,
  DROP COLUMN `spec_type`,
  DROP COLUMN `temp_id`,
  DROP COLUMN `weight`,
  DROP COLUMN `volume`,
  DROP COLUMN `version`;

ALTER TABLE `litemall_goods_attribute`
  DROP COLUMN `type`;

ALTER TABLE `litemall_goods_product`
  DROP COLUMN `cost`,
  DROP COLUMN `bar_code`,
  DROP COLUMN `weight`,
  DROP COLUMN `volume`;

ALTER TABLE `litemall_cart`
  DROP COLUMN `seckill_id`,
  DROP COLUMN `bargain_id`,
  DROP COLUMN `combination_id`;

ALTER TABLE `litemall_order`
  DROP COLUMN `deduction_price`,
  DROP COLUMN `total_num`,
  DROP COLUMN `seckill_id`,
  DROP COLUMN `bargain_id`;

ALTER TABLE `litemall_coupon`
  DROP COLUMN `last_total`,
  DROP COLUMN `is_limited`,
  DROP COLUMN `use_type`,
  DROP COLUMN `primary_key`,
  DROP COLUMN `receive_start_time`,
  DROP COLUMN `receive_end_time`,
  DROP COLUMN `is_fixed_time`,
  DROP COLUMN `sort`;

ALTER TABLE `litemall_coupon_user`
  DROP COLUMN `use_type`,
  DROP COLUMN `primary_key`;