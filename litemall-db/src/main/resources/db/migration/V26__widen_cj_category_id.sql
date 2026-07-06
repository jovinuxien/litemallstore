-- CJ category-tree mirroring (full 3-level hierarchy): non-leaf CJ levels have no upstream id,
-- so they are keyed by synthetic natural keys ('L1:<normalized name>', 'L2:<norm l1>/<norm l2>')
-- alongside the leaf UUIDs. Widen cj_category_id so the composed L2 keys always fit.
ALTER TABLE `litemall_category`
    MODIFY COLUMN `cj_category_id` VARCHAR(128) NULL
        COMMENT 'CJ category natural key: leaf UUID, or synthetic L1:/L2: path key for non-leaf tiers; NULL for local categories';
