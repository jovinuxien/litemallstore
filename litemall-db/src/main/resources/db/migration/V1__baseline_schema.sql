-- =============================================================================
-- V1 — Baseline schema (current litemall state as of initial Flyway adoption)
-- Applied automatically on a fresh database.
-- On an existing database, baseline-on-migrate=true marks this version as done
-- without executing it, so no tables are dropped or recreated.
-- =============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS `litemall_ad` (
  `id`          int(11) NOT NULL AUTO_INCREMENT,
  `name`        varchar(63) NOT NULL DEFAULT '' COMMENT '广告标题',
  `link`        varchar(255) NOT NULL DEFAULT '' COMMENT '广告链接地址',
  `url`         varchar(255) NOT NULL COMMENT '广告宣传图片',
  `position`    tinyint(3) DEFAULT '1' COMMENT '广告位置：1首页',
  `content`     varchar(255) DEFAULT '' COMMENT '活动内容',
  `start_time`  datetime DEFAULT NULL COMMENT '广告开始时间',
  `end_time`    datetime DEFAULT NULL COMMENT '广告结束时间',
  `enabled`     tinyint(1) DEFAULT '0' COMMENT '是否启动',
  `add_time`    datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted`     tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='广告表';

CREATE TABLE IF NOT EXISTS `litemall_address` (
  `id`             int(11) NOT NULL AUTO_INCREMENT,
  `name`           varchar(63) NOT NULL DEFAULT '' COMMENT '收货人名称',
  `user_id`        int(11) NOT NULL DEFAULT '0' COMMENT '用户ID',
  `province`       varchar(63) NOT NULL COMMENT '省',
  `city`           varchar(63) NOT NULL COMMENT '市',
  `county`         varchar(63) NOT NULL COMMENT '区县',
  `address_detail` varchar(127) NOT NULL DEFAULT '' COMMENT '详细收货地址',
  `area_code`      char(6) DEFAULT NULL COMMENT '地区编码',
  `postal_code`    char(6) DEFAULT NULL COMMENT '邮政编码',
  `tel`            varchar(20) NOT NULL DEFAULT '' COMMENT '手机号码',
  `is_default`     tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否默认地址',
  `add_time`       datetime DEFAULT NULL COMMENT '创建时间',
  `update_time`    datetime DEFAULT NULL COMMENT '更新时间',
  `deleted`        tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收货地址表';

CREATE TABLE IF NOT EXISTS `litemall_admin` (
  `id`              int(11) NOT NULL AUTO_INCREMENT,
  `username`        varchar(63) NOT NULL DEFAULT '' COMMENT '管理员名称',
  `password`        varchar(63) NOT NULL DEFAULT '' COMMENT '管理员密码',
  `last_login_ip`   varchar(63) DEFAULT '' COMMENT '最近登录IP',
  `last_login_time` datetime DEFAULT NULL COMMENT '最近登录时间',
  `avatar`          varchar(255) DEFAULT '' COMMENT '头像图片',
  `add_time`        datetime DEFAULT NULL COMMENT '创建时间',
  `update_time`     datetime DEFAULT NULL COMMENT '更新时间',
  `deleted`         tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  `role_ids`        varchar(127) DEFAULT '[]' COMMENT '角色列表',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员表';

CREATE TABLE IF NOT EXISTS `litemall_aftersale` (
  `id`          int(11) NOT NULL AUTO_INCREMENT,
  `aftersale_sn` varchar(63) DEFAULT NULL COMMENT '售后编号',
  `order_id`    int(11) NOT NULL COMMENT '订单ID',
  `user_id`     int(11) NOT NULL COMMENT '用户ID',
  `type`        smallint(6) DEFAULT '0' COMMENT '售后类型 0未收货退款 1已收货退款 2退货退款',
  `reason`      varchar(31) DEFAULT '' COMMENT '退款原因',
  `amount`      decimal(10,2) DEFAULT '0.00' COMMENT '退款金额',
  `pictures`    varchar(1023) DEFAULT '[]' COMMENT '退款凭证图片',
  `comment`     varchar(511) DEFAULT '' COMMENT '退款说明',
  `status`      smallint(6) DEFAULT '0' COMMENT '售后状态 0可申请 1已申请 2审核通过 3退款成功 4拒绝 5用户取消',
  `handle_time` datetime DEFAULT NULL COMMENT '管理员操作时间',
  `add_time`    datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `deleted`     tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='售后表';

CREATE TABLE IF NOT EXISTS `litemall_brand` (
  `id`          int(11) NOT NULL AUTO_INCREMENT,
  `name`        varchar(255) NOT NULL DEFAULT '' COMMENT '品牌名称',
  `desc`        varchar(255) NOT NULL DEFAULT '' COMMENT '品牌简介',
  `pic_url`     varchar(255) NOT NULL DEFAULT '' COMMENT '品牌图片',
  `sort_order`  tinyint(3) DEFAULT '50',
  `floor_price` decimal(10,2) DEFAULT '0.00' COMMENT '品牌商品低价',
  `add_time`    datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `deleted`     tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='品牌商表';

CREATE TABLE IF NOT EXISTS `litemall_cart` (
  `id`             int(11) NOT NULL AUTO_INCREMENT,
  `user_id`        int(11) DEFAULT NULL COMMENT '用户ID',
  `goods_id`       int(11) DEFAULT NULL COMMENT '商品ID',
  `goods_sn`       varchar(63) DEFAULT NULL COMMENT '商品编号',
  `goods_name`     varchar(127) DEFAULT NULL COMMENT '商品名称',
  `product_id`     int(11) DEFAULT NULL COMMENT '货品ID',
  `price`          decimal(10,2) DEFAULT '0.00' COMMENT '货品价格',
  `number`         smallint(5) DEFAULT '0' COMMENT '货品数量',
  `specifications` varchar(1023) DEFAULT NULL COMMENT '规格值列表JSON',
  `checked`        tinyint(1) DEFAULT '1' COMMENT '是否选中',
  `pic_url`        varchar(255) DEFAULT NULL COMMENT '商品图片',
  `add_time`       datetime DEFAULT NULL,
  `update_time`    datetime DEFAULT NULL,
  `deleted`        tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车商品表';

CREATE TABLE IF NOT EXISTS `litemall_category` (
  `id`          int(11) NOT NULL AUTO_INCREMENT,
  `name`        varchar(63) NOT NULL DEFAULT '' COMMENT '类目名称',
  `keywords`    varchar(1023) NOT NULL DEFAULT '' COMMENT '关键字JSON',
  `desc`        varchar(255) DEFAULT '' COMMENT '类目广告语',
  `pid`         int(11) NOT NULL DEFAULT '0' COMMENT '父类目ID',
  `icon_url`    varchar(255) DEFAULT '' COMMENT '类目图标',
  `pic_url`     varchar(255) DEFAULT '' COMMENT '类目图片',
  `level`       varchar(255) DEFAULT 'L1',
  `sort_order`  tinyint(3) DEFAULT '50' COMMENT '排序',
  `add_time`    datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `deleted`     tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `parent_id` (`pid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='类目表';

CREATE TABLE IF NOT EXISTS `litemall_collect` (
  `id`          int(11) NOT NULL AUTO_INCREMENT,
  `user_id`     int(11) NOT NULL DEFAULT '0' COMMENT '用户ID',
  `value_id`    int(11) NOT NULL DEFAULT '0' COMMENT 'type=0商品ID type=1专题ID',
  `type`        tinyint(3) NOT NULL DEFAULT '0' COMMENT '收藏类型 0商品 1专题',
  `add_time`    datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `deleted`     tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `goods_id` (`value_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收藏表';

CREATE TABLE IF NOT EXISTS `litemall_comment` (
  `id`            int(11) NOT NULL AUTO_INCREMENT,
  `value_id`      int(11) NOT NULL DEFAULT '0' COMMENT 'type=0商品 type=1专题',
  `type`          tinyint(3) NOT NULL DEFAULT '0' COMMENT '评论类型',
  `content`       varchar(1023) DEFAULT '' COMMENT '评论内容',
  `admin_content` varchar(511) DEFAULT '' COMMENT '管理员回复',
  `user_id`       int(11) NOT NULL DEFAULT '0' COMMENT '用户ID',
  `has_picture`   tinyint(1) DEFAULT '0' COMMENT '是否含图片',
  `pic_urls`      varchar(1023) DEFAULT NULL COMMENT '图片地址列表JSON',
  `star`          smallint(6) DEFAULT '1' COMMENT '评分 1-5',
  `add_time`      datetime DEFAULT NULL,
  `update_time`   datetime DEFAULT NULL,
  `deleted`       tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `id_value` (`value_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论表';

CREATE TABLE IF NOT EXISTS `litemall_coupon` (
  `id`          int(11) NOT NULL AUTO_INCREMENT,
  `name`        varchar(63) NOT NULL COMMENT '优惠券名称',
  `desc`        varchar(127) DEFAULT '' COMMENT '优惠券介绍',
  `tag`         varchar(63) DEFAULT '' COMMENT '优惠券标签',
  `total`       int(11) NOT NULL DEFAULT '0' COMMENT '总数 0无限量',
  `discount`    decimal(10,2) DEFAULT '0.00' COMMENT '优惠金额',
  `min`         decimal(10,2) DEFAULT '0.00' COMMENT '最低消费',
  `limit`       smallint(6) DEFAULT '1' COMMENT '限领数量 0不限制',
  `type`        smallint(6) DEFAULT '0' COMMENT '赠送类型 0通用 1注册赠 2码兑换',
  `status`      smallint(6) DEFAULT '0' COMMENT '状态 0正常 1过期 2下架',
  `goods_type`  smallint(6) DEFAULT '0' COMMENT '商品限制 0全场 1类目 2商品',
  `goods_value` varchar(1023) DEFAULT '[]' COMMENT '商品限制值JSON',
  `code`        varchar(63) DEFAULT NULL COMMENT '兑换码',
  `time_type`   smallint(6) DEFAULT '0' COMMENT '有效时间类型 0领取后days天 1固定时间段',
  `days`        smallint(6) DEFAULT '0' COMMENT '领取后有效天数',
  `start_time`  datetime DEFAULT NULL COMMENT '券开始时间',
  `end_time`    datetime DEFAULT NULL COMMENT '券截止时间',
  `add_time`    datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `deleted`     tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠券信息及规则表';

CREATE TABLE IF NOT EXISTS `litemall_coupon_user` (
  `id`          int(11) NOT NULL AUTO_INCREMENT,
  `user_id`     int(11) NOT NULL COMMENT '用户ID',
  `coupon_id`   int(11) NOT NULL COMMENT '优惠券ID',
  `status`      smallint(6) DEFAULT '0' COMMENT '状态 0未使用 1已使用 2已过期 3已下架',
  `used_time`   datetime DEFAULT NULL COMMENT '使用时间',
  `start_time`  datetime DEFAULT NULL COMMENT '有效期开始',
  `end_time`    datetime DEFAULT NULL COMMENT '有效期截止',
  `order_id`    int(11) DEFAULT NULL COMMENT '订单ID',
  `add_time`    datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `deleted`     tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠券用户使用表';

CREATE TABLE IF NOT EXISTS `litemall_feedback` (
  `id`          int(11) NOT NULL AUTO_INCREMENT,
  `user_id`     int(11) NOT NULL DEFAULT '0' COMMENT '用户ID',
  `username`    varchar(63) NOT NULL DEFAULT '' COMMENT '用户名称',
  `mobile`      varchar(20) NOT NULL DEFAULT '' COMMENT '手机号',
  `feed_type`   varchar(63) NOT NULL DEFAULT '' COMMENT '反馈类型',
  `content`     varchar(1023) NOT NULL COMMENT '反馈内容',
  `status`      int(3) NOT NULL DEFAULT '0' COMMENT '状态',
  `has_picture` tinyint(1) DEFAULT '0' COMMENT '是否含图片',
  `pic_urls`    varchar(1023) DEFAULT NULL COMMENT '图片地址JSON',
  `add_time`    datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `deleted`     tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `id_value` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='意见反馈表';

CREATE TABLE IF NOT EXISTS `litemall_footprint` (
  `id`          int(11) NOT NULL AUTO_INCREMENT,
  `user_id`     int(11) NOT NULL DEFAULT '0' COMMENT '用户ID',
  `goods_id`    int(11) NOT NULL DEFAULT '0' COMMENT '浏览商品ID',
  `add_time`    datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `deleted`     tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户浏览足迹表';

CREATE TABLE IF NOT EXISTS `litemall_goods` (
  `id`            int(11) NOT NULL AUTO_INCREMENT,
  `goods_sn`      varchar(63) NOT NULL DEFAULT '' COMMENT '商品编号',
  `name`          varchar(127) NOT NULL DEFAULT '' COMMENT '商品名称',
  `category_id`   int(11) DEFAULT '0' COMMENT '类目ID',
  `brand_id`      int(11) DEFAULT '0',
  `gallery`       varchar(1023) DEFAULT NULL COMMENT '宣传图片JSON',
  `keywords`      varchar(255) DEFAULT '' COMMENT '关键字',
  `brief`         varchar(255) DEFAULT '' COMMENT '商品简介',
  `is_on_sale`    tinyint(1) DEFAULT '1' COMMENT '是否上架',
  `sort_order`    smallint(4) DEFAULT '100',
  `pic_url`       varchar(255) DEFAULT NULL COMMENT '商品图片',
  `share_url`     varchar(255) DEFAULT NULL COMMENT '分享海报',
  `is_new`        tinyint(1) DEFAULT '0' COMMENT '是否新品首发',
  `is_hot`        tinyint(1) DEFAULT '0' COMMENT '是否人气推荐',
  `unit`          varchar(31) DEFAULT '件' COMMENT '商品单位',
  `counter_price` decimal(10,2) DEFAULT '0.00' COMMENT '专柜价格',
  `retail_price`  decimal(10,2) DEFAULT '100000.00' COMMENT '零售价格',
  `detail`        text COMMENT '商品详细介绍富文本',
  `add_time`      datetime DEFAULT NULL,
  `update_time`   datetime DEFAULT NULL,
  `deleted`       tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `goods_sn` (`goods_sn`),
  KEY `cat_id` (`category_id`),
  KEY `brand_id` (`brand_id`),
  KEY `sort_order` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品基本信息表';

CREATE TABLE IF NOT EXISTS `litemall_goods_attribute` (
  `id`          int(11) NOT NULL AUTO_INCREMENT,
  `goods_id`    int(11) NOT NULL DEFAULT '0' COMMENT '商品ID',
  `attribute`   varchar(255) NOT NULL COMMENT '参数名称',
  `value`       varchar(255) NOT NULL COMMENT '参数值',
  `add_time`    datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `deleted`     tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `goods_id` (`goods_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品参数表';

CREATE TABLE IF NOT EXISTS `litemall_goods_product` (
  `id`             int(11) NOT NULL AUTO_INCREMENT,
  `goods_id`       int(11) NOT NULL DEFAULT '0' COMMENT '商品ID',
  `specifications` varchar(1023) NOT NULL COMMENT '规格值列表JSON',
  `price`          decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '货品价格',
  `number`         int(11) NOT NULL DEFAULT '0' COMMENT '货品数量',
  `url`            varchar(125) DEFAULT NULL COMMENT '货品图片',
  `add_time`       datetime DEFAULT NULL,
  `update_time`    datetime DEFAULT NULL,
  `deleted`        tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `goods_id` (`goods_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品货品表';

CREATE TABLE IF NOT EXISTS `litemall_goods_specification` (
  `id`            int(11) NOT NULL AUTO_INCREMENT,
  `goods_id`      int(11) NOT NULL DEFAULT '0' COMMENT '商品ID',
  `specification` varchar(255) NOT NULL DEFAULT '' COMMENT '规格名称',
  `value`         varchar(255) NOT NULL DEFAULT '' COMMENT '规格值',
  `pic_url`       varchar(255) NOT NULL DEFAULT '' COMMENT '规格图片',
  `add_time`      datetime DEFAULT NULL,
  `update_time`   datetime DEFAULT NULL,
  `deleted`       tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `goods_id` (`goods_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品规格表';

CREATE TABLE IF NOT EXISTS `litemall_groupon` (
  `id`               int(11) NOT NULL AUTO_INCREMENT,
  `order_id`         int(11) NOT NULL COMMENT '关联订单ID',
  `groupon_id`       int(11) DEFAULT '0' COMMENT '开团为0 参团为活动ID',
  `rules_id`         int(11) NOT NULL COMMENT '团购规则ID',
  `user_id`          int(11) NOT NULL COMMENT '用户ID',
  `share_url`        varchar(255) DEFAULT NULL COMMENT '团购分享图片',
  `creator_user_id`  int(11) NOT NULL COMMENT '开团用户ID',
  `creator_user_time` datetime DEFAULT NULL COMMENT '开团时间',
  `status`           smallint(6) DEFAULT '0' COMMENT '状态 0未支付 1进行中 2失败',
  `add_time`         datetime NOT NULL,
  `update_time`      datetime DEFAULT NULL,
  `deleted`          tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT='团购活动表';

CREATE TABLE IF NOT EXISTS `litemall_groupon_rules` (
  `id`              int(11) NOT NULL AUTO_INCREMENT,
  `goods_id`        int(11) NOT NULL COMMENT '商品ID',
  `goods_name`      varchar(127) NOT NULL COMMENT '商品名称',
  `pic_url`         varchar(255) DEFAULT NULL COMMENT '商品图片',
  `discount`        decimal(63,0) NOT NULL COMMENT '优惠金额',
  `discount_member` int(11) NOT NULL COMMENT '达到优惠条件人数',
  `expire_time`     datetime DEFAULT NULL COMMENT '团购过期时间',
  `status`          smallint(6) DEFAULT '0' COMMENT '状态 0正常 1到期下线 2手动下线',
  `add_time`        datetime NOT NULL,
  `update_time`     datetime DEFAULT NULL,
  `deleted`         tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `goods_id` (`goods_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT='团购规则表';

CREATE TABLE IF NOT EXISTS `litemall_issue` (
  `id`          int(11) NOT NULL AUTO_INCREMENT,
  `question`    varchar(255) DEFAULT NULL COMMENT '问题标题',
  `answer`      varchar(255) DEFAULT NULL COMMENT '问题答案',
  `add_time`    datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `deleted`     tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='常见问题表';

CREATE TABLE IF NOT EXISTS `litemall_keyword` (
  `id`          int(11) NOT NULL AUTO_INCREMENT,
  `keyword`     varchar(127) NOT NULL DEFAULT '' COMMENT '关键字',
  `url`         varchar(255) NOT NULL DEFAULT '' COMMENT '跳转链接',
  `is_hot`      tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否热门',
  `is_default`  tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否默认',
  `sort_order`  int(11) NOT NULL DEFAULT '100' COMMENT '排序',
  `add_time`    datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `deleted`     tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='关键字表';

CREATE TABLE IF NOT EXISTS `litemall_log` (
  `id`          int(11) NOT NULL AUTO_INCREMENT,
  `admin`       varchar(45) DEFAULT NULL COMMENT '管理员',
  `ip`          varchar(45) DEFAULT NULL COMMENT '管理员IP',
  `type`        int(11) DEFAULT NULL COMMENT '操作分类',
  `action`      varchar(45) DEFAULT NULL COMMENT '操作动作',
  `status`      tinyint(1) DEFAULT NULL COMMENT '操作状态',
  `result`      varchar(127) DEFAULT NULL COMMENT '操作结果',
  `comment`     varchar(255) DEFAULT NULL COMMENT '补充信息',
  `add_time`    datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `deleted`     tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表';

CREATE TABLE IF NOT EXISTS `litemall_notice` (
  `id`          int(11) NOT NULL AUTO_INCREMENT,
  `title`       varchar(63) DEFAULT NULL COMMENT '通知标题',
  `content`     varchar(511) DEFAULT NULL COMMENT '通知内容',
  `admin_id`    int(11) DEFAULT '0' COMMENT '创建管理员ID 0=系统',
  `add_time`    datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `deleted`     tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知表';

CREATE TABLE IF NOT EXISTS `litemall_notice_admin` (
  `id`           int(11) NOT NULL AUTO_INCREMENT,
  `notice_id`    int(11) DEFAULT NULL COMMENT '通知ID',
  `notice_title` varchar(63) DEFAULT NULL COMMENT '通知标题',
  `admin_id`     int(11) DEFAULT NULL COMMENT '接收管理员ID',
  `read_time`    datetime DEFAULT NULL COMMENT '阅读时间 NULL=未读',
  `add_time`     datetime DEFAULT NULL,
  `update_time`  datetime DEFAULT NULL,
  `deleted`      tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知管理员表';

CREATE TABLE IF NOT EXISTS `litemall_order` (
  `id`               int(11) NOT NULL AUTO_INCREMENT,
  `user_id`          int(11) NOT NULL COMMENT '用户ID',
  `order_sn`         varchar(63) NOT NULL COMMENT '订单编号',
  `order_status`     smallint(6) NOT NULL COMMENT '订单状态',
  `aftersale_status` smallint(6) DEFAULT '0' COMMENT '售后状态',
  `consignee`        varchar(63) NOT NULL COMMENT '收货人',
  `mobile`           varchar(63) NOT NULL COMMENT '收货手机',
  `address`          varchar(127) NOT NULL COMMENT '收货地址',
  `message`          varchar(512) NOT NULL DEFAULT '' COMMENT '留言',
  `goods_price`      decimal(10,2) NOT NULL COMMENT '商品总费用',
  `freight_price`    decimal(10,2) NOT NULL COMMENT '配送费用',
  `coupon_price`     decimal(10,2) NOT NULL COMMENT '优惠券减免',
  `integral_price`   decimal(10,2) NOT NULL COMMENT '积分减免',
  `groupon_price`    decimal(10,2) NOT NULL COMMENT '团购减免',
  `order_price`      decimal(10,2) NOT NULL COMMENT '订单费用',
  `actual_price`     decimal(10,2) NOT NULL COMMENT '实付费用',
  `pay_id`           varchar(63) DEFAULT NULL COMMENT '支付流水号',
  `pay_time`         datetime DEFAULT NULL COMMENT '支付时间',
  `ship_sn`          varchar(63) DEFAULT NULL COMMENT '发货编号',
  `ship_channel`     varchar(63) DEFAULT NULL COMMENT '快递公司',
  `ship_time`        datetime DEFAULT NULL COMMENT '发货时间',
  `refund_amount`    decimal(10,2) DEFAULT NULL COMMENT '退款金额',
  `refund_type`      varchar(63) DEFAULT NULL COMMENT '退款方式',
  `refund_content`   varchar(127) DEFAULT NULL COMMENT '退款备注',
  `refund_time`      datetime DEFAULT NULL COMMENT '退款时间',
  `confirm_time`     datetime DEFAULT NULL COMMENT '确认收货时间',
  `comments`         smallint(6) DEFAULT '0' COMMENT '待评价商品数',
  `end_time`         datetime DEFAULT NULL COMMENT '订单关闭时间',
  `add_time`         datetime DEFAULT NULL,
  `update_time`      datetime DEFAULT NULL,
  `deleted`          tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

CREATE TABLE IF NOT EXISTS `litemall_order_goods` (
  `id`             int(11) NOT NULL AUTO_INCREMENT,
  `order_id`       int(11) NOT NULL DEFAULT '0' COMMENT '订单ID',
  `goods_id`       int(11) NOT NULL DEFAULT '0' COMMENT '商品ID',
  `goods_name`     varchar(127) NOT NULL DEFAULT '' COMMENT '商品名称',
  `goods_sn`       varchar(63) NOT NULL DEFAULT '' COMMENT '商品编号',
  `product_id`     int(11) NOT NULL DEFAULT '0' COMMENT '货品ID',
  `number`         smallint(5) NOT NULL DEFAULT '0' COMMENT '购买数量',
  `price`          decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '售价',
  `specifications` varchar(1023) NOT NULL COMMENT '规格列表',
  `pic_url`        varchar(255) NOT NULL DEFAULT '' COMMENT '商品图片',
  `comment`        int(11) DEFAULT '0' COMMENT '评论ID -1超期 0可评 >0已评',
  `add_time`       datetime DEFAULT NULL,
  `update_time`    datetime DEFAULT NULL,
  `deleted`        tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `order_id` (`order_id`),
  KEY `goods_id` (`goods_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单商品表';

CREATE TABLE IF NOT EXISTS `litemall_permission` (
  `id`          int(11) NOT NULL AUTO_INCREMENT,
  `role_id`     int(11) DEFAULT NULL COMMENT '角色ID',
  `permission`  varchar(63) DEFAULT NULL COMMENT '权限',
  `add_time`    datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `deleted`     tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限表';

CREATE TABLE IF NOT EXISTS `litemall_region` (
  `id`   int(11) NOT NULL AUTO_INCREMENT,
  `pid`  int(11) NOT NULL DEFAULT '0' COMMENT '父级ID',
  `name` varchar(120) NOT NULL DEFAULT '' COMMENT '行政区域名称',
  `type` tinyint(3) NOT NULL DEFAULT '0' COMMENT '类型 1省 2市 3区县',
  `code` int(11) NOT NULL DEFAULT '0' COMMENT '行政区域编码',
  PRIMARY KEY (`id`),
  KEY `parent_id` (`pid`),
  KEY `region_type` (`type`),
  KEY `agency_id` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行政区域表';

CREATE TABLE IF NOT EXISTS `litemall_role` (
  `id`          int(11) NOT NULL AUTO_INCREMENT,
  `name`        varchar(63) NOT NULL COMMENT '角色名称',
  `desc`        varchar(1023) DEFAULT NULL COMMENT '角色描述',
  `enabled`     tinyint(1) DEFAULT '1' COMMENT '是否启用',
  `add_time`    datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `deleted`     tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `name_UNIQUE` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

CREATE TABLE IF NOT EXISTS `litemall_search_history` (
  `id`          int(11) NOT NULL AUTO_INCREMENT,
  `user_id`     int(11) NOT NULL COMMENT '用户ID',
  `keyword`     varchar(63) NOT NULL COMMENT '搜索关键字',
  `from`        varchar(63) NOT NULL DEFAULT '' COMMENT '来源 pc/wx/app',
  `add_time`    datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `deleted`     tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='搜索历史表';

CREATE TABLE IF NOT EXISTS `litemall_storage` (
  `id`          int(11) NOT NULL AUTO_INCREMENT,
  `key`         varchar(63) NOT NULL COMMENT '文件唯一索引',
  `name`        varchar(255) NOT NULL COMMENT '文件名',
  `type`        varchar(20) NOT NULL COMMENT '文件类型',
  `size`        int(11) NOT NULL COMMENT '文件大小',
  `url`         varchar(255) DEFAULT NULL COMMENT '文件访问链接',
  `add_time`    datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `deleted`     tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `key` (`key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件存储表';

CREATE TABLE IF NOT EXISTS `litemall_system` (
  `id`          int(11) NOT NULL AUTO_INCREMENT,
  `key_name`    varchar(255) NOT NULL COMMENT '系统配置名',
  `key_value`   varchar(255) NOT NULL COMMENT '系统配置值',
  `add_time`    datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `deleted`     tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT='系统配置表';

CREATE TABLE IF NOT EXISTS `litemall_topic` (
  `id`          int(11) NOT NULL AUTO_INCREMENT,
  `title`       varchar(255) NOT NULL DEFAULT '' COMMENT '专题标题',
  `subtitle`    varchar(255) DEFAULT '' COMMENT '专题子标题',
  `content`     text COMMENT '专题内容富文本',
  `price`       decimal(10,2) DEFAULT '0.00' COMMENT '相关商品最低价',
  `read_count`  varchar(255) DEFAULT '1k' COMMENT '阅读量',
  `pic_url`     varchar(255) DEFAULT '' COMMENT '专题图片',
  `sort_order`  int(11) DEFAULT '100' COMMENT '排序',
  `goods`       varchar(1023) DEFAULT '' COMMENT '相关商品JSON',
  `add_time`    datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `deleted`     tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `topic_id` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专题表';

CREATE TABLE IF NOT EXISTS `litemall_user` (
  `id`              int(11) NOT NULL AUTO_INCREMENT,
  `username`        varchar(63) NOT NULL COMMENT '用户名称',
  `password`        varchar(63) NOT NULL DEFAULT '' COMMENT '用户密码',
  `gender`          tinyint(3) NOT NULL DEFAULT '0' COMMENT '性别 0未知 1男 2女',
  `birthday`        date DEFAULT NULL COMMENT '生日',
  `last_login_time` datetime DEFAULT NULL COMMENT '最近登录时间',
  `last_login_ip`   varchar(63) NOT NULL DEFAULT '' COMMENT '最近登录IP',
  `user_level`      tinyint(3) DEFAULT '0' COMMENT '0普通 1VIP 2高级VIP',
  `nickname`        varchar(63) NOT NULL DEFAULT '' COMMENT '昵称',
  `mobile`          varchar(20) NOT NULL DEFAULT '' COMMENT '手机号',
  `avatar`          varchar(255) NOT NULL DEFAULT '' COMMENT '头像',
  `weixin_openid`   varchar(63) NOT NULL DEFAULT '' COMMENT '微信openid',
  `session_key`     varchar(100) NOT NULL DEFAULT '' COMMENT '微信会话KEY',
  `status`          tinyint(3) NOT NULL DEFAULT '0' COMMENT '0可用 1禁用 2注销',
  `add_time`        datetime DEFAULT NULL,
  `update_time`     datetime DEFAULT NULL,
  `deleted`         tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `user_name` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

CREATE TABLE IF NOT EXISTS `litemall_recommendation` (
  `id`                   int(11) NOT NULL AUTO_INCREMENT,
  `user_id`              int(11) NOT NULL,
  `recommendation_type`  varchar(50) NOT NULL,
  `algorithm_version`    varchar(20) NOT NULL,
  `confidence_score`     decimal(5,4) DEFAULT '0.0000',
  `status`               varchar(20) DEFAULT 'ACTIVE',
  `context_page_type`    varchar(50) DEFAULT NULL,
  `context_device_type`  varchar(50) DEFAULT NULL,
  `context_user_segment` varchar(50) DEFAULT NULL,
  `context_data`         json DEFAULT NULL,
  `created_at`           datetime NOT NULL,
  `expires_at`           datetime NOT NULL,
  `updated_at`           datetime NOT NULL,
  `metadata`             json DEFAULT NULL,
  `deleted`              tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_type_status` (`recommendation_type`,`status`),
  KEY `idx_expires_at` (`expires_at`),
  CONSTRAINT `fk_recommendation_user` FOREIGN KEY (`user_id`) REFERENCES `litemall_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='推荐表';

CREATE TABLE IF NOT EXISTS `litemall_recommendation_item` (
  `id`                 int(11) NOT NULL AUTO_INCREMENT,
  `recommendation_id`  int(11) NOT NULL,
  `goods_id`           int(11) NOT NULL,
  `goods_name`         varchar(255) NOT NULL,
  `price`              decimal(10,2) DEFAULT NULL,
  `image_url`          varchar(255) DEFAULT NULL,
  `reason_code`        varchar(50) DEFAULT NULL,
  `reason_description` text,
  `reason_data`        json DEFAULT NULL,
  `confidence_score`   decimal(5,4) DEFAULT '0.0000',
  `recommended_at`     datetime NOT NULL,
  `clicked_at`         datetime DEFAULT NULL,
  `purchased_at`       datetime DEFAULT NULL,
  `click_count`        int(11) DEFAULT '0',
  `is_expired`         tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_recommendation_id` (`recommendation_id`),
  KEY `idx_goods_id` (`goods_id`),
  CONSTRAINT `fk_recommendation_item` FOREIGN KEY (`recommendation_id`) REFERENCES `litemall_recommendation` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='推荐商品明细表';

CREATE TABLE IF NOT EXISTS `litemall_user_behavior` (
  `id`            int(11) NOT NULL AUTO_INCREMENT,
  `user_id`       int(11) NOT NULL,
  `goods_id`      int(11) DEFAULT NULL,
  `behavior_type` varchar(50) NOT NULL,
  `page_type`     varchar(50) DEFAULT NULL,
  `device_type`   varchar(50) DEFAULT NULL,
  `behavior_data` json DEFAULT NULL,
  `created_at`    datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_goods_id` (`goods_id`),
  KEY `idx_behavior_type` (`behavior_type`),
  KEY `idx_created_at` (`created_at`),
  CONSTRAINT `fk_behavior_user` FOREIGN KEY (`user_id`) REFERENCES `litemall_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户行为追踪表';

SET FOREIGN_KEY_CHECKS = 1;