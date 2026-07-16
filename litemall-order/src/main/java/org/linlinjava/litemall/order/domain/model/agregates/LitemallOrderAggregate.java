package org.linlinjava.litemall.order.domain.model.agregates;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.core.events.LitemallDomainEvent;
import org.linlinjava.litemall.order.domain.events.order.LitemallOrderCancelledEvent;
import org.linlinjava.litemall.order.domain.events.order.LitemallOrderDeliveredEvent;
import org.linlinjava.litemall.order.domain.events.order.LitemallOrderPaidEvent;
import org.linlinjava.litemall.order.domain.events.order.LitemallOrderRefundRequestedEvent;
import org.linlinjava.litemall.order.domain.events.order.LitemallOrderRefundedEvent;
import org.linlinjava.litemall.order.domain.events.order.LitemallOrderShippedEvent;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallAddressId;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallAfterSaleStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderStatusChange;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


@Getter
@Setter
public class LitemallOrderAggregate {

    /** {@code source} value for an order fulfilled by CJ Dropshipping after payment. */
    public static final String SOURCE_CJ = "cj";
    /** {@code source} value for a natively-fulfilled order (column default). */
    public static final String SOURCE_LOCAL = "local";

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

    /** Tax collected at checkout (US sales tax / EU VAT); 0.00 when tax is off. (Wave 7) */
    private LitemallMoney taxPrice;
    /** Provider per-jurisdiction breakdown JSON, for invoices/audit. Never summed. (Wave 7) */
    private String taxBreakdown;


    private String payId;
    /**
     * The verified Stripe PaymentIntent (pi_...). Separate from {@link #payId}, which is an
     * overloaded tender label and therefore cannot be unique; this one is UNIQUE in the DB,
     * which is what stops a PaymentIntent being replayed onto a second order. NULL for
     * wallet/offline tenders. (Wave 7, Task A)
     */
    private String paymentIntentId;
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

    // CJ-fulfillment linkage (V27). addressId/countryCode are captured at submit so the
    // pay-time CJ placement can rebuild the STRUCTURED shipping address (the flattened
    // address string above cannot be split back into province/city/zip); cjOrderId/Num
    // are CJ's identifiers once createOrder accepts (order_sn is the idempotency key).
    private LitemallAddressId addressId;
    private String countryCode;
    private String source;
    private String cjOrderId;
    private String cjOrderNum;
    // Last CJ-side status seen by the lifecycle sync (V33): CREATED/IN_CART/UNPAID/
    // UNSHIPPED/SHIPPED/DELIVERED/CANCELLED. Null for local orders.
    private String cjOrderStatus;

    /** {@code delivery_type} value for courier delivery (column default). */
    public static final String DELIVERY_EXPRESS = "express";
    /** {@code delivery_type} value for in-store pickup (Wave 4, V35). */
    public static final String DELIVERY_PICKUP = "pickup";

    // In-store pickup / write-off (Wave 4, V35). verifyCode is assigned at PAY time
    // (never on an unpaid order); verifyTime/verifiedBy record the write-off (核销).
    private String deliveryType;
    private Integer storeId;
    private String verifyCode;
    private LocalDateTime verifyTime;
    private String verifiedBy;

    /** True when this order is fulfilled through CJ Dropshipping after payment. */
    public boolean isCjFulfilled() {
        return SOURCE_CJ.equals(this.source);
    }

    /** True when the customer picks the order up in a store (no shipping leg). */
    public boolean isPickup() {
        return DELIVERY_PICKUP.equals(this.deliveryType);
    }

    private List<LitemallDomainEvent> domainEvents = new ArrayList<>();

    // Every state transition the aggregate applies is recorded here and persisted to
    // the litemall_order_status history table in the SAME transaction as the status
    // write — so the customer/admin timeline is a gap-free record of how the order moved.
    private List<LitemallOrderStatusChange> statusChanges = new ArrayList<>();

    private void recordChange(LitemallOrderStatus from, LitemallOrderStatus to,
                              String changeType, String message, String operator) {
        this.statusChanges.add(new LitemallOrderStatusChange(
                this.orderId, from, to, changeType, message, operator, LocalDateTime.now()));
    }



    /** Customer cancels an unpaid order (CREATED → CANCELED). */
    public void cancel(String reason){
        LitemallOrderStatus from = this.orderStatus;
        if(!from.canTransitionTo(LitemallOrderStatus.CANCELED)){
            throw new IllegalStateException("Order status cannot transition from " + from + " to CANCELED");
        }
        this.setOrderStatus(LitemallOrderStatus.CANCELED);
        this.setEndTime(LocalDateTime.now());
        this.domainEvents.add(new LitemallOrderCancelledEvent(this.getOrderId(), reason));
        recordChange(from, LitemallOrderStatus.CANCELED, "cancel",
                reason == null || reason.isBlank() ? "Order cancelled" : "Order cancelled: " + reason, "user");
    }

    /**
     * Mark the order as paid (CREATED → PAID).
     */
    public void markAsPaid(){
        LitemallOrderStatus from = this.orderStatus;
        if(!from.canTransitionTo(LitemallOrderStatus.PAID)){
            throw new IllegalStateException("Order status cannot transition from " + from + " to PAID");
        }
        this.setOrderStatus(LitemallOrderStatus.PAID);
        this.setPayTime(LocalDateTime.now());
        this.domainEvents.add(new LitemallOrderPaidEvent(this.getOrderId()));
        recordChange(from, LitemallOrderStatus.PAID, "pay", "Payment received", "user");
    }

    /**
     * Mark the order as canceled by the system (CREATED → SYSTEM_CANCELED), e.g. the
     * unpaid-order timeout sweep.
     */
    public void autoCancel() {
        LitemallOrderStatus from = this.orderStatus;
        if(!from.canTransitionTo(LitemallOrderStatus.SYSTEM_CANCELED)){
            throw new IllegalStateException("Order status cannot transition from " + from + " to SYSTEM_CANCELED");
        }
        this.setOrderStatus(LitemallOrderStatus.SYSTEM_CANCELED);
        this.setEndTime(LocalDateTime.now());
        this.domainEvents.add(new LitemallOrderCancelledEvent(this.getOrderId(), "System cancelled"));
        recordChange(from, LitemallOrderStatus.SYSTEM_CANCELED, "system_cancel",
                "Auto-cancelled: payment window expired", "system");
    }

    /** Admin/fulfillment ships a paid order (PAID → SHIPPED). */
    public void ship(String shipChannel, String shipSn){
        LitemallOrderStatus from = this.orderStatus;
        if(!from.canTransitionTo(LitemallOrderStatus.SHIPPED)){
            throw new IllegalStateException("Order status cannot transition from " + from + " to SHIPPED");
        }
        this.setOrderStatus(LitemallOrderStatus.SHIPPED);
        this.setShipChannel(shipChannel);
        this.setShipSn(shipSn);
        this.setShipTime(LocalDateTime.now());
        this.domainEvents.add(new LitemallOrderShippedEvent(this.getOrderId()));
        recordChange(from, LitemallOrderStatus.SHIPPED, "ship",
                "Shipped via " + shipChannel + " (" + shipSn + ")", "admin");
    }

    /** Customer confirms receipt (SHIPPED → DELIVERED). */
    public void confirmDelivery(){
        LitemallOrderStatus from = this.orderStatus;
        // Explicit SHIPPED gate: the graph also allows PAID→DELIVERED (pickup
        // write-off, Wave 4), but a customer receipt confirmation only ever applies
        // to a shipped order — write-off goes through writeOff() below.
        if(from != LitemallOrderStatus.SHIPPED || !from.canTransitionTo(LitemallOrderStatus.DELIVERED)){
            throw new IllegalStateException("Order status cannot transition from " + from + " to DELIVERED");
        }
        this.setOrderStatus(LitemallOrderStatus.DELIVERED);
        this.setConfirmTime(LocalDateTime.now());
        this.domainEvents.add(new LitemallOrderDeliveredEvent(this.getOrderId(), false));
        recordChange(from, LitemallOrderStatus.DELIVERED, "receive", "Delivery confirmed by customer", "user");
    }

    /**
     * Staff writes off a paid PICKUP order at the counter (PAID → DELIVERED, Wave 4).
     * The customer presented the verify code; delivery happens over the counter, so
     * there is no SHIPPED leg. Raises {@link LitemallOrderDeliveredEvent} (not
     * auto-confirmed) like a customer receipt confirmation.
     *
     * @param verifiedBy audit identity, e.g. {@code "admin:<X-User-Id>"}
     */
    public void writeOff(String verifiedBy) {
        LitemallOrderStatus from = this.orderStatus;
        if (!isPickup()) {
            throw new IllegalStateException("Only pickup orders can be written off");
        }
        if (!from.canTransitionTo(LitemallOrderStatus.DELIVERED)
                || from != LitemallOrderStatus.PAID) {
            // The graph gained PAID→DELIVERED for exactly this hop; keep the explicit
            // PAID guard so SHIPPED→DELIVERED (courier receipt) never routes through here.
            throw new IllegalStateException("Order status cannot transition from " + from
                    + " to DELIVERED by write-off");
        }
        LocalDateTime now = LocalDateTime.now();
        this.setOrderStatus(LitemallOrderStatus.DELIVERED);
        this.setConfirmTime(now);
        this.setVerifyTime(now);
        this.setVerifiedBy(verifiedBy);
        this.domainEvents.add(new LitemallOrderDeliveredEvent(this.getOrderId(), false));
        recordChange(from, LitemallOrderStatus.DELIVERED, "writeoff",
                "Picked up in store — verify code redeemed", verifiedBy);
    }

    /** System auto-confirms a shipped order after the grace window (SHIPPED → AUTO_DELIVERED). */
    public void autoConfirm(){
        LitemallOrderStatus from = this.orderStatus;
        if(!from.canTransitionTo(LitemallOrderStatus.AUTO_DELIVERED)){
            throw new IllegalStateException("Order status cannot transition from " + from + " to AUTO_DELIVERED");
        }
        this.setOrderStatus(LitemallOrderStatus.AUTO_DELIVERED);
        this.setConfirmTime(LocalDateTime.now());
        this.domainEvents.add(new LitemallOrderDeliveredEvent(this.getOrderId(), true));
        recordChange(from, LitemallOrderStatus.AUTO_DELIVERED, "auto_receive",
                "Delivery auto-confirmed after grace window", "system");
    }

    /** Customer opens a refund/return on a paid or shipped order (→ REFUND_REQUEST). */
    public void requestRefund(String reason){
        LitemallOrderStatus from = this.orderStatus;
        if(!from.canTransitionTo(LitemallOrderStatus.REFUND_REQUEST)){
            throw new IllegalStateException("Order status cannot transition from " + from + " to REFUND_REQUEST");
        }
        this.setOrderStatus(LitemallOrderStatus.REFUND_REQUEST);
        this.setRefundContent(reason);
        this.domainEvents.add(new LitemallOrderRefundRequestedEvent(this.getOrderId(), reason));
        recordChange(from, LitemallOrderStatus.REFUND_REQUEST, "refund_request",
                reason == null || reason.isBlank() ? "Refund requested" : "Refund requested: " + reason, "user");
    }

    /** Admin approves a refund; the money has been returned (REFUND_REQUEST → REFUNDED). */
    public void refund(LitemallMoney amount){
        LitemallOrderStatus from = this.orderStatus;
        if(!from.canTransitionTo(LitemallOrderStatus.REFUNDED)){
            throw new IllegalStateException("Order status cannot transition from " + from + " to REFUNDED");
        }
        this.setOrderStatus(LitemallOrderStatus.REFUNDED);
        this.setRefundAmount(amount);
        this.setRefundTime(LocalDateTime.now());
        this.setEndTime(LocalDateTime.now());
        this.domainEvents.add(new LitemallOrderRefundedEvent(this.getOrderId()));
        recordChange(from, LitemallOrderStatus.REFUNDED, "refund", "Refund completed", "admin");
    }

}
