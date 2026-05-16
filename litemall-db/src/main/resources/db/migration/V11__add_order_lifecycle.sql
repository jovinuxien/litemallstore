-- =============================================================================
-- V11 — Order status history table (full audit trail of order state changes)
-- Undo: db/undo/U11__undo_order_lifecycle.sql
-- =============================================================================

CREATE TABLE IF NOT EXISTS `litemall_order_status` (
  `id`             int(11) NOT NULL AUTO_INCREMENT,
  `order_id`       int(11) NOT NULL COMMENT '订单ID',
  `change_type`    varchar(32) NOT NULL DEFAULT '' COMMENT '状态变更类型 pay/ship/receive/cancel/refund等',
  `change_message` varchar(255)         DEFAULT ''  COMMENT '变更说明(可展示给用户)',
  `change_time`    datetime NOT NULL               COMMENT '变更发生时间',
  `operator`       varchar(63)          DEFAULT ''  COMMENT '操作人 system/admin名/user',
  `add_time`       datetime DEFAULT NULL,
  `update_time`    datetime DEFAULT NULL,
  `deleted`        tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_change_type` (`change_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单状态变更历史表';