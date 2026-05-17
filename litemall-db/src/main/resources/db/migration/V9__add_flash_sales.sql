-- =============================================================================
-- V9 — Flash sale (seckill) tables
-- Undo: db/undo/U9__undo_flash_sales.sql
-- =============================================================================

CREATE TABLE IF NOT EXISTS `litemall_seckill_time` (
  `id`          int(11) NOT NULL AUTO_INCREMENT,
  `time`        tinyint(3) NOT NULL COMMENT '秒杀时段 0-23小时',
  `status`      tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否开启 1开启 0关闭',
  `add_time`    datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `deleted`     tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_time` (`time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀时段管理表';

CREATE TABLE IF NOT EXISTS `litemall_seckill` (
  `id`          int(11) NOT NULL AUTO_INCREMENT,
  `goods_id`    int(11) NOT NULL COMMENT '关联商品ID',
  `goods_name`  varchar(127) NOT NULL DEFAULT '' COMMENT '商品名称',
  `pic_url`     varchar(255)          DEFAULT ''  COMMENT '商品图片',
  `price`       decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '秒杀价格',
  `cost`        decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '成本价',
  `stock`       int(11) NOT NULL DEFAULT '0'     COMMENT '秒杀库存',
  `sales`       int(11) NOT NULL DEFAULT '0'     COMMENT '已售数量',
  `quota`       int(11) NOT NULL DEFAULT '0'     COMMENT '限购总数 0不限',
  `quota_show`  int(11) NOT NULL DEFAULT '0'     COMMENT '页面显示限购数',
  `time`        tinyint(3) NOT NULL DEFAULT '0'  COMMENT '秒杀时段(小时)',
  `is_postage`  tinyint(1) NOT NULL DEFAULT '0'  COMMENT '是否包邮',
  `postage`     decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '邮费',
  `temp_id`     int(11) NOT NULL DEFAULT '0'     COMMENT '运费模板ID',
  `weight`      decimal(8,2) NOT NULL DEFAULT '0.00' COMMENT '重量kg',
  `volume`      decimal(8,2) NOT NULL DEFAULT '0.00' COMMENT '体积m³',
  `sort`        int(11) NOT NULL DEFAULT '0'     COMMENT '排序',
  `status`      tinyint(1) NOT NULL DEFAULT '0'  COMMENT '状态 0下架 1上架',
  `is_del`      tinyint(1) NOT NULL DEFAULT '0'  COMMENT '是否删除',
  `start_time`  datetime DEFAULT NULL COMMENT '秒杀开始时间',
  `stop_time`   datetime DEFAULT NULL COMMENT '秒杀结束时间',
  `add_time`    datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `deleted`     tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_goods_id` (`goods_id`),
  KEY `idx_time` (`time`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀活动商品表';
