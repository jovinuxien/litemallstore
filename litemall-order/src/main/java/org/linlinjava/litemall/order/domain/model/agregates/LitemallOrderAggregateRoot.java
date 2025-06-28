package org.linlinjava.litemall.order.domain.model.agregates;

import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.db.domain.LitemallCart;
import org.linlinjava.litemall.db.domain.LitemallGrouponRules;
import org.linlinjava.litemall.order.domain.model.agregates.goods.LitemallGoodsAggregate;
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
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


@Getter
@Setter
public class LitemallOrderAggregateRoot {

    private LitemallOrderAggregate orderAggregate;
    private LitemallOrderGoodsAggregate orderGoodsAggregate;
    private LitemallGoodsAggregate goodsAggregate;


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

        if(!orderAggregate.getOrderStatus().canTransitionTo(LitemallOrderStatus.PAID)){
            throw new IllegalStateException("Order status cannot transition to " + orderAggregate.getOrderStatus() + "to Paid");
        }

        this.orderAggregate.setOrderStatus(LitemallOrderStatus.PAID);
        this.domainEvents.add(new LitemallOrderPaidEvent(this.orderAggregate.getOrderId()));
    }

    public void cancel(String reason) {
        if(!orderAggregate.getOrderStatus().canTransitionTo(LitemallOrderStatus.CANCELED)){
            throw new IllegalStateException("Order status cannot transition to " + orderAggregate.getOrderStatus() + "to CANCELED");
        }

        this.orderAggregate.setOrderStatus(LitemallOrderStatus.CANCELED);
        this.domainEvents.add(new LitemallOrderCanceledEvent(this.orderAggregate.getOrderId(), reason));
    }

    public void autoCancel() {
        if(!orderAggregate.getOrderStatus().canTransitionTo(LitemallOrderStatus.SYSTEM_CANCELED)){
            throw new IllegalStateException("Order status cannot transition to " + orderAggregate.getOrderStatus() + "to CANCELED");
        }

        this.orderAggregate.setOrderStatus(LitemallOrderStatus.SYSTEM_CANCELED);
        this.domainEvents.add(new LitemallOrderCanceledEvent(this.orderAggregate.getOrderId(), " System auto cancel after 24 hours"));
    }

    public void ship(){
        if(!orderAggregate.getOrderStatus().canTransitionTo(LitemallOrderStatus.SHIPPED)){
            throw new IllegalStateException("Order status cannot transition to " + orderAggregate.getOrderStatus() + "to SHIPPED");
        }

        this.orderAggregate.setOrderStatus(LitemallOrderStatus.SHIPPED);
        this.domainEvents.add(new LitemallOrderShippedEvent(this.orderAggregate.getOrderId()));
    }

    public LitemallOrderHandleOption getAvailableActions(){
        return LitemallOrderHandleOption.forStatus(this.orderAggregate.getOrderStatus());
    }
    public List<LitemallDomainEvent> getDomainEvents(){
        return Collections.unmodifiableList(this.domainEvents);
    }

    public void clearDomainEvents(){
        this.domainEvents.clear();
    }
}
