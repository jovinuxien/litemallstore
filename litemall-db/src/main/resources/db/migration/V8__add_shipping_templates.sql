-- =============================================================================
-- V8 — Shipping template tables (template definition, free-shipping rules, region pricing)
-- Undo: db/undo/U8__undo_shipping_templates.sql
-- =============================================================================

CREATE TABLE IF NOT EXISTS `litemall_shipping_templates` (
  `id`          int(11) NOT NULL AUTO_INCREMENT,
  `name`        varchar(64) NOT NULL DEFAULT '' COMMENT '运费模板名称',
  `type`        tinyint(1) NOT NULL DEFAULT '1'  COMMENT '计费类型 1按件 2按重量 3按体积',
  `appoint`     tinyint(1) NOT NULL DEFAULT '0'  COMMENT '是否有指定包邮条件',
  `sort`        int(11) NOT NULL DEFAULT '0'     COMMENT '排序',
  `add_time`    datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `deleted`     tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运费模板表';

CREATE TABLE IF NOT EXISTS `litemall_shipping_templates_free` (
  `id`          int(11) NOT NULL AUTO_INCREMENT,
  `temp_id`     int(11) NOT NULL COMMENT '运费模板ID',
  `province`    varchar(1023) NOT NULL DEFAULT '[]' COMMENT '包邮省市区JSON',
  `number`      decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '包邮件数/重量/体积阈值',
  `price`       decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '包邮订单金额阈值',
  `add_time`    datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `deleted`     tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_temp_id` (`temp_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运费模板包邮条件表';

CREATE TABLE IF NOT EXISTS `litemall_shipping_templates_region` (
  `id`             int(11) NOT NULL AUTO_INCREMENT,
  `temp_id`        int(11) NOT NULL COMMENT '运费模板ID',
  `province`       varchar(1023) NOT NULL DEFAULT '[]' COMMENT '适用省市区JSON',
  `first`          decimal(10,2) NOT NULL DEFAULT '1.00'  COMMENT '首件/首重/首体积数量',
  `first_price`    decimal(10,2) NOT NULL DEFAULT '0.00'  COMMENT '首费',
  `continue_p`     decimal(10,2) NOT NULL DEFAULT '1.00'  COMMENT '续件/续重/续体积数量',
  `continue_price` decimal(10,2) NOT NULL DEFAULT '0.00'  COMMENT '续费',
  `add_time`       datetime DEFAULT NULL,
  `update_time`    datetime DEFAULT NULL,
  `deleted`        tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_temp_id` (`temp_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运费模板区域定价表';