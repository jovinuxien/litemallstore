-- =============================================================================
-- V12 — System enhancement tables (grouped config, admin menu, SMS)
-- Undo: db/undo/U12__undo_system_enhancements.sql
-- =============================================================================

-- Grouped config (extends litemall_system with namespaced key-value sets)
CREATE TABLE IF NOT EXISTS `litemall_system_group` (
  `id`          int(11) NOT NULL AUTO_INCREMENT,
  `config_name` varchar(50) NOT NULL DEFAULT '' COMMENT '配置组名(唯一标识)',
  `desc`        varchar(255)         DEFAULT ''  COMMENT '配置组说明',
  `add_time`    datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `deleted`     tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_config_name` (`config_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置分组表';

CREATE TABLE IF NOT EXISTS `litemall_system_group_data` (
  `id`          int(11) NOT NULL AUTO_INCREMENT,
  `config_name` varchar(50) NOT NULL DEFAULT '' COMMENT '所属配置组名',
  `value`       text                            COMMENT '数据值(JSON格式)',
  `sort`        int(11) NOT NULL DEFAULT '0'   COMMENT '排序',
  `status`      tinyint(1) NOT NULL DEFAULT '1' COMMENT '1显示 0隐藏',
  `add_time`    datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `deleted`     tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_config_name` (`config_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置分组数据表';

-- Admin menu tree (enables dynamic sidebar without code changes)
CREATE TABLE IF NOT EXISTS `litemall_system_menu` (
  `id`          int(11) NOT NULL AUTO_INCREMENT,
  `pid`         int(11) NOT NULL DEFAULT '0'   COMMENT '父菜单ID 0=顶级',
  `menu_name`   varchar(64) NOT NULL DEFAULT '' COMMENT '菜单名称',
  `path`        varchar(128)         DEFAULT ''  COMMENT '前端路由path',
  `component`   varchar(128)         DEFAULT ''  COMMENT '前端组件路径',
  `auth`        varchar(64)          DEFAULT ''  COMMENT '权限标识(如 admin:goods:list)',
  `icon`        varchar(64)          DEFAULT ''  COMMENT '菜单图标',
  `menu_type`   tinyint(1) NOT NULL DEFAULT '0' COMMENT '0目录 1菜单 2按钮',
  `sort`        int(11) NOT NULL DEFAULT '0'    COMMENT '排序',
  `is_show`     tinyint(1) NOT NULL DEFAULT '1' COMMENT '1显示 0隐藏',
  `add_time`    datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `deleted`     tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_pid` (`pid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台菜单表';
