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
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderGoodsAggregate;
import org.linlinjava.litemall.order.domain.model.commands.LitemallOrderCancelCommand;
import org.linlinjava.litemall.order.domain.model.commands.payment.LitemallOrderPaymentCommand;
import org.linlinjava.litemall.order.domain.model.commands.wallet.LitemallWalletCreditCommand;
import org.linlinjava.litemall.order.domain.model.commands.wallet.LitemallWalletDebitCommand;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderStatusChange;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.order.domain.model.commands.LitemallOrderSubmitResult;
import org.linlinjava.litemall.order.domain.model.commands.LitemallPlaceOrderCommand;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.payment.PaymentMethod;
import org.linlinjava.litemall.order.domain.service.order.LitemallOrderOperationResult;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallBillRepository;
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

    // ACL over goods-management — used to enrich a cart line built from just
    // {goodsId, productId, number} (legacy /srv/cart/add) with name/sn/price/specs/image.
    @Autowired
    private org.linlinjava.litemall.order.infrastructure.services.acl.facades.LitemallGoodsFacade goodsFacade;

    @Autowired
    private NotifyService notifyService;

    // Unpaid-order timeout mechanism: DB-row + @Scheduled sweep
    // (see UnpaidOrderTaskScheduler). Survives restart; replaces the
    // in-memory DelayQueue/TaskService that used to live in litemall-core.
    @Autowired
    private UnpaidOrderTaskScheduler unpaidOrderTaskScheduler;

    @Autowired
    LitemallDomainEventPublisher domainEventPublisher;

    // Pay-first CJ fulfillment: a source='cj' order is replayed to CJ createOrder
    // right after it turns PAID, inside the same transaction (CJ rejection rolls
    // back the wallet debit + PAID status together).
    @Autowired
    private org.linlinjava.litemall.order.application.internal.cj.CjFulfillmentService cjFulfillmentService;

    // Tags/validates the order's fulfillment source from the cart before placement
    // (mixed CJ+local carts are a clean client error, pre-checked outside placeOrder).
    @Autowired
    private org.linlinjava.litemall.order.application.internal.cj.OrderSourceResolver orderSourceResolver;

    // Wallet vertical (absorbed from litemall-wallet-service). Used to debit the
    // user's wallet when PaymentMethod.WALLET is selected — mirrors how groupon
    // flows through grouponServiceLayer.
    @org.springframework.beans.factory.annotation.Autowired
    private org.linlinjava.litemall.order.application.LitemallIWalletService walletService;

    // Read-side: order line items, used to populate the customer order list/detail
    // (goodsList / orderGoods) without going back through goods-management.
    @Autowired
    private org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderGoodsRepository orderGoodsRepository;


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
        // Pre-validate the cart OUTSIDE the nested transactional placeOrder. An empty
        // checked cart is a clean client error (422 with this message), not a phantom
        // zero-line order nor a rollback-only 502: throwing from inside placeOrder
        // (@Transactional) would mark the shared transaction rollback-only and surface
        // a generic 500/502 even though we catch it. placeOrder keeps its own guard as
        // a safety net for any other caller.
        int cartId = command.getCartId() == null ? 0 : command.getCartId();
        java.util.List<LitemallCartAggregate> checkedItems = cartServiceLayer.getCheckedCartItems(
                new org.linlinjava.litemall.order.domain.model.valueobjects.LitemallCartId(cartId),
                new org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId(command.getUserId()));
        if (checkedItems == null || checkedItems.isEmpty()
                || checkedItems.stream().allMatch(java.util.Objects::isNull)) {
            return LitemallOrderOperationResult.submitFailed(
                    "Your cart is empty — add at least one item before placing an order.");
        }
        // Same pre-check rationale as the empty-cart guard above: a mixed CJ+local
        // cart must fail as a clean 422 from OUTSIDE the transactional placeOrder
        // (which re-resolves the source when tagging the order).
        try {
            orderSourceResolver.resolve(checkedItems);
        } catch (LitemallOrderServiceException e) {
            return LitemallOrderOperationResult.submitFailed(e.getMessage());
        }
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
        } catch (org.linlinjava.litemall.order.application.util.exception.product.LitemallInsufficientStockException
                 | org.linlinjava.litemall.order.application.util.exception.product.LitemallGoodsServiceUnavailableException
                 | org.linlinjava.litemall.order.application.util.exception.coupon.LitemallInvalidCouponException
                 | org.linlinjava.litemall.order.application.util.exception.coupon.LitemallPromotionServiceUnavailableException e) {
            // Clean placement failures (out of stock / goods-service down / coupon
            // rejected / promotion down with a coupon selected). They were thrown
            // inside the transactional placeOrder, so the shared transaction is
            // already rollback-only — returning a result here would trip
            // UnexpectedRollbackException at commit. Propagate typed; the REST layer
            // (outside the transaction) maps them to a 422/503 envelope.
            throw e;
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
            // commit or both roll back. Emits LitemallOrderPaidEvent. pay_id records
            // the tender ("WALLET" or "<METHOD>:<reference>") so a later refund can
            // be routed back to the channel that paid (see settleRefundToTender).
            orderServiceImpl.markOrderPaid(orderId, tenderPayId(paymentCommand));

            // Pay-first CJ fulfillment: replay a source='cj' order to CJ createOrder
            // now that the money is captured, BEFORE any post-payment notification or
            // event goes out. A CJ rejection throws LitemallCjOrderException, rolling
            // back the debit + PAID status in this same transaction (the REST layer
            // surfaces it as a clean payment failure; order_sn is CJ's idempotency
            // key, so a retried pay never double-places).
            if (order.isCjFulfilled()) {
                cjFulfillmentService.placeForPaidOrder(order, orderGoodsRepository.findByOId(orderId));
            }

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
     * Tender record for {@code pay_id}: {@code "WALLET"} for a wallet debit,
     * {@code "<METHOD>:<reference>"} for a client-confirmed external charge.
     * Refund settlement parses this back to route money to the paying channel.
     */
    private String tenderPayId(LitemallOrderPaymentCommand command) {
        PaymentMethod method = command.getPaymentMethod();
        if (method == null) {
            return null;
        }
        String reference = command.getPaymentReference();
        return reference == null || reference.isBlank()
                ? method.name()
                : method.name() + ":" + reference;
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
                LitemallBillRepository.CATEGORY_ORDER,
                LitemallBillRepository.TYPE_PAYMENT,
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

    /** Total orders for the user under the same (optional) status filter — the {@code total} for the paged list. */
    public int countOrders(org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId userId,
                           java.util.List<Short> orderStatuses) {
        return orderRepository.countByOrderStatus(userId, orderStatuses);
    }

    /**
     * Resolve a single order for its owner. Returns {@code null} when the order
     * does not exist or belongs to another user — the caller surfaces a 404,
     * so a customer can never read another customer's order (same identity rule
     * as submit/cancel/pay).
     */
    public LitemallOrderAggregate getOrderForUser(org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId userId,
                                                  org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId orderId) {
        return orderRepository.findById(orderId)
                .filter(o -> o.getUserId() != null && o.getUserId().getId().equals(userId.getId()))
                .orElse(null);
    }

    /** Line items of an order, for the list/detail {@code goodsList} / {@code orderGoods}. */
    public java.util.List<LitemallOrderGoodsAggregate> getOrderGoods(
            org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId orderId) {
        return orderGoodsRepository.findByOId(orderId);
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

    /**
     * Legacy cart-add ({@code POST /srv/cart/add}): the SPA sends only goodsId/productId/
     * number, so look up the goods + chosen variant through the goods ACL and build a
     * fully-populated, checked cart line before persisting. A missing goods/variant is a
     * clean client error (mapped to a 4xx by the controller), not a later NPE.
     */
    public LitemallCartAggregate addToCart(org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId userId,
                                           Integer goodsId, Integer productId, Integer number) {
        org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsId gid =
                new org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsId(goodsId);

        org.linlinjava.litemall.order.domain.model.agregates.goods.LitemallGoodsAggregate goods =
                goodsFacade.batchGetGoods(java.util.Set.of(goodsId)).get(gid);
        org.linlinjava.litemall.order.domain.model.agregates.goods.LitemallGoodsProductAggregate product =
                goodsFacade.getProductsByGoods(gid).stream()
                        .filter(p -> p.getGoodsProductId() != null && productId.equals(p.getGoodsProductId().getId()))
                        .findFirst()
                        .orElse(null);

        if (goods == null || product == null || product.getPrice() == null) {
            throw new LitemallOrderServiceException(
                    "Cannot add to cart: goods " + goodsId + " / product " + productId + " not found in goods-management");
        }

        LitemallCartAggregate cart = new LitemallCartAggregate();
        cart.setUserId(userId);
        cart.setGoodsId(gid);
        cart.setProductId(new org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsProductId(productId));
        cart.setNumber(number == null || number < 1 ? 1 : number);
        cart.setChecked(true);
        cart.setGoodsName(goods.getGoodsName());
        cart.setGoodsSn(goods.getGoodsSn());
        cart.setPrice(product.getPrice());
        cart.setSpecifications(product.getSpecification());
        // Prefer the variant image; fall back to the goods cover.
        cart.setPicUrl(product.getUrl() != null ? product.getUrl() : goods.getPicUrl());
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
    // FULFILMENT / POST-PAYMENT LIFECYCLE (ship → confirm → refund)
    // =========================================================================

    /**
     * Admin/fulfilment ships a paid order (PAID → SHIPPED). Admin-gated at the gateway,
     * so no per-user ownership check here.
     */
    public LitemallOrderOperationResult shipOrder(LitemallOrderId orderId, String shipChannel, String shipSn) {
        LitemallOrderAggregate order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return LitemallOrderOperationResult.orderNotFound(orderId);
        }
        LitemallOrderStatus previous = order.getOrderStatus();
        if (!previous.canTransitionTo(LitemallOrderStatus.SHIPPED)) {
            return LitemallOrderOperationResult.invalidStateTransition(
                    orderId, LitemallOrderOperationResult.OperationType.SHIP, previous);
        }
        orderServiceImpl.shipOrder(orderId, shipChannel, shipSn);
        return LitemallOrderOperationResult.shipSuccess(
                orderId, previous, LitemallOrderHandleOption.forStatus(LitemallOrderStatus.SHIPPED));
    }

    /**
     * Customer confirms receipt of a shipped order (SHIPPED → DELIVERED). Scoped to the
     * header user — a customer can only confirm their own order.
     */
    public LitemallOrderOperationResult confirmReceipt(LitemallOrderId orderId, LitemallUserId userId) {
        LitemallOrderAggregate order = getOrderForUser(userId, orderId);
        if (order == null) {
            return LitemallOrderOperationResult.orderNotFound(orderId);
        }
        LitemallOrderStatus previous = order.getOrderStatus();
        if (!previous.canTransitionTo(LitemallOrderStatus.DELIVERED)) {
            return LitemallOrderOperationResult.invalidStateTransition(
                    orderId, LitemallOrderOperationResult.OperationType.CONFIRM, previous);
        }
        orderServiceImpl.confirmDelivery(orderId);
        return LitemallOrderOperationResult.confirmSuccess(
                orderId, previous, LitemallOrderHandleOption.forStatus(LitemallOrderStatus.DELIVERED));
    }

    /**
     * Customer opens a refund/return (PAID|SHIPPED → REFUND_REQUEST). Scoped to the
     * header user. Awaits admin approval (see {@link #approveRefund}).
     */
    public LitemallOrderOperationResult requestRefund(LitemallOrderId orderId, LitemallUserId userId, String reason) {
        LitemallOrderAggregate order = getOrderForUser(userId, orderId);
        if (order == null) {
            return LitemallOrderOperationResult.orderNotFound(orderId);
        }
        LitemallOrderStatus previous = order.getOrderStatus();
        if (!LitemallOrderStatusQuery.isActionAllowed(order, OrderAction.REQUEST_REFUND)) {
            return LitemallOrderOperationResult.invalidStateTransition(
                    orderId, LitemallOrderOperationResult.OperationType.REFUND, previous);
        }
        orderServiceImpl.requestRefund(orderId, reason);
        return LitemallOrderOperationResult.refundRequestSuccess(
                orderId, previous, LitemallOrderHandleOption.forStatus(LitemallOrderStatus.REFUND_REQUEST));
    }

    /**
     * Admin approves a pending refund (REFUND_REQUEST → REFUNDED). Returns the money
     * to the tender that paid — capped at the captured amount — and flips the status
     * in ONE transaction, so the money return and REFUNDED are atomic.
     *
     * <p>Refund-to-tender (see {@code docs/plan-refund-tender-parity.md}):
     * <ul>
     *   <li><b>WALLET</b> — credit back exactly the wallet debit recorded at pay time
     *       (the ledger is the captured amount), never more than {@code actualPrice}.</li>
     *   <li><b>CARD / digital</b> — the charge lives at the PSP, not the wallet, so no
     *       wallet credit; the reversal is a documented seam (log + the
     *       {@code LitemallOrderRefundedEvent} the aggregate raises), mirroring the
     *       client-confirmed-charge boundary in {@code processPayment}.</li>
     *   <li><b>No tender / no capture</b> — nothing was taken, nothing to return;
     *       the status still flips with {@code refund_amount = 0}.</li>
     * </ul>
     */
    public LitemallOrderOperationResult approveRefund(LitemallOrderId orderId) {
        LitemallOrderAggregate order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return LitemallOrderOperationResult.orderNotFound(orderId);
        }
        LitemallOrderStatus previous = order.getOrderStatus();
        if (!previous.canTransitionTo(LitemallOrderStatus.REFUNDED)) {
            return LitemallOrderOperationResult.invalidStateTransition(
                    orderId, LitemallOrderOperationResult.OperationType.REFUND, previous);
        }
        LitemallMoney refund = settleRefundToTender(order, orderId);
        orderServiceImpl.refundOrder(orderId, refund);
        return LitemallOrderOperationResult.refundSuccess(
                orderId, previous, LitemallOrderHandleOption.forStatus(LitemallOrderStatus.REFUNDED));
    }

    /**
     * Route the refund money to the tender that paid and return the amount actually
     * refunded (what {@code refund_amount} must record). Never exceeds the captured
     * amount: WALLET refunds the recorded debit; an external (CARD/digital) charge is
     * reversed at the PSP seam without touching the wallet.
     */
    private LitemallMoney settleRefundToTender(LitemallOrderAggregate order, LitemallOrderId orderId) {
        LitemallMoney actual = order.getActualPrice();
        if (actual == null || actual.getAmount().signum() <= 0) {
            log.info("Refund for order {}: non-positive order amount, nothing to return", orderId.getId());
            return new LitemallMoney(java.math.BigDecimal.ZERO);
        }

        String orderRef = String.valueOf(orderId.getId());
        LitemallMoney captured = walletService
                .findOrderPaymentDebit(order.getUserId().getId(), orderRef)
                .map(bill -> bill.getAmount())
                .orElse(null);
        PaymentMethod tender = paidTender(order, captured != null);

        if (tender == PaymentMethod.WALLET) {
            if (captured == null) {
                // Wallet tender but no debit on the ledger (e.g. the pay path skipped a
                // non-positive debit). Nothing was captured, so nothing comes back.
                log.warn("Refund for order {}: wallet tender but no recorded wallet debit; nothing to return",
                        orderId.getId());
                return new LitemallMoney(java.math.BigDecimal.ZERO);
            }
            LitemallMoney refund = new LitemallMoney(actual.getAmount().min(captured.getAmount()));
            creditWalletForRefund(order, orderId, refund);
            return refund;
        }

        if (tender == null) {
            log.warn("Refund for order {}: no recorded tender and no wallet capture; "
                    + "completing refund with amount 0", orderId.getId());
            return new LitemallMoney(java.math.BigDecimal.ZERO);
        }

        // External charge (CARD / digital wallet): the money sits at the PSP, so the
        // wallet must NOT be credited. The server-side reversal is the same deliberate
        // seam as the charge itself (client-confirmed Stripe — see processPayment);
        // the LitemallOrderRefundedEvent raised by the aggregate carries the signal.
        log.info("Refund for order {}: would reverse {} charge {} for {} at the PSP (no wallet credit)",
                orderId.getId(), tender, externalReference(order), actual.getAmount());
        return actual;
    }

    /**
     * The tender that paid this order, parsed from the {@code pay_id} record written
     * by {@code markOrderPaid} ({@code "WALLET"} or {@code "<METHOD>:<reference>"}).
     * Orders paid before the tender was recorded fall back to the wallet ledger:
     * a payment debit for the order means it was wallet-paid; otherwise unknown.
     */
    private PaymentMethod paidTender(LitemallOrderAggregate order, boolean walletDebitExists) {
        String payId = order.getPayId();
        if (payId != null && !payId.isBlank()) {
            String name = payId.split(":", 2)[0].trim();
            try {
                return PaymentMethod.valueOf(name);
            } catch (IllegalArgumentException e) {
                log.warn("Order {} has unparseable pay_id tender '{}'; falling back to the wallet ledger",
                        order.getOrderId().getId(), payId);
            }
        }
        return walletDebitExists ? PaymentMethod.WALLET : null;
    }

    /** The PSP reference recorded after the tender in {@code pay_id}, if any. */
    private String externalReference(LitemallOrderAggregate order) {
        String payId = order.getPayId();
        if (payId == null || payId.indexOf(':') < 0) {
            return "<none>";
        }
        return payId.substring(payId.indexOf(':') + 1);
    }

    /** Customer soft-deletes a terminal order (delete is offered only on terminal states). */
    public LitemallOrderOperationResult deleteOrder(LitemallOrderId orderId, LitemallUserId userId) {
        LitemallOrderAggregate order = getOrderForUser(userId, orderId);
        if (order == null) {
            return LitemallOrderOperationResult.orderNotFound(orderId);
        }
        LitemallOrderStatus current = order.getOrderStatus();
        if (!LitemallOrderHandleOption.forStatus(current).isDelete()) {
            return LitemallOrderOperationResult.operationFailed(
                    LitemallOrderOperationResult.OperationType.UPDATE, orderId,
                    "Order cannot be deleted in " + current + " status");
        }
        orderServiceImpl.deleteOrder(orderId);
        return LitemallOrderOperationResult.updateSuccess(
                orderId, current, current, "deleted", LitemallOrderHandleOption.forStatus(current));
    }

    /** System auto-confirm hook for the SHIPPED→AUTO_DELIVERED sweep. */
    public void autoConfirmOrder(LitemallOrderId orderId) {
        orderServiceImpl.autoConfirmOrder(orderId);
    }

    /**
     * Status-history timeline for an order, oldest first. Owner-scoped: returns null
     * when the order does not exist or belongs to another user (caller → 404).
     */
    public java.util.List<LitemallOrderStatusChange> getOrderTimeline(LitemallUserId userId, LitemallOrderId orderId) {
        if (getOrderForUser(userId, orderId) == null) {
            return null;
        }
        return orderServiceImpl.getStatusHistory(orderId);
    }

    /**
     * Credit the given (already capped) refund amount back to the buyer's wallet.
     * Idempotent on the ledger's ORDER/REFUND/orderId business key: a retried or
     * replayed approval finds the existing credit and skips — defense in depth on
     * top of the guarded REFUND_REQUEST→REFUNDED transition that already rolls
     * back a losing concurrent approval.
     */
    private void creditWalletForRefund(LitemallOrderAggregate order, LitemallOrderId orderId, LitemallMoney amount) {
        if (amount == null || amount.getAmount().signum() <= 0) {
            log.info("Skipping wallet refund credit for order {}: non-positive amount", orderId.getId());
            return;
        }
        String orderRef = String.valueOf(orderId.getId());
        if (walletService.hasOrderRefundCredit(order.getUserId().getId(), orderRef)) {
            log.warn("Refund credit for order {} already on the wallet ledger; skipping duplicate credit",
                    orderId.getId());
            return;
        }
        LitemallWalletCreditCommand creditCommand = new LitemallWalletCreditCommand(
                order.getUserId().getId(),
                amount.getAmount(),
                "Order refund",
                LitemallBillRepository.CATEGORY_ORDER,
                LitemallBillRepository.TYPE_REFUND,
                orderRef,
                "Wallet refund for order " + order.getOrderSn());
        walletService.credit(creditCommand);
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
