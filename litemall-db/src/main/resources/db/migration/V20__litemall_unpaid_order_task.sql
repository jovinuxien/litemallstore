-- Restart-safe replacement for the in-memory DelayQueue that previously
-- tracked unpaid-order timeouts via litemall-core's TaskService.
-- A @Scheduled sweep in litemall-order drains rows whose due_at has passed.

CREATE TABLE IF NOT EXISTS `litemall_unpaid_order_task` (
    `order_id`   INT          NOT NULL,
    `due_at`     DATETIME     NOT NULL,
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`order_id`),
    KEY `idx_unpaid_order_task_due_at` (`due_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
