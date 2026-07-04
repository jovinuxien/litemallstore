package org.linlinjava.litemall.order.interfaces.rest;

import org.linlinjava.litemall.order.application.LitemallOrderOrchestratorService;
import org.linlinjava.litemall.order.application.util.exception.wallet.LitemallInsufficientBalanceException;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.commands.LitemallOrderCancelCommand;
import org.linlinjava.litemall.order.domain.model.commands.LitemallPlaceOrderCommand;
import org.linlinjava.litemall.order.domain.model.commands.payment.LitemallOrderPaymentCommand;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.order.domain.model.valueobjects.ApiResponse;
import org.linlinjava.litemall.order.domain.service.order.LitemallOrderOperationResult;
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

    public LitemallOrderRestController(LitemallOrderOrchestratorService orderOrchestrationService) {
        this.orderOrchestrationService = orderOrchestrationService;
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
        LitemallOrderOperationResult result = orderOrchestrationService.createOrder(authoritativeCommand);
        return buildResponse(result);
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
}
