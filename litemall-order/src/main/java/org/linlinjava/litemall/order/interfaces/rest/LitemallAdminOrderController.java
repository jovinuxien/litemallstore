package org.linlinjava.litemall.order.interfaces.rest;

import org.linlinjava.litemall.order.application.LitemallOrderOrchestratorService;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.service.order.LitemallOrderOperationResult;
import org.linlinjava.litemall.order.interfaces.dtos.order.OrderOperationDtoResponse;
import org.linlinjava.litemall.order.interfaces.dtos.order.ShipActionRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static org.linlinjava.litemall.order.interfaces.util.LitemallHttpResponseUtil.buildResponse;

/**
 * Admin-driven order lifecycle transitions. Mounted under {@code /srv/private/admin/**},
 * which the gateway gates to ROLE_ADMIN — there is no per-user ownership check here.
 *
 * <p>Routing note (gateway-admin follow-up): the admin gateway must route
 * {@code /srv/private/admin/order/**} to the order service for these to be reachable.
 */
@RestController
@RequestMapping("/srv/private/admin/order")
public class LitemallAdminOrderController {

    private final LitemallOrderOrchestratorService orchestrator;

    public LitemallAdminOrderController(LitemallOrderOrchestratorService orchestrator) {
        this.orchestrator = orchestrator;
    }

    /** Ship a paid order (PAID → SHIPPED), recording courier + tracking number. */
    @PostMapping("/{orderId}/ship")
    public ResponseEntity<OrderOperationDtoResponse> ship(
            @PathVariable Integer orderId,
            @RequestBody ShipActionRequest request) {
        LitemallOrderOperationResult result = orchestrator.shipOrder(
                new LitemallOrderId(orderId), request.getShipChannel(), request.getShipSn());
        return buildResponse(result);
    }

    /**
     * Approve a pending refund (REFUND_REQUEST → REFUNDED): credits the buyer's wallet
     * back and flips the status atomically.
     */
    @PostMapping("/{orderId}/refund")
    public ResponseEntity<OrderOperationDtoResponse> approveRefund(@PathVariable Integer orderId) {
        LitemallOrderOperationResult result = orchestrator.approveRefund(new LitemallOrderId(orderId));
        return buildResponse(result);
    }
}
