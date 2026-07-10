package org.linlinjava.litemall.db.dao;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.litemall.db.domain.LitemallOrder;
import org.linlinjava.litemall.db.domain.OrderVo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface OrderMapper {
    int updateWithOptimisticLocker(@Param("lastUpdateTime") LocalDateTime lastUpdateTime, @Param("order") LitemallOrder order);
    List<Map> getOrderIds(@Param("query") String query, @Param("orderByClause") String orderByClause);
    List<OrderVo> getOrderList(@Param("query") String query, @Param("orderByClause") String orderByClause);

    /**
     * Hand-maintained (V33): ids of CJ-fulfilled orders the lifecycle sync should poll —
     * placed at CJ (cj_order_id set), CJ status not yet terminal, local status not terminal.
     * Least-recently-updated first so a stalled order never starves behind fresh ones.
     */
    List<Integer> selectSyncableCjOrderIds(@Param("limit") int limit);
}
