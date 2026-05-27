package org.linlinjava.litemall.order.application;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;


import org.linlinjava.litemall.core.notify.NotifyService;
import org.linlinjava.litemall.core.notify.NotifyType;
import org.linlinjava.litemall.order.application.exceptions.LitemallOrderServiceException;
import org.linlinjava.litemall.order.application.internal.LitemallGrouponServiceLayer;
import org.linlinjava.litemall.order.application.internal.LitemallOrderServiceImpl;
import org.linlinjava.litemall.order.domain.events.LitemallDomainEventPublisher;
import org.linlinjava.litemall.order.domain.events.groupon.LitemallGrouponParticipatedEvent;
import org.linlinjava.litemall.order.domain.events.order.LitemallOrderCreatedEvent;
import org.linlinjava.litemall.order.domain.events.payment.LitemallOrderPaymentSuccessEvent;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallCartAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallGrouponAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallGrouponRulesAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.commands.LitemallOrderCancelCommand;
import org.linlinjava.litemall.order.domain.model.commands.cart.LitemallAddCartItemCommand;
import org.linlinjava.litemall.order.domain.model.commands.cart.LitemallUpdateCartItemCommand;
import org.linlinjava.litemall.order.domain.model.commands.payment.LitemallOrderPaymentCommand;
import org.linlinjava.litemall.order.domain.model.commands.LitemallOrderSubmitResult;
import org.linlinjava.litemall.order.domain.model.commands.LitemallPlaceOrderCommand;
import org.linlinjava.litemall.order.domain.service.order.LitemallOrderOperationResult;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallCartRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.linlinjava.litemall.order.domain.model.util.LitemallOrderHandleOption;
import org.linlinjava.litemall.order.domain.model.util.LitemallOrderStatusQuery;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallCartId;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallGrouponStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsId;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsProductId;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class LitemallOrderOrchestratorService {

    private final LitemallOrderServiceImpl orderServiceImpl;
    private final LitemallOrderRepository orderRepository;
    private final LitemallGrouponServiceLayer grouponServiceLayer;
    private final LitemallCartRepository cartRepository;

    @Autowired
    private NotifyService notifyService;

    @Autowired
    LitemallDomainEventPublisher domainEventPublisher;


    public LitemallOrderOrchestratorService(LitemallOrderServiceImpl orderService, LitemallOrderRepository orderRepository,
                                            LitemallGrouponServiceLayer grouponService,
                                            LitemallCartRepository cartRepository) {
        this.orderServiceImpl = orderService;
        this.orderRepository = orderRepository;
        this.grouponServiceLayer = grouponService;
        this.cartRepository = cartRepository;
    }


    public enum OrderAction {
        CREATE,    // Uses orderService.placeOrder()
        CANCEL, PAY, CONFIRM_PAYMENT, REQUEST_REFUND,
        CONFIRM_SHIPPING, CONFIRM_DELIVERY, ADD_COMMENT,
        REBUY, REQUEST_AFTERSALE
    }

    // MAIN ENTRY POINT - handles ALL order actions
    public LitemallOrderOperationResult performOrderAction(OrderAction action,
                                                           Object actionData) {

        switch (action) {
            case CREATE:
                return handleOrderCreation((LitemallPlaceOrderCommand) actionData);

            case CANCEL:
                return handleOrderCancellation((LitemallOrderCancelCommand) actionData);

           /* case PAY:
                return handlePayAction((OrderActionRequest) actionData);*/

            // ... other actions

            default:
                throw new IllegalStateException("Unmapped order action: " + action);
        }
    }

    // =========================================================================
    // ACTION HANDLERS - USING YOUR EXISTING SERVICE
    // =========================================================================

    private LitemallOrderOperationResult handleOrderCreation(LitemallPlaceOrderCommand command) {
        try {
            LitemallOrderSubmitResult submitResult = orderServiceImpl.placeOrder(command);
            LitemallOrderOperationResult result = convertSubmitResultToOperationResult(submitResult, command);

            // Domain-event emission is in-process here; the AFTER_COMMIT
            // listener forwards to Kafka only if this transaction commits.
            publishOrderCreationEvents(submitResult.getOrderId(), !submitResult.isNeedsPayment());

            return result;

        } catch (LitemallOrderServiceException e) {
            return LitemallOrderOperationResult.submitFailed("Order creation failed: " + e.getMessage());
        } catch (Exception e) {
            return LitemallOrderOperationResult.operationFailed(
                    LitemallOrderOperationResult.OperationType.SUBMIT, null,
                    "System error during order creation: " + e.getMessage());
        }
    }

    private LitemallOrderOperationResult handleOrderCancellation(LitemallOrderCancelCommand cancelCommand) {

        LitemallOrderAggregate order = orderServiceImpl.getOrderAggregate(new LitemallOrderId(cancelCommand.getOrderId().getId())).orElseThrow(
                () -> new IllegalArgumentException("Order not found")
        );
        LitemallOrderId orderId = new LitemallOrderId(cancelCommand.getOrderId().getId());
        // Validate using StatusQuery
        if (!LitemallOrderStatusQuery.isActionAllowed(order, OrderAction.CANCEL)) {
            return LitemallOrderOperationResult.invalidStateTransition(
                    orderId,
                    LitemallOrderOperationResult.OperationType.CANCEL,
                    order.getOrderStatus());
        }
        // Perform cancellation using domain logic through orderServiceImpl
        LitemallOrderStatus previousStatus = order.getOrderStatus();
        //order.cancel(request.getReason());
        orderServiceImpl.cancelOrder(orderId, cancelCommand.getReason());

        // Use your existing service for post-cancellation logic if needed
        handlePostCancellation(order);

        return LitemallOrderOperationResult.cancelSuccess(
                order.getOrderId(), previousStatus,
                LitemallOrderHandleOption.forStatus(order.getOrderStatus()));
    }

    private LitemallOrderOperationResult handlePaymentAction(LitemallOrderPaymentCommand paymentCommand) {
        LitemallOrderId orderId = new LitemallOrderId(paymentCommand.getOrderId().getId());
        LitemallOrderAggregate order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        // Validate payment action
        if (!LitemallOrderStatusQuery.isActionAllowed(order, OrderAction.PAY)) {
            return LitemallOrderOperationResult.invalidStateTransition(
                    orderId,
                    LitemallOrderOperationResult.OperationType.PAY,
                    order.getOrderStatus());
        }

        // Process payment (simplified - integrate with your payment gateway)
        boolean paymentSuccess = processPayment(order, paymentCommand.getPaymentInfo());

        if (paymentSuccess) {
            // Update order status
            //updateOrderStatusToPaid(orderId);

            // Handle post-payment logic
            handlePostPayment(orderId, paymentCommand);

            // Publish events
            domainEventPublisher.publish(new LitemallOrderPaymentSuccessEvent(
                    orderId,
                    order.getActualPrice(),
                    LocalDateTime.now()
            ));

            return LitemallOrderOperationResult.paySuccess(
                    orderId,
                    order.getOrderStatus(),
                    LitemallOrderHandleOption.forStatus(LitemallOrderStatus.PAID));
        } else {
            return LitemallOrderOperationResult.payFailed(
                    orderId, "Payment processing failed");
        }
    }

    /*private LitemallOrderOperationResult handlePayAction(OrderActionRequest request) {
        LitemallOrderAggregate order = orderRepository.findById(request.getOrderId());
        LitemallOrderStatus previousStatus = order.getOrderStatus();

        PaymentResult paymentResult = paymentService.processPayment(order, request.getPaymentInfo());

        if (paymentResult.isSuccess()) {
            order.markAsPaid();
            orderRepository.save(order);

            // Use your existing service's logic for post-payment handling
            handlePostPayment(order, request.getPaymentInfo());

            return LitemallOrderOperationResult.paySuccess(
                    order.getOrderId(), previousStatus,
                    LitemallOrderHandleOption.forStatus(order.getOrderStatus()));
        } else {
            return LitemallOrderOperationResult.payFailed(
                    order.getOrderId(), paymentResult.getErrorMessage());
        }
    }*/

    // =========================================================================
    // UTILITY METHODS - FOR POST-PROCESSING AND OTHER LOGIC
    // =========================================================================

   private void handlePostPayment(LitemallOrderId orderId, LitemallOrderPaymentCommand paymentCommand) {
       handleGrouponAfterPayment(orderId);
       sendPaymentSuccessNotifications(orderRepository.findById(orderId).get());
       // No per-order timeout to cancel — LitemallOrderUnpaidSweeper now
       // reaps unpaid orders by querying status + add_time, so a paid order
       // is naturally excluded by its updated status.
   }

    private boolean processPayment(LitemallOrderAggregate order, Object paymentInfo) {
        // Integrate with your payment gateway here
        // This is a simplified implementation
        return true; // Assume success for demo
    }

    private void handlePostCancellation(LitemallOrderAggregate order) {
        // Reuse inventory restoration logic from your service if needed
        // This would be similar to your stock reduction but in reverse

        // Notify relevant services about cancellation
        //orderServiceImpl.getNotifyService().notifyMail("Order cancelled", order.toString());
    }

    private void updateGrouponAfterPayment(LitemallGrouponAggregate groupon,
                                           LitemallGrouponRulesAggregate rules) {
        // Reuse your existing groupon update logic
        groupon.setGrouponStatus(LitemallGrouponStatus.STATUS_ON);
        orderServiceImpl.getGrouponRepository().saveGroupon(groupon);
    }

    // =========================================================================
    // RESULT CONVERSION - BRIDGING OLD AND NEW
    // =========================================================================

    private LitemallOrderOperationResult convertSubmitResultToOperationResult(LitemallOrderSubmitResult submitResult, LitemallPlaceOrderCommand command) {

        // Extract order ID from your existing result
        LitemallOrderId orderId = new LitemallOrderId(submitResult.getOrderId());

        if (submitResult.isNeedsPayment()) {
            return LitemallOrderOperationResult.submitSuccessWithPayment(
                    orderId,
                    LitemallOrderHandleOption.forStatus(LitemallOrderStatus.CREATED)
            );
        } else {
            return LitemallOrderOperationResult.submitSuccessPaid(
                    orderId,
                    LitemallOrderHandleOption.forStatus(LitemallOrderStatus.PAID)
            );
        }
    }


    /**
     *
     * @param orderAggregate
     */
    private void sendPaymentSuccessNotifications(LitemallOrderAggregate orderAggregate) {
        // Send email notification to admin
        notifyService.notifyMail("New order notification", orderAggregate.toString());

        // Send SMS notification to user
        String orderSnSuffix = orderAggregate.getOrderSn().substring(8, 14);
        notifyService.notifySmsTemplateSync(
                orderAggregate.getMobile(),
                NotifyType.PAY_SUCCEED,
                new String[]{orderSnSuffix}
        );
    }

    private void handleGrouponAfterPayment(LitemallOrderId orderId) {
        LitemallGrouponAggregate grouponAggregate = grouponServiceLayer.getGrouponAggregateByOrderId(orderId);

        if (grouponAggregate != null) {
            LitemallGrouponRulesAggregate grouponRulesAggregate =
                    grouponServiceLayer.getGrouponRulesById(grouponAggregate.getGrouponRulesId());
            grouponServiceLayer.updateGrouponAfterPayment(grouponAggregate, grouponRulesAggregate);

            // Publish groupon participation event
            domainEventPublisher.publish(new LitemallGrouponParticipatedEvent(
                    grouponAggregate.getGrouponId(),
                    orderId,
                    grouponAggregate.getCreatorUserId().getId(),
                    LocalDateTime.now()
            ));
        }
    }


    // =========================================================================
    // CONVENIENCE METHODS FOR SPECIFIC OPERATIONS
    // =========================================================================

    public LitemallOrderOperationResult createOrder(LitemallPlaceOrderCommand command) {
        return performOrderAction(OrderAction.CREATE, command);
    }

    //public LitemallOrderOperationResult cancelOrder(LitemallOrderId orderId, LitemallUserId userId, String reason) {
    public LitemallOrderOperationResult cancelOrder(LitemallOrderCancelCommand cancelCommand) {
        //OrderActionRequest request = new OrderActionRequest(orderId, userId, reason);
        return performOrderAction(OrderAction.CANCEL, cancelCommand);
    }

   /* public LitemallOrderOperationResult payOrder(LitemallOrderId orderId, LitemallUserId userId, PaymentInfo paymentInfo) {
        OrderActionRequest request = new OrderActionRequest(orderId, userId, paymentInfo);
        return performOrderAction(OrderAction.PAY, request);
    }*/


    // =========================================================================
    // DOMAIN EVENT PUBLISHING
    // =========================================================================

    private void publishOrderCreationEvents(Integer orderId, boolean paymentProcessed) {
        LitemallOrderId orderIdObj = new LitemallOrderId(orderId);
        LitemallOrderAggregate order = orderServiceImpl.getOrderAggregate(orderIdObj).orElseThrow(() -> new RuntimeException("Order not found"));

        // Publish order created event
        domainEventPublisher.publish(new LitemallOrderCreatedEvent(
                orderIdObj,
                order.getActualPrice(),
                order.getUserId().getId(),
                order.getOrderSn()
                //LocalDateTime.now()
        ));

        if (paymentProcessed) {
            domainEventPublisher.publish(new LitemallOrderPaymentSuccessEvent(
                    orderIdObj,
                    order.getActualPrice(),
                    LocalDateTime.now()
            ));
        }
    }

    // =========================================================================
    // CART CRUD — exposed for LitemallCartController. The controller never
    // touches persistence directly; every read and write flows through here.
    // =========================================================================

    public List<LitemallCartAggregate> getCart(LitemallUserId userId) {
        return cartRepository.findByUserId(userId);
    }

    public LitemallCartAggregate getCartItem(LitemallCartId cartId) {
        LitemallCartAggregate item = cartRepository.findById(cartId);
        if (item == null) {
            throw new LitemallOrderServiceException("Cart item not found: " + cartId.getId());
        }
        return item;
    }

    public LitemallCartAggregate addCartItem(LitemallAddCartItemCommand command) {
        if (command.getNumber() == null || command.getNumber() <= 0) {
            throw new LitemallOrderServiceException("Cart item number must be positive");
        }
        LitemallUserId userId = new LitemallUserId(command.getUserId());
        LitemallGoodsId goodsId = new LitemallGoodsId(command.getGoodsId());
        LitemallGoodsProductId productId = new LitemallGoodsProductId(command.getProductId());

        LitemallCartAggregate existing = cartRepository.findByUserIdAndGoodsId(userId, goodsId, productId);
        if (existing != null) {
            existing.setNumber(existing.getNumber() + command.getNumber());
            cartRepository.update(existing);
            return existing;
        }

        LitemallCartAggregate cart = new LitemallCartAggregate();
        cart.setUserId(userId);
        cart.setGoodsId(goodsId);
        cart.setProductId(productId);
        cart.setNumber(command.getNumber());
        cart.setGoodsName(command.getGoodsName());
        cart.setGoodsSn(command.getGoodsSn());
        cart.setPicUrl(command.getPicUrl());
        cart.setPrice(command.getPrice() == null ? null : new LitemallMoney(command.getPrice()));
        cart.setSpecifications(command.getSpecifications());
        cart.setChecked(true);
        cart.setDeleted(false);
        cartRepository.addNewCart(cart);
        return cart;
    }

    public LitemallCartAggregate updateCartItem(LitemallUpdateCartItemCommand command) {
        LitemallCartAggregate existing = cartRepository.findById(new LitemallCartId(command.getCartId()));
        if (existing == null) {
            throw new LitemallOrderServiceException("Cart item not found: " + command.getCartId());
        }
        if (command.getNumber() != null) {
            if (command.getNumber() <= 0) {
                throw new LitemallOrderServiceException("Cart item number must be positive");
            }
            existing.setNumber(command.getNumber());
        }
        if (command.getProductId() != null) {
            existing.setProductId(new LitemallGoodsProductId(command.getProductId()));
        }
        if (command.getSpecifications() != null) {
            existing.setSpecifications(command.getSpecifications());
        }
        cartRepository.update(existing);
        return existing;
    }

    public void removeCartItem(LitemallCartId cartId) {
        cartRepository.deleteById(cartId);
    }

    public void clearCart(LitemallUserId userId) {
        cartRepository.clearCheckedByUserId(userId);
    }
}
