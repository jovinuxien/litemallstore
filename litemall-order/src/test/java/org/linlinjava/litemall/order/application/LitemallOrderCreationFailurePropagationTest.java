package org.linlinjava.litemall.order.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.linlinjava.litemall.order.application.internal.LitemallCartServiceLayer;
import org.linlinjava.litemall.order.application.internal.LitemallGrouponServiceLayer;
import org.linlinjava.litemall.order.application.internal.LitemallOrderServiceImpl;
import org.linlinjava.litemall.order.application.internal.UnpaidOrderTaskScheduler;
import org.linlinjava.litemall.order.application.internal.cj.OrderSourceResolver;
import org.linlinjava.litemall.order.application.util.exception.product.LitemallGoodsServiceUnavailableException;
import org.linlinjava.litemall.order.application.util.exception.product.LitemallInsufficientStockException;
import org.linlinjava.litemall.order.domain.events.LitemallDomainEventPublisher;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallCartAggregate;
import org.linlinjava.litemall.order.domain.model.commands.LitemallPlaceOrderCommand;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Verifies handleOrderCreation propagates the typed placement failures —
 * {@link LitemallInsufficientStockException} and
 * {@link LitemallGoodsServiceUnavailableException} — instead of rewrapping them in
 * the generic "System error during order creation" LitemallOrderServiceException.
 * They are thrown inside the transactional placeOrder, so the shared transaction is
 * rollback-only; the REST layer (outside the transaction) maps them to 422/503.
 * Also asserts no creation events / unpaid-order timers fire for a failed placement.
 */
@ExtendWith(MockitoExtension.class)
class LitemallOrderCreationFailurePropagationTest {

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
    private UnpaidOrderTaskScheduler unpaidOrderTaskScheduler;
    @Mock
    private LitemallDomainEventPublisher domainEventPublisher;

    private LitemallOrderOrchestratorService orchestrator;

    private static final LitemallPlaceOrderCommand COMMAND =
            new LitemallPlaceOrderCommand(99, 10, 20, 0, -1, "msg", 0, 0, null);

    @BeforeEach
    void wireOrchestrator() {
        orchestrator = new LitemallOrderOrchestratorService(
                orderServiceImpl, orderRepository, grouponServiceLayer);
        ReflectionTestUtils.setField(orchestrator, "cartServiceLayer", cartServiceLayer);
        ReflectionTestUtils.setField(orchestrator, "orderSourceResolver", orderSourceResolver);
        ReflectionTestUtils.setField(orchestrator, "unpaidOrderTaskScheduler", unpaidOrderTaskScheduler);
        ReflectionTestUtils.setField(orchestrator, "domainEventPublisher", domainEventPublisher);
        // Pre-checks pass: one checked cart line, single fulfillment source.
        when(cartServiceLayer.getCheckedCartItems(any(), any()))
                .thenReturn(List.of(mock(LitemallCartAggregate.class)));
        when(orderSourceResolver.resolve(anyList())).thenReturn("local");
    }

    @Test
    void insufficientStock_propagatesTyped_noEventsNoTimer() throws Exception {
        when(orderServiceImpl.placeOrder(any())).thenThrow(
                new LitemallInsufficientStockException("Insufficient stock for products: ..."));

        assertThrows(LitemallInsufficientStockException.class,
                () -> orchestrator.createOrder(COMMAND));
        verifyNoMoreInteractions(domainEventPublisher, unpaidOrderTaskScheduler);
    }

    @Test
    void goodsServiceUnavailable_propagatesTyped_noEventsNoTimer() throws Exception {
        when(orderServiceImpl.placeOrder(any())).thenThrow(
                new LitemallGoodsServiceUnavailableException("stock reservation unconfirmed for product IDs [7]"));

        assertThrows(LitemallGoodsServiceUnavailableException.class,
                () -> orchestrator.createOrder(COMMAND));
        verifyNoMoreInteractions(domainEventPublisher, unpaidOrderTaskScheduler);
    }
}
