package org.linlinjava.litemall.goods.application.inventoryflow;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.linlinjava.litemall.db.dao.InsightMapper;
import org.linlinjava.litemall.db.dao.LitemallRetireCandidateMapper;
import org.linlinjava.litemall.db.domain.LitemallRetireCandidate;
import org.linlinjava.litemall.goods.infrastructure.configuration.InventoryFlowProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Wave-14 catalog-size governor. After each catalog run's inventory flow completes, it measures
 * the on-sale CJ catalog against {@code litemall.inventoryflow.catalog-target} and — when the
 * catalog overshoots — tops today's retire proposals up to roughly the overage (never below it
 * while the weakest-goods pool lasts) by ranking on-sale goods weakest-first (no sales, then no
 * views, then thin/unknown margin, then low stock). Hard availability problems are proposed by
 * {@link RetireCandidateScorer} on the flow lanes regardless of the target.
 *
 * <p>Proposes only — an admin approves, the executor flips. At/below target it proposes nothing.
 * Every sizing decision is logged; a pool that runs dry is reported, never papered over.
 */
@Component
public class RetirementGovernor {

    private static final Logger log = LoggerFactory.getLogger(RetirementGovernor.class);

    /** Pool over-fetch factor: rows can be filtered by decisions/cooldowns, so ask for slack. */
    private static final int POOL_SLACK_FACTOR = 2;

    private final InsightMapper insightMapper;
    private final LitemallRetireCandidateMapper retireMapper;
    private final RetireCandidateScorer scorer;
    private final InventoryFlowProperties properties;

    public RetirementGovernor(InsightMapper insightMapper,
                              LitemallRetireCandidateMapper retireMapper,
                              RetireCandidateScorer scorer,
                              InventoryFlowProperties properties) {
        this.insightMapper = insightMapper;
        this.retireMapper = retireMapper;
        this.scorer = scorer;
        this.properties = properties;
    }

    /** Invoked from the flow aggregator after a CATALOG run (never the enrichment trickle). */
    public void governAfterCatalogRun() {
        try {
            govern();
        } catch (RuntimeException ex) {
            log.warn("retirement governor failed (skipped this run): {}", ex.getMessage());
        }
    }

    private void govern() {
        long onSale = insightMapper.countOnSaleCj();
        int target = properties.getCatalogTarget();
        long overage = onSale - target;
        if (overage <= 0) {
            log.info("retirement governor: {} on-sale CJ goods vs target {} — no overage, "
                    + "only hard availability proposals apply", onSale, target);
            return;
        }

        List<LitemallRetireCandidate> today =
                retireMapper.selectByDay(LocalDate.now(), null);
        Set<Integer> alreadyToday = new HashSet<>();
        int proposedToday = 0;
        for (LitemallRetireCandidate c : today) {
            alreadyToday.add(c.getGoodsId());
            if (LitemallRetireCandidate.STATUS_PROPOSED.equals(c.getStatus())) {
                proposedToday++;
            }
        }
        long need = overage - proposedToday;
        if (need <= 0) {
            log.info("retirement governor: overage {} already covered by {} proposals today",
                    overage, proposedToday);
            return;
        }

        int poolSize = (int) Math.min(Integer.MAX_VALUE, need * POOL_SLACK_FACTOR + today.size());
        List<Map<String, Object>> pool = insightMapper.selectWeakestOnSale(poolSize);
        int toppedUp = 0;
        for (Map<String, Object> row : pool) {
            if (toppedUp >= need) {
                break;
            }
            Integer goodsId = intOf(row.get("goodsId"));
            if (goodsId == null || alreadyToday.contains(goodsId)) {
                continue;
            }
            if (scorer.proposeWeak(goodsId, weaknessReasons(row, onSale, target))) {
                toppedUp++;
            }
        }
        if (toppedUp < need) {
            log.warn("retirement governor: overage {} but only {} new proposals possible "
                            + "({} already proposed today, weakest pool of {} exhausted)",
                    overage, toppedUp, proposedToday, pool.size());
        } else {
            log.info("retirement governor: {} on-sale vs target {} — {} proposals today "
                            + "({} governance top-ups)",
                    onSale, target, proposedToday + toppedUp, toppedUp);
        }
    }

    private List<String> weaknessReasons(Map<String, Object> row, long onSale, int target) {
        java.util.ArrayList<String> reasons = new java.util.ArrayList<>();
        reasons.add("catalog governance: " + onSale + " on-sale vs target " + target);
        Number sales = (Number) row.get("salesQty");
        Number views = (Number) row.get("views");
        if (sales != null && sales.longValue() == 0) {
            reasons.add("never sold");
        }
        if (views != null && views.longValue() == 0) {
            reasons.add("never viewed");
        }
        Object marginPct = row.get("marginPct");
        if (marginPct == null) {
            reasons.add("cost not captured — margin unknown");
        } else if (marginPct instanceof Number n && n.doubleValue() <= 0) {
            reasons.add("no positive margin at current retail");
        }
        return reasons;
    }

    private static Integer intOf(Object o) {
        return o instanceof Number n ? n.intValue() : null;
    }
}
