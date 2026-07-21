package org.linlinjava.litemall.order.application;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;


import org.linlinjava.litemall.core.notify.NotifyService;
import org.linlinjava.litemall.core.notify.NotifyType;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.payment.PaymentVerification;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.payment.RefundOutcome;
import org.linlinjava.litemall.order.application.util.exception.payment.LitemallRefundFailedException;
import org.linlinjava.litemall.order.application.internal.BrokerageService;
import org.linlinjava.litemall.order.application.internal.LitemallCartServiceLayer;
import org.linlinjava.litemall.order.application.internal.LitemallGrouponServiceLayer;
import org.linlinjava.litemall.order.application.internal.LitemallOrderServiceImpl;
import org.linlinjava.litemall.order.application.internal.UnpaidOrderTaskScheduler;
import org.linlinjava.litemall.order.application.util.exception.order.LitemallOrderServiceException;
import org.linlinjava.litemall.order.application.util.exception.order.LitemallWriteoffException;
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

    // PSP seam (Wave 7): server-side PaymentIntent verification + refunds. Disabled by
    // default, and the disabled adapter REJECTS — a card payment can never be recorded
    // on the client's say-so.
    @Autowired
    private org.linlinjava.litemall.order.infrastructure.services.acl.facades.PaymentGatewayPort paymentGatewayPort;

    // Webhook idempotency ledger (Wave 7): Stripe retries deliveries, so event.id is
    // claimed via INSERT IGNORE against a UNIQUE key before anything is acted on.
    @Autowired
    private org.linlinjava.litemall.db.dao.StripeEventMapper stripeEventMapper;

    // Server-authoritative checkout totals (Wave 7): shares the submit path's freight
    // service, coupon facade and tax port so preview and charge cannot disagree.
    @Autowired
    private org.linlinjava.litemall.order.application.internal.CheckoutSummaryService checkoutSummaryService;

    @Autowired
    private NotifyService notifyService;

    // Unpaid-order timeout mechanism: DB-row + @Scheduled sweep
    // (see UnpaidOrderTaskScheduler). Survives restart; replaces the
    // in-memory DelayQueue/TaskService that used to live in litemall-core.
    @Autowired
    private UnpaidOrderTaskScheduler unpaidOrderTaskScheduler;

    @Autowired
    LitemallDomainEventPublisher domainEventPublisher;

    // CJ fulfillment (Wave 8): placement is DECOUPLED from the money transaction.
    // Paying settles unconditionally; a 'queued' timeline hop lands in the pay TX and
    // CjPlacementService places the order afterwards (afterCommit fast path + the
    // durable placement sweep — a paid CJ order survives any CJ outage/disabled window
    // and is placed when CJ returns). cjFulfillmentService remains for the best-effort
    // CJ-side delete on cancel/refund.
    @Autowired
    private org.linlinjava.litemall.order.application.internal.cj.CjFulfillmentService cjFulfillmentService;

    @Autowired
    private org.linlinjava.litemall.order.application.internal.cj.CjPlacementService cjPlacementService;

    // Brokerage clawback (Wave 5): an approved aftersale invalidates the order's
    // still-frozen commission inside this same transaction (guarded status=0 → -1;
    // an already-unfrozen row deliberately stays valid — see the lifecycle ADR).
    @Autowired
    private BrokerageService brokerageService;

    // Tags/validates the order's fulfillment source from the cart before placement
    // (mixed CJ+local carts are a clean client error, pre-checked outside placeOrder).
    @Autowired
    private org.linlinjava.litemall.order.application.internal.cj.OrderSourceResolver orderSourceResolver;

    // CJ submit gate (cj_vid + live stock): pre-checked here, outside the nested
    // transactional placeOrder, so an unfulfillable/over-stock line is a clean 422.
    @Autowired
    private org.linlinjava.litemall.order.application.internal.cj.CjOrderAvailabilityChecker cjOrderAvailabilityChecker;

    // Wallet vertical (absorbed from litemall-wallet-service). Used to debit the
    // user's wallet when PaymentMethod.WALLET is selected — mirrors how groupon
    // flows through grouponServiceLayer.
    @org.springframework.beans.factory.annotation.Autowired
    private org.linlinjava.litemall.order.application.LitemallIWalletService walletService;

    // Read-side: order line items, used to populate the customer order list/detail
    // (goodsList / orderGoods) without going back through goods-management.
    @Autowired
    private org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderGoodsRepository orderGoodsRepository;

    // Aftersale/RMA vertical: admin decisions (approve → tender-parity refund,
    // reject) live here because the orchestrator owns refund settlement.
    @Autowired
    private org.linlinjava.litemall.order.domain.model.repositories.LitemallAftersaleRepository aftersaleRepository;

    // Aftersale hops that don't move the ORDER status (approve/reject markers) still
    // land on the order's single timeline as same-status entries.
    @Autowired
    private org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderStatusHistoryRepository statusHistoryRepository;

    // Pickup stores (Wave 4, Task B): submit pre-checks + write-off store rendering.
    @Autowired
    private org.linlinjava.litemall.order.application.internal.LitemallStoreServiceLayer storeServiceLayer;

    // Submit pre-check only: a dangling addressId must 422 from OUTSIDE the
    // transactional placeOrder (whose own guard is the rollback-only→502 landmine).
    @Autowired
    private org.linlinjava.litemall.order.domain.model.repositories.LitemallAddressRepository addressRepository;

    // Same fallback CjFulfillmentService applies at placement time — a CJ submit
    // without a countryCode is only acceptable when this deployment default exists.
    @org.springframework.beans.factory.annotation.Value("${spring.cjdropship.api.ship-to-country-code:}")
    private String defaultShipToCountryCode;

    // Kill-switch for the pickup vertical (Wave 4). ON by default — flipping it off
    // 422s NEW pickup submits; already-placed pickup orders keep working (write-off
    // and reads are unaffected).
    @org.springframework.beans.factory.annotation.Value("${litemall.order.pickup-enabled:true}")
    private boolean pickupEnabled;


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
        // (which re-resolves the source when tagging the order). The CJ availability
        // gate (cj_vid + live stock, Wave 3) sits here for the same reason — its
        // LitemallOrderServiceException thrown inside placeOrder would mark the shared
        // transaction rollback-only and surface as a 502 instead of this clean 422.
        try {
            String orderSource = orderSourceResolver.resolve(checkedItems);
            // Pickup pre-checks (Wave 4, Task B) — ALL pickup 422s fire HERE, outside
            // the transactional placeOrder (the in-TX-422→502 landmine): kill-switch,
            // CJ-in-cart, store missing/hidden, blank pickup contact. placeOrder keeps
            // typed safety-net copies (LitemallPickupException, rethrown below).
            // Deliberately BEFORE the CJ availability gate: a pickup submit with CJ
            // lines is doomed regardless of CJ stock — reject it with the accurate
            // message and without spending a live CJ API call on it.
            if (command.isPickup()) {
                if (!pickupEnabled) {
                    return LitemallOrderOperationResult.submitFailed(
                            "In-store pickup is currently unavailable — choose delivery instead.");
                }
                if (LitemallOrderAggregate.SOURCE_CJ.equals(orderSource)) {
                    return LitemallOrderOperationResult.submitFailed(
                            "Dropshipped items cannot be picked up in store — choose delivery or remove them.");
                }
                if (storeServiceLayer.findPickupable(command.getStoreId()) == null) {
                    return LitemallOrderOperationResult.submitFailed(
                            "The selected pickup store is not available.");
                }
                if (command.getPickupName() == null || command.getPickupName().isBlank()
                        || command.getPickupMobile() == null || command.getPickupMobile().isBlank()) {
                    return LitemallOrderOperationResult.submitFailed(
                            "Pickup contact name and mobile are required.");
                }
            }
            // Delivery submits need a resolvable shipping address. placeOrder's own
            // guard throws from inside the transaction (rollback-only → generic 502),
            // so a dangling/foreign addressId is rejected HERE as a clean 422 instead.
            if (!command.isPickup()) {
                org.linlinjava.litemall.order.domain.model.agregates.LitemallAddressAggregate shippingAddress =
                        command.getAddressId() == null
                                ? addressRepository.findDefaultAddress(new LitemallUserId(command.getUserId()))
                                : addressRepository.findAddress(new LitemallUserId(command.getUserId()),
                                        new org.linlinjava.litemall.order.domain.model.valueobjects.LitemallAddressId(
                                                command.getAddressId()));
                if (shippingAddress == null) {
                    return LitemallOrderOperationResult.submitFailed(
                            "The selected shipping address was not found — choose a valid address.");
                }
            }
            if (LitemallOrderAggregate.SOURCE_CJ.equals(orderSource)) {
                cjOrderAvailabilityChecker.assertAllFulfillable(checkedItems);
                // CJ placement is hard-blocked without a destination country
                // (CjFulfillmentService rejects TERMINALLY at placement time — after
                // the customer has already paid). Refuse at submit instead, unless the
                // deployment-market fallback country is configured.
                if (!org.springframework.util.StringUtils.hasText(command.getCountryCode())
                        && !org.springframework.util.StringUtils.hasText(defaultShipToCountryCode)) {
                    return LitemallOrderOperationResult.submitFailed(
                            "A destination country is required for these items — please re-select your shipping address.");
                }
            }
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
                 | org.linlinjava.litemall.order.application.util.exception.coupon.LitemallPromotionServiceUnavailableException
                 | org.linlinjava.litemall.order.application.util.exception.order.LitemallPickupException e) {
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

        // Process payment. WALLET is settled by the debit above; CARD is VERIFIED against
        // Stripe server-side (see processPayment) — the client's id is a claim, not proof.
        PaymentVerification verification = processPayment(order, paymentCommand);

        if (verification.isVerified()) {
            // Transition the order to PAID and persist it within this transaction,
            // so a wallet debit (above) and the paid status are atomic — either both
            // commit or both roll back. Emits LitemallOrderPaidEvent. pay_id records
            // the tender ("WALLET" or "<METHOD>:<reference>") so a later refund can
            // be routed back to the channel that paid (see settleRefundToTender);
            // payment_intent_id records the VERIFIED Stripe reference on its own column,
            // where a UNIQUE index makes replaying it onto a second order impossible.
            orderServiceImpl.markOrderPaid(orderId, tenderPayId(paymentCommand),
                    verifiedPaymentIntentId(paymentCommand));

            // CJ fulfillment (Wave 8): the money is settled by THIS transaction no matter
            // what CJ does. Placement happens after commit (fast path) with the placement
            // sweep as the durable retry — see queueCjPlacement.
            if (order.isCjFulfilled()) {
                queueCjPlacement(orderId);
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
            log.warn("Payment REJECTED for order {} via {}: {}",
                    orderId.getId(), paymentCommand.getPaymentMethod(), verification.getReason());
            return LitemallOrderOperationResult.payFailed(
                    orderId, "Payment was not accepted: " + verification.getReason());
        }
    }

    /**
     * Handle a Stripe webhook delivery (Wave 7, Task A3). The authoritative paid signal.
     *
     * <p>Order of operations matters:
     * <ol>
     *   <li><b>Verify the signature first</b> — the endpoint is anonymous, so nothing is
     *       trusted until this passes. Throws {@link LitemallPaymentGatewayException} → 400.</li>
     *   <li><b>Claim the event id</b> — an INSERT IGNORE against the UNIQUE key. 0 rows
     *       means another delivery already handled it, so we stop. This is what makes a
     *       Stripe retry (or two concurrent deliveries) safe.</li>
     *   <li><b>Re-verify against the order</b> — metadata is only as good as what we wrote,
     *       so the amount/currency/status are asserted through the same path the client
     *       call uses. The webhook is authoritative about WHETHER Stripe says it is paid,
     *       not about how much the order costs.</li>
     * </ol>
     *
     * <p>Not annotated {@code @Transactional} beyond the class default; the claim commits
     * with the paid flip, so a crash mid-processing leaves the event claimed but the order
     * unpaid — recoverable via Stripe's dashboard resend, whereas double-paying is not.
     */
    public void handleStripeWebhook(String payload, String signatureHeader) {
        var event = paymentGatewayPort.parseWebhook(payload, signatureHeader);

        if (stripeEventMapper.claim(event.getEventId(), event.getType(), LocalDateTime.now()) == 0) {
            log.info("Stripe event {} already processed — ignoring duplicate delivery", event.getEventId());
            return;
        }

        if (!LitemallStripeEvent.TYPE_PAYMENT_SUCCEEDED.equals(event.getType())) {
            // payment_intent.payment_failed and everything else: recorded (so it is not
            // reprocessed) but not acted on — the order simply stays CREATED and the
            // unpaid sweep eventually cancels it. There is no failed state to move to.
            log.info("Stripe event {} of type {} recorded, no action taken",
                    event.getEventId(), event.getType());
            return;
        }

        Integer orderIdValue = event.getOrderId();
        if (orderIdValue == null) {
            log.warn("Stripe event {} (intent {}) carries no usable orderId metadata — ignoring",
                    event.getEventId(), event.getPaymentIntentId());
            return;
        }
        stripeEventMapper.attachOrder(event.getEventId(), orderIdValue, LocalDateTime.now());

        LitemallOrderId orderId = new LitemallOrderId(orderIdValue);
        LitemallOrderAggregate order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("Stripe event {} references unknown order {}", event.getEventId(), orderIdValue);
            return;
        }

        if (!LitemallOrderStatusQuery.isActionAllowed(order, OrderAction.PAY)) {
            // Overwhelmingly the normal case: the client-confirm path already paid it and
            // the webhook is just confirming. Not an error.
            log.info("Stripe event {}: order {} is already {} — nothing to do",
                    event.getEventId(), orderIdValue, order.getOrderStatus());
            return;
        }

        PaymentVerification verification = paymentGatewayPort.verify(
                event.getPaymentIntentId(), orderIdValue, order.getActualPrice());
        if (!verification.isVerified()) {
            log.error("Stripe event {} claims order {} was paid, but verification REJECTED it: {}",
                    event.getEventId(), orderIdValue, verification.getReason());
            return;
        }

        log.info("Stripe event {}: marking order {} paid from the webhook (intent {})",
                event.getEventId(), orderIdValue, event.getPaymentIntentId());
        orderServiceImpl.markOrderPaid(orderId,
                PaymentMethod.CREDIT_CARD.name() + ":" + event.getPaymentIntentId(),
                event.getPaymentIntentId());

        // Same decoupled CJ placement as the client pay path (Wave 8). This also gives the
        // webhook path the first-lifecycle-pass kick it never had — placement and advance
        // both live in CjPlacementService now.
        if (order.isCjFulfilled()) {
            queueCjPlacement(orderId);
        }
        domainEventPublisher.publish(new LitemallOrderPaymentSuccessEvent(
                orderId, order.getActualPrice(), LocalDateTime.now()));
    }

    /**
     * Queue CJ placement for an order this transaction is marking PAID (Wave 8).
     *
     * <p>Inside the TX: one honest same-status timeline hop ("queued") so the customer sees
     * fulfilment state truthfully even if CJ is disabled/down for days. After commit: the
     * fast-path placement attempt (async — checkout latency never grows by CJ round-trips).
     * The durable retry is the {@code CjPlacementSweepScheduler} sweep over
     * paid-but-unplaced order rows; a lost afterCommit (crash, restart) loses nothing.
     */
    private void queueCjPlacement(LitemallOrderId orderId) {
        statusHistoryRepository.record(new org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderStatusChange(
                orderId, LitemallOrderStatus.PAID, LitemallOrderStatus.PAID,
                org.linlinjava.litemall.order.application.internal.cj.CjPlacementService.CHANGE_TYPE_CJ_PLACEMENT,
                "Queued for CJ fulfilment placement", "system", LocalDateTime.now()));
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager
                    .registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            cjPlacementService.placeAsync(orderId);
                        }
                    });
        } else {
            // No active transaction synchronization (plain unit tests / non-TX callers):
            // fire directly — placeAsync re-checks eligibility against the committed row.
            cjPlacementService.placeAsync(orderId);
        }
    }

    /**
     * Mint a Stripe PaymentIntent for an order the caller already owns (Wave 7, Task A).
     *
     * <p>The amount is read from the ORDER, not from the request, which is what makes the
     * later {@code amount_received} assertion meaningful: both sides of the comparison
     * originate server-side. Only a payable order qualifies — minting an intent for an
     * already-paid or cancelled order would let a customer be charged twice.
     *
     * @throws org.linlinjava.litemall.order.application.util.exception.payment.LitemallPaymentGatewayException
     *         Stripe disabled/refused/unreachable. Never returns a stub.
     */
    public org.linlinjava.litemall.order.infrastructure.services.acl.facades.payment.PaymentIntentDraft
            createPaymentIntent(LitemallOrderAggregate order) {
        if (!LitemallOrderStatusQuery.isActionAllowed(order, OrderAction.PAY)) {
            throw new org.linlinjava.litemall.order.application.util.exception.payment.LitemallPaymentGatewayException(
                    "Order " + order.getOrderId().getId() + " is " + order.getOrderStatus()
                    + " and cannot be paid.");
        }
        return paymentGatewayPort.createIntent(order.getOrderId().getId(), order.getActualPrice());
    }

    /**
     * The verified PaymentIntent id to persist, or null for wallet/offline tenders (which
     * have no PSP reference and must stay NULL so they don't collide under the UNIQUE index).
     * Only ever called after {@link #processPayment} accepted.
     */
    private String verifiedPaymentIntentId(LitemallOrderPaymentCommand command) {
        if (command.getPaymentMethod() == null || command.getPaymentMethod() == PaymentMethod.WALLET) {
            return null;
        }
        String reference = command.getPaymentReference();
        return reference == null || reference.isBlank() ? null : reference;
    }

    /**
     * Admin marks an order paid OFFLINE (Wave 4, Task C): bank transfer, cash on the
     * counter, out-of-band PSP. The tender lands as {@code pay_id = "OFFLINE:<ref>"}
     * — inside the existing {@code <METHOD>:<reference>} convention, so refund
     * tender-parity resolves it automatically (non-wallet ⇒ PSP-seam log, no wallet
     * credit is invented).
     *
     * <p><b>CJ orders: placement is queued exactly as for a normal pay</b> (Wave 8 —
     * live-fire once it runs; the admin SPA's confirm dialog must say so; the
     * {@code spring.cjdropship.api.sandbox} flag governs whether CJ simulates the
     * money). The PAID flip is final regardless of CJ: a rejection parks the order
     * for ops, an outage retries via the placement sweep. See
     * docs/adr-offline-mark-paid.md.
     *
     * <p>Only a CREATED order qualifies — the controller pre-checks OUTSIDE this
     * transaction (422); the markOrderPaid 0-row guard still catches races
     * (IllegalStateException → controller 422, rollback, no double pay). The pickup
     * verify code is generated by markOrderPaid as for any pay. Timeline: the normal
     * {@code pay} transition hop plus a same-status {@code admin_offline_pay} marker
     * carrying the reference and the acting admin.
     */
    public LitemallOrderOperationResult adminOfflinePay(LitemallOrderId orderId, String reference, String adminId) {
        LitemallOrderAggregate order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        String ref = reference == null || reference.isBlank()
                ? "admin:" + System.currentTimeMillis()
                : reference.trim();
        String payId = "OFFLINE:" + ref;

        // CREATED→PAID with the 0-row race guard (throws IllegalStateException when
        // the order left CREATED between the controller pre-check and here).
        // No payment_intent_id: an offline tender has no Stripe reference, and NULL keeps
        // these rows out of the UNIQUE index entirely.
        orderServiceImpl.markOrderPaid(orderId, payId, null);

        // Same-status audit marker on the single timeline (aftersale-marker pattern).
        statusHistoryRepository.record(new org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderStatusChange(
                orderId, LitemallOrderStatus.PAID, LitemallOrderStatus.PAID,
                "admin_offline_pay", "Marked paid offline (reference: " + ref + ")",
                "admin:" + (adminId == null || adminId.isBlank() ? "unknown" : adminId),
                LocalDateTime.now()));

        // CJ placement — identical to the customer pay path (Wave 8): queued after this
        // transaction commits, retried durably by the placement sweep.
        if (order.isCjFulfilled()) {
            queueCjPlacement(orderId);
        }

        // Post-payment parity with the customer path: groupon settlement, success
        // notification, unpaid-timeout cancellation, payment-success event.
        handleGrouponAfterPayment(orderId);
        sendPaymentSuccessNotifications(orderRepository.findById(orderId).orElse(order));
        unpaidOrderTaskScheduler.cancel(orderId);
        domainEventPublisher.publish(new LitemallOrderPaymentSuccessEvent(
                orderId, order.getActualPrice(), LocalDateTime.now()));

        return LitemallOrderOperationResult.paySuccess(
                orderId, LitemallOrderStatus.CREATED,
                LitemallOrderHandleOption.forStatus(LitemallOrderStatus.PAID));
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
     * Settle the non-wallet portion of a payment (Wave 7, Task A —
     * docs/adr-stripe-payments.md).
     *
     * <ul>
     *   <li><b>WALLET</b> — already debited server-side in {@link #debitWalletForOrder}
     *       within this transaction; nothing more to settle.</li>
     *   <li><b>CARD / digital</b> — the SPA confirms the PaymentIntent with Elements and
     *       passes its id; the server then asks STRIPE what happened. The client's word is
     *       not evidence.</li>
     * </ul>
     *
     * <p>Until Wave 7 this method accepted any non-blank string, so
     * {@code {"paymentIntentId":"x"}} produced a paid order. The verification now lives
     * behind {@link PaymentGatewayPort} and fails CLOSED: a rejection — including "Stripe
     * is disabled" and "Stripe is unreachable" — means the order stays CREATED and no CJ
     * placement fires. The only safe answer to "did this get paid?" when we cannot tell
     * is no.
     *
     * <p>Replay across orders is NOT checked here: the UNIQUE index on
     * {@code litemall_order.payment_intent_id} decides it at write time, which is race-free
     * where a read-then-write check would not be.
     */
    private PaymentVerification processPayment(LitemallOrderAggregate order, LitemallOrderPaymentCommand command) {
        PaymentMethod method = command.getPaymentMethod();
        if (method == null) {
            // Jackson leaves this null for an absent/unknown name; it used to fall through
            // to the card branch, where any non-blank reference passed.
            return PaymentVerification.rejected("no payment method supplied");
        }
        if (method == PaymentMethod.WALLET) {
            return PaymentVerification.accepted();
        }
        return paymentGatewayPort.verify(
                command.getPaymentReference(), order.getOrderId().getId(), order.getActualPrice());
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

    /**
     * Server-authoritative checkout totals (Wave 7, Task D) — backs
     * {@code GET /srv/cart/checkout}.
     *
     * <p>The SPA has been computing its own grand total by reducing over cart-carried
     * prices ({@code Checkout.tsx:248}), while only freight came from the server. That is
     * two implementations of the same arithmetic, and they can disagree — a customer can be
     * shown one number and charged another. This method exists so there is exactly one:
     * it calls the same freight service, the same coupon facade and the same tax port that
     * {@code placeOrder} calls. With tax in the picture it stops being a nicety, because a
     * browser cannot compute a tax-inclusive total at all.
     *
     * <p>Read-only preview: it prices what the cart currently holds and reserves nothing.
     * Submit re-derives everything, so a price that moves in between surfaces there as a
     * clean 422 rather than being silently absorbed here.
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public org.linlinjava.litemall.order.interfaces.dtos.cart.CheckoutSummaryDto checkoutSummary(
            org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId userId,
            Integer addressId, Integer userCouponId, String countryCode) {
        return checkoutSummaryService.summarize(userId, addressId, userCouponId, countryCode);
    }

    /**
     * The ONE way a cart line is created ({@code POST /srv/cart/add} and
     * {@code POST /srv/cart/items}): callers send only goodsId/productId/number, so look
     * up the goods + chosen variant through the goods ACL and build a fully-populated,
     * checked cart line before persisting. A missing goods/variant is a clean client
     * error (mapped to a 4xx by the controller), not a later NPE.
     *
     * <p>Deliberately there is no overload taking a caller-built aggregate: one used to
     * exist for the RESTful surface and let the client assert its own price, name and
     * image (Wave 7, Task E0). Price resolves to whatever goods-management currently
     * holds, which is also how a live flash deal reaches the cart — the deal price IS
     * the catalog price while the deal is active (the lifecycle task swaps the SKU rows).
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
        // Off-sale goods stay readable on their detail URL but must not enter a
        // cart — the legacy non-CJ catalog was deactivated (is_on_sale=0) because
        // no inventory backs it, and nothing upstream gates this path.
        if (!goods.isOnSale()) {
            throw new LitemallOrderServiceException(
                    "Cannot add to cart: \"" + goods.getGoodsName() + "\" is not available for sale");
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
        // Pickup orders have no shipping leg — they are written off at the counter
        // (Wave 4). Refuse ship cleanly (422 via the SHIP error mapping).
        if (order.isPickup()) {
            return LitemallOrderOperationResult.shipFailed(orderId,
                    "This is an in-store pickup order — redeem its verify code via write-off instead of shipping.");
        }
        if (!previous.canTransitionTo(LitemallOrderStatus.SHIPPED)) {
            return LitemallOrderOperationResult.invalidStateTransition(
                    orderId, LitemallOrderOperationResult.OperationType.SHIP, previous);
        }
        orderServiceImpl.shipOrder(orderId, shipChannel, shipSn);
        return LitemallOrderOperationResult.shipSuccess(
                orderId, previous, LitemallOrderHandleOption.forStatus(LitemallOrderStatus.SHIPPED));
    }

    // ---- pickup write-off (核销, Wave 4 Task B) ---------------------------------------

    /**
     * Preview a scanned verify code WITHOUT redeeming it: returns the paid pickup order
     * the code belongs to, or throws {@link LitemallWriteoffException} (three distinct
     * kinds → three distinct 422s at the controller). Read-only.
     */
    public LitemallOrderAggregate writeoffPreview(String verifyCode) {
        return loadWriteoffableOrder(verifyCode);
    }

    /**
     * Redeem a verify code: PAID → DELIVERED with the {@code writeoff} timeline hop,
     * {@code verified_by} audit stamp and the delivered domain event — all in THIS
     * orchestrator transaction (the approveAftersale pattern; a bare service call
     * would run without one). A concurrent scan loses on the conditional UPDATE and
     * surfaces as ALREADY_VERIFIED.
     *
     * @param verifiedBy audit identity, e.g. {@code "admin:<X-User-Id>"}
     */
    public LitemallOrderAggregate writeoffCommit(String verifyCode, String verifiedBy) {
        LitemallOrderAggregate order = loadWriteoffableOrder(verifyCode);
        orderServiceImpl.writeoffOrder(order.getOrderId(), verifiedBy);
        return orderRepository.findById(order.getOrderId()).orElse(order);
    }

    /** Shared write-off gate: unknown code / already verified / wrong state → typed throws. */
    private LitemallOrderAggregate loadWriteoffableOrder(String verifyCode) {
        LitemallOrderAggregate order = orderRepository.findByVerifyCode(
                verifyCode == null ? null : verifyCode.trim()).orElse(null);
        if (order == null) {
            throw new LitemallWriteoffException(LitemallWriteoffException.Kind.UNKNOWN_CODE,
                    "No order carries this pickup code — check the scan and try again.");
        }
        if (order.getVerifyTime() != null) {
            throw new LitemallWriteoffException(LitemallWriteoffException.Kind.ALREADY_VERIFIED,
                    "This pickup code was already redeemed on " + order.getVerifyTime()
                    + " by " + order.getVerifiedBy() + ".");
        }
        if (!order.isPickup() || order.getOrderStatus() != LitemallOrderStatus.PAID) {
            throw new LitemallWriteoffException(LitemallWriteoffException.Kind.WRONG_STATE,
                    "Order " + order.getOrderSn() + " is not a redeemable paid pickup order (status: "
                    + order.getOrderStatus().getDisplayName() + ").");
        }
        return order;
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
        // Explicit SHIPPED gate: PAID→DELIVERED exists in the graph for the pickup
        // write-off (Wave 4) but must never be reachable through a customer receipt
        // confirmation — a paid, unshipped order is not "received".
        if (previous != LitemallOrderStatus.SHIPPED
                || !previous.canTransitionTo(LitemallOrderStatus.DELIVERED)) {
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
        LitemallMoney refund = settleRefundToTender(order, orderId, null);
        orderServiceImpl.refundOrder(orderId, refund);
        // CJ cleanup: a CJ order still CREATED/IN_CART/UNPAID at CJ (payBalance not yet
        // through) is deleted there so it can never ship after the money went back.
        cjFulfillmentService.cancelAtCjIfDeletable(order, "refund approved");
        return LitemallOrderOperationResult.refundSuccess(
                orderId, previous, LitemallOrderHandleOption.forStatus(LitemallOrderStatus.REFUNDED));
    }

    /**
     * Admin approves an aftersale application: accept it, drive the order through the
     * existing refund transitions and settle the money to the paying tender — capped
     * by the CUSTOMER-REQUESTED amount as well as the capture — all in ONE
     * transaction. The aftersale row, the order's {@code after_sale_status}, the
     * order status hops and the wallet credit land (or roll back) together.
     */
    public LitemallOrderOperationResult approveAftersale(Integer aftersaleId) {
        org.linlinjava.litemall.order.domain.model.agregates.LitemallAftersaleAggregate aftersale =
                aftersaleRepository.findById(aftersaleId).orElse(null);
        if (aftersale == null) {
            return LitemallOrderOperationResult.orderNotFound(new LitemallOrderId(0));
        }
        LitemallOrderId orderId = aftersale.getOrderId();
        LitemallOrderAggregate order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return LitemallOrderOperationResult.orderNotFound(orderId);
        }
        LitemallOrderStatus previous = order.getOrderStatus();
        aftersale.approve(); // guarded: only an APPLIED application can be accepted

        // Move the order into the refund flow (PAID/SHIPPED/DELIVERED/AUTO_DELIVERED →
        // REFUND_REQUEST) unless a plain refund request already did.
        if (previous != LitemallOrderStatus.REFUND_REQUEST) {
            if (!previous.canTransitionTo(LitemallOrderStatus.REFUND_REQUEST)) {
                return LitemallOrderOperationResult.invalidStateTransition(
                        orderId, LitemallOrderOperationResult.OperationType.REFUND, previous);
            }
            orderServiceImpl.requestRefund(orderId,
                    "Aftersale " + aftersale.getAftersaleSn() + " approved: " + aftersale.getReason());
            order = orderRepository.findById(orderId).orElseThrow();
        }

        LitemallMoney refund = settleRefundToTender(order, orderId, aftersale.getAmount());
        orderServiceImpl.refundOrder(orderId, refund);
        // Same CJ cleanup as approveRefund: never leave a paid-back order fulfillable at CJ.
        cjFulfillmentService.cancelAtCjIfDeletable(order, "aftersale approved");
        // Wave 5: claw back the referrer's still-frozen commission in this same
        // transaction (0 rows when none was awarded or it already matured).
        brokerageService.invalidateFrozenForOrder(order.getOrderSn());

        aftersale.markRefunded();
        aftersaleRepository.update(aftersale);
        orderRepository.updateAfterSaleStatus(orderId,
                org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallAfterSaleStatus.STATUS_REFUND.getCode());
        statusHistoryRepository.record(new LitemallOrderStatusChange(
                orderId, LitemallOrderStatus.REFUNDED, LitemallOrderStatus.REFUNDED,
                "aftersale_approve",
                "Aftersale " + aftersale.getAftersaleSn() + " approved; refunded " + refund.getAmount(),
                "admin", LocalDateTime.now()));
        return LitemallOrderOperationResult.refundSuccess(
                orderId, previous, LitemallOrderHandleOption.forStatus(LitemallOrderStatus.REFUNDED));
    }

    /**
     * Admin declines an aftersale application. The order keeps its status and its
     * money — only the aftersale row and the order's {@code after_sale_status} flag
     * move, plus a timeline marker.
     */
    public LitemallOrderOperationResult rejectAftersale(Integer aftersaleId, String reason) {
        org.linlinjava.litemall.order.domain.model.agregates.LitemallAftersaleAggregate aftersale =
                aftersaleRepository.findById(aftersaleId).orElse(null);
        if (aftersale == null) {
            return LitemallOrderOperationResult.orderNotFound(new LitemallOrderId(0));
        }
        LitemallOrderId orderId = aftersale.getOrderId();
        LitemallOrderAggregate order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return LitemallOrderOperationResult.orderNotFound(orderId);
        }
        aftersale.reject(); // guarded: only an APPLIED application can be declined
        if (reason != null && !reason.isBlank()) {
            aftersale.setComment(reason);
        }
        aftersaleRepository.update(aftersale);
        orderRepository.updateAfterSaleStatus(orderId,
                org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallAfterSaleStatus.STATUS_REJECT.getCode());
        statusHistoryRepository.record(new LitemallOrderStatusChange(
                orderId, order.getOrderStatus(), order.getOrderStatus(),
                "aftersale_reject",
                "Aftersale " + aftersale.getAftersaleSn() + " rejected"
                        + (reason == null || reason.isBlank() ? "" : ": " + reason),
                "admin", LocalDateTime.now()));
        return LitemallOrderOperationResult.refundRequestSuccess(
                orderId, order.getOrderStatus(),
                LitemallOrderHandleOption.forStatus(order.getOrderStatus()));
    }

    /**
     * Route the refund money to the tender that paid and return the amount actually
     * refunded (what {@code refund_amount} must record). Never exceeds the captured
     * amount: WALLET refunds the recorded debit; an external (CARD/digital) charge is
     * reversed at the PSP seam without touching the wallet.
     *
     * @param requestedCap optional further cap (aftersale's customer-requested
     *                     amount); null means "refund the full order amount"
     */
    private LitemallMoney settleRefundToTender(LitemallOrderAggregate order, LitemallOrderId orderId,
                                               LitemallMoney requestedCap) {
        LitemallMoney actual = order.getActualPrice();
        if (actual == null || actual.getAmount().signum() <= 0) {
            log.info("Refund for order {}: non-positive order amount, nothing to return", orderId.getId());
            return new LitemallMoney(java.math.BigDecimal.ZERO);
        }
        if (requestedCap != null && requestedCap.getAmount() != null
                && requestedCap.getAmount().compareTo(actual.getAmount()) < 0) {
            actual = requestedCap;
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

        // External charge (CARD / digital wallet): the money sits at the PSP, so the wallet
        // must NOT be credited — the reversal has to happen at Stripe (Wave 7, Task B).
        //
        // An order with no recorded PaymentIntent cannot be reversed automatically. That is
        // the OFFLINE tender ("OFFLINE:<ref>", written by adminOfflinePay): paidTender
        // cannot parse it back to an enum constant, so it arrives here as a card-ish tender
        // with a null intent. Refuse rather than invent a refund — an admin who took the
        // money out of band returns it out of band, and the order stays retryable/visible
        // instead of silently reading REFUNDED with money that never moved.
        String intentId = order.getPaymentIntentId();
        if (intentId == null || intentId.isBlank()) {
            throw new LitemallRefundFailedException(
                    "Order " + orderId.getId() + " was paid via " + tender + " with no reversible "
                    + "payment reference (pay_id=" + order.getPayId() + "). Refund it in the "
                    + "payment provider or the original channel, then record it there — the "
                    + "order has been left in REFUND_REQUEST.");
        }

        RefundOutcome outcome = paymentGatewayPort.refund(intentId, actual, orderId.getId());
        if (!outcome.isOk()) {
            // Rolls this transaction back: no REFUNDED flip, no refund_amount, aftersale
            // stays open. Deliberately fails loudly to the admin rather than fail-soft.
            throw new LitemallRefundFailedException(
                    "Refund of " + actual.getAmount() + " for order " + orderId.getId()
                    + " was REJECTED by the payment provider: " + outcome.getFailureReason()
                    + ". The order has been left in REFUND_REQUEST — retry once resolved.");
        }

        log.info("Refund for order {}: reversed {} on PaymentIntent {} (refund {}); no wallet credit",
                orderId.getId(), actual.getAmount(), intentId, outcome.getRefundId());
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
