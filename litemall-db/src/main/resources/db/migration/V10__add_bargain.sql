-- =============================================================================
-- V10 — Bargain (price-cutting) campaign tables
-- Undo: db/undo/U10__undo_bargain.sql
-- =============================================================================

CREATE TABLE IF NOT EXISTS `litemall_bargain` (
  `id`               int(11) NOT NULL AUTO_INCREMENT,
  `goods_id`         int(11) NOT NULL COMMENT '关联商品ID',
  `title`            varchar(255) NOT NULL DEFAULT '' COMMENT '砍价活动名称',
  `pic_url`          varchar(255)          DEFAULT ''  COMMENT '活动图片',
  `unit`             varchar(16)           DEFAULT ''  COMMENT '单位名称',
  `stock`            int(11) NOT NULL DEFAULT '0'     COMMENT '库存',
  `sales`            int(11) NOT NULL DEFAULT '0'     COMMENT '销量',
  `price`            decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '原始价格',
  `min_price`        decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '砍至最低价',
  `num`              int(11) NOT NULL DEFAULT '1'     COMMENT '每用户发起砍价次数限制',
  `bargain_max_price` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '每次砍价最大金额',
  `bargain_min_price` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '每次砍价最小金额',
  `bargain_num`      int(11) NOT NULL DEFAULT '1'     COMMENT '帮砍次数(达到可购买)',
  `people_num`       int(11) NOT NULL DEFAULT '0'     COMMENT '砍价成功所需人数',
  `quota`            int(11) NOT NULL DEFAULT '0'     COMMENT '限购总数 0不限',
  `is_postage`       tinyint(1) NOT NULL DEFAULT '0'  COMMENT '是否包邮',
  `postage`          decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '邮费',
  `temp_id`          int(11) NOT NULL DEFAULT '0'     COMMENT '运费模板ID',
  `weight`           decimal(8,2) NOT NULL DEFAULT '0.00' COMMENT '重量kg',
  `volume`           decimal(8,2) NOT NULL DEFAULT '0.00' COMMENT '体积m³',
  `sort`             int(11) NOT NULL DEFAULT '0'     COMMENT '排序',
  `status`           tinyint(1) NOT NULL DEFAULT '0'  COMMENT '状态 0下架 1上架',
  `is_del`           tinyint(1) NOT NULL DEFAULT '0'  COMMENT '是否删除',
  `start_time`       datetime DEFAULT NULL COMMENT '活动开始时间',
  `stop_time`        datetime DEFAULT NULL COMMENT '活动结束时间',
  `add_time`         datetime DEFAULT NULL,
  `update_time`      datetime DEFAULT NULL,
  `deleted`          tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_goods_id` (`goods_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='砍价活动商品表';

CREATE TABLE IF NOT EXISTS `litemall_bargain_user` (
  `id`                int(11) NOT NULL AUTO_INCREMENT,
  `user_id`           int(11) NOT NULL COMMENT '发起砍价的用户ID',
  `bargain_id`        int(11) NOT NULL COMMENT '砍价活动ID',
  `bargain_price_min` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '该活动最低砍价价格',
  `bargain_price`     decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '当前已砍至的价格',
  `status`            tinyint(1) NOT NULL DEFAULT '0' COMMENT '0进行中 1成功 2失败',
  `add_time`          datetime DEFAULT NULL,
  `update_time`       datetime DEFAULT NULL,
  `deleted`           tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_bargain_id` (`bargain_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户参与砍价记录表';

CREATE TABLE IF NOT EXISTS `litemall_bargain_help` (
  `id`              int(11) NOT NULL AUTO_INCREMENT,
  `user_id`         int(11) NOT NULL COMMENT '帮砍用户ID',
  `bargain_id`      int(11) NOT NULL COMMENT '砍价活动ID',
  `bargain_user_id` int(11) NOT NULL COMMENT '被帮砍的用户ID',
  `price`           decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '帮砍金额',
  `add_time`        datetime DEFAULT NULL,
  `update_time`     datetime DEFAULT NULL,
  `deleted`         tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_bargain_user_id` (`bargain_user_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='砍价帮砍记录表';