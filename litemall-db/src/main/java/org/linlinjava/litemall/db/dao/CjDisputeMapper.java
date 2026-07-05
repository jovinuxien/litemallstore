package org.linlinjava.litemall.db.dao;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.litemall.db.domain.LitemallCjDispute;

import java.util.List;

/**
 * Hand-written mapper for the CJ dispute projection table ({@code litemall_cj_dispute},
 * V28). Mirrors {@link OrderStatusLogMapper}: co-located with the generated DAOs so the
 * existing {@code @MapperScan} + {@code dao/*.xml} mapper-location pattern picks it up.
 */
public interface CjDisputeMapper {

    /** Insert a new dispute row; populates the generated id back onto the record. */
    int insert(LitemallCjDispute record);

    /** All (non-deleted) disputes for an order, newest first. */
    List<LitemallCjDispute> selectByOrderId(@Param("orderId") Integer orderId);

    LitemallCjDispute selectById(@Param("id") Integer id);

    /** Open disputes for an order: not cancelled and no final resolution from CJ yet. */
    List<LitemallCjDispute> selectOpenByOrderId(@Param("orderId") Integer orderId);

    /** Disputes raised by a user across orders, newest first. */
    List<LitemallCjDispute> selectByUserId(@Param("userId") Integer userId);

    /** Patch the CJ-synced projection fields (null args leave the column unchanged). */
    int updateCjProjection(LitemallCjDispute record);

    /** Mark a dispute cancelled. */
    int markCancelled(@Param("id") Integer id);
}
