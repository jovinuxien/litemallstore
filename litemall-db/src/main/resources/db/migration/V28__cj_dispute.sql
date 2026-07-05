-- V28__cj_dispute.sql
--
-- Local record of a CJ Dropshipping dispute opened for a source='cj' order
-- (customer had a problem — not received / damaged / wrong item). CJ OWNS the
-- dispute lifecycle; this table is our side of the anticorruption boundary:
-- ownership enforcement (user_id), idempotent creation (business_dispute_id is
-- the merchant key CJ dedupes on), and a lazily-refreshed projection of CJ's
-- status so My Orders can show it without a CJ round-trip per page view.
--
--   business_dispute_id -> OUR deterministic key sent to disputes/create
--                          (CJ's create returns only `true`; their id arrives
--                          later via getDisputeList and is back-filled below).
--   cj_dispute_id       -> CJ's dispute id once known (needed for cancel).
--   status/finally_deal/refund_amount/resend_order_code -> CJ projection,
--                          refreshed when the customer views their disputes.

CREATE TABLE IF NOT EXISTS `litemall_cj_dispute` (
    `id`                   INT NOT NULL AUTO_INCREMENT,
    `order_id`             INT NOT NULL COMMENT 'litemall_order.id (source=cj) this dispute belongs to',
    `user_id`              INT NOT NULL COMMENT 'owner — every read/write scopes to this',
    `cj_order_id`          VARCHAR(64) NOT NULL COMMENT 'CJ order id the dispute was raised against',
    `business_dispute_id`  VARCHAR(100) NOT NULL COMMENT 'our idempotency key sent to disputes/create',
    `cj_dispute_id`        VARCHAR(100) NULL COMMENT 'CJ dispute id, back-filled from getDisputeList',
    `reason_id`            INT NULL COMMENT 'CJ disputeReasonId chosen at creation',
    `reason_name`          VARCHAR(255) NULL,
    `expect_type`          SMALLINT NOT NULL COMMENT '1=refund, 2=reissue (CjDisputeExpectation)',
    `message`              VARCHAR(512) NOT NULL DEFAULT '',
    `image_urls`           VARCHAR(1024) NULL COMMENT 'comma-separated evidence image URLs',
    `status`               VARCHAR(64) NULL COMMENT 'CJ status string as last seen',
    `finally_deal`         SMALLINT NULL COMMENT 'CJ resolution: 1=refund, 2=reissue, 3=reject (CjDisputeResolution)',
    `refund_amount`        DECIMAL(10,2) NULL COMMENT 'USD amount CJ refunded (informational; customer money-back stays on the local refund flow)',
    `resend_order_code`    VARCHAR(100) NULL COMMENT 'CJ reissue order code when finally_deal=2',
    `cancelled`            TINYINT(1) NOT NULL DEFAULT 0,
    `add_time`             DATETIME NULL,
    `update_time`          DATETIME NULL,
    `deleted`              TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cj_dispute_business_id` (`business_dispute_id`),
    KEY `idx_cj_dispute_order` (`order_id`),
    KEY `idx_cj_dispute_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT 'CJ Dropshipping dispute projection per order (V28)';
