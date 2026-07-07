-- =============================================================================
-- V17 — Combination (group-buy / 拼团) CAMPAIGN-DEFINITION table
-- Owned by litemall-promotion-service. Promotion owns the campaign *definition*
-- (the offer/rules); litemall-order continues to own participation/pink
-- (litemall_groupon). See litemall-promotion-service/docs/
-- adr-combination-groupon-split.md.
-- Undo: db/undo/U17__undo_combination.sql
-- =============================================================================

CREATE TABLE IF NOT EXISTS `litemall_combination` (
  `id`                int(11) NOT NULL AUTO_INCREMENT,
  `goods_id`          int(11) NOT NULL COMMENT '关联商品ID',
  `title`             varchar(255) NOT NULL DEFAULT '' COMMENT '拼团活动名称',
  `pic_url`           varchar(255)          DEFAULT ''  COMMENT '活动图片',
  `combination_price` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '拼团价',
  `original_price`    decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '原价(参考)',
  `required_members`  int(11) NOT NULL DEFAULT '2'      COMMENT '成团所需人数',
  `limit_per_user`    int(11) NOT NULL DEFAULT '0'      COMMENT '每人限购 0不限',
  `start_time`        datetime DEFAULT NULL COMMENT '活动开始时间',
  `end_time`          datetime DEFAULT NULL COMMENT '活动结束时间',
  `status`            tinyint(1) NOT NULL DEFAULT '0'   COMMENT '状态 0草稿 1进行中 2已过期 3下架',
  `add_time`          datetime DEFAULT NULL,
  `update_time`       datetime DEFAULT NULL,
  `deleted`           tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_goods_id` (`goods_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='拼团活动(营销活动定义)表';
