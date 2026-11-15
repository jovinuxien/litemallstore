package org.linlinjava.litemall.order.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.linlinjava.litemall.order.application.internal.BuyerEmailGuard;
import org.linlinjava.litemall.order.application.internal.LitemallCartServiceLayer;
import org.linlinjava.litemall.order.application.internal.LitemallGrouponServiceLayer;
import org.linlinjava.litemall.order.application.internal.LitemallOrderServiceImpl;
import org.linlinjava.litemall.order.application.internal.cj.OrderSourceResolver;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallCartAggregate;
import org.linlinjava.litemall.order.domain.model.commands.LitemallPlaceOrderCommand;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.linlinjava.litemall.order.domain.service.order.LitemallOrderOperationResult;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The orchestrator refuses a submit from an email-less account in the PRE-CHECK zone:
 * a submit-failed result carrying the guard's message, with placeOrder never reached
 * (so no transaction, no order row, no stock touched).
 */
@ExtendWith(MockitoExtension.class)
class BuyerEmailSubmitGateTest {

    @Mock
    private LitemallOrderServiceImpl orderServiceImpl;
    @Mock
    private LitemallOrderRepository orderRepository;
    @Mock
    private LitemallGrouponServiceLayer grouponServiceLayer;
    @Mock
    private LitemallCartServiceLayer cartServiceLayer;
    @Mock
    private OrderSourceResolver orderSourceResolver;
    @Mock
    private BuyerEmailGuard buyerEmailGuard;

    private LitemallOrderOrchestratorService orchestrator;

    private static final LitemallPlaceOrderCommand COMMAND =
            new LitemallPlaceOrderCommand(99, 10, 20, 0, -1, "msg", 0, 0, null);

    @BeforeEach
    void wireOrchestrator() {
        orchestrator = new LitemallOrderOrchestratorService(orderServiceImpl, orderRepository, grouponServiceLayer);
        ReflectionTestUtils.setField(orchestrator, "cartServiceLayer", cartServiceLayer);
        ReflectionTestUtils.setField(orchestrator, "orderSourceResolver", orderSourceResolver);
        ReflectionTestUtils.setField(orchestrator, "buyerEmailGuard", buyerEmailGuard);
        when(cartServiceLayer.getCheckedCartItems(any(), any()))
                .thenReturn(List.of(mock(LitemallCartAggregate.class)));
    }

    @Test
    void emailLessAccount_isRefusedBeforePlacement() throws Exception {
        when(buyerEmailGuard.refusalFor(99)).thenReturn(Optional.of(BuyerEmailGuard.REFUSAL));

        LitemallOrderOperationResult result = orchestrator.createOrder(COMMAND);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isEqualTo(BuyerEmailGuard.REFUSAL);
        verify(orderServiceImpl, never()).placeOrder(any());
        verify(orderSourceResolver, never()).resolve(any());
    }
}
