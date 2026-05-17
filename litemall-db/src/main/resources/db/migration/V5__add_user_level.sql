-- =============================================================================
-- V5 — VIP member level tables (level definition + user level record)
-- Undo: db/undo/U5__undo_user_level.sql
-- =============================================================================

CREATE TABLE IF NOT EXISTS `litemall_system_user_level` (
  `id`          int(11) NOT NULL AUTO_INCREMENT,
  `name`        varchar(64) NOT NULL DEFAULT '' COMMENT '等级名称',
  `level`       tinyint(3)  NOT NULL DEFAULT '0' COMMENT '等级值(越大越高)',
  `experience`  int(11)     NOT NULL DEFAULT '0' COMMENT '升级所需经验值',
  `discount`    decimal(5,2) NOT NULL DEFAULT '100.00' COMMENT '享受折扣百分比 100=无折扣',
  `icon`        varchar(255)          DEFAULT ''  COMMENT '等级图标URL',
  `is_show`     tinyint(1)  NOT NULL DEFAULT '1'  COMMENT '是否在前端展示',
  `add_time`    datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `deleted`     tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_level` (`level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员等级定义表';

CREATE TABLE IF NOT EXISTS `litemall_user_level` (
  `id`         int(11) NOT NULL AUTO_INCREMENT,
  `user_id`    int(11) NOT NULL COMMENT '用户ID',
  `level_id`   int(11) NOT NULL COMMENT '等级ID(关联litemall_system_user_level)',
  `grade`      tinyint(3) NOT NULL DEFAULT '0' COMMENT '等级值快照',
  `experience` int(11) NOT NULL DEFAULT '0'   COMMENT '达到该等级时的经验值',
  `status`     tinyint(1) NOT NULL DEFAULT '1' COMMENT '1有效 0失效',
  `add_time`   datetime DEFAULT NULL COMMENT '升级时间',
  `update_time` datetime DEFAULT NULL,
  `deleted`    tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_level_id` (`level_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户会员等级记录表';