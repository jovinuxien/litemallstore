package org.linlinjava.litemall.order.application.internal;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

import com.google.protobuf.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.linlinjava.litemall.core.events.LitemallDomainEvent;
import org.linlinjava.litemall.core.notify.NotifyService;
import org.linlinjava.litemall.core.system.SystemConfig;
import org.linlinjava.litemall.core.task.TaskService;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.order.application.LitemallIOrderService;
import org.linlinjava.litemall.order.application.util.exception.coupon.LitemallValidCouponException;
import org.linlinjava.litemall.order.domain.model.agregates.*;
import org.linlinjava.litemall.order.domain.model.agregates.goods.LitemallGoodsAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.goods.LitemallGoodsProductAggregate;
import org.linlinjava.litemall.order.domain.model.commands.LitemallOrderSubmitResult;
import org.linlinjava.litemall.order.domain.model.commands.LitemallPlaceOrderCommand;
import org.linlinjava.litemall.order.domain.service.groupon.LitemallGrouponValidationResult;
import org.linlinjava.litemall.order.domain.service.order.LitemallOrderDomainService;
import org.linlinjava.litemall.order.domain.events.LitemallDomainEventPublisher;
import org.linlinjava.litemall.order.domain.model.repositories.*;
import org.linlinjava.litemall.order.domain.model.valueobjects.*;
import org.linlinjava.litemall.order.domain.model.valueobjects.coupon.LitemallCouponId;
import org.linlinjava.litemall.order.domain.model.valueobjects.coupon.LitemallCouponValidationResult;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.coupons.LitemallCouponUserStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsId;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsProductId;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.AggregatesValidationContext;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderStatusChange;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.LitemallGoodsFacade;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.UserServiceFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;


@Service
@Slf4j
public class LitemallOrderServiceImpl implements LitemallIOrderService {

    private final LitemallOrderRepository orderRepository;
    private final LitemallCartRepository cartRepository;
    private final LitemallAddressRepository addressRepository;
    private final LitemallOrderGoodsRepository orderGoodsRepository;


    @Autowired
    private final LitemallDomainEventPublisher domainEventPublisher;
    // Service internal to orderService
    @Autowired
    private final LitemallCouponServiceLayer couponService;
    @Autowired
    private LitemallOrderDomainService orderDomainService;
    @Autowired
    private LitemallCartServiceLayer cartServiceLayer;
    @Autowired
    private LitemallGrouponServiceLayer grouponServiceLayer;

    @Autowired
    private  NotifyService notifyService;
    @Autowired
    private  TaskService taskService;
    @Autowired
    private LitemallGoodsFacade goodsFacade;
    @Autowired
    private UserServiceFeignClient userServiceFeignClient;
    // Append-only status-history store. Written in the SAME transaction as each status
    // change so the customer/admin timeline never loses a hop.
    @Autowired
    private LitemallOrderStatusHistoryRepository statusHistoryRepository;

    public LitemallOrderServiceImpl(LitemallOrderRepository orderRepo,
                                    LitemallGrouponRepository grouponRepo,
                                    LitemallCartRepository cartRepo,
                                    LitemallAddressRepository addressRepo,
                                    LitemallOrderGoodsRepository orderGoodsRepo,
                                    LitemallCouponServiceLayer couponService,
                                    LitemallDomainEventPublisher domainEventPublisher) {
        this.orderRepository = orderRepo;
        this.cartRepository = cartRepo;
        this.addressRepository = addressRepo;
        this.orderGoodsRepository = orderGoodsRepo;
        this.couponService = couponService;
        this.domainEventPublisher = domainEventPublisher;
    }


    @Override
    //public LitemallOrderSubmitResult placeOrder(LitemallPlaceOrderCommand command)  {
    @Transactional
    public LitemallOrderSubmitResult placeOrder(LitemallPlaceOrderCommand command) throws ServiceException {

        // Validate the command
        if(command.getUserId() == null){
            throw new IllegalArgumentException("User id is required.");
        }

        // addressId is OPTIONAL: when the command carries none (the customer SPA has no
        // address picker yet — /srv/address follow-up), fall back to the user's default
        // shipping address below. Resolution + the "no address at all" guard happen where
        // the address is actually loaded.

        // Groupon and coupon are OPTIONAL checkout selections. litemall encodes
        // "none" as the sentinel 0 (couponId may also be -1 = none); cartId 0 means
        // "whole checked cart". A plain order arrives with these null/0, so normalize
        // null -> 0 here rather than rejecting it. The previous hard null-checks 500'd
        // every order placed without a groupon AND a coupon.
        int cartId = command.getCartId() == null ? 0 : command.getCartId();
        int grouponLinkId = command.getGrouponLinkId() == null ? 0 : command.getGrouponLinkId();

        LitemallUserId cmdUserId = new LitemallUserId(command.getUserId());
        LitemallGrouponRulesId cmdGrouponRulesId = new LitemallGrouponRulesId(
                command.getGrouponRulesId() == null ? 0 : command.getGrouponRulesId());
        LitemallCouponId cmdCouponId = new LitemallCouponId(
                command.getCouponId() == null ? 0 : command.getCouponId());
        LitemallCouponUserId cmdCouponUserId = new LitemallCouponUserId(
                command.getUserCouponId() == null ? 0 : command.getUserCouponId());


       /* LitemallUserAggregate user = FeignResponseHandler.handleResponse(userServiceFeignClient.geUserById(userId.getId()), "Get userAggregate by Id");

        if(user == null){
            throw new IllegalArgumentException("User is not found");
        }*/

        // Validate the groupon rules ONLY for an actual groupon purchase. A plain
        // order carries grouponRulesId 0, which must skip validation — otherwise
        // validateGrouponRules throws "Groupon rules not found" for the non-existent
        // rule 0 and fails every normal checkout. Args follow the method signature
        // order (grouponRulesId, grouponLinkId, userId); the previous call passed
        // them as (userId, rulesId, linkId), mis-validating every groupon order.
        boolean grouponValid = cmdGrouponRulesId.getId() > 0
                && grouponServiceLayer.validateGrouponRules(
                        cmdGrouponRulesId.getId(), grouponLinkId, cmdUserId.getId()).isValid();

        // Get and Check the shipping address. Use the explicit addressId when supplied,
        // otherwise fall back to the user's default address. Either way a missing address
        // is a hard error — the order needs a consignee/mobile/address to ship to.
        LitemallAddressAggregate addressAggregate = command.getAddressId() == null
                ? addressRepository.findDefaultAddress(cmdUserId)
                : addressRepository.findAddress(cmdUserId, new LitemallAddressId(command.getAddressId()));
        if (addressAggregate == null) {
            throw new IllegalArgumentException("Address info is required");
        }


        // Get the Checked cart items
        List<LitemallCartAggregate> cartList = null;
        cartList = cartServiceLayer.getCheckedCartItems(new LitemallCartId(cartId), cmdUserId);

        // An order needs at least one checked, non-deleted cart line. Without this
        // guard an empty cart still creates a zero-line order and only fails much
        // later in validateAndReduceStock with the misleading "Failed to load
        // required product data for validation" (empty goodsIds -> empty maps).
        // Fail fast with an accurate, client-facing message instead — handleOrderCreation
        // maps LitemallOrderServiceException to a clean submitFailed result (not a 500).
        if (cartList == null || cartList.stream().allMatch(java.util.Objects::isNull) || cartList.isEmpty()) {
            throw new org.linlinjava.litemall.order.application.util.exception.order.LitemallOrderServiceException(
                    "Cannot place an order: there are no checked items in the cart for user " + cmdUserId.getId());
        }

        // Validate the productStock through the goods ACL (price/stock authoritative read)
        this.orderDomainService.validateProductStock(cartList, goodsFacade);

        // Group purchase discount
        BigDecimal grouponPrice = new BigDecimal(0);  // initialize grouponPrice is not redundant;
        if(grouponValid) {
            grouponPrice = grouponServiceLayer.getGrouponDiscount(cmdGrouponRulesId).getAmount();
        }

        // Calculate checked goods price
        BigDecimal checkedGoodsPrice;  // initialize checkedGoodsPrice is redundant;
        LitemallGrouponRulesAggregate grouponRulesAggregate = grouponServiceLayer.getGrouponRulesAggregate(cmdGrouponRulesId);
        checkedGoodsPrice = this.orderDomainService.priceCalculation(cartList, grouponRulesAggregate, new LitemallMoney(grouponPrice));


        //LitemallMoney checkedGoodsPriceMoney = new LitemallMoney(checkedGoodsPrice);
        //Calculate and get the Coupon price info
        // Amount reduced using coupons
        BigDecimal couponPrice = new BigDecimal(0);
        if(cmdCouponId.getId() != 0 && cmdCouponId.getId() != -1){
            LitemallCouponValidationResult couponValidationResult = couponService.validateCouponApplication(cmdUserId, cmdCouponId, cartList);
            if(!couponValidationResult.isValid()){
                //ResponseUtil.badArgumentType(couponValidationResult.getMessage());
                ResponseUtil.badArgument();
            }

            LitemallCouponAggregate couponAggregate = couponService.getCouponAggregate(cmdCouponId);
            if(checkedGoodsPrice.compareTo(couponAggregate.getMinPrice()) < 0) {
                return null;
            }
            couponPrice = couponAggregate.getDiscount();
        }

        // Calculate shipping costs based on total order price，
        // Meet the conditions (e.g. $88), free shipping，Otherwise you need to pay shipping fee（For example, $8）；
        BigDecimal freightPrice = new BigDecimal(0);
        if (checkedGoodsPrice.compareTo(SystemConfig.getFreightLimit()) < 0) {
            freightPrice = SystemConfig.getFreight();
        }
        // Other money available，For example, user points
        BigDecimal integralPrice = new BigDecimal(0);

        // Order Fee calculation: Adding freightPrice, subtracting couponPrice
        BigDecimal orderTotalPrice = checkedGoodsPrice.add(freightPrice).subtract(couponPrice).max(new BigDecimal(0));

        // Final payment: removing integralPrice from orderTotalPrice
        BigDecimal actualPrice = orderTotalPrice.subtract(integralPrice);

        LitemallOrderId newOrderId = null;
        LitemallOrderAggregate orderAggregate =  new LitemallOrderAggregate();

        // Order creation
        newOrderId = new LitemallOrderId(0);// the OrderId to be generated
        orderAggregate.setOrderId(newOrderId);
        orderAggregate.setUserId(cmdUserId);// authoritative buyer from the gateway header
        orderAggregate.setOrderSn(orderRepository.generateOrderSn(cmdUserId));

        orderAggregate.setOrderStatus(LitemallOrderStatus.CREATED);
        // Creation-time defaults for fields the data mapper dereferences but that a
        // fresh order doesn't carry yet (else convertToDataModel NPEs / inserts null
        // into NOT NULL columns).
        orderAggregate.setAfterSaleStatus(org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallAfterSaleStatus.STATUS_INIT);
        orderAggregate.setRefundAmount(new LitemallMoney(new BigDecimal(0)));
        orderAggregate.setRefundType("");
        orderAggregate.setComments((short) 0);
        orderAggregate.setDeleted(false);
        orderAggregate.setAddTime(LocalDateTime.now());
        orderAggregate.setUpdateTime(LocalDateTime.now());
        orderAggregate.setConsignee(addressAggregate.getName());
        orderAggregate.setMobile(addressAggregate.getTel());
        orderAggregate.setMessage(command.getMessage());
        String detailedAddress = addressAggregate.getProvince() + addressAggregate.getCity() + addressAggregate.getCounty() + " " + addressAggregate.getAddressDetail();
        orderAggregate.setAddress(detailedAddress);
        orderAggregate.setGoodsPrice(new LitemallMoney(checkedGoodsPrice));
        orderAggregate.setFreightPrice(new LitemallMoney(freightPrice));
        orderAggregate.setCouponPrice(new LitemallMoney(couponPrice));
        orderAggregate.setIntegralPrice(new LitemallMoney(integralPrice));
        orderAggregate.setOrderPrice(new LitemallMoney(orderTotalPrice));
        orderAggregate.setActualPrice(new LitemallMoney(actualPrice));

        if(grouponRulesAggregate != null){
            orderAggregate.setGrouponPrice(new LitemallMoney(grouponPrice));
        }else {
            orderAggregate.setGrouponPrice(new LitemallMoney(new BigDecimal(0)));
        }
        // Save the order
        orderRepository.addOrder(orderAggregate);

        // Get the id from the order
        LitemallOrderAggregate existingOrderAggregate = orderRepository.findById(orderAggregate.getOrderId()).orElseThrow(() -> new NoSuchElementException("Order not found"));

        // First hop of the lifecycle timeline: the order was placed (→ CREATED). Recorded
        // in THIS transaction so a row exists from the very first state. fromStatus is null.
        statusHistoryRepository.record(new LitemallOrderStatusChange(
                existingOrderAggregate.getOrderId(), null, LitemallOrderStatus.CREATED,
                "create", "Order placed", "user", LocalDateTime.now()));

        // Add the order goods items information
        for(LitemallCartAggregate cartGoods: cartList){

            LitemallOrderGoodsAggregate orderGoodsAggregate = new LitemallOrderGoodsAggregate();
            orderGoodsAggregate.setOrderId(existingOrderAggregate.getOrderId());
            orderGoodsAggregate.setGoodsId(cartGoods.getGoodsId());
            orderGoodsAggregate.setProductId(cartGoods.getProductId());

            orderGoodsAggregate.setGoodsSn(cartGoods.getGoodsSn());
            orderGoodsAggregate.setGoodsName(cartGoods.getGoodsName());

            orderGoodsAggregate.setPrice(cartGoods.getPrice());
            orderGoodsAggregate.setNumber(cartGoods.getNumber().shortValue());
            orderGoodsAggregate.setSpecifications(cartGoods.getSpecifications());
            orderGoodsAggregate.setPicUrl(cartGoods.getPicUrl());

            orderGoodsAggregate.setAddTime(LocalDateTime.now());
            orderGoodsAggregate.setUpdateTime(LocalDateTime.now());

            orderGoodsRepository.add(orderGoodsAggregate);
        }
        // Clear the cart
        clearCart(cmdUserId, new LitemallCartId(cartId));

        // Update coupon usage if applicable
        if (cmdCouponId.getId() != 0 && cmdCouponId.getId() != -1) {
            LitemallCouponUserAggregate couponUserAggregate = couponService.getUserCouponById(cmdCouponUserId);
            //couponUserAggregate.setStatus(CouponUserConstant.STATUS_USED);
            couponUserAggregate.setStatus(LitemallCouponUserStatus.USED);
            couponUserAggregate.setUsedTime(LocalDateTime.now());
            couponUserAggregate.setOrderId(newOrderId);
            couponService.updateCouponUser(couponUserAggregate);
        }

        // If it's a groupon purchase project, add group buying information
        Integer createdGrouponLinkId = grouponServiceLayer.createGrouponOrder(
                grouponLinkId, cmdUserId.getId(), cmdGrouponRulesId.getId(), existingOrderAggregate.getOrderId());

        if (createdGrouponLinkId != null) {
            // Handle groupon-specific logic if needed
            log.info("Groupon order created with link ID: {}", createdGrouponLinkId);
        }

        // Reserve/reduce stock LAST — the remote decrement (a Feign call to
        // goods-management) cannot be rolled back by this local DB transaction, so
        // it is kept as the final mutation to minimise the window in which a later
        // step could fail after stock is taken. reduceStockForAllItems additionally
        // registers a rollback-time compensating restore (best-effort).
        validateAndReduceStock(cartList);

        //Validate and process groupon if available
        return new LitemallOrderSubmitResult(
                existingOrderAggregate.getOrderId().getId(),
                existingOrderAggregate.getOrderSn(),
                false, // payment handled by orchestrator
                grouponLinkId,
                existingOrderAggregate.getActualPrice().getAmount(),
                LocalDateTime.now(),
                LitemallOrderSubmitResult.LitemallOrderSubmitResultStatus.SUCCESS
        );
    }

    /**
     *
     * @param userId
     * @param cartId
     */
    private void clearCart(LitemallUserId userId, LitemallCartId cartId){
        if(cartId.getId() == 0){
            cartRepository.clearCheckedByUserId(userId);
        }else {
            cartRepository.deleteById(cartId);
        }
    }
    /**
     *
     * @param command
     * @param orderId
     */
    private void updateCouponUsage(LitemallPlaceOrderCommand command, LitemallOrderId orderId){
        if(command.getCouponId() != null && command.getCouponId() > 0){
            couponService.couponUserUpdateUsage(new LitemallCouponUserId(command.getUserCouponId()), orderId);
        } else {
            throw new LitemallValidCouponException("The coupon is still valid");
        }
    }


   /* private LitemallGrouponAggregate handleGrouponCreation(LitemallPlaceOrderCommand command, LitemallOrderAggregateRoot orderAggregateRoot, LitemallGrouponRulesAggregate rulesAggregate) {

        if (rulesAggregate == null) {
            return null;
        }
        LitemallGrouponAggregate grouponAggregate;
        LitemallGrouponAggregate baseGroupon = null;
        if (command.getGrouponRulesId() != null && command.getGrouponLinkId() > 0) {
            baseGroupon = grouponRepository.findById(new LitemallGrouponId(command.getGrouponLinkId()));
            grouponAggregate = LitemallGrouponAggregate.createJoin(orderAggregateRoot.getOrderAggregate().getOrderId(), orderAggregateRoot.getOrderAggregate().getUserId(), rulesAggregate.getGrouponRulesId(), baseGroupon);

        } else {
            grouponAggregate = LitemallGrouponAggregate.createNewGroupon(orderAggregateRoot.getOrderAggregate().getOrderId(), orderAggregateRoot.getOrderAggregate().getUserId(), rulesAggregate.getGrouponRulesId());
        }

        grouponRepository.saveGroupon(grouponAggregate);
        return grouponAggregate;

    }*/

    public void publishDomainEvents(LitemallGrouponAggregate grouponAggregate){
        grouponAggregate.getDomainEvents().forEach(this.domainEventPublisher::publish);

    }

    public Optional<LitemallOrderAggregate> getOrderAggregate(LitemallOrderId orderId) {
        return orderRepository.findById(orderId);
    }

    /**
     * Drain the aggregate's recorded status transitions into the history table
     * (within the caller's transaction) and clear them so they are not double-written.
     */
    private void persistStatusHistory(LitemallOrderAggregate agg) {
        for (LitemallOrderStatusChange change : agg.getStatusChanges()) {
            statusHistoryRepository.record(change);
        }
        agg.getStatusChanges().clear();
    }

    /**
     * Publish the aggregate's pending domain events, then clear them. Done after the
     * status write + history persist so subscribers only see committed transitions.
     */
    private void publishAndClearEvents(LitemallOrderAggregate agg) {
        agg.getDomainEvents().forEach(domainEventPublisher::publish);
        agg.getDomainEvents().clear();
    }

    /**
     * Customer-initiated cancellation (CREATED → CANCELED). Applies the guarded
     * status update, records the transition to the history timeline, releases
     * reserved stock (best-effort) and publishes the resulting domain events — all
     * in one transaction.
     */
    public void cancelOrder(LitemallOrderId orderId, String reason) {
        LitemallOrderAggregate orderAggregate = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found"));
        orderAggregate.cancel(reason); // validates CREATED→CANCELED, records change + event
        int updated = orderRepository.markCanceledIfCreated(orderId);
        if (updated == 0) {
            throw new IllegalStateException(
                    "Order " + orderId.getId() + " can no longer be cancelled (already paid/cancelled)");
        }
        restoreStockForOrder(orderId);
        persistStatusHistory(orderAggregate);
        publishAndClearEvents(orderAggregate);
    }

    /**
     * System-initiated cancellation (e.g. the unpaid-order sweep). Transitions the
     * order to SYSTEM_CANCELED; otherwise identical to {@link #cancelOrder}.
     *
     * <p>Runs in its OWN transaction (REQUIRES_NEW): the sweep calls this while
     * holding {@code FOR UPDATE SKIP LOCKED} claim-locks on the task rows, so an
     * independent transaction here means a failure rolls back only this order's
     * work and does not mark the sweep's claim transaction rollback-only. It
     * touches order tables only (never the task table), so there is no lock
     * contention with the sweep's claim.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void autoCancelOrder(LitemallOrderId orderId, String reason) {
        LitemallOrderAggregate orderAggregate = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found"));
        orderAggregate.autoCancel(); // validates CREATED→SYSTEM_CANCELED, records change + event
        int updated = orderRepository.markSystemCanceledIfCreated(orderId);
        if (updated == 0) {
            // The order left CREATED between the sweep's selection and now (e.g. the
            // customer paid). That is not an error for the sweep — just skip it.
            log.info("Skipping auto-cancel of order {}: no longer in CREATED state", orderId.getId());
            return;
        }
        restoreStockForOrder(orderId);
        persistStatusHistory(orderAggregate);
        publishAndClearEvents(orderAggregate);
    }

    /**
     * Mark an order as paid: validate + apply the CREATED→PAID transition on the
     * aggregate, persist the status (+ pay_time) and history, and publish the
     * resulting domain events. Runs inside the caller's transaction so it is atomic
     * with the payment debit.
     */
    public void markOrderPaid(LitemallOrderId orderId) {
        LitemallOrderAggregate orderAggregate = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found"));
        orderAggregate.markAsPaid();

        // Conditional CREATED->PAID transition: the UPDATE only matches a row still
        // in CREATED, so a retried or concurrent PAY (which already flipped the row)
        // affects 0 rows. We then abort, rolling back any wallet debit applied in
        // this same transaction — preventing a double charge for one order.
        int updated = orderRepository.markPaidIfCreated(orderId);
        if (updated == 0) {
            throw new IllegalStateException(
                    "Order " + orderId.getId() + " is no longer in CREATED state; payment already applied");
        }

        persistStatusHistory(orderAggregate);
        publishAndClearEvents(orderAggregate);
    }

    /**
     * Admin/fulfillment ships a paid order (PAID → SHIPPED). Guarded so only a paid
     * order can ship; records the transition and publishes the shipped event.
     */
    public void shipOrder(LitemallOrderId orderId, String shipChannel, String shipSn) {
        LitemallOrderAggregate agg = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found"));
        agg.ship(shipChannel, shipSn);
        int updated = orderRepository.markShippedIfPaid(orderId, shipChannel, shipSn, agg.getShipTime());
        if (updated == 0) {
            throw new IllegalStateException("Order " + orderId.getId() + " cannot ship: not in PAID state");
        }
        persistStatusHistory(agg);
        publishAndClearEvents(agg);
    }

    /** Customer confirms receipt (SHIPPED → DELIVERED). */
    public void confirmDelivery(LitemallOrderId orderId) {
        LitemallOrderAggregate agg = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found"));
        agg.confirmDelivery();
        int updated = orderRepository.markDeliveredIfShipped(orderId, agg.getConfirmTime());
        if (updated == 0) {
            throw new IllegalStateException("Order " + orderId.getId() + " cannot be confirmed: not in SHIPPED state");
        }
        persistStatusHistory(agg);
        publishAndClearEvents(agg);
    }

    /**
     * System auto-confirms a shipped order after the grace window
     * (SHIPPED → AUTO_DELIVERED). Own transaction, like {@link #autoCancelOrder}.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void autoConfirmOrder(LitemallOrderId orderId) {
        LitemallOrderAggregate agg = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found"));
        agg.autoConfirm();
        int updated = orderRepository.markAutoDeliveredIfShipped(orderId, agg.getConfirmTime());
        if (updated == 0) {
            log.info("Skipping auto-confirm of order {}: no longer in SHIPPED state", orderId.getId());
            return;
        }
        persistStatusHistory(agg);
        publishAndClearEvents(agg);
    }

    /** Customer opens a refund/return (PAID|SHIPPED → REFUND_REQUEST). */
    public void requestRefund(LitemallOrderId orderId, String reason) {
        LitemallOrderAggregate agg = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found"));
        agg.requestRefund(reason);
        int updated = orderRepository.markRefundRequestedIfPayable(orderId, reason);
        if (updated == 0) {
            throw new IllegalStateException(
                    "Order " + orderId.getId() + " cannot request refund: not in PAID/SHIPPED state");
        }
        persistStatusHistory(agg);
        publishAndClearEvents(agg);
    }

    /**
     * Admin approves a refund; the money has already been returned by the caller
     * (REFUND_REQUEST → REFUNDED). The orchestrator credits the wallet before calling
     * this, so the credit and the status flip are atomic.
     */
    public void refundOrder(LitemallOrderId orderId, LitemallMoney amount) {
        LitemallOrderAggregate agg = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found"));
        agg.refund(amount);
        int updated = orderRepository.markRefundedIfRequested(
                orderId, amount == null ? null : amount.getAmount(), agg.getRefundTime());
        if (updated == 0) {
            throw new IllegalStateException(
                    "Order " + orderId.getId() + " cannot be refunded: not in REFUND_REQUEST state");
        }
        persistStatusHistory(agg);
        publishAndClearEvents(agg);
    }

    /** Soft-delete a terminal order (sets the logical-delete flag). */
    public void deleteOrder(LitemallOrderId orderId) {
        orderRepository.deleteByOrderId(orderId);
    }

    /** Full status-history timeline for an order, oldest first. */
    public List<LitemallOrderStatusChange> getStatusHistory(LitemallOrderId orderId) {
        return statusHistoryRepository.findByOrderId(orderId);
    }

    /**
     * Best-effort release of the stock reserved for an order's lines (used when an
     * order is cancelled). Reads the persisted order-goods rows and asks the goods
     * ACL to add the quantities back; never throws.
     */
    private void restoreStockForOrder(LitemallOrderId orderId) {
        List<LitemallOrderGoodsAggregate> orderGoods = orderGoodsRepository.findByOId(orderId);
        if (orderGoods == null || orderGoods.isEmpty()) {
            return;
        }
        Map<Integer, Integer> productQuantities = orderGoods.stream()
                .collect(Collectors.toMap(
                        g -> g.getProductId().getId(),
                        g -> (int) g.getNumber(),
                        Integer::sum));
        goodsFacade.restoreStock(productQuantities);
    }
    /**
     * @Desc: Validate and reduce stock for all items in a batch
     * @param cartList
     */
    private void validateAndReduceStock(List<LitemallCartAggregate> cartList) {
        // STEP 1: Load all required data in batch
        AggregatesValidationContext context = loadAggregatesValidationContext(cartList);

        if (!context.isValidForValidation()) {
            throw new RuntimeException("Failed to load required product data for validation");
        }

        // STEP 2: Validate stock for all items
        validateStockForAllItems(cartList, context);

        // STEP 3: Reduce stock for all items in batch
        reduceStockForAllItems(cartList, context);
    }
    /**
     *
     * @param cartList
     * @return
     */
    private AggregatesValidationContext loadAggregatesValidationContext(List<LitemallCartAggregate> cartList){

        try {
            //Extract all required IDs from one pass
            Set<Integer> goodsIds = cartList.stream()
                    .map(item -> item.getGoodsId().getId())
                    .collect(Collectors.toSet());

            Set<Integer> productIds = cartList.stream()
                    .map(item -> item.getProductId().getId())
                    .collect(Collectors.toSet());

            // Batch load all data in parallel
            CompletableFuture<Map<LitemallGoodsId, LitemallGoodsAggregate>> goodsFuture =
                    CompletableFuture.supplyAsync(() ->
                           batchGetGoodsAggregates(goodsIds));

            CompletableFuture<Map<LitemallGoodsProductId, LitemallGoodsProductAggregate>> productsFuture =
                    CompletableFuture.supplyAsync(() ->
                            batchGetProductAggregates(goodsIds));

            // Wait for all batch requests to complete
            //Map<LitemallGoodsId, LitemallGoodsAggregate> goodsMap = goodsFuture.get();
            Map<LitemallGoodsId, LitemallGoodsAggregate> goodsMap = goodsFuture.join();
            Map<LitemallGoodsProductId, LitemallGoodsProductAggregate> productsMap = productsFuture.join();

            return AggregatesValidationContext.create(goodsMap, productsMap);


        } catch (Exception e) {
            // A goods-management outage must fail placement cleanly (rollback, no
            // stock taken), not be swallowed into an empty validation context.
            Throwable cause = (e instanceof java.util.concurrent.CompletionException && e.getCause() != null)
                    ? e.getCause() : e;
            log.error("Failed to load goods from the goods ACL for cartList {}", cartList, cause);
            if (cause instanceof org.linlinjava.litemall.order.application.util.exception.product.LitemallGoodsServiceUnavailableException unavailable) {
                throw unavailable;
            }
            throw new org.linlinjava.litemall.order.application.util.exception.product.LitemallGoodsServiceUnavailableException(
                    "failed to load goods for stock reservation", cause);
        }
    }

    private Map<LitemallGoodsId, LitemallGoodsAggregate> convertToDomainGoodsMap(Map<LitemallGoodsId, LitemallGoodsAggregate> goodsMap) {
        return goodsMap.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> new LitemallGoodsId(entry.getKey().getId()),
                        Map.Entry::getValue
                ));
    }

    private Map<LitemallGoodsId, LitemallGoodsAggregate> batchGetGoodsAggregates(Set<Integer> goodsIds) {
        // Single batch call through the goods ACL instead of N individual calls
        return goodsFacade.batchGetGoods(goodsIds);
    }

    private Map<LitemallGoodsProductId, LitemallGoodsProductAggregate> batchGetProductAggregates(Set<Integer> goodsIds) {
        // goods-management is goodsId-centric; fetch each goods' variants through the
        // ACL and key them by product id for the stock-validation lookups.
        Map<LitemallGoodsProductId, LitemallGoodsProductAggregate> productsMap = new java.util.HashMap<>();
        for (Integer goodsId : goodsIds) {
            for (LitemallGoodsProductAggregate product : goodsFacade.getProductsByGoods(new LitemallGoodsId(goodsId))) {
                productsMap.put(product.getGoodsProductId(), product);
            }
        }
        return productsMap;
    }

    private void validateStockForAllItems(List<LitemallCartAggregate> cartList, AggregatesValidationContext context){
        List<String> outOfStockItems = new ArrayList<>();

        for (LitemallCartAggregate cartItem : cartList) {
            LitemallGoodsProductAggregate product = context.getProduct(cartItem.getProductId());

            int remainNumber = product.getNumber() - cartItem.getNumber();
            if (remainNumber < 0) {
                LitemallGoodsAggregate goods = context.getGoods(cartItem.getGoodsId());
                outOfStockItems.add(String.format(
                        "Product %s (ID: %d): requested %d, available %d",
                        goods.getGoodsName(),
                        product.getGoodsProductId().getId(),
                        cartItem.getNumber(),
                        product.getNumber()
                ));
            }
        }

        if (!outOfStockItems.isEmpty()) {
            String errorMessage = "Insufficient stock for products:\n" +
                    String.join("\n", outOfStockItems);
            throw new RuntimeException(errorMessage);
        }
    }

    private void reduceStockForAllItems(List<LitemallCartAggregate> cartList, AggregatesValidationContext context){

        // Prepare batch reduce stock requests (product id -> requested quantity)
        Map<Integer, Integer> productQuantities = cartList.stream()
                .collect(Collectors.toMap(
                        cartItem -> cartItem.getProductId().getId(),
                        LitemallCartAggregate::getNumber,
                        Integer::sum
                ));

        // Single batch reserve/reduce through the goods ACL
        Map<Integer, Boolean> reduceResults = goodsFacade.reduceStock(productQuantities);

        // Verify EVERY requested product was confirmed reduced. Treat a missing key
        // (null) the same as an explicit false — a degraded/partial response must
        // never be read as "all reduced".
        List<Integer> failedReductions = productQuantities.keySet().stream()
                .filter(productId -> !Boolean.TRUE.equals(reduceResults.get(productId)))
                .toList();

        if (!failedReductions.isEmpty()) {
            throw new RuntimeException("Stock reduction failed/unconfirmed for product IDs: " + failedReductions);
        }

        // The remote reserve has now committed in goods-management. If THIS local
        // transaction subsequently rolls back, the decrement would be orphaned, so
        // register a compensating release on rollback (best-effort — see
        // LitemallGoodsFacade.restoreStock).
        registerStockRestoreOnRollback(productQuantities);
    }

    /**
     * Register a transaction-synchronization that releases the just-reserved stock
     * if (and only if) the surrounding transaction rolls back. No-op when there is
     * no active transaction.
     */
    private void registerStockRestoreOnRollback(Map<Integer, Integer> productQuantities) {
        if (productQuantities.isEmpty() || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    log.warn("Order placement rolled back after stock reserve; compensating restore for {}",
                            productQuantities);
                    goodsFacade.restoreStock(productQuantities);
                }
            }
        });
    }

    public LitemallGrouponRepository getGrouponRepository() {
        return this.grouponServiceLayer.getGrouponRepository();
    }


}
