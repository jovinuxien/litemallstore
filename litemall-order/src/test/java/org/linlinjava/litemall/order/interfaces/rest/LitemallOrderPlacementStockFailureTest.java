package org.linlinjava.litemall.order.interfaces.rest;

import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.order.application.LitemallOrderOrchestratorService;
import org.linlinjava.litemall.order.application.internal.cj.CjFreightQuoteService;
import org.linlinjava.litemall.order.application.util.exception.product.LitemallGoodsServiceUnavailableException;
import org.linlinjava.litemall.order.application.util.exception.product.LitemallInsufficientStockException;
import org.linlinjava.litemall.order.domain.model.commands.LitemallPlaceOrderCommand;
import org.linlinjava.litemall.order.interfaces.dtos.order.OrderOperationDtoResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies the submit endpoint fails placement CLEANLY when the goods path fails:
 * a reserve-time stock shortfall surfaces as a 422 submit-failed envelope carrying
 * the per-product detail, and a goods-management outage (circuit open / down /
 * unconfirmed reservation) surfaces as a 503 — in both cases the placement
 * transaction rolled back (no order row), never a raw 500 "System error".
 */
class LitemallOrderPlacementStockFailureTest {

    private static final LitemallPlaceOrderCommand BODY =
            new LitemallPlaceOrderCommand(1, 10, 20, 0, -1, "msg", 0, 0, null);

    private LitemallOrderRestController controllerWith(LitemallOrderOrchestratorService orchestrator) {
        return new LitemallOrderRestController(orchestrator, mock(CjFreightQuoteService.class));
    }

    @Test
    void insufficientStockAtReserve_surfacesAs422WithDetail() {
        LitemallOrderOrchestratorService orchestrator = mock(LitemallOrderOrchestratorService.class);
        when(orchestrator.createOrder(any())).thenThrow(new LitemallInsufficientStockException(
                "Insufficient stock for products:\nProduct Mug (ID: 7): requested 3, available 1"));

        ResponseEntity<OrderOperationDtoResponse> response =
                controllerWith(orchestrator).createOrder(99, BODY);

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        assertTrue(response.getBody().getMessage().contains("requested 3, available 1"));
    }

    @Test
    void goodsServiceUnavailable_surfacesAs503_orderNotPlaced() {
        LitemallOrderOrchestratorService orchestrator = mock(LitemallOrderOrchestratorService.class);
        when(orchestrator.createOrder(any())).thenThrow(
                new LitemallGoodsServiceUnavailableException("get products for goods 7 (circuit open/fallback)"));

        ResponseEntity<OrderOperationDtoResponse> response =
                controllerWith(orchestrator).createOrder(99, BODY);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        assertTrue(response.getBody().getMessage().contains("order was not placed"));
    }
}
