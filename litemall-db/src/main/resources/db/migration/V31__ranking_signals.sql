-- Ranking signals for OCS relevance boosting (SuperDeals / New Arrivals / All Products).
-- Populated uniformly for local and CJ goods, read by createProductDocument, indexed as
-- numeric Sort+Score fields, and combined multiplicatively in the searcher scoring-configuration
-- (see Relevant Search ch. 7: general-quality metric + recency decay).

-- litemall_goods: the row createProductDocument reads. listed_num = CJ platform popularity
-- (number of merchant listings; 0/n-a for local), review_count + rating = unified review
-- aggregates (local from litemall_comment, CJ from CJ productComments). Recency continues to
-- ride litemall_goods.add_time (set to the CJ product create date on promote for CJ rows).
ALTER TABLE litemall_goods
    ADD COLUMN listed_num INT DEFAULT 0 COMMENT 'CJ platform listing count (popularity); 0 for local',
    ADD COLUMN review_count INT DEFAULT 0 COMMENT 'Unified review count (local litemall_comment or CJ productComments)',
    ADD COLUMN rating DECIMAL(2,1) DEFAULT 0.0 COMMENT 'Average review star 0.0-5.0';

-- litemall_cj_product: the enrichment pass write target. These mirror the CJ detail response
-- (listedNum, createTime) plus the CJ review aggregate, then flow onto litemall_goods at promote.
ALTER TABLE litemall_cj_product
    ADD COLUMN listed_num INT DEFAULT NULL COMMENT 'CJ detail listedNum (merchant listing count)',
    ADD COLUMN review_count INT DEFAULT NULL COMMENT 'CJ productComments total',
    ADD COLUMN rating DECIMAL(2,1) DEFAULT NULL COMMENT 'Average CJ review score 0.0-5.0',
    ADD COLUMN cj_create_time DATETIME DEFAULT NULL COMMENT 'CJ product createTime (true creation date for recency)',
    ADD COLUMN reviews_synced_time DATETIME DEFAULT NULL COMMENT 'Last CJ review fetch (staleness cursor)';
