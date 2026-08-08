package org.linlinjava.litemall.goods.application.promo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * One costed on-sale goods row from {@code InsightMapper.selectPromoScoringPool}, typed.
 * {@code categoryId} is the goods' LEAF category — the task resolves L1 roots app-side.
 */
public record PromoScoringRow(int goodsId,
                              Integer categoryId,
                              BigDecimal retail,
                              BigDecimal cost,
                              BigDecimal marginPct,
                              int stockTotal,
                              int salesQty,
                              int views,
                              BigDecimal rating,
                              int reviewCount,
                              LocalDateTime arrivalDate) {

    /** Null-tolerant mapping from the mapper's HashMap row; returns null on a broken row. */
    public static PromoScoringRow fromMap(Map<String, Object> row) {
        Object goodsId = row.get("goodsId");
        Object retail = row.get("retailPrice");
        Object cost = row.get("cost");
        if (goodsId == null || retail == null || cost == null) {
            return null;
        }
        return new PromoScoringRow(
                ((Number) goodsId).intValue(),
                row.get("categoryId") == null ? null : ((Number) row.get("categoryId")).intValue(),
                toDecimal(retail),
                toDecimal(cost),
                toDecimal(row.get("marginPct")),
                toInt(row.get("stockTotal")),
                toInt(row.get("salesQty")),
                toInt(row.get("views")),
                toDecimal(row.get("rating")),
                toInt(row.get("reviewCount")),
                (LocalDateTime) row.get("arrivalDate"));
    }

    private static BigDecimal toDecimal(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof BigDecimal bd ? bd : new BigDecimal(value.toString());
    }

    private static int toInt(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
    }
}
