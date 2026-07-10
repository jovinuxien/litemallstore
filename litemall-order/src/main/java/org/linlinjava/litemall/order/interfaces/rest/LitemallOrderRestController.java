package org.linlinjava.litemall.order.interfaces.rest;

import org.linlinjava.litemall.core.system.SystemConfig;
import org.linlinjava.litemall.order.application.LitemallOrderOrchestratorService;
import org.linlinjava.litemall.order.application.internal.cj.CjFreightQuoteService;
import org.linlinjava.litemall.order.application.util.exception.product.LitemallGoodsServiceUnavailableException;
import org.linlinjava.litemall.order.application.util.exception.product.LitemallInsufficientStockException;
import org.linlinjava.litemall.order.application.util.exception.wallet.LitemallInsufficientBalanceException;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.commands.LitemallOrderCancelCommand;
import org.linlinjava.litemall.order.domain.model.commands.LitemallPlaceOrderCommand;
import org.linlinjava.litemall.order.domain.model.commands.payment.LitemallOrderPaymentCommand;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.order.domain.model.valueobjects.ApiResponse;
import org.linlinjava.litemall.order.domain.service.order.LitemallOrderOperationResult;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.CjLogisticsOption;
import org.linlinjava.litemall.order.interfaces.dtos.order.FreightQuoteDtoResponse;
import org.linlinjava.litemall.order.interfaces.dtos.order.FreightQuoteRequest;
import org.linlinjava.litemall.order.interfaces.dtos.order.OrderDetailDtoResponse;
import org.linlinjava.litemall.order.interfaces.dtos.order.OrderListDtoResponse;
import org.linlinjava.litemall.order.interfaces.dtos.order.OrderListItemDtoResponse;
import org.linlinjava.litemall.order.interfaces.dtos.order.OrderOperationDtoResponse;
import org.linlinjava.litemall.order.interfaces.dtos.order.OrderStatusTimelineDtoResponse;
import org.linlinjava.litemall.order.interfaces.dtos.order.PaymentActionRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

import static org.linlinjava.litemall.order.interfaces.util.LitemallHttpResponseUtil.buildResponse;

@RestController
@RequestMapping("/srv/order")
public class LitemallOrderRestController {

    private final LitemallOrderOrchestratorService orderOrchestrationService;
    private final CjFreightQuoteService cjFreightQuoteService;
    private final org.linlinjava.litemall.order.application.internal.cj.CjTrackingService cjTrackingService;

    public LitemallOrderRestController(LitemallOrderOrchestratorService orderOrchestrationService,
                                       CjFreightQuoteService cjFreightQuoteService,
                                       org.linlinjava.litemall.order.application.internal.cj.CjTrackingService cjTrackingService) {
        this.orderOrchestrationService = orderOrchestrationService;
        this.cjFreightQuoteService = cjFreightQuoteService;
        this.cjTrackingService = cjTrackingService;
    }

    /**
     * Checkout freight/logistics quote. {@code freightPrice} mirrors the exact rule submit
     * charges (free at/above {@code litemall_express_freight_min}, else the flat
     * {@code litemall_express_freight_value}); the CJ block is an informational carrier +
     * delivery-time estimate for CJ cart groups. CJ problems degrade to {@code cj:null} +
     * {@code cjNote} — this endpoint never fails a checkout.
     */
    @PostMapping("/freight-quote")
    public ApiResponse<FreightQuoteDtoResponse> freightQuote(@RequestBody FreightQuoteRequest request) {
        java.math.BigDecimal subtotal = request.getSubtotal() != null ? request.getSubtotal() : java.math.BigDecimal.ZERO;
        java.math.BigDecimal freightPrice = subtotal.compareTo(SystemConfig.getFreightLimit()) < 0
                ? SystemConfig.getFreight() : java.math.BigDecimal.ZERO;

        FreightQuoteDtoResponse.CjInfo cjInfo = null;
        String cjNote = null;
        boolean cjRequested = request.getCountryCode() != null && !request.getCountryCode().isBlank()
                && request.getCjItems() != null && !request.getCjItems().isEmpty();
        if (cjRequested) {
            CjLogisticsOption option = cjFreightQuoteService.quote(request.getCountryCode(),
                    request.getCjItems().stream()
                            .map(i -> new CjFreightQuoteService.QuoteItem(i.getProductId(), i.getQuantity()))
                            .collect(Collectors.toList()));
            if (option != null) {
                cjInfo = new FreightQuoteDtoResponse.CjInfo(option.getLogisticName(), option.getLogisticAging());
            } else {
                cjNote = "Logistics estimate unavailable right now";
            }
        }
        return ApiResponse.ok(new FreightQuoteDtoResponse(
                freightPrice, SystemConfig.getFreightLimit(), cjInfo, cjNote));
    }

    /**
     * Customer "My Orders" list. Returns the agreed {@code { list, total }} shape
     * (SPA {@code orderApi.list}) inside the order {@code ApiResponse} envelope.
     * Scoped to the gateway-injected {@code X-User-Id} — a caller only ever sees
     * their own orders. {@code showType} filters by lifecycle bucket:
     * 0/absent=all, 1=unpaid(101), 2=to-ship(201), 3=shipped(301), 4=completed(401/402).
     */
    @GetMapping("/list")
    public ApiResponse<OrderListDtoResponse> list(@RequestHeader("X-User-Id") Integer userId,
                                                  @RequestParam(defaultValue = "0") int showType,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "10") int limit) {
        LitemallUserId uid = new LitemallUserId(userId);
        List<Short> statuses = orderStatusesForShowType(showType);
        List<LitemallOrderAggregate> orders =
                orderOrchestrationService.listOrders(uid, statuses, page, limit, "add_time", "desc");
        long total = orderOrchestrationService.countOrders(uid, statuses);
        List<OrderListItemDtoResponse> items = orders.stream()
                .map(o -> OrderListItemDtoResponse.fromDomain(
                        o, orderOrchestrationService.getOrderGoods(o.getOrderId())))
                .collect(Collectors.toList());
        return ApiResponse.ok(new OrderListDtoResponse(items, total));
    }

    /**
     * Customer order detail. Scoped to {@code X-User-Id}; a request for an order
     * the caller does not own returns a 404 envelope (no cross-customer read).
     */
    @GetMapping("/detail")
    public ApiResponse<OrderDetailDtoResponse> detail(@RequestHeader("X-User-Id") Integer userId,
                                                      @RequestParam Integer orderId) {
        LitemallOrderAggregate order = orderOrchestrationService.getOrderForUser(
                new LitemallUserId(userId), new LitemallOrderId(orderId));
        if (order == null) {
            return ApiResponse.fail(404, "Order not found");
        }
        return ApiResponse.ok(OrderDetailDtoResponse.fromDomain(
                order, orderOrchestrationService.getOrderGoods(order.getOrderId())));
    }

    /** Map the SPA {@code showType} bucket to the order_status codes it covers (null = all). */
    private static List<Short> orderStatusesForShowType(int showType) {
        switch (showType) {
            case 1:
                return List.of((short) 101);
            case 2:
                return List.of((short) 201);
            case 3:
                return List.of((short) 301);
            case 4:
                return List.of((short) 401, (short) 402);
            default:
                return null;
        }
    }

    @PostMapping("/submit")
    public ResponseEntity<OrderOperationDtoResponse> createOrder(
            @RequestHeader("X-User-Id") Integer userId,
            @RequestBody LitemallPlaceOrderCommand command) {
        // Bind the buyer from the authenticated gateway header (as list/cancel do),
        // never from the request body — a body-supplied userId would let a caller
        // place an order on behalf of another user (IDOR). Rebuild the command with
        // the header identity as authoritative, keeping the rest of the body.
        LitemallPlaceOrderCommand authoritativeCommand = new LitemallPlaceOrderCommand(
                userId,
                command.getCartId(),
                command.getAddressId(),
                command.getCouponId(),
                command.getUserCouponId(),
                command.getMessage(),
                command.getGrouponRulesId(),
                command.getGrouponLinkId(),
                command.getCountryCode());
        try {
            LitemallOrderOperationResult result = orderOrchestrationService.createOrder(authoritativeCommand);
            return buildResponse(result);
        } catch (LitemallInsufficientStockException e) {
            // Reserve-time stock shortfall: the placement transaction rolled back (no
            // order row, cart untouched). Surface the per-product detail as a clean
            // 422 submit-failed envelope instead of a raw 500. Caught HERE (outside
            // the orchestrator's transaction) to dodge the rollback-only trap.
            return buildResponse(LitemallOrderOperationResult.submitFailed(e.getMessage()));
        } catch (org.linlinjava.litemall.order.application.util.exception.coupon.LitemallInvalidCouponException e) {
            // Coupon rejected (not owned / expired / below threshold / out of scope /
            // redeem refused): rolled back, no order row, coupon untouched. The 422
            // message names the coupon — the SPA's inline "remove coupon and retry"
            // affordance keys on exactly that.
            return buildResponse(LitemallOrderOperationResult.submitFailed(e.getMessage()));
        } catch (org.linlinjava.litemall.order.application.util.exception.coupon.LitemallPromotionServiceUnavailableException e) {
            // Promotion down while the checkout carried a coupon: fail cleanly rather
            // than silently dropping the selected discount. 503 = transient/retryable.
            return ResponseEntity.status(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE)
                    .body(OrderOperationDtoResponse.fromResult(LitemallOrderOperationResult.submitFailed(
                            "Promotion service is unavailable — the order was not placed. "
                            + "Please retry, or remove the coupon to order without it.")));
        } catch (LitemallGoodsServiceUnavailableException e) {
            // goods-management down / circuit open / reservation unconfirmed: the
            // order was NOT created against unvalidated stock. 503 tells the SPA the
            // condition is transient and retryable, unlike the 422 client errors.
            return ResponseEntity.status(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE)
                    .body(OrderOperationDtoResponse.fromResult(LitemallOrderOperationResult.submitFailed(
                            "Goods service is unavailable — the order was not placed. Please try again.")));
        }
    }

    @PostMapping("/{orderId}/actions/cancel")
    public ResponseEntity<OrderOperationDtoResponse> cancelOrder(
            @PathVariable Integer orderId,
            @RequestHeader("X-User-Id") Integer userId,
            @RequestBody String reason) {
        LitemallOrderCancelCommand request = new LitemallOrderCancelCommand(
                new LitemallOrderId(orderId), new LitemallUserId(userId), reason);
        LitemallOrderOperationResult result = orderOrchestrationService.cancelOrder(request);
        return buildResponse(result);
    }

    /**
     * Pay a placed order. Mirrors {@code /{orderId}/actions/cancel}: the order is
     * taken from the path and the buyer from the gateway-injected {@code X-User-Id}
     * header (never the body — same identity rule as submit/cancel). The body carries
     * the selected {@code paymentMethod} and, for the CARD path, the client-confirmed
     * Stripe {@code paymentIntentId} (client-confirmed boundary — see
     * {@code docs/handoff-gateway-api-order-payment.md}).
     *
     * <p>WALLET debits the wallet vertical and marks the order PAID atomically. An
     * underfunded wallet raises {@link LitemallInsufficientBalanceException}, which
     * rolls the whole transaction back — no paid order is produced — and is surfaced
     * here as a non-zero errno (HTTP 402) with the order left unpaid.
     */
    @PostMapping("/{orderId}/actions/pay")
    public ResponseEntity<OrderOperationDtoResponse> payOrder(
            @PathVariable Integer orderId,
            @RequestHeader("X-User-Id") Integer userId,
            @RequestBody PaymentActionRequest request) {
        LitemallOrderId orderIdVo = new LitemallOrderId(orderId);
        LitemallOrderPaymentCommand command = new LitemallOrderPaymentCommand(
                orderIdVo,
                new LitemallUserId(userId),
                request.getPaymentMethod(),
                request.getPaymentIntentId());
        try {
            LitemallOrderOperationResult result = orderOrchestrationService.payOrder(command);
            return buildResponse(result);
        } catch (LitemallInsufficientBalanceException e) {
            // Wallet underfunded: the orchestrator transaction rolled back (no paid
            // order). Surface a clean payment-failed envelope rather than a raw 500.
            return buildResponse(
                    LitemallOrderOperationResult.payFailed(orderIdVo, e.getMessage()));
        } catch (org.linlinjava.litemall.order.application.util.exception.cj.LitemallCjOrderException e) {
            // CJ refused the pay-first fulfillment placement: the transaction rolled
            // back (debit undone, order still unpaid). Same clean envelope as above,
            // carrying CJ's message so the customer/support can act on it.
            return buildResponse(
                    LitemallOrderOperationResult.payFailed(orderIdVo,
                            "CJ fulfillment could not be placed: " + e.getMessage()));
        }
    }

    /** Customer confirms receipt of a shipped order (SHIPPED → DELIVERED). */
    @PostMapping("/{orderId}/actions/confirm")
    public ResponseEntity<OrderOperationDtoResponse> confirmOrder(
            @PathVariable Integer orderId,
            @RequestHeader("X-User-Id") Integer userId) {
        LitemallOrderOperationResult result = orderOrchestrationService.confirmReceipt(
                new LitemallOrderId(orderId), new LitemallUserId(userId));
        return buildResponse(result);
    }

    /** Customer opens a refund/return (PAID|SHIPPED → REFUND_REQUEST). */
    @PostMapping("/{orderId}/actions/refund")
    public ResponseEntity<OrderOperationDtoResponse> refundOrder(
            @PathVariable Integer orderId,
            @RequestHeader("X-User-Id") Integer userId,
            @RequestBody(required = false) String reason) {
        LitemallOrderOperationResult result = orderOrchestrationService.requestRefund(
                new LitemallOrderId(orderId), new LitemallUserId(userId), reason);
        return buildResponse(result);
    }

    /** Customer soft-deletes a terminal order. */
    @PostMapping("/{orderId}/actions/delete")
    public ResponseEntity<OrderOperationDtoResponse> deleteOrder(
            @PathVariable Integer orderId,
            @RequestHeader("X-User-Id") Integer userId) {
        LitemallOrderOperationResult result = orderOrchestrationService.deleteOrder(
                new LitemallOrderId(orderId), new LitemallUserId(userId));
        return buildResponse(result);
    }

    /**
     * Full lifecycle timeline for an order (every recorded state transition, oldest
     * first). Owner-scoped to {@code X-User-Id}; a non-owned/absent order returns 404.
     */
    @GetMapping("/{orderId}/timeline")
    public ApiResponse<List<OrderStatusTimelineDtoResponse>> timeline(
            @RequestHeader("X-User-Id") Integer userId,
            @PathVariable Integer orderId) {
        List<org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderStatusChange> changes =
                orderOrchestrationService.getOrderTimeline(
                        new LitemallUserId(userId), new LitemallOrderId(orderId));
        if (changes == null) {
            return ApiResponse.fail(404, "Order not found");
        }
        return ApiResponse.ok(changes.stream()
                .map(OrderStatusTimelineDtoResponse::fromDomain)
                .collect(Collectors.toList()));
    }

    /**
     * Shipment tracking for an order (Wave 3). Owner-scoped to {@code X-User-Id}; a
     * non-owned/absent order returns 404. Not shipped yet → a clean
     * {@code {shipped:false, status:"NOT_SHIPPED"}} payload, never an error. See
     * docs/handoff-gateway-admin-cj-tracking.md for the contract.
     */
    @GetMapping("/{orderId}/tracking")
    public ApiResponse<org.linlinjava.litemall.order.interfaces.dtos.cj.tracking.TrackingDtoResponse> tracking(
            @RequestHeader("X-User-Id") Integer userId,
            @PathVariable Integer orderId) {
        org.linlinjava.litemall.order.interfaces.dtos.cj.tracking.TrackingDtoResponse dto =
                cjTrackingService.getTrackingForUser(new LitemallUserId(userId), new LitemallOrderId(orderId));
        if (dto == null) {
            return ApiResponse.fail(404, "Order not found");
        }
        return ApiResponse.ok(dto);
    }
}
