-- V57__search_stats.sql
--
-- Wave 22 (search demand analytics): per-day per-keyword search rollup.
--
-- 1) litemall_search_stat_daily — nightly aggregate over BOTH demand sources:
--    litemall_search_history (logged-in searches; zero_results via the new
--    result_count column) and the Phase-0 behavioral log litemall_user_event
--    (anonymous consented `search` events + `click_result` clicks). Rows are
--    recomputed absolutely per (day, keyword) — the upsert overwrites counts,
--    so re-running a day is idempotent. Keywords are normalized at rollup
--    (trim + lowercase, capped at 127) — the UNIQUE key is on the normalized
--    form. Aggregate-only by design: no visitor/user ids ever land here.
--
-- 2) litemall_search_history.result_count — the hit total the search returned,
--    recorded by the search endpoint after the OCS round-trip. NULL = unknown
--    (pre-V57 rows, or the OCS call failed after the intent was recorded);
--    0 = a real zero-result search. Turns zero-result queries from log-only
--    warnings (SearchService "zero-results search") into queryable data.

CREATE TABLE litemall_search_stat_daily (
    id INT NOT NULL AUTO_INCREMENT,
    `day` DATE NOT NULL,
    keyword VARCHAR(127) NOT NULL COMMENT 'normalized (trim + lowercase, max 127) search keyword',
    searches INT NOT NULL DEFAULT 0 COMMENT 'logged-in history rows + anonymous consented search events',
    zero_results INT NOT NULL DEFAULT 0 COMMENT 'searches that returned 0 hits (history result_count = 0)',
    clicks INT NOT NULL DEFAULT 0 COMMENT 'click_result behavioral events attributed to this keyword',
    add_time DATETIME DEFAULT NULL,
    update_time DATETIME DEFAULT NULL,
    deleted TINYINT(1) DEFAULT '0',
    PRIMARY KEY (id),
    UNIQUE KEY uk_day_keyword (`day`, keyword),
    KEY idx_stat_keyword (keyword)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='per-day per-keyword search demand rollup (Wave 22); aggregate-only, no identities';

ALTER TABLE litemall_search_history
    ADD COLUMN result_count INT NULL
        COMMENT 'total hits this search returned; NULL = unknown (pre-V57 row or search failed), 0 = zero-result search'
        AFTER `from`;
