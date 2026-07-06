-- =============================================================================
-- V25 — Transactional outbox for order domain events.
--
-- Today order events publish in-process and a forwarder sends 3 of them to Kafka
-- AFTER_COMMIT — a JVM crash between commit and send loses the event. This table is
-- the durable fix: each event is inserted in the SAME transaction as the order change
-- (status PENDING); a scheduled relay forwards PENDING rows to the message broker and
-- flips them to SENT. At-least-once delivery, no silent loss.
-- Undo: db/undo/U25__undo_event_outbox.sql
-- =============================================================================

CREATE TABLE IF NOT EXISTS `litemall_event_outbox` (
  `id`             bigint(20)  NOT NULL AUTO_INCREMENT,
  `aggregate_type` varchar(64)          DEFAULT '' COMMENT '聚合类型, e.g. order',
  `aggregate_id`   varchar(64)          DEFAULT '' COMMENT '聚合ID (订单ID)',
  `event_type`     varchar(255) NOT NULL COMMENT '事件类型(全限定类名)',
  `binding`        varchar(64)          DEFAULT NULL COMMENT 'Spring Cloud Stream binding; null = 仅审计不外发',
  `payload`        mediumtext   NOT NULL COMMENT '事件JSON',
  `status`         varchar(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SENT/FAILED',
  `attempts`       int(11)      NOT NULL DEFAULT 0,
  `created_at`     datetime     NOT NULL,
  `sent_at`        datetime              DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_status_id` (`status`, `id`),
  KEY `idx_aggregate` (`aggregate_type`, `aggregate_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='领域事件事务发件箱';
