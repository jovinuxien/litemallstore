-- =============================================================================
-- V41 — Transactional customer mail outbox (Wave 6, Phase C).
-- Backs order's enqueue listeners (paid/shipped/refund-approved/pickup-code)
-- and the @Scheduled sweep that actually delivers via litemall-core's
-- CustomerMailSender. `send_at` is the scheduled-send seam: rows default to
-- NOW but a future timestamp is honored by findSendable, so any writer can
-- schedule mail without new schema.
--
-- V40 is owned by goods-management (deal SKU swap); this wave starts at V41
-- per the Wave-6 numbering agreement (checked against flyway_schema_history).
-- =============================================================================

CREATE TABLE IF NOT EXISTS `litemall_mail_outbox` (
  `id`           int(11) NOT NULL AUTO_INCREMENT,
  `recipient`    varchar(127) NOT NULL              COMMENT 'Destination email address (buyer email at enqueue time)',
  `subject`      varchar(255) NOT NULL              COMMENT 'Rendered subject line',
  `body`         mediumtext                         COMMENT 'Rendered plain-text body',
  `template_key` varchar(63) DEFAULT NULL           COMMENT 'Shared template key: order-confirmation|shipped|refund-approved|pickup-code|password-reset',
  `status`       varchar(15) NOT NULL DEFAULT 'pending' COMMENT 'pending|sent|failed',
  `attempts`     int(11) NOT NULL DEFAULT '0'       COMMENT 'Delivery attempts so far (failed at 5)',
  `send_at`      datetime NOT NULL                  COMMENT 'Earliest delivery time; rows default to enqueue time (future = scheduled send)',
  `last_error`   varchar(511) DEFAULT NULL          COMMENT 'Last delivery error (truncated)',
  `add_time`     datetime DEFAULT NULL,
  `update_time`  datetime DEFAULT NULL,
  `deleted`      tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_mail_outbox_status_send_at` (`status`, `send_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Transactional customer mail outbox (Wave 6)';
