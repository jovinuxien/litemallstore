-- =============================================================================
-- V2 — Enrich existing tables with fields borrowed from CRMEB schema.
-- All changes are non-breaking: new nullable columns with safe defaults.
-- Undo: db/undo/U2__undo_enrich_existing_tables.sql
-- =============================================================================

-- ---- litemall_user ----------------------------------------------------------
ALTER TABLE `litemall_user`
  ADD COLUMN `now_money`       decimal(16,2) NOT NULL DEFAULT '0.00'  COMMENT '钱包余额'        AFTER `status`,
  ADD COLUMN `brokerage_price` decimal(8,2)  NOT NULL DEFAULT '0.00'  COMMENT '佣金余额'        AFTER `now_money`,
  ADD COLUMN `integral`        int(11)       NOT NULL DEFAULT '0'     COMMENT '剩余积分'         AFTER `brokerage_price`,
  ADD COLUMN `experience`      int(11)       NOT NULL DEFAULT '0'     COMMENT '剩余经验值'       AFTER `integral`,
  ADD COLUMN `sign_num`        int(11)       NOT NULL DEFAULT '0'     COMMENT '连续签到天数'     AFTER `experience`,
  ADD COLUMN `spread_uid`      int(10)       NOT NULL DEFAULT '0'     COMMENT '推广员ID'         AFTER `sign_num`,
  ADD COLUMN `spread_time`     datetime               DEFAULT NULL    COMMENT '推广员关联时间'   AFTER `spread_uid`,
  ADD COLUMN `is_promoter`     tinyint(1)    NOT NULL DEFAULT '0'     COMMENT '是否推广员'       AFTER `spread_time`,
  ADD COLUMN `pay_count`       int(11)       NOT NULL DEFAULT '0'     COMMENT '购买次数'         AFTER `is_promoter`,
  ADD COLUMN `spread_count`    int(11)       NOT NULL DEFAULT '0'     COMMENT '下级人数'         AFTER `pay_count`,
  ADD COLUMN `card_id`         varchar(20)            DEFAULT ''      COMMENT '身份证号'         AFTER `spread_count`,
  ADD COLUMN `group_id`        varchar(127)           DEFAULT ''      COMMENT '用户分组ID'       AFTER `card_id`,
  ADD COLUMN `tag_id`          varchar(127)           DEFAULT ''      COMMENT '标签ID'           AFTER `group_id`,
  ADD COLUMN `login_type`      varchar(36)            DEFAULT ''      COMMENT '登录类型 h5/wechat/routine' AFTER `tag_id`,
  ADD COLUMN `path`            varchar(255)           DEFAULT '/0/'   COMMENT '推广等级链路'     AFTER `login_type`,
  ADD COLUMN `subscribe`       tinyint(1)             DEFAULT '0'     COMMENT '是否关注公众号'   AFTER `path`,
  ADD COLUMN `subscribe_time`  datetime               DEFAULT NULL    COMMENT '关注公众号时间'   AFTER `subscribe`,
  ADD COLUMN `country`         varchar(20)            DEFAULT 'CN'    COMMENT '国家代码'         AFTER `subscribe_time`;

-- ---- litemall_address -------------------------------------------------------
ALTER TABLE `litemall_address`
  ADD COLUMN `city_id`   int(11)     NOT NULL DEFAULT '0'  COMMENT '城市ID(关联litemall_region)' AFTER `county`,
  ADD COLUMN `longitude` varchar(16) NOT NULL DEFAULT '0'  COMMENT '经度'                        AFTER `is_default`,
  ADD COLUMN `latitude`  varchar(16) NOT NULL DEFAULT '0'  COMMENT '纬度'                        AFTER `longitude`;

-- ---- litemall_goods ---------------------------------------------------------
ALTER TABLE `litemall_goods`
  ADD COLUMN `vip_price`    decimal(10,2) NOT NULL DEFAULT '0.00'  COMMENT 'VIP会员价格'            AFTER `retail_price`,
  ADD COLUMN `cost`         decimal(10,2) NOT NULL DEFAULT '0.00'  COMMENT '成本价'                 AFTER `vip_price`,
  ADD COLUMN `give_integral` int(11)      NOT NULL DEFAULT '0'     COMMENT '购买赠送积分'            AFTER `cost`,
  ADD COLUMN `is_seckill`   tinyint(1)    NOT NULL DEFAULT '0'     COMMENT '是否秒杀商品'            AFTER `give_integral`,
  ADD COLUMN `is_bargain`   tinyint(1)    NOT NULL DEFAULT '0'     COMMENT '是否砍价商品'            AFTER `is_seckill`,
  ADD COLUMN `is_benefit`   tinyint(1)    NOT NULL DEFAULT '0'     COMMENT '是否优惠商品'            AFTER `is_bargain`,
  ADD COLUMN `is_best`      tinyint(1)    NOT NULL DEFAULT '0'     COMMENT '是否精品推荐'            AFTER `is_benefit`,
  ADD COLUMN `is_postage`   tinyint(1)    NOT NULL DEFAULT '0'     COMMENT '是否包邮'               AFTER `is_best`,
  ADD COLUMN `postage`      decimal(10,2) NOT NULL DEFAULT '0.00'  COMMENT '邮费'                   AFTER `is_postage`,
  ADD COLUMN `fictitious`   int(11)       NOT NULL DEFAULT '0'     COMMENT '虚拟销量'               AFTER `postage`,
  ADD COLUMN `browse`       int(11)       NOT NULL DEFAULT '0'     COMMENT '浏览量'                 AFTER `fictitious`,
  ADD COLUMN `bar_code`     varchar(15)            DEFAULT ''      COMMENT '商品条码'               AFTER `browse`,
  ADD COLUMN `video_link`   varchar(200)           DEFAULT ''      COMMENT '主图视频链接'            AFTER `bar_code`,
  ADD COLUMN `spec_type`    tinyint(1)    NOT NULL DEFAULT '0'     COMMENT '规格类型 0单规格 1多规格' AFTER `video_link`,
  ADD COLUMN `temp_id`      int(11)       NOT NULL DEFAULT '0'     COMMENT '运费模板ID'              AFTER `spec_type`,
  ADD COLUMN `weight`       decimal(8,2)  NOT NULL DEFAULT '0.00'  COMMENT '重量kg'                 AFTER `temp_id`,
  ADD COLUMN `volume`       decimal(8,2)  NOT NULL DEFAULT '0.00'  COMMENT '体积m³'                 AFTER `weight`,
  ADD COLUMN `version`      int(11)       NOT NULL DEFAULT '0'     COMMENT '并发版本号(乐观锁)'      AFTER `volume`;

-- ---- litemall_goods_attribute -----------------------------------------------
ALTER TABLE `litemall_goods_attribute`
  ADD COLUMN `type` tinyint(1) NOT NULL DEFAULT '0' COMMENT '属性类型 0商品 1秒杀 2砍价 3拼团' AFTER `value`;

-- ---- litemall_goods_product (SKUs) ------------------------------------------
ALTER TABLE `litemall_goods_product`
  ADD COLUMN `cost`     decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '成本价'  AFTER `number`,
  ADD COLUMN `bar_code` varchar(15)            DEFAULT ''     COMMENT 'SKU条码' AFTER `cost`,
  ADD COLUMN `weight`   decimal(8,2)  NOT NULL DEFAULT '0.00' COMMENT '重量kg'  AFTER `bar_code`,
  ADD COLUMN `volume`   decimal(8,2)  NOT NULL DEFAULT '0.00' COMMENT '体积m³'  AFTER `weight`;

-- ---- litemall_cart ----------------------------------------------------------
ALTER TABLE `litemall_cart`
  ADD COLUMN `seckill_id`     int(11) NOT NULL DEFAULT '0' COMMENT '秒杀商品ID(0=无)' AFTER `checked`,
  ADD COLUMN `bargain_id`     int(11) NOT NULL DEFAULT '0' COMMENT '砍价ID(0=无)'     AFTER `seckill_id`,
  ADD COLUMN `combination_id` int(11) NOT NULL DEFAULT '0' COMMENT '拼团ID(0=无)'     AFTER `bargain_id`;

-- ---- litemall_order ---------------------------------------------------------
ALTER TABLE `litemall_order`
  ADD COLUMN `deduction_price` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '钱包抵扣金额'   AFTER `groupon_price`,
  ADD COLUMN `total_num`       int(11)       NOT NULL DEFAULT '0'    COMMENT '订单商品总数'   AFTER `deduction_price`,
  ADD COLUMN `seckill_id`      int(11)       NOT NULL DEFAULT '0'    COMMENT '秒杀活动ID 0=无' AFTER `total_num`,
  ADD COLUMN `bargain_id`      int(11)       NOT NULL DEFAULT '0'    COMMENT '砍价活动ID 0=无' AFTER `seckill_id`;

-- ---- litemall_coupon --------------------------------------------------------
ALTER TABLE `litemall_coupon`
  ADD COLUMN `last_total`         int(11)    NOT NULL DEFAULT '0' COMMENT '剩余发放数量'         AFTER `total`,
  ADD COLUMN `is_limited`         tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否限量'             AFTER `last_total`,
  ADD COLUMN `use_type`           tinyint(2) NOT NULL DEFAULT '1' COMMENT '使用类型 1全场 2商品 3品类' AFTER `goods_value`,
  ADD COLUMN `primary_key`        varchar(255)        DEFAULT ''  COMMENT '关联商品/类目ID串'    AFTER `use_type`,
  ADD COLUMN `receive_start_time` datetime            DEFAULT NULL COMMENT '可领取开始时间'      AFTER `primary_key`,
  ADD COLUMN `receive_end_time`   datetime            DEFAULT NULL COMMENT '可领取结束时间'      AFTER `receive_start_time`,
  ADD COLUMN `is_fixed_time`      tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否固定使用时间段'  AFTER `receive_end_time`,
  ADD COLUMN `sort`               int(11)    NOT NULL DEFAULT '0' COMMENT '排序'                 AFTER `is_fixed_time`;

-- ---- litemall_coupon_user ---------------------------------------------------
ALTER TABLE `litemall_coupon_user`
  ADD COLUMN `use_type`    tinyint(1) NOT NULL DEFAULT '1' COMMENT '使用类型 1全场 2商品 3品类' AFTER `order_id`,
  ADD COLUMN `primary_key` varchar(255)        DEFAULT NULL COMMENT '关联商品/类目ID串'         AFTER `use_type`;