package org.linlinjava.litemall.order.interfaces.rest;

import org.linlinjava.litemall.order.application.LitemallOrderOrchestratorService;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.commands.LitemallOrderCancelCommand;
import org.linlinjava.litemall.order.domain.model.commands.LitemallPlaceOrderCommand;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.order.domain.service.order.LitemallOrderOperationResult;
import org.linlinjava.litemall.order.interfaces.dtos.order.OrderOperationDtoResponse;
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

    @PostMapping
    public ResponseEntity<OrderOperationDtoResponse> createOrder(
            @RequestBody LitemallPlaceOrderCommand command) {
        LitemallOrderOperationResult result = orderOrchestrationService.createOrder(command);
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

    // POST /{orderId}/actions/pay is deferred: LitemallPaymentInfo requires a
    // Stripe PaymentMethod and the orchestrator's processPayment is a stub.
    // Pay endpoint activation will land with the Stripe integration sprint.
}
