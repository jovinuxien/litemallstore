-- V49__behavioral_event_log.sql
--
-- Behavioral targeting Phase 0 (goods-management): first-party event log.
-- Contract: doc/behavioral-events.md (frozen vocabulary, edge-owned identity,
-- strict prior consent).
--
-- 1) litemall_user_event — append-only behavioral log. Hybrid shape: indexed
--    hot columns for everything filtered/joined on, JSON payload for the long
--    tail. DELIBERATE deviations from house convention, per the contract:
--    no deleted column (events are a log, not a mutable entity — soft-delete
--    semantics do not apply and the column would only cost index space) and
--    no add_time/update_time (occurred_at/received_at are the timeline).
--    visitor_id/session_id are NULLable: server-origin rows (purchase/refund
--    from the Stripe-webhook path) have no browser cookie and join through
--    user_id + litemall_visitor_identity instead. No raw IP or user agent is
--    ever stored — country_code/device_type are derived at ingest.
--
-- 2) litemall_visitor_identity — visitor->user stitching, written when a
--    batch arrives carrying both identities (login, guest-claim). Events stay
--    immutable; identity resolves at query time through this table.
--
-- 3) litemall_consent_record — server-side lawful-basis audit of consent
--    choices (the SPA's localStorage grant alone cannot prove anything).

CREATE TABLE litemall_user_event (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    event_id CHAR(36) NOT NULL COMMENT 'client-generated UUID v4 (server rows: deterministic); idempotency key',
    visitor_id CHAR(36) NULL COMMENT 'edge-minted lm_vid; NULL for server-origin rows',
    session_id CHAR(36) NULL COMMENT 'edge-minted lm_sid (30-min inactivity); NULL for server-origin rows',
    user_id INT(11) NULL COMMENT 'edge-verified X-User-Id; NULL until login, never backfilled',
    event_type VARCHAR(32) NOT NULL COMMENT 'frozen vocabulary — doc/behavioral-events.md',
    origin TINYINT NOT NULL COMMENT '1 = client (SPA via /srv/track/collect), 2 = server (order service)',
    occurred_at DATETIME(3) NOT NULL COMMENT 'client clock, clamped to [received_at - 7d, received_at + 5min]',
    received_at DATETIME(3) NOT NULL COMMENT 'server clock — the audit anchor',
    goods_id INT(11) NULL,
    product_id INT(11) NULL,
    category_id INT(11) NULL,
    search_query VARCHAR(255) NULL,
    position SMALLINT NULL COMMENT 'result rank for click_result',
    page_type VARCHAR(32) NULL,
    locale VARCHAR(8) NULL COMMENT 'first Accept-Language tag',
    country_code CHAR(2) NULL COMMENT 'from CF-IPCountry at ingest; raw IP is never stored',
    device_type VARCHAR(16) NULL COMMENT 'mobile | tablet | desktop | bot, derived then UA discarded',
    payload JSON NULL COMMENT 'long-tail attributes; purchase/refund carry order lines here',
    PRIMARY KEY (id),
    UNIQUE KEY uk_event (event_id),
    KEY idx_visitor_time (visitor_id, occurred_at),
    KEY idx_user_time (user_id, occurred_at),
    KEY idx_type_time (event_type, occurred_at),
    KEY idx_goods_type (goods_id, event_type, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='append-only first-party behavioral event log (behavioral targeting Phase 0); no deleted column by design';

CREATE TABLE litemall_visitor_identity (
    id INT(11) NOT NULL AUTO_INCREMENT,
    visitor_id CHAR(36) NOT NULL,
    user_id INT(11) NOT NULL,
    linked_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_link (visitor_id, user_id),
    KEY idx_identity_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='visitor->user stitching (behavioral targeting Phase 0); events stay immutable, identity resolves at query time';

CREATE TABLE litemall_consent_record (
    id INT(11) NOT NULL AUTO_INCREMENT,
    visitor_id CHAR(36) NULL COMMENT 'NULL for a denial that never had identity minted (by design under strict prior consent)',
    user_id INT(11) NULL,
    choice VARCHAR(8) NOT NULL COMMENT 'granted | denied',
    scope VARCHAR(32) NOT NULL DEFAULT 'analytics',
    occurred_at DATETIME(3) NULL COMMENT 'client clock, clamped like events',
    received_at DATETIME(3) NOT NULL,
    country_code CHAR(2) NULL COMMENT 'from CF-IPCountry',
    PRIMARY KEY (id),
    KEY idx_consent_visitor (visitor_id, received_at),
    KEY idx_consent_time (received_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='server-side lawful-basis audit of consent choices (behavioral targeting Phase 0)';
