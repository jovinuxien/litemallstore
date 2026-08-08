package org.linlinjava.litemall.goods.domain.service.elastic;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.linlinjava.litemall.db.dao.LitemallCombinationMapper;
import org.linlinjava.litemall.db.domain.LitemallCombination;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Wave-21 {@code groupon_flag} basis: a memoized snapshot of the ACTIVE, in-window
 * {@code litemall_combination} group-buy campaigns, matched against a product's id at
 * document-build time — the {@link CouponSignalResolver} pattern applied to group-buy.
 * Read-only over the shared {@code litemall_combination} table (owned by
 * litemall-promotion-service — same shared-DB precedent as the coupon read). ~60s TTL
 * keeps a full reindex at ONE combination query per minute instead of one per document;
 * a same-day campaign activation lags at most one refresh cycle on incremental upserts
 * (nightly reindex covers the rest — accepted v1 semantics, same as coupon_flag).
 *
 * <p>ACTIVE mirrors promotion's {@code LitemallCombinationAggregate}: status
 * {@code 1} (ACTIVE — {@code LitemallCombinationStatus.ACTIVE}; 0 draft / 2 expired /
 * 3 offline stay dark) AND inside the {@code start_time}/{@code end_time} window (null
 * bound = open). Pink/slot fill is deliberately NOT checked — the flag means "a group-buy
 * campaign exists for this product", and the start/join path stays authoritative.
 *
 * <p>The mapper is hand-written (no generated Example class); {@code selectAll()} binds
 * {@code deleted = 0} literally in its SQL — the inverted {@code andLogicalDeleted()}
 * landmine cannot apply here. Status + window are filtered in Java at refresh time,
 * exactly as {@code CouponSignalResolver} filters its window.
 */
@Component
public class GrouponSignalResolver {

    private static final Logger log = LoggerFactory.getLogger(GrouponSignalResolver.class);
    private static final long TTL_MS = 60 * 1000L;
    /** promotion's LitemallCombinationStatus.ACTIVE (1 进行中). */
    private static final short STATUS_ACTIVE = 1;

    private final LitemallCombinationMapper combinationMapper;

    private volatile List<LitemallCombination> snapshot = List.of();
    private volatile long builtAt = 0L;

    public GrouponSignalResolver(LitemallCombinationMapper combinationMapper) {
        this.combinationMapper = combinationMapper;
    }

    /** 1 when any ACTIVE in-window group-buy campaign targets this goods, else 0. */
    public int grouponFlag(Integer goodsId) {
        if (goodsId == null) {
            return 0;
        }
        for (LitemallCombination campaign : active()) {
            if (goodsId.equals(campaign.getGoodsId())) {
                return 1;
            }
        }
        return 0;
    }

    private List<LitemallCombination> active() {
        long now = System.currentTimeMillis();
        List<LitemallCombination> snap = snapshot;
        if (now - builtAt < TTL_MS) {
            return snap;
        }
        try {
            // selectAll = deleted 0 only (literal in the hand-written SQL); status + window
            // are re-derived here so the snapshot holds exactly the live campaigns.
            List<LitemallCombination> rows = combinationMapper.selectAll();
            List<LitemallCombination> live = new ArrayList<>(rows.size());
            LocalDateTime nowTime = LocalDateTime.now();
            for (LitemallCombination campaign : rows) {
                if (isLive(campaign, nowTime)) {
                    live.add(campaign);
                }
            }
            snapshot = live;
            builtAt = now;
            return live;
        } catch (RuntimeException ex) {
            // Fail-soft: indexing must never die on the combination read — stale (or empty)
            // snapshot simply means the flag lags until the next successful refresh.
            log.warn("groupon_flag snapshot refresh failed (keeping previous): {}", ex.getMessage());
            builtAt = now;
            return snap;
        }
    }

    private static boolean isLive(LitemallCombination campaign, LocalDateTime now) {
        Short status = campaign.getStatus();
        if (status == null || status != STATUS_ACTIVE) {
            return false;
        }
        return (campaign.getStartTime() == null || !now.isBefore(campaign.getStartTime()))
                && (campaign.getEndTime() == null || !now.isAfter(campaign.getEndTime()));
    }
}
