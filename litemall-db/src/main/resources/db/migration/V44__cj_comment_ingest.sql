-- V44__cj_comment_ingest.sql
--
-- CJ product reviews, persisted (Wave 8, goods-management).
--
-- The PDP reviews endpoint (/srv/comment/list) previously served CJ-sourced goods
-- straight from the CJ "productComments" API (Redis-memoized pass-through). That
-- breaks the Wave-8 contract twice: nothing survives the cache TTL (and prod has
-- no CJ key at all), and any locally posted customer review suppressed the CJ set
-- entirely. Reviews now follow the demand-driven enrichment precedent: on first
-- view of a CJ good they are fetched once, landed in litemall_comment, and served
-- from the local store forever after — merged with customer-posted rows.
--
-- litemall_comment: source discriminator + external identity for ingested rows.
-- All columns are defaulted/nullable so existing inserts (which never name them)
-- keep working unchanged. MySQL treats NULLs as distinct in a UNIQUE index, so
-- local rows (external_id NULL) are exempt from the uniqueness guard while CJ
-- rows are deduplicated by their CJ commentId — re-ingest is idempotent.
ALTER TABLE litemall_comment
    ADD COLUMN source VARCHAR(32) NOT NULL DEFAULT 'local'
        COMMENT 'origin of the review row: local (customer-posted) | cj (ingested CJ productComments)',
    ADD COLUMN external_id VARCHAR(64) NULL
        COMMENT 'CJ commentId when source=cj (idempotent ingest key)',
    ADD COLUMN author_name VARCHAR(64) NULL
        COMMENT 'external reviewer display name (masked by CJ) when source=cj; local rows join litemall_user',
    ADD COLUMN author_avatar VARCHAR(255) NULL
        COMMENT 'external reviewer avatar (CJ country-flag icon URL) when source=cj',
    ADD UNIQUE KEY uk_comment_source_external (source, external_id);

-- litemall_goods: the demand-driven ingest marker. NULL = never ingested (first
-- /srv/comment read of a CJ good triggers the capped fetch); non-NULL = serve
-- purely from litemall_comment, no CJ traffic. Distinct from litemall_cj_product
-- .reviews_synced_time, which is the nightly AGGREGATE (count/rating) cursor and
-- persists no review bodies.
ALTER TABLE litemall_goods
    ADD COLUMN cj_reviews_ingested_time DATETIME NULL
        COMMENT 'when CJ review bodies were ingested into litemall_comment (demand-driven)';
