package org.linlinjava.litemall.order.domain.model.repositories;

import org.linlinjava.litemall.db.domain.LitemallOrder;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallUserId;

import java.util.List;
import java.util.Map;

public interface LitemallOrderRepository {

    LitemallOrder findById(LitemallOrderId orderId);

    int count(LitemallUserId userId);

    LitemallOrder findByIdAndUserId(LitemallUserId userId, LitemallOrderId orderId);

    int countByOrderSn(LitemallUserId userId, String orderSn);

    List<LitemallOrder> queryByOrderStatus(LitemallUserId userId, List<Short> orderStatus, int page, int limit, String sort, String order);

    void deleteByOrderId(LitemallOrderId orderId);

    int count();

    List<LitemallOrder> queryUnPaid(int minutes);

    List<LitemallOrder> queryUnconfirm(int days);

    Map<Object, Object> orderInfo(LitemallUserId userId);

    List<LitemallOrder> queryComment(int days);

    void updateAfterSaleStatus(LitemallOrderId orderId, Short statuReject);
}
