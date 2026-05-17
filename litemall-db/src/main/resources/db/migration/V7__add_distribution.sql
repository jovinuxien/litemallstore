-- =============================================================================
-- V7 — Distribution / referral commission table
-- Undo: db/undo/U7__undo_distribution.sql
-- =============================================================================

CREATE TABLE IF NOT EXISTS `litemall_user_brokerage_record` (
  `id`             int(11) NOT NULL AUTO_INCREMENT,
  `user_id`        int(11) NOT NULL COMMENT '佣金归属用户ID',
  `link_id`        varchar(32) NOT NULL DEFAULT '0' COMMENT '关联订单ID',
  `link_type`      varchar(32)          DEFAULT ''  COMMENT '关联类型 order/extract',
  `pm`             tinyint(1) NOT NULL DEFAULT '1'  COMMENT '0支出 1收入',
  `title`          varchar(64) NOT NULL DEFAULT ''  COMMENT '标题',
  `price`          decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '佣金金额',
  `balance`        decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '变动后余额',
  `mark`           varchar(255)          DEFAULT ''  COMMENT '备注',
  `status`         tinyint(1) NOT NULL DEFAULT '0'  COMMENT '0冻结 1有效 -1无效',
  `freeze_time`    datetime DEFAULT NULL COMMENT '冻结时间',
  `unfreeze_time`  datetime DEFAULT NULL COMMENT '解冻时间',
  `add_time`       datetime DEFAULT NULL,
  `update_time`    datetime DEFAULT NULL,
  `deleted`        tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户佣金记录表';