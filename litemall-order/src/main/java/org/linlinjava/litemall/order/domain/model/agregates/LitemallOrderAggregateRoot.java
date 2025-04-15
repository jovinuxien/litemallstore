package org.linlinjava.litemall.order.domain.model.agregates;

import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.db.domain.LitemallCart;
import org.linlinjava.litemall.db.domain.LitemallGrouponRules;
import org.linlinjava.litemall.order.domain.model.events.LitemallDomainEvent;
import org.linlinjava.litemall.order.domain.model.events.order.LitemallOrderCanceledEvent;
import org.linlinjava.litemall.order.domain.model.events.order.LitemallOrderPaidEvent;
import org.linlinjava.litemall.order.domain.model.events.order.LitemallOrderShippedEvent;
import org.linlinjava.litemall.order.domain.model.util.LitemallOrderHandleOption;
import org.linlinjava.litemall.order.domain.model.valueobjects.*;
import org.linlinjava.litemall.order.domain.model.valueobjects.coupon.LitemallCouponId;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallAfterSaleStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


@Getter
@Setter
public class LitemallOrderAggregateRoot {

    private LitemallOrderId orderId;
    private LitemallUserId userId; // Holding just reference not the actual entity
    private LitemallCouponId couponId; // Holding just reference to the coupon aggregate. Not the actual entity,because the coupon has its own lifecycle


    private String orderSn;
    private LitemallOrderStatus orderStatus;
    private LitemallAfterSaleStatus afterSaleStatus;
    private String consignee;
    private String mobile;
    private String message;
    private String address;


    //Information on price
    private LitemallMoney goodsPrice;
    private LitemallMoney freightPrice;
    private LitemallMoney couponPrice;
    private LitemallMoney integralPrice;

    private LitemallMoney orderPrice;
    private LitemallMoney actualPrice;
    private LitemallMoney grouponPrice;


    private String payId;
    private LocalDateTime payTime;
    private String shipSn;
    private String shipChannel;
    private LocalDateTime shipTime;
    private LitemallMoney refundAmount;
    private String refundType;
    private String refundContent;
    private LocalDateTime refundTime;
    private LocalDateTime confirmTime;
    private Short comments;

    private LocalDateTime endTime;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private Boolean deleted;


    private List<LitemallCart> checkedGoodsList = new ArrayList<>(); // We hold LitemallCart because it's an entity'
    private List<LitemallDomainEvent> domainEvents = new ArrayList<>();



    public static void createOrder(LitemallUserId userId,
                                   LitemallAddressId shippingAddress,
                                   String message, List<LitemallCart> checkedGoodsList,
                                   LitemallGrouponRules grouponRules){}

    /**
     * Method allowing communication btw aggregates (OrderAggregate and CouponAggregate)
     * @param
     */

   /* public void applyCoupon(LitemallCouponAggregate couponAggregate){

        if(!couponAggregate.isApplicableTo(this)){
            thow new InvalidCouponaApplicationException("Coupon is not applicable to this order");
        }
        this.couponId = couponAggregate.getCouponId();
        this.domainEvents.add(new CouponAppliedEvent(this.orderId, couponAggregate.getCouponId()));
    }*/

    public void markAsPaid(){

        if(!orderStatus.canTransitionTo(LitemallOrderStatus.PAID)){
            throw new IllegalStateException("Order status cannot transition to " + orderStatus + "to Paid");
        }

        this.orderStatus = LitemallOrderStatus.PAID;
        this.domainEvents.add(new LitemallOrderPaidEvent(this.orderId));
    }

    public void cancel(String reason) {
        if(!orderStatus.canTransitionTo(LitemallOrderStatus.CANCELED)){
            throw new IllegalStateException("Order status cannot transition to " + orderStatus + "to CANCELED");
        }

        this.orderStatus = LitemallOrderStatus.CANCELED;
        this.domainEvents.add(new LitemallOrderCanceledEvent(this.orderId, reason));
    }

    public void autoCancel() {
        if(!orderStatus.canTransitionTo(LitemallOrderStatus.SYSTEM_CANCELED)){
            throw new IllegalStateException("Order status cannot transition to " + orderStatus + "to CANCELED");
        }

        this.orderStatus = LitemallOrderStatus.SYSTEM_CANCELED;
        this.domainEvents.add(new LitemallOrderCanceledEvent(this.orderId, " System auto cancel after 24 hours"));
    }

    public void ship(){
        if(!orderStatus.canTransitionTo(LitemallOrderStatus.SHIPPED)){
            throw new IllegalStateException("Order status cannot transition to " + orderStatus + "to SHIPPED");
        }

        this.orderStatus = LitemallOrderStatus.SHIPPED;
        this.domainEvents.add(new LitemallOrderShippedEvent(this.orderId));
    }

    public LitemallOrderHandleOption getAvailableActions(){
        return LitemallOrderHandleOption.forStatus(this.orderStatus);
    }
    public List<LitemallDomainEvent> getDomainEvents(){
        return Collections.unmodifiableList(this.domainEvents);
    }

    public void clearDomainEvents(){
        this.domainEvents.clear();
    }
}
