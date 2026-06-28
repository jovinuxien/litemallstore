package org.linlinjava.litemall.order.domain.model.repositories;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface LitemallOrderRepository {

    Optional<LitemallOrderAggregate> findById(LitemallOrderId orderId);

    void addOrder(LitemallOrderAggregate order);

    int count(LitemallUserId userId);

    LitemallOrderAggregate findByIdAndUserId(LitemallUserId userId, LitemallOrderId orderId);

    int countByOrderSn(LitemallUserId userId, String orderSn);

    List<LitemallOrderAggregate> queryByOrderStatus(LitemallUserId userId, List<Short> orderStatus, int page, int limit, String sort, String order);

    /** Total orders for a user filtered by the same (optional) status set — the {@code total} for the paged list. */
    int countByOrderStatus(LitemallUserId userId, List<Short> orderStatus);

    /** Admin: page across ALL users' orders (not user-scoped). sortColumn must be a vetted DB column. */
    List<LitemallOrderAggregate> adminQuery(String orderSn, List<Short> orderStatus, int page, int limit, String sortColumn, String order);

    /** Admin: total order count matching the same filters as {@link #adminQuery}. */
    long adminCount(String orderSn, List<Short> orderStatus);

    void deleteByOrderId(LitemallOrderId orderId);

    public String generateOrderSn(LitemallUserId userId);

    int count();

    List<LitemallOrderAggregate> queryUnPaid(int minutes);

    List<LitemallOrderAggregate> queryUnconfirm(int days);

    Map<Object, Object> orderInfo(LitemallUserId userId);

    List<LitemallOrderAggregate> queryComment(int days);

    public int updateSelective(LitemallOrderAggregate orderAggregate);

    /**
     * Conditionally transition an order from CREATED to PAID. The update only
     * matches a row still in CREATED, so a retried or concurrent payment (which
     * already flipped the row) affects 0 rows. Returns the number of rows
     * updated (1 on success, 0 if the order was no longer in CREATED).
     */
    int markPaidIfCreated(LitemallOrderId orderId);

    void updateAfterSaleStatus(LitemallOrderId orderId, Short statuReject);
}
