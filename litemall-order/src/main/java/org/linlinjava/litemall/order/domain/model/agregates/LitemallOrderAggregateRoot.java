package org.linlinjava.litemall.order.domain.model.agregates;

import org.linlinjava.litemall.db.domain.LitemallCart;
import org.linlinjava.litemall.db.domain.LitemallGrouponRules;
import org.linlinjava.litemall.order.domain.model.events.LitemallDomainEvent;
import org.linlinjava.litemall.order.domain.model.events.order.LitemallOrderPaidEvent;
import org.linlinjava.litemall.order.domain.model.valueobjects.*;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;

import java.util.ArrayList;
import java.util.List;

public class LitemallOrderAggregateRoot {
    private LitemallOrderId orderId;
    private LitemallUserId userId; // Holding just reference not the actual entity
    private LitemallCouponId couponId; // Holding just reference to the coupon aggregate. Not the actual entity,because the coupon has its own lifecycle


    private String orderSn;
    private LitemallOrderStatus orderStatus;
    private LitemallAddressId shippingAddress;
    private String message;

    private LitemallMoney goodsPrice;
    private LitemallMoney freightPrice;
    private LitemallMoney couponPrice;
    private LitemallMoney integralPrice;
    private LitemallMoney orderPrice;
    private LitemallMoney actualPrice;
    private LitemallMoney grouponPrice;


    private List<LitemallCart> checkedGoodsList = new ArrayList<>(); // We hold LitemallCart because it's an entity'
    private List<LitemallDomainEvent> domainEvents = new ArrayList<>();



    public static void createOrder(LitemallUserId userId,
                                   LitemallAddressId shippingAddress,
                                   String message, List<LitemallCart> checkedGoodsList,
                                   LitemallGrouponRules grouponRules){}


    public void markAsPaid(){

        if(!orderStatus.canTransitionTo(LitemallOrderStatus.PAID)){
            throw new IllegalStateException("Order status cannot transition to " + orderStatus + "to Paid");
        }

        this.orderStatus = LitemallOrderStatus.PAID;
        this.domainEvents.add(new LitemallOrderPaidEvent(this.orderId));
    }
}
