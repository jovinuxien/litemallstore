package org.linlinjava.litemall.order.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.linlinjava.litemall.core.events.LitemallDomainEvent;
import org.linlinjava.litemall.order.domain.events.order.LitemallOrderCancelledEvent;
import org.linlinjava.litemall.order.domain.events.order.LitemallOrderPaidEvent;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderGoodsAggregate;
import org.linlinjava.litemall.order.domain.events.LitemallDomainEventPublisher;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderGoodsRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderStatusHistoryRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsProductId;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.LitemallGoodsFacade;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the paid / cancel write-paths on {@link LitemallOrderServiceImpl}.
 *
 * <p>Guards the two blockers fixed on fix/order: (1) a successful payment must
 * actually transition the order to PAID, persist it, and publish
 * {@link LitemallOrderPaidEvent} (previously the mark-paid call was commented out);
 * (2) cancellation must persist the CANCELED/SYSTEM_CANCELED status, release the
 * reserved stock through the goods ACL, and publish the cancelled event (previously
 * {@code cancelOrder} mutated only in-memory and discarded its events).
 */
@ExtendWith(MockitoExtension.class)
class LitemallOrderPaidCancelTest {

    @Mock
    private LitemallOrderRepository orderRepository;
    @Mock
    private LitemallOrderGoodsRepository orderGoodsRepository;
    @Mock
    private LitemallDomainEventPublisher domainEventPublisher;
    @Mock
    private LitemallGoodsFacade goodsFacade;
    @Mock
    private LitemallOrderStatusHistoryRepository statusHistoryRepository;

    @InjectMocks
    private LitemallOrderServiceImpl service;

    @BeforeEach
    void wireFieldInjectedDeps() {
        // goodsFacade + statusHistoryRepository are field-injected (@Autowired), not
        // constructor args, so @InjectMocks (which uses constructor injection here)
        // does not set them. Wire them explicitly so the write-paths can restore
        // stock and persist the status-history timeline.
        ReflectionTestUtils.setField(service, "goodsFacade", goodsFacade);
        ReflectionTestUtils.setField(service, "statusHistoryRepository", statusHistoryRepository);
    }

    private LitemallOrderAggregate createdOrder(int id) {
        LitemallOrderAggregate order = new LitemallOrderAggregate();
        order.setOrderId(new LitemallOrderId(id));
        order.setOrderSn("SN" + id);
        order.setOrderStatus(LitemallOrderStatus.CREATED);
        return order;
    }

    @Test
    void markOrderPaid_persistsPaidStatusAndPublishesPaidEvent() {
        LitemallOrderId orderId = new LitemallOrderId(7);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(createdOrder(7)));
        // Conditional CREATED->PAID transition applied exactly one row.
        when(orderRepository.markPaidIfCreated(orderId, "WALLET")).thenReturn(1);

        service.markOrderPaid(orderId, "WALLET");

        // The tender is persisted alongside the status flip so refund settlement
        // can route the money back to the channel that paid.
        verify(orderRepository).markPaidIfCreated(orderId, "WALLET");

        ArgumentCaptor<LitemallDomainEvent> event = ArgumentCaptor.forClass(LitemallDomainEvent.class);
        verify(domainEventPublisher).publish(event.capture());
        assertInstanceOf(LitemallOrderPaidEvent.class, event.getValue());
    }

    @Test
    void markOrderPaid_whenNoLongerCreated_throwsAndPublishesNoEvent() {
        // A retried or concurrent PAY already flipped the row, so the guarded
        // update matches 0 rows. markOrderPaid must abort (rolling back any wallet
        // debit in the same transaction) and publish no paid event.
        LitemallOrderId orderId = new LitemallOrderId(8);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(createdOrder(8)));
        when(orderRepository.markPaidIfCreated(orderId, "WALLET")).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> service.markOrderPaid(orderId, "WALLET"));

        verify(domainEventPublisher, never()).publish(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void cancelOrder_persistsCancelled_restoresStock_andPublishesEvent() {
        LitemallOrderId orderId = new LitemallOrderId(9);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(createdOrder(9)));
        // Guarded CREATED->CANCELED transition applied exactly one row.
        when(orderRepository.markCanceledIfCreated(orderId)).thenReturn(1);
        when(orderGoodsRepository.findByOId(orderId)).thenReturn(List.of(orderGoods(5, (short) 2)));

        service.cancelOrder(orderId, "changed my mind");

        verify(orderRepository).markCanceledIfCreated(orderId);
        verify(goodsFacade).restoreStock(Map.of(5, 2));

        ArgumentCaptor<LitemallDomainEvent> event = ArgumentCaptor.forClass(LitemallDomainEvent.class);
        verify(domainEventPublisher).publish(event.capture());
        assertInstanceOf(LitemallOrderCancelledEvent.class, event.getValue());
    }

    @Test
    void cancelOrder_whenGuardedTransitionLoses_throwsAndReleasesNoStock() {
        // The order left CREATED between the read and the guarded update (e.g. it
        // was paid concurrently): cancellation must abort without touching stock.
        LitemallOrderId orderId = new LitemallOrderId(10);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(createdOrder(10)));
        when(orderRepository.markCanceledIfCreated(orderId)).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> service.cancelOrder(orderId, "too late"));

        verify(goodsFacade, never()).restoreStock(org.mockito.ArgumentMatchers.any());
        verify(domainEventPublisher, never()).publish(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void autoCancelOrder_persistsSystemCancelled_andRestoresStock() {
        LitemallOrderId orderId = new LitemallOrderId(11);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(createdOrder(11)));
        // Guarded CREATED->SYSTEM_CANCELED transition applied exactly one row.
        when(orderRepository.markSystemCanceledIfCreated(orderId)).thenReturn(1);
        when(orderGoodsRepository.findByOId(orderId)).thenReturn(List.of(orderGoods(8, (short) 3)));

        service.autoCancelOrder(orderId, "auto-cancelled: unpaid timeout");

        verify(orderRepository).markSystemCanceledIfCreated(orderId);
        verify(goodsFacade).restoreStock(Map.of(8, 3));
    }

    private LitemallOrderGoodsAggregate orderGoods(int productId, short number) {
        LitemallOrderGoodsAggregate g = new LitemallOrderGoodsAggregate();
        g.setProductId(new LitemallGoodsProductId(productId));
        g.setNumber(number);
        return g;
    }
}
