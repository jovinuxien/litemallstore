-- Wave 26: authoritative delisting evidence for CJ products.
--
-- WHY: reconcile infers "gone from CJ" from "absent from tonight's fetch". That inference was
-- sound when the plan was a census; it is not now. The plan plans 25 products per leaf across
-- ~540 leaves — a SAMPLE — while the catalogue holds 33,420 rows accumulated from earlier, larger
-- plans. Measured 2026-08-16: a full sweep fetched 14,426 pids, so 19,079 goods (57%) looked
-- "vanished" and the erosion tripwire correctly refused the whole batch. It refuses EVERY week,
-- which means nothing is ever pruned AND a real mass-delisting would be indistinguishable from
-- the weekly false alarm.
--
-- The fix is not a bigger tripwire threshold — it is better evidence. Enrichment already asks CJ
-- about individual products and is told, explicitly, when one no longer exists ("no CJ detail for
-- pid"). That is CJ's own answer about that product, not an inference from a sample.
--
-- Strikes, not a single hit: a not-found can also be a transient upstream hiccup, so delisting
-- requires repeated confirmation across separate enrichment passes.
ALTER TABLE `litemall_cj_product`
    ADD COLUMN `delisted_strikes` INT NOT NULL DEFAULT 0
        COMMENT 'Consecutive times CJ explicitly reported no detail for this pid. Reset to 0 on any successful detail fetch. Delisting acts at >= 2.';

-- The delisting sweep filters on this; without an index it scans the whole snapshot table.
CREATE INDEX `idx_cj_product_delisted_strikes` ON `litemall_cj_product` (`delisted_strikes`);
