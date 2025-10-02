package org.linlinjava.litemall.order.interfaces.rest;


import org.linlinjava.litemall.core.validator.Order;
import org.linlinjava.litemall.core.validator.Sort;
import org.linlinjava.litemall.db.domain.LitemallUser;
import org.linlinjava.litemall.order.application.LitemallOrderOrchestratorService;
import org.linlinjava.litemall.order.domain.model.commands.LitemallOrderCancelCommand;
import org.linlinjava.litemall.order.domain.model.commands.LitemallPlaceOrderCommand;
import org.linlinjava.litemall.order.domain.model.domainservices.order.LitemallOrderOperationResult;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.order.interfaces.dtos.order.OrderOperationDtoResponse;
import org.linlinjava.litemall.wx.annotation.LoginUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static org.linlinjava.litemall.order.interfaces.util.LitemallHttpResponseUtil.buildResponse;

@RestController
@RequestMapping("/srv/order")
public class LitemallOrderRestController {

    private LitmallOrderService wxOrderService;
    private  LitemallOrderOrchestratorService orderOrchestrationService;


    @GetMapping("list")
    public Object list(@LoginUser Integer userId,
                       @RequestParam(defaultValue = "0") Integer showType,
                       @RequestParam(defaultValue = "1") Integer page,
                       @RequestParam(defaultValue = "10") Integer limit,
                       @Sort @RequestParam(defaultValue = "add_time") String sort,
                       @Order @RequestParam(defaultValue = "desc") String order) {
        return wxOrderService.list(userId, showType, page, limit, sort, order);
    }


    // Order creation - uses orchestration service which delegates to your existing service
    @PostMapping
    public ResponseEntity<OrderOperationDtoResponse> createOrder(
            @RequestBody LitemallPlaceOrderCommand command,
            @RequestHeader Integer userId) {

        command.setUserId(userId); // Set user from auth context

        LitemallOrderOperationResult result = orderOrchestrationService.createOrder(command);
        return buildResponse(result);
    }

    // Order actions - use orchestration service directly
    @PostMapping("/{orderId}/actions/cancel")
    public ResponseEntity<OrderOperationDtoResponse> cancelOrder(
            @PathVariable Integer orderId,
            @RequestHeader Integer userId,
            @RequestBody String reason) {

        // Extract User info from userId through auth context
        LitemallOrderCancelCommand request = new LitemallOrderCancelCommand(new LitemallOrderId(orderId), new LitemallUserId(userId), reason);

        LitemallOrderOperationResult result = orderOrchestrationService.cancelOrder(request);

        return buildResponse(result);
    }

    @PostMapping("/{orderId}/actions/pay")
    public ResponseEntity<OrderOperationDtoResponse> payOrder(
            @PathVariable Long orderId,
            @RequestHeader Long userId,
            @RequestBody PaymentRequest paymentRequest) {

        LitemallOrderOperationResult result = orderOrchestrationService.payOrder(
                new LitemallOrderId(orderId), new UserId(userId), paymentRequest.toPaymentInfo());

        return buildResponse(result);
    }




}


