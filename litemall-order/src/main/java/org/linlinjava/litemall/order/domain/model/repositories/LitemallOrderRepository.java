package org.linlinjava.litemall.order.domain.model.repositories;

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

    void deleteByOrderId(LitemallOrderId orderId);

    public String generateOrderSn(LitemallUserId userId);

    int count();

    List<LitemallOrderAggregate> queryUnPaid(int minutes);

    List<LitemallOrderAggregate> queryUnconfirm(int days);

    Map<Object, Object> orderInfo(LitemallUserId userId);

    List<LitemallOrderAggregate> queryComment(int days);

    public int updateSelective(LitemallOrderAggregate orderAggregate);

    void updateAfterSaleStatus(LitemallOrderId orderId, Short statuReject);
}
