package org.linlinjava.litemall.order.interfaces.rest;

import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.order.application.LitemallOrderOrchestratorService;
import org.linlinjava.litemall.order.application.internal.cj.CjFreightQuoteService;
import org.linlinjava.litemall.order.application.util.exception.wallet.LitemallInsufficientBalanceException;
import org.linlinjava.litemall.order.domain.model.commands.payment.LitemallOrderPaymentCommand;
import org.linlinjava.litemall.order.domain.model.util.LitemallOrderHandleOption;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.payment.PaymentMethod;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.service.order.LitemallOrderOperationResult;
import org.linlinjava.litemall.order.interfaces.dtos.order.OrderOperationDtoResponse;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies POST /srv/order/{orderId}/actions/pay mirrors the cancel verb's contract:
 * the order comes from the path and the payer from the gateway-trusted X-User-Id
 * header, the body's paymentMethod/paymentIntentId flow into the dispatched
 * {@link LitemallOrderPaymentCommand}, and an underfunded wallet surfaces as a clean
 * 402 payment-failed envelope (no paid order) instead of a raw 500.
 */
class LitemallOrderRestControllerPayActionTest {

    private LitemallOrderRestController controllerWith(LitemallOrderOrchestratorService orchestrator) {
        return new LitemallOrderRestController(orchestrator, mock(CjFreightQuoteService.class),
                mock(org.linlinjava.litemall.order.application.internal.cj.CjTrackingService.class));
    }

    @Test
    void payOrder_bindsPathOrderAndHeaderUser_intoPayCommand() {
        LitemallOrderOrchestratorService orchestrator = mock(LitemallOrderOrchestratorService.class);
        when(orchestrator.payOrder(any())).thenReturn(
                LitemallOrderOperationResult.paySuccess(new LitemallOrderId(31),
                        LitemallOrderStatus.CREATED,
                        LitemallOrderHandleOption.forStatus(LitemallOrderStatus.PAID)));

        PaymentActionRequestFixture body = new PaymentActionRequestFixture(PaymentMethod.WALLET, null);
        ResponseEntity<OrderOperationDtoResponse> response =
                controllerWith(orchestrator).payOrder(31, 99, body.dto());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());

        ArgumentCaptor<LitemallOrderPaymentCommand> cap = ArgumentCaptor.forClass(LitemallOrderPaymentCommand.class);
        verify(orchestrator).payOrder(cap.capture());
        assertEquals(31, cap.getValue().getOrderId().getId());   // path wins
        assertEquals(99, cap.getValue().getUserId().getId());    // header identity wins
        assertEquals(PaymentMethod.WALLET, cap.getValue().getPaymentMethod());
    }

    @Test
    void payOrder_insufficientBalance_returns402Envelope_noSuccess() {
        LitemallOrderOrchestratorService orchestrator = mock(LitemallOrderOrchestratorService.class);
        when(orchestrator.payOrder(any())).thenThrow(new LitemallInsufficientBalanceException("balance 3.00 < 10.00"));

        PaymentActionRequestFixture body = new PaymentActionRequestFixture(PaymentMethod.WALLET, null);
        ResponseEntity<OrderOperationDtoResponse> response =
                controllerWith(orchestrator).payOrder(31, 99, body.dto());

        assertEquals(HttpStatus.PAYMENT_REQUIRED, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        assertTrue(response.getBody().getMessage().contains("balance 3.00 < 10.00"));
    }

    /** Small builder so the fixture reads as (method, intentId) at the call site. */
    private record PaymentActionRequestFixture(PaymentMethod method, String intentId) {
        org.linlinjava.litemall.order.interfaces.dtos.order.PaymentActionRequest dto() {
            var r = new org.linlinjava.litemall.order.interfaces.dtos.order.PaymentActionRequest();
            r.setPaymentMethod(method);
            r.setPaymentIntentId(intentId);
            return r;
        }
    }
}
