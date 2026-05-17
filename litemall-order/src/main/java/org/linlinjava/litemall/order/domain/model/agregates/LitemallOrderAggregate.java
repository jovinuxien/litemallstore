package org.linlinjava.litemall.order.domain.model.agregates;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.core.events.LitemallDomainEvent;
import org.linlinjava.litemall.order.domain.events.order.LitemallOrderCancelledEvent;
import org.linlinjava.litemall.order.domain.events.order.LitemallOrderPaidEvent;
import org.linlinjava.litemall.order.domain.events.order.LitemallOrderShippedEvent;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallAfterSaleStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


@Getter
@Setter
public class LitemallOrderAggregate {

    private LitemallOrderId orderId;
    private LitemallUserId userId;

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

    private List<LitemallDomainEvent> domainEvents = new ArrayList<>();



    public void cancel(String reason){
        // 1. Delegate the rule check to the current state object.
        if(!this.orderStatus.canTransitionTo(LitemallOrderStatus.CANCELED)){
            throw new IllegalStateException("Order status cannot transition to " + this.getOrderStatus() + "to CANCELED");
        }

        // 2. If the transition is valid, change the state.
        this.setOrderStatus(LitemallOrderStatus.CANCELED);

        // 3. Publish a domain event to notify other parts of the system: Moved to LitemallOrderServiceImpl class.
        this.domainEvents.add(new LitemallOrderCancelledEvent(this.getOrderId(), reason));

    }


    /**
     * Mark the order as paid.
     */
    public void markAsPaid(){

        if(!this.orderStatus.canTransitionTo(LitemallOrderStatus.PAID)){
            throw new IllegalStateException("Order status cannot transition to " + this.getOrderStatus() + "to Paid");
        }

        this.setOrderStatus(LitemallOrderStatus.PAID);
        this.domainEvents.add(new LitemallOrderPaidEvent(this.getOrderId()));
    }

    /**
     * Mark the order as canceled by system.
     */
    public void autoCancel() {
        if(!this.orderStatus.canTransitionTo(LitemallOrderStatus.SYSTEM_CANCELED)){
            throw new IllegalStateException("Order status cannot transition to " + this.getOrderStatus() + "to CANCELED");
        }

        this.setOrderStatus(LitemallOrderStatus.SYSTEM_CANCELED);
        this.domainEvents.add(new LitemallOrderCancelledEvent(this.getOrderId(), "System cancelled"));
    }

    public void ship(){
        if(!this.orderStatus.canTransitionTo(LitemallOrderStatus.SHIPPED)){
            throw new IllegalStateException("Order status cannot transition to " + this.getOrderStatus() + "to SHIPPED");
        }

        this.setOrderStatus(LitemallOrderStatus.SHIPPED);
        this.domainEvents.add(new LitemallOrderShippedEvent(this.getOrderId()));
    }


}
