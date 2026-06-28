package org.linlinjava.litemall.db.dao;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.litemall.db.domain.LitemallOrderStatusLog;

import java.util.List;

/**
 * Hand-written mapper for the order status-history audit table
 * ({@code litemall_order_status}). The MyBatis-Generator {@code *Example} API was never
 * generated for this table (it was created in V11 but left unused), so the few
 * operations the order lifecycle needs are written by hand here.
 *
 * <p>Co-located with the generated DAOs so it is picked up by both {@code @MapperScan}
 * and the {@code dao/*.xml} mapper-location pattern; no extra registration needed.
 */
public interface OrderStatusLogMapper {

    /** Append one transition row. Populates the generated id back onto the record. */
    int insert(LitemallOrderStatusLog record);

    /** Full transition history for an order, oldest first (the timeline order). */
    List<LitemallOrderStatusLog> selectByOrderId(@Param("orderId") Integer orderId);

    /** Number of recorded transitions for an order. */
    int countByOrderId(@Param("orderId") Integer orderId);
}