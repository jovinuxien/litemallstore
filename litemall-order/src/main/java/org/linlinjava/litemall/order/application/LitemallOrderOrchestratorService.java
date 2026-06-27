package org.linlinjava.litemall.order.application;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;


import org.linlinjava.litemall.core.notify.NotifyService;
import org.linlinjava.litemall.core.notify.NotifyType;
import org.linlinjava.litemall.order.application.internal.LitemallCartServiceLayer;
import org.linlinjava.litemall.order.application.internal.LitemallGrouponServiceLayer;
import org.linlinjava.litemall.order.application.internal.LitemallOrderServiceImpl;
import org.linlinjava.litemall.order.application.internal.UnpaidOrderTaskScheduler;
import org.linlinjava.litemall.order.application.util.exception.order.LitemallOrderServiceException;
import org.linlinjava.litemall.order.domain.events.LitemallDomainEventPublisher;
import org.linlinjava.litemall.order.domain.events.groupon.LitemallGrouponParticipatedEvent;
import org.linlinjava.litemall.order.domain.events.order.LitemallOrderCreatedEvent;
import org.linlinjava.litemall.order.domain.events.payment.LitemallOrderPaymentSuccessEvent;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallCartAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallGrouponAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallGrouponRulesAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.commands.LitemallOrderCancelCommand;
import org.linlinjava.litemall.order.domain.model.commands.payment.LitemallOrderPaymentCommand;
import org.linlinjava.litemall.order.domain.model.commands.wallet.LitemallWalletDebitCommand;
import org.linlinjava.litemall.order.domain.model.commands.LitemallOrderSubmitResult;
import org.linlinjava.litemall.order.domain.model.commands.LitemallPlaceOrderCommand;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.payment.PaymentMethod;
import org.linlinjava.litemall.order.domain.service.order.LitemallOrderOperationResult;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.linlinjava.litemall.order.domain.model.util.LitemallOrderHandleOption;
import org.linlinjava.litemall.order.domain.model.util.LitemallOrderStatusQuery;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallGrouponStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional
public class LitemallOrderOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(LitemallOrderOrchestratorService.class);

    private final LitemallOrderServiceImpl orderServiceImpl;
    private final LitemallOrderRepository orderRepository;
    private final LitemallGrouponServiceLayer grouponServiceLayer;

    @Autowired
    private LitemallCartServiceLayer cartServiceLayer;

    @Autowired
    private NotifyService notifyService;

    // Unpaid-order timeout mechanism: DB-row + @Scheduled sweep
    // (see UnpaidOrderTaskScheduler). Survives restart; replaces the
    // in-memory DelayQueue/TaskService that used to live in litemall-core.
    @Autowired
    private UnpaidOrderTaskScheduler unpaidOrderTaskScheduler;

    @Autowired
    LitemallDomainEventPublisher domainEventPublisher;

    // Wallet vertical (absorbed from litemall-wallet-service). Used to debit the
    // user's wallet when PaymentMethod.WALLET is selected — mirrors how groupon
    // flows through grouponServiceLayer.
    @org.springframework.beans.factory.annotation.Autowired
    private org.linlinjava.litemall.order.application.LitemallIWalletService walletService;


    public LitemallOrderOrchestratorService(LitemallOrderServiceImpl orderService, LitemallOrderRepository orderRepository,
                                            LitemallGrouponServiceLayer grouponService) {
        this.orderServiceImpl = orderService;
        this.orderRepository = orderRepository;
        this.grouponServiceLayer = grouponService;
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

            case PAY:
                return handlePaymentAction((LitemallOrderPaymentCommand) actionData);

            default:
                throw new IllegalStateException("Unhandled order action: " + action);
        }
    }

    // =========================================================================
    // ACTION HANDLERS - USING YOUR EXISTING SERVICE
    // =========================================================================

    private LitemallOrderOperationResult handleOrderCreation(LitemallPlaceOrderCommand command) {
        try {
            LitemallOrderSubmitResult submitResult = orderServiceImpl.placeOrder(command);
            LitemallOrderOperationResult result = convertSubmitResultToOperationResult(submitResult, command);

            boolean paymentProcessed = !submitResult.isNeedsPayment();
            publishOrderCreationEvents(submitResult.getOrderId(), paymentProcessed);
            if (!paymentProcessed) {
                scheduleUnpaidOrderTask(new LitemallOrderId(submitResult.getOrderId()));
            }

            return result;
        } catch (LitemallOrderServiceException e) {
            return LitemallOrderOperationResult.submitFailed("Order creation failed: " + e.getMessage());
        } catch (com.google.protobuf.ServiceException e) {
            return LitemallOrderOperationResult.submitFailed("Order creation failed: " + e.getMessage());
        } catch (RuntimeException e) {
            log.error("System error during order creation", e);
            throw new LitemallOrderServiceException("System error during order creation: " + e.getMessage(), e);
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

        // Wallet payment: debit the user's wallet through the wallet vertical
        // (aggregate -> LitemallWalletDomainService -> repository, emitting
        // LitemallWalletDebitedEvent). Insufficient balance raises
        // LitemallInsufficientBalanceException, which rolls back this transaction
        // so no paid order is produced.
        if (paymentCommand.getPaymentMethod() == PaymentMethod.WALLET) {
            debitWalletForOrder(order, orderId);
        }

        // Process payment. WALLET is settled by the debit above; CARD is recorded
        // from the client-confirmed Stripe PaymentIntent (see processPayment).
        boolean paymentSuccess = processPayment(order, paymentCommand);

        if (paymentSuccess) {
            // Transition the order to PAID and persist it within this transaction,
            // so a wallet debit (above) and the paid status are atomic — either both
            // commit or both roll back. Emits LitemallOrderPaidEvent.
            orderServiceImpl.markOrderPaid(orderId);

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
       unpaidOrderTaskScheduler.cancel(orderId);
   }

    /**
     * Settle the non-wallet portion of a payment.
     *
     * <p>Boundary (accepted — see {@code docs/handoff-gateway-api-order-payment.md}):
     * <ul>
     *   <li><b>WALLET</b> — already debited server-side in {@link #debitWalletForOrder};
     *       nothing more to do here, so this returns {@code true}.</li>
     *   <li><b>CARD / digital</b> — <i>client-confirmed Stripe</i>: the SPA confirms the
     *       PaymentIntent and passes its id as {@code paymentReference}; we record that
     *       confirmed result rather than charging Stripe server-side. A missing reference
     *       means the client never confirmed, so the payment fails (no paid order).</li>
     * </ul>
     * The server-side Stripe capture remains a deliberate seam: wiring a server-side
     * charge would read the token from {@link LitemallPaymentInfo} here instead.
     */
    private boolean processPayment(LitemallOrderAggregate order, LitemallOrderPaymentCommand command) {
        PaymentMethod method = command.getPaymentMethod();
        if (method == PaymentMethod.WALLET) {
            // Wallet debit already committed within this transaction.
            return true;
        }
        // CARD / digital wallet: require the client-confirmed PaymentIntent id.
        String reference = command.getPaymentReference();
        if (reference == null || reference.isBlank()) {
            log.warn("Payment for order {} via {} has no client-confirmed reference; rejecting",
                    order.getOrderId().getId(), method);
            return false;
        }
        log.info("Recording client-confirmed payment for order {} via {} (ref={})",
                order.getOrderId().getId(), method, reference);
        return true;
    }

    /**
     * Debit the order's actual price from the buyer's wallet via the wallet
     * vertical. Propagates {@code LitemallInsufficientBalanceException} on an
     * underfunded wallet so the payment transaction rolls back without producing
     * a paid order.
     */
    private void debitWalletForOrder(LitemallOrderAggregate order, LitemallOrderId orderId) {
        LitemallMoney payable = order.getActualPrice();
        if (payable == null) {
            throw new IllegalStateException(
                    "Order " + orderId.getId() + " has no payable amount; cannot debit wallet");
        }
        if (payable.getAmount().signum() <= 0) {
            // Nothing to charge (e.g. a fully discounted order) — skip the wallet
            // debit rather than writing a zero-value debit and bill.
            log.info("Skipping wallet debit for order {}: non-positive payable amount", orderId.getId());
            return;
        }
        LitemallWalletDebitCommand debitCommand = new LitemallWalletDebitCommand(
                order.getUserId().getId(),
                payable.getAmount(),
                "Order payment",
                "ORDER",
                "PAYMENT",
                String.valueOf(orderId.getId()),
                "Wallet debit for order " + order.getOrderSn());
        walletService.debit(debitCommand);
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

    private void scheduleUnpaidOrderTask(LitemallOrderId orderId) {
        unpaidOrderTaskScheduler.schedule(orderId);
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

    public java.util.List<LitemallOrderAggregate> listOrders(org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId userId,
                                                             java.util.List<Short> orderStatuses,
                                                             int page, int limit, String sort, String order) {
        return orderRepository.queryByOrderStatus(userId, orderStatuses, page, limit, sort, order);
    }

    // =========================================================================
    // CART OPERATIONS (per-user; admin acts on behalf of a userId)
    // =========================================================================

    public java.util.List<LitemallCartAggregate> listCartItems(org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId userId) {
        return cartServiceLayer.listAllCartItems(userId);
    }

    public LitemallCartAggregate getCartItem(org.linlinjava.litemall.order.domain.model.valueobjects.LitemallCartId cartId,
                                             org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId userId) {
        return cartServiceLayer.getCartItem(cartId, userId);
    }

    public LitemallCartAggregate addCartItem(LitemallCartAggregate cart) {
        return cartServiceLayer.addCartItem(cart);
    }

    public LitemallCartAggregate updateCartItem(org.linlinjava.litemall.order.domain.model.valueobjects.LitemallCartId cartId,
                                                org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId userId,
                                                Integer number, String[] specifications) {
        return cartServiceLayer.updateCartItem(cartId, userId, number, specifications);
    }

    public void removeCartItem(org.linlinjava.litemall.order.domain.model.valueobjects.LitemallCartId cartId,
                               org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId userId) {
        cartServiceLayer.removeCartItem(cartId, userId);
    }

    public void clearCart(org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId userId) {
        cartServiceLayer.clearCart(userId);
    }

    //public LitemallOrderOperationResult cancelOrder(LitemallOrderId orderId, LitemallUserId userId, String reason) {
    public LitemallOrderOperationResult cancelOrder(LitemallOrderCancelCommand cancelCommand) {
        //OrderActionRequest request = new OrderActionRequest(orderId, userId, reason);
        return performOrderAction(OrderAction.CANCEL, cancelCommand);
    }

    /**
     * Pay a placed order. Dispatches {@link OrderAction#PAY} through
     * {@link #performOrderAction}, which (for WALLET) debits the wallet vertical
     * and marks the order PAID within one transaction. An underfunded wallet lets
     * {@link org.linlinjava.litemall.order.application.util.exception.wallet.LitemallInsufficientBalanceException}
     * propagate so the whole transaction rolls back — no paid order is produced;
     * the REST layer maps that to a clean errno.
     */
    public LitemallOrderOperationResult payOrder(LitemallOrderPaymentCommand paymentCommand) {
        return performOrderAction(OrderAction.PAY, paymentCommand);
    }


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

}
