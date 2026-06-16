package org.linlinjava.litemall.order.interfaces.rest;

import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.order.application.LitemallOrderOrchestratorService;
import org.linlinjava.litemall.order.domain.model.commands.LitemallPlaceOrderCommand;
import org.linlinjava.litemall.order.domain.model.util.LitemallOrderHandleOption;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.service.order.LitemallOrderOperationResult;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the order-submit endpoint takes the buyer from the authenticated
 * gateway header (X-User-Id) and ignores any body-supplied userId — closing the
 * IDOR vector and the null-userId failure behind the place-order 404 handoff.
 */
class LitemallOrderRestControllerIdentityTest {

    @Test
    void createOrder_usesHeaderUserId_andIgnoresBodyUserId() {
        LitemallOrderOrchestratorService orchestrator = mock(LitemallOrderOrchestratorService.class);
        when(orchestrator.createOrder(any())).thenReturn(
                LitemallOrderOperationResult.submitSuccess(new LitemallOrderId(5),
                        LitemallOrderStatus.CREATED,
                        LitemallOrderHandleOption.forStatus(LitemallOrderStatus.CREATED)));

        LitemallOrderRestController controller = new LitemallOrderRestController(orchestrator);

        // Body claims userId=1 (spoofed); authenticated header says 99.
        LitemallPlaceOrderCommand body = new LitemallPlaceOrderCommand(1, 10, 20, 0, -1, "msg", 0, 0);

        controller.createOrder(99, body);

        ArgumentCaptor<LitemallPlaceOrderCommand> cap = ArgumentCaptor.forClass(LitemallPlaceOrderCommand.class);
        verify(orchestrator).createOrder(cap.capture());
        assertEquals(99, cap.getValue().getUserId());  // header identity wins
        assertEquals(10, cap.getValue().getCartId());   // rest of the body preserved
        assertEquals(20, cap.getValue().getAddressId());
    }
}
