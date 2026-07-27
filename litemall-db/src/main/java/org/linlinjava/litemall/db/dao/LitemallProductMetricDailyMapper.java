package org.linlinjava.litemall.db.dao;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.linlinjava.litemall.db.domain.LitemallProductMetricDaily;

/**
 * Hand-written mapper for the per-goods daily metric series
 * ({@code litemall_product_metric_daily}, V45; PK goods_id + day).
 * No {@code Example} machinery — upserts are idempotent per (goods_id, day).
 */
public interface LitemallProductMetricDailyMapper {

    /** Full-row idempotent upsert for (goodsId, day); re-running a day replaces its values. */
    int upsert(LitemallProductMetricDaily metric);

    /**
     * Availability-only upsert for goods that vanished from the CJ feed (no full context
     * available); preserves any previously recorded values for the day.
     */
    int upsertAvailability(@Param("goodsId") Integer goodsId, @Param("day") LocalDate day,
                           @Param("available") boolean available);

    /** The series for one goods, oldest day first, capped to the trailing {@code days} days. */
    List<LitemallProductMetricDaily> selectSeries(@Param("goodsId") int goodsId, @Param("days") int days);

    /** One day's row for one goods, or null. */
    LitemallProductMetricDaily selectOne(@Param("goodsId") int goodsId, @Param("day") LocalDate day);
}
