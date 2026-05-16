-- =============================================================================
-- V14 — Goods enhancement tables (spec rule templates, separate description, audit log)
-- Undo: db/undo/U14__undo_goods_enhancements.sql
-- =============================================================================

-- Reusable spec rule templates (avoids re-entering Color/Size combos per product)
CREATE TABLE IF NOT EXISTS `litemall_goods_rule` (
  `id`          int(11) NOT NULL AUTO_INCREMENT,
  `rule_name`   varchar(64) NOT NULL DEFAULT '' COMMENT '规格模板名称',
  `value`       text                            COMMENT '规格维度+值列表(JSON)',
  `add_time`    datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `deleted`     tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品规格模板表';

-- Separate rich-text description table (keeps litemall_goods rows slim for list queries)
CREATE TABLE IF NOT EXISTS `litemall_goods_description` (
  `id`          int(11) NOT NULL AUTO_INCREMENT,
  `goods_id`    int(11) NOT NULL COMMENT '商品ID(唯一)',
  `description` longtext COMMENT '商品详情富文本(HTML)',
  `add_time`    datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `deleted`     tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_goods_id` (`goods_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品详情描述表';

-- Product change audit log (who changed what, when)
CREATE TABLE IF NOT EXISTS `litemall_goods_log` (
  `id`            int(11) NOT NULL AUTO_INCREMENT,
  `goods_id`      int(11) NOT NULL COMMENT '商品ID',
  `admin_id`      int(11)          DEFAULT '0'  COMMENT '操作管理员ID',
  `action`        varchar(64) NOT NULL DEFAULT '' COMMENT '操作类型 create/update/on_sale/off_sale/delete',
  `change_detail` varchar(512)     DEFAULT ''   COMMENT '变更说明',
  `add_time`      datetime DEFAULT NULL,
  `update_time`   datetime DEFAULT NULL,
  `deleted`       tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_goods_id` (`goods_id`),
  KEY `idx_admin_id` (`admin_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品操作日志表';