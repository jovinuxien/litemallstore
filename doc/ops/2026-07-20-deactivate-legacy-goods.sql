-- Deactivate the legacy (non-CJ) seed goods — 2026-07-20
--
-- WHY: the 239 legacy goods (the original litemall demo catalog, images on
-- yanxuan.nosdn.127.net) have NO real inventory behind them — an order placed
-- for one cannot be fulfilled. The store therefore sells ONLY the CJ
-- dropshipping catalog for now. The local-goods pipeline (goods management,
-- pricing, checkout, fulfilment seams) is deliberately KEPT working: when real
-- local inventory exists later, those goods will be loaded and flow through
-- exactly the same process. This is why the rows are DEACTIVATED
-- (is_on_sale=0), not deleted.
--
-- SET DEFINITION: legacy == cj_pid IS NULL OR cj_pid = ''. Verified 2026-07-20
-- in both dev and prod: this matches exactly the yanxuan-image set (239 rows;
-- 238 were on sale) and zero CJ rows.
--
-- HOW TO APPLY (idempotent — safe to re-run):
--   dev : mysql -uroot -p litemall < doc/ops/2026-07-20-deactivate-legacy-goods.sql
--   prod: pipe the same file into the mysql container, then REINDEX:
--         cd /opt/litemall/docker-compose
--         docker exec -i litemall-prod-mysql-1 sh -c \
--           'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" litemall' < .../this-file.sql
--         ./litemall-prod.sh reindex     # <- REQUIRED: search serves from ES,
--                                        #    not MySQL; without it the goods
--                                        #    stay visible in the storefront.
--
-- SIDE EFFECT: `litemall-prod.sh smoke-checkout` picks the cheapest LOCAL SKU
-- on purpose (a CJ line would drag fulfilment-availability into a money-path
-- test). With every local good off sale it now reports "no in-stock goods";
-- to run it, temporarily re-activate ONE legacy good, reindex, test, revert.
--
-- ROLLBACK (when real local inventory is registered, or for a smoke test):
--   -- everything:  UPDATE litemall_goods SET is_on_sale = 1
--   --              WHERE deleted = 0 AND (cj_pid IS NULL OR cj_pid = '');
--   -- one good:    UPDATE litemall_goods SET is_on_sale = 1 WHERE id = <id>;
--   -- then reindex.

UPDATE litemall_goods
SET    is_on_sale = 0,
       update_time = NOW()
WHERE  deleted = 0
  AND  is_on_sale = 1
  AND  (cj_pid IS NULL OR cj_pid = '');

-- Expected on first run: 238 rows changed (dev and prod both). 0 on re-run.
SELECT CONCAT('legacy still on sale: ', COUNT(*)) AS check_result
FROM   litemall_goods
WHERE  deleted = 0 AND is_on_sale = 1 AND (cj_pid IS NULL OR cj_pid = '');
