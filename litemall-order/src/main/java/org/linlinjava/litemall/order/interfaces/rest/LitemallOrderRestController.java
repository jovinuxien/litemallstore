package org.linlinjava.litemall.order.interfaces.rest;

import org.linlinjava.litemall.order.application.LitemallOrderOrchestratorService;
import org.linlinjava.litemall.order.application.util.exception.wallet.LitemallInsufficientBalanceException;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.commands.LitemallOrderCancelCommand;
import org.linlinjava.litemall.order.domain.model.commands.LitemallPlaceOrderCommand;
import org.linlinjava.litemall.order.domain.model.commands.payment.LitemallOrderPaymentCommand;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.order.domain.service.order.LitemallOrderOperationResult;
import org.linlinjava.litemall.order.interfaces.dtos.order.OrderOperationDtoResponse;
import org.linlinjava.litemall.order.interfaces.dtos.order.PaymentActionRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.linlinjava.litemall.order.interfaces.util.LitemallHttpResponseUtil.buildResponse;

@RestController
@RequestMapping("/srv/order")
public class LitemallOrderRestController {

    private final LitemallOrderOrchestratorService orderOrchestrationService;

    public LitemallOrderRestController(LitemallOrderOrchestratorService orderOrchestrationService) {
        this.orderOrchestrationService = orderOrchestrationService;
    }

    @GetMapping("/list")
    public List<LitemallOrderAggregate> list(@RequestHeader("X-User-Id") Integer userId,
                                             @RequestParam(required = false) List<Short> status,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "10") int limit,
                                             @RequestParam(defaultValue = "add_time") String sort,
                                             @RequestParam(defaultValue = "desc") String order) {
        return orderOrchestrationService.listOrders(new LitemallUserId(userId), status, page, limit, sort, order);
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
                command.getGrouponLinkId());
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
        }
    }
}
