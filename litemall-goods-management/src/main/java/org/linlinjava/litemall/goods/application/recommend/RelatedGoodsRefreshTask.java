package org.linlinjava.litemall.goods.application.recommend;

import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.service.LitemallGoodsRelatedService;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Item-item co-occurrence batch behind the detail page's "related items" rail
 * (Deals wave, Phase C). Recomputes {@code litemall_goods_related} wholesale:
 * co-purchase pairs (distinct paid orders containing both goods) weighted ×3
 * blended with co-view pairs (distinct users with both goods in their 90-day
 * footprints) ×1, top {@value #TOP_N} strongest-first per goods. Only on-sale
 * non-deleted goods appear on either side of a row; rows whose signal vanished
 * are deleted, so the read path's category fallback takes back over cleanly.
 *
 * <p>Nightly at {@code litemall.recommend.refresh-cron} (default 02:45, before
 * the 03:00/03:30 CJ crons) plus on demand via
 * {@code POST /srv/private/admin/recommend/rebuild}.
 */
@Component
public class RelatedGoodsRefreshTask {

    private static final Logger log = LoggerFactory.getLogger(RelatedGoodsRefreshTask.class);

    private static final long PURCHASE_WEIGHT = 3;
    private static final long VIEW_WEIGHT = 1;
    private static final int TOP_N = 8;

    private final LitemallGoodsRelatedService relatedService;
    private final LitemallGoodsService goodsService;

    @Value("${litemall.recommend.refresh-enabled:true}")
    private boolean refreshEnabled;

    public RelatedGoodsRefreshTask(LitemallGoodsRelatedService relatedService,
                                   LitemallGoodsService goodsService) {
        this.relatedService = relatedService;
        this.goodsService = goodsService;
    }

    @Scheduled(cron = "${litemall.recommend.refresh-cron:0 45 2 * * *}")
    public void nightly() {
        if (!refreshEnabled) {
            return;
        }
        try {
            int rows = rebuild();
            log.info("related-goods nightly refresh wrote {} rows", rows);
        } catch (Exception e) {
            // Never let one bad run kill the schedule.
            log.error("related-goods nightly refresh failed", e);
        }
    }

    /** Full recompute; returns the number of goods rows written. Synchronized: cron and admin trigger share one run. */
    public synchronized int rebuild() {
        Map<Integer, Map<Integer, Long>> scores = new HashMap<>();
        accumulate(scores, relatedService.queryCoPurchasePairs(), PURCHASE_WEIGHT);
        accumulate(scores, relatedService.queryCoViewPairs(), VIEW_WEIGHT);

        Set<Integer> involved = new HashSet<>(scores.keySet());
        for (Map<Integer, Long> neighbors : scores.values()) {
            involved.addAll(neighbors.keySet());
        }
        Set<Integer> valid = new HashSet<>();
        if (!involved.isEmpty()) {
            // queryByIds filters is_on_sale + !deleted — off-sale ids never waste a top-N slot.
            for (LitemallGoods goods : goodsService.queryByIds(involved.toArray(new Integer[0]))) {
                valid.add(goods.getId());
            }
        }

        Set<Integer> written = new HashSet<>();
        for (Map.Entry<Integer, Map<Integer, Long>> entry : scores.entrySet()) {
            if (!valid.contains(entry.getKey())) {
                continue;
            }
            String relatedIds = entry.getValue().entrySet().stream()
                    .filter(e -> valid.contains(e.getKey()))
                    .sorted(Comparator.<Map.Entry<Integer, Long>>comparingLong(Map.Entry::getValue).reversed()
                            .thenComparing(Map.Entry::getKey))
                    .limit(TOP_N)
                    .map(e -> String.valueOf(e.getKey()))
                    .collect(Collectors.joining(","));
            if (relatedIds.isEmpty()) {
                continue;
            }
            relatedService.upsert(entry.getKey(), relatedIds);
            written.add(entry.getKey());
        }

        // Stale cleanup: rows whose co-occurrence signal disappeared (goods off-sale,
        // footprints aged out) fall back to the category query on read.
        List<Integer> stale = new ArrayList<>();
        for (Integer existing : relatedService.queryAllGoodsIds()) {
            if (!written.contains(existing)) {
                stale.add(existing);
            }
        }
        stale.forEach(relatedService::deleteByGoodsId);
        if (!stale.isEmpty()) {
            log.info("related-goods refresh pruned {} stale rows", stale.size());
        }
        return written.size();
    }

    private static void accumulate(Map<Integer, Map<Integer, Long>> scores,
                                   List<Map<String, Object>> pairs, long weight) {
        for (Map<String, Object> row : pairs) {
            Integer goodsId = ((Number) row.get("goodsId")).intValue();
            Integer relatedId = ((Number) row.get("relatedId")).intValue();
            long score = ((Number) row.get("weight")).longValue() * weight;
            scores.computeIfAbsent(goodsId, k -> new HashMap<>()).merge(relatedId, score, Long::sum);
        }
    }
}
