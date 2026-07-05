package org.linlinjava.litemall.goods.application.comment;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.linlinjava.litemall.db.domain.LitemallComment;
import org.linlinjava.litemall.db.service.LitemallCommentService;
import org.linlinjava.litemall.goods.domain.model.aggregates.LitemallGoodsAggregate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-goods review aggregates (average star + count) for listing surfaces, computed from
 * {@code litemall_comment} in ONE batched query per page of items (no per-item round-trips).
 * CJ-sourced items ({@code cj_<pid>} ids) have no local reviews and are skipped — their
 * rating enrichment is a nightly-sync follow-up, not a live per-item CJ call (1-QPS quota).
 */
@Service
public class CommentStatsService {

    /** Average star (1 decimal, HALF_UP) and review count for one goods. */
    public record Stats(BigDecimal star, int reviewCount) {}

    private final LitemallCommentService commentService;
    private final ObjectMapper objectMapper;

    public CommentStatsService(LitemallCommentService commentService, ObjectMapper objectMapper) {
        this.commentService = commentService;
        this.objectMapper = objectMapper;
    }

    /** Review stats for a batch of goods ids; goods without reviews are absent from the map. */
    public Map<Integer, Stats> batchStats(Collection<Integer> goodsIds) {
        Map<Integer, Stats> stats = new HashMap<>();
        if (goodsIds == null || goodsIds.isEmpty()) {
            return stats;
        }
        Map<Integer, int[]> acc = new HashMap<>(); // valueId -> [starSum, count]
        for (LitemallComment c : commentService.queryByValueIds((byte) 0, new ArrayList<>(new java.util.LinkedHashSet<>(goodsIds)))) {
            if (c.getValueId() == null) {
                continue;
            }
            int[] a = acc.computeIfAbsent(c.getValueId(), k -> new int[2]);
            a[0] += c.getStar() != null ? c.getStar() : 0;
            a[1] += 1;
        }
        for (Map.Entry<Integer, int[]> e : acc.entrySet()) {
            BigDecimal avg = BigDecimal.valueOf(e.getValue()[0])
                    .divide(BigDecimal.valueOf(e.getValue()[1]), 1, RoundingMode.HALF_UP);
            stats.put(e.getKey(), new Stats(avg, e.getValue()[1]));
        }
        return stats;
    }

    /**
     * Decorate map-shaped list items (OCS hits) in place with {@code star} + {@code reviewCount}.
     * Items whose {@code id} does not parse as an int (e.g. {@code cj_<pid>} docs) are skipped.
     */
    public void decorate(List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        Map<Integer, Map<String, Object>> byId = new HashMap<>();
        for (Map<String, Object> item : items) {
            Integer id = parseId(item.get("id"));
            if (id != null) {
                byId.put(id, item);
            }
        }
        for (Map.Entry<Integer, Stats> e : batchStats(byId.keySet()).entrySet()) {
            Map<String, Object> item = byId.get(e.getKey());
            item.put("star", e.getValue().star());
            item.put("reviewCount", e.getValue().reviewCount());
        }
    }

    /**
     * Aggregate list → Jackson-identical maps plus {@code star}/{@code reviewCount} for the
     * DB-served listing branches (same JSON shape as before, two extra keys on reviewed goods).
     */
    public List<Map<String, Object>> withStats(List<LitemallGoodsAggregate> goodsList) {
        List<Map<String, Object>> items = new ArrayList<>(goodsList == null ? 0 : goodsList.size());
        if (goodsList == null || goodsList.isEmpty()) {
            return items;
        }
        List<Integer> ids = new ArrayList<>(goodsList.size());
        for (LitemallGoodsAggregate goods : goodsList) {
            @SuppressWarnings("unchecked")
            Map<String, Object> item = objectMapper.convertValue(goods, Map.class);
            items.add(item);
            if (goods.getGoodsId() != null) {
                ids.add(goods.getGoodsId().getId());
            }
        }
        Map<Integer, Stats> stats = batchStats(ids);
        for (int i = 0; i < goodsList.size(); i++) {
            LitemallGoodsAggregate goods = goodsList.get(i);
            Stats s = goods.getGoodsId() != null ? stats.get(goods.getGoodsId().getId()) : null;
            if (s != null) {
                items.get(i).put("star", s.star());
                items.get(i).put("reviewCount", s.reviewCount());
            }
        }
        return items;
    }

    private static Integer parseId(Object id) {
        if (id == null) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(id).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
