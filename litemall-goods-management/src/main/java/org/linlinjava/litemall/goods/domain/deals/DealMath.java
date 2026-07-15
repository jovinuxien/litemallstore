package org.linlinjava.litemall.goods.domain.deals;

import org.linlinjava.litemall.db.domain.LitemallSeckill;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Pure flash-deal arithmetic shared by the indexer (document signals), the
 * lifecycle scheduler and the admin/customer views — one formula each for
 * urgency, claimed-% and epoch conversion so ranking, countdown and bar can
 * never disagree.
 */
public final class DealMath {

    /**
     * {@code deal_end_epoch} emitted for documents with NO live deal (2100-01-01 UTC).
     * Every document must carry the field — OCS resolves filter/sort/score fields against
     * the index MAPPING, which only materializes once some document has held the field;
     * a full reindex with zero live deals would otherwise silently disable the deal
     * filter/sort/scoring until the next searcher restart (found live 2026-07-16). The
     * far-future value keeps "ending soon" (ascending) correct — live deals always sort
     * first — and never reaches clients: the DTO maps deal fields only when
     * {@code deal_active=1}.
     */
    public static final long NO_DEAL_END_EPOCH = 4102444800000L;

    private DealMath() {
    }

    /** 0–100 gaussian urgency in hours-to-end: 100 now, ~61 at 24h, ~14 at 48h, ~0 past 3 days. */
    public static int urgencyOf(LocalDateTime stopTime) {
        if (stopTime == null) {
            return 0;
        }
        long minutesLeft = Math.max(0, Duration.between(LocalDateTime.now(), stopTime).toMinutes());
        double hours = minutesLeft / 60.0;
        return (int) Math.round(100 * Math.exp(-0.5 * Math.pow(hours / 24.0, 2)));
    }

    /** Claimed percent of a capped deal, or null when uncapped (stock ≤ 0). */
    public static Integer claimedPct(LitemallSeckill deal) {
        if (deal.getStock() == null || deal.getStock() <= 0 || deal.getSales() == null) {
            return null;
        }
        return BigDecimal.valueOf(Math.min(deal.getSales(), deal.getStock()))
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(deal.getStock()), 0, RoundingMode.HALF_UP)
                .intValue();
    }

    /**
     * DB datetimes are naive server-local values (MySQL {@code NOW()}); the service and MySQL
     * share a host/zone in every environment we run, so systemDefault is the faithful conversion
     * ({@code created_epoch}'s UTC convention predates this and only feeds relative ordering).
     */
    public static Long toEpochMilli(LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
