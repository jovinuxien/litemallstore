package org.linlinjava.litemall.order.domain.model.repositories;

import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregateRoot;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallUserId;

import java.util.List;
import java.util.Map;

public interface LitemallOrderRepository {

    LitemallOrderAggregateRoot findById(LitemallOrderId orderId);

    void addOrder(LitemallOrderAggregateRoot order);

    int count(LitemallUserId userId);

    LitemallOrderAggregateRoot findByIdAndUserId(LitemallUserId userId, LitemallOrderId orderId);

    int countByOrderSn(LitemallUserId userId, String orderSn);

    List<LitemallOrderAggregateRoot> queryByOrderStatus(LitemallUserId userId, List<Short> orderStatus, int page, int limit, String sort, String order);

    void deleteByOrderId(LitemallOrderId orderId);

    public String generateOrderSn(LitemallUserId userId);

    int count();

    List<LitemallOrderAggregateRoot> queryUnPaid(int minutes);

    List<LitemallOrderAggregateRoot> queryUnconfirm(int days);

    Map<Object, Object> orderInfo(LitemallUserId userId);

    List<LitemallOrderAggregateRoot> queryComment(int days);

    public int updateSelective(LitemallOrderAggregateRoot orderAggregateRoot);

    void updateAfterSaleStatus(LitemallOrderId orderId, Short statuReject);
}
