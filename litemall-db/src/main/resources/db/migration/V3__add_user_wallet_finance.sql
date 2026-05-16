-- =============================================================================
-- V3 — User wallet & finance tables (bill ledger, recharge, withdrawal)
-- Undo: db/undo/U3__undo_user_wallet_finance.sql
-- =============================================================================

CREATE TABLE IF NOT EXISTS `litemall_user_bill` (
  `id`          int(11) NOT NULL AUTO_INCREMENT,
  `user_id`     int(11) NOT NULL DEFAULT '0'   COMMENT '用户ID',
  `link_id`     varchar(32) NOT NULL DEFAULT '0' COMMENT '关联业务ID(订单号等)',
  `pm`          tinyint(1) NOT NULL DEFAULT '1'  COMMENT '0支出 1收入',
  `title`       varchar(64) NOT NULL DEFAULT ''  COMMENT '账单标题',
  `category`    varchar(64) NOT NULL DEFAULT ''  COMMENT '明细种类 now_money/integral/experience',
  `type`        varchar(64) NOT NULL DEFAULT ''  COMMENT '明细类型 pay_order/recharge/sign等',
  `number`      decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '变动数额',
  `balance`     decimal(16,2) NOT NULL DEFAULT '0.00' COMMENT '变动后余额',
  `mark`        varchar(512)          DEFAULT ''  COMMENT '备注',
  `status`      tinyint(1) NOT NULL DEFAULT '1'  COMMENT '0待确认 1有效 -1无效',
  `add_time`    datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `deleted`     tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_pm` (`pm`),
  KEY `idx_category_type` (`category`, `type`),
  KEY `idx_add_time` (`add_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户账单流水表';

CREATE TABLE IF NOT EXISTS `litemall_user_recharge` (
  `id`             int(11) NOT NULL AUTO_INCREMENT,
  `user_id`        int(11) NOT NULL COMMENT '用户ID',
  `order_id`       varchar(32) NOT NULL DEFAULT '' COMMENT '充值订单号',
  `price`          decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '充值金额',
  `give_price`     decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '赠送金额',
  `recharge_type`  varchar(32)           DEFAULT ''     COMMENT '充值类型 weixin/alipay/stripe',
  `paid`           tinyint(1) NOT NULL DEFAULT '0'      COMMENT '是否已支付',
  `pay_time`       datetime DEFAULT NULL                COMMENT '支付时间',
  `add_time`       datetime DEFAULT NULL,
  `update_time`    datetime DEFAULT NULL,
  `deleted`        tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_paid` (`paid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户充值记录表';

CREATE TABLE IF NOT EXISTS `litemall_user_extract` (
  `id`             int(11) NOT NULL AUTO_INCREMENT,
  `user_id`        int(11) NOT NULL COMMENT '用户ID',
  `real_name`      varchar(32)  NOT NULL DEFAULT '' COMMENT '提现姓名',
  `extract_type`   varchar(32)  NOT NULL DEFAULT '' COMMENT '提现类型 bank/alipay/wechat',
  `bank_code`      varchar(64)           DEFAULT ''  COMMENT '银行卡号/账号',
  `bank_address`   varchar(128)          DEFAULT ''  COMMENT '开户行',
  `extract_price`  decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '提现金额',
  `balance`        decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '提现后余额',
  `status`         tinyint(1) NOT NULL DEFAULT '0'  COMMENT '-1拒绝 0待审核 1提现中 2已完成',
  `fail_msg`       varchar(255)          DEFAULT ''  COMMENT '拒绝原因',
  `fail_time`      datetime DEFAULT NULL             COMMENT '拒绝时间',
  `add_time`       datetime DEFAULT NULL,
  `update_time`    datetime DEFAULT NULL,
  `deleted`        tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户提现申请表';