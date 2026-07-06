package org.linlinjava.litemall.order.application.internal.cj;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.linlinjava.litemall.order.application.util.exception.cj.LitemallCjDisputeException;
import org.linlinjava.litemall.order.domain.events.LitemallDomainEventPublisher;
import org.linlinjava.litemall.order.domain.events.cj.LitemallCjDisputeCancelledEvent;
import org.linlinjava.litemall.order.domain.events.cj.LitemallCjDisputeOpenedEvent;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallCjDisputeAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallCjDisputeRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.cj.CjDisputeExpectation;
import org.linlinjava.litemall.order.domain.model.valueobjects.cj.CjDisputeResolution;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.CjDisputeFacade;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.dispute.CjDisputableLine;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.dispute.CjDisputeOpenCommand;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.dispute.CjDisputeSnapshot;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CjDisputeService}: open guards (owner, cj order, paid, single open dispute),
 * server-side line truth (quantity clamped to CJ's, unknown lines rejected),
 * deterministic businessDisputeId, lazy CJ reconciliation on reads, and cancel flow.
 */
@ExtendWith(MockitoExtension.class)
class CjDisputeServiceTest {

    private static final LitemallUserId USER = new LitemallUserId(42);
    private static final LitemallOrderId ORDER = new LitemallOrderId(71);

    @Mock
    private LitemallOrderRepository orderRepository;
    @Mock
    private LitemallCjDisputeRepository disputeRepository;
    @Mock
    private CjDisputeFacade disputeFacade;
    @Mock
    private LitemallDomainEventPublisher domainEventPublisher;

    @InjectMocks
    private CjDisputeService service;

    private LitemallOrderAggregate cjOrder(LitemallOrderStatus status) {
        LitemallOrderAggregate order = new LitemallOrderAggregate();
        order.setOrderId(ORDER);
        order.setUserId(USER);
        order.setOrderSn("20260705112233");
        order.setOrderStatus(status);
        order.setSource(LitemallOrderAggregate.SOURCE_CJ);
        order.setCjOrderId("cj-order-9");
        return order;
    }

    private CjDisputableLine cjLine(String lineItemId, int maxQty, String price) {
        return CjDisputableLine.builder()
                .lineItemId(lineItemId)
                .cjVariantId("vid-" + lineItemId)
                .productName("item " + lineItemId)
                .unitPriceUsd(new BigDecimal(price))
                .maxQuantity(maxQty)
                .disputable(true)
                .build();
    }

    private CjDisputeService.OpenDisputeCommand openCommand(String lineItemId, int quantity) {
        return new CjDisputeService.OpenDisputeCommand(
                7, "Item damaged", CjDisputeExpectation.REFUND, "arrived broken", List.of(),
                List.of(new CjDisputeService.OpenDisputeCommand.Line(lineItemId, quantity)));
    }

    @Test
    void open_happyPath_usesCjPricesAndDeterministicBusinessId_andEmitsEvent() {
        when(orderRepository.findByIdAndUserId(USER, ORDER)).thenReturn(cjOrder(LitemallOrderStatus.PAID));
        when(disputeRepository.findOpenByOrder(ORDER)).thenReturn(List.of());
        when(disputeRepository.findByOrder(ORDER)).thenReturn(List.of());
        when(disputeFacade.disputableLines("cj-order-9")).thenReturn(List.of(cjLine("L1", 2, "9.99")));

        LitemallCjDisputeAggregate dispute = service.openDispute(USER, ORDER, openCommand("L1", 5));

        ArgumentCaptor<CjDisputeOpenCommand> sent = ArgumentCaptor.forClass(CjDisputeOpenCommand.class);
        verify(disputeFacade).open(sent.capture());
        assertEquals("20260705112233-D1", sent.getValue().getBusinessDisputeId());
        assertEquals(1, sent.getValue().getLines().size());
        // quantity clamped to CJ's max, price taken from CJ (never the client)
        assertEquals(2, sent.getValue().getLines().get(0).getQuantity());
        assertEquals(0, new BigDecimal("9.99").compareTo(sent.getValue().getLines().get(0).getUnitPriceUsd()));

        verify(disputeRepository).add(any(LitemallCjDisputeAggregate.class));
        verify(domainEventPublisher).publish(any(LitemallCjDisputeOpenedEvent.class));
        assertTrue(dispute.isOpen());
        assertEquals("20260705112233-D1", dispute.getBusinessDisputeId());
    }

    @Test
    void open_onNonCjOrder_failsBeforeAnyCjCall() {
        LitemallOrderAggregate localOrder = cjOrder(LitemallOrderStatus.PAID);
        localOrder.setSource(LitemallOrderAggregate.SOURCE_LOCAL);
        when(orderRepository.findByIdAndUserId(USER, ORDER)).thenReturn(localOrder);

        assertThrows(LitemallCjDisputeException.class,
                () -> service.openDispute(USER, ORDER, openCommand("L1", 1)));

        verify(disputeFacade, never()).disputableLines(any());
        verify(disputeFacade, never()).open(any());
    }

    @Test
    void open_onUnpaidOrder_isRejected() {
        when(orderRepository.findByIdAndUserId(USER, ORDER)).thenReturn(cjOrder(LitemallOrderStatus.CREATED));

        assertThrows(LitemallCjDisputeException.class,
                () -> service.openDispute(USER, ORDER, openCommand("L1", 1)));
        verify(disputeFacade, never()).open(any());
    }

    @Test
    void open_whenAnotherDisputeIsOpen_isRejected() {
        when(orderRepository.findByIdAndUserId(USER, ORDER)).thenReturn(cjOrder(LitemallOrderStatus.PAID));
        LitemallCjDisputeAggregate existing = new LitemallCjDisputeAggregate();
        when(disputeRepository.findOpenByOrder(ORDER)).thenReturn(List.of(existing));

        assertThrows(LitemallCjDisputeException.class,
                () -> service.openDispute(USER, ORDER, openCommand("L1", 1)));
        verify(disputeFacade, never()).open(any());
    }

    @Test
    void open_withUnknownLine_isRejected() {
        when(orderRepository.findByIdAndUserId(USER, ORDER)).thenReturn(cjOrder(LitemallOrderStatus.PAID));
        when(disputeRepository.findOpenByOrder(ORDER)).thenReturn(List.of());
        when(disputeFacade.disputableLines("cj-order-9")).thenReturn(List.of(cjLine("L1", 2, "9.99")));

        assertThrows(LitemallCjDisputeException.class,
                () -> service.openDispute(USER, ORDER, openCommand("L-unknown", 1)));
        verify(disputeFacade, never()).open(any());
    }

    @Test
    void list_backFillsCjIdAndResolution_fromTheCjProjection() {
        when(orderRepository.findByIdAndUserId(USER, ORDER)).thenReturn(cjOrder(LitemallOrderStatus.PAID));
        LitemallCjDisputeAggregate pending = new LitemallCjDisputeAggregate();
        pending.setDisputeId(5);
        pending.setOrderId(ORDER);
        pending.setUserId(USER);
        pending.setBusinessDisputeId("20260705112233-D1");
        when(disputeRepository.findByOrder(ORDER)).thenReturn(List.of(pending));
        when(disputeFacade.fetchDisputes("cj-order-9")).thenReturn(List.of(CjDisputeSnapshot.builder()
                .cjDisputeId("777")
                .status("Finished")
                .resolution(CjDisputeResolution.REFUND)
                .refundAmountUsd(new BigDecimal("9.99"))
                .build()));

        List<LitemallCjDisputeAggregate> result = service.listDisputes(USER, ORDER);

        assertEquals("777", result.get(0).getCjDisputeId());
        assertEquals(CjDisputeResolution.REFUND, result.get(0).getResolution());
        verify(disputeRepository).updateCjProjection(result.get(0));
    }

    @Test
    void list_survivesCjOutage_withTheLocalProjection() {
        when(orderRepository.findByIdAndUserId(USER, ORDER)).thenReturn(cjOrder(LitemallOrderStatus.PAID));
        LitemallCjDisputeAggregate pending = new LitemallCjDisputeAggregate();
        pending.setOrderId(ORDER);
        pending.setUserId(USER);
        when(disputeRepository.findByOrder(ORDER)).thenReturn(List.of(pending));
        when(disputeFacade.fetchDisputes("cj-order-9"))
                .thenThrow(new LitemallCjDisputeException("CJ dispute getDisputeList failed: down"));

        List<LitemallCjDisputeAggregate> result = service.listDisputes(USER, ORDER);

        assertEquals(1, result.size()); // degraded read, no exception to the customer
    }

    @Test
    void cancel_openDisputeWithCjId_cancelsAtCjAndLocally() {
        when(orderRepository.findByIdAndUserId(USER, ORDER)).thenReturn(cjOrder(LitemallOrderStatus.PAID));
        LitemallCjDisputeAggregate dispute = new LitemallCjDisputeAggregate();
        dispute.setDisputeId(6);
        dispute.setOrderId(ORDER);
        dispute.setUserId(USER);
        dispute.setBusinessDisputeId("20260705112233-D1");
        dispute.setCjDisputeId("777");
        when(disputeRepository.findById(6)).thenReturn(Optional.of(dispute));

        service.cancelDispute(USER, ORDER, 6);

        verify(disputeFacade).cancel("cj-order-9", "777");
        verify(disputeRepository).markCancelled(6);
        verify(domainEventPublisher).publish(any(LitemallCjDisputeCancelledEvent.class));
    }

    @Test
    void cancel_alreadyResolvedDispute_isRejected() {
        when(orderRepository.findByIdAndUserId(USER, ORDER)).thenReturn(cjOrder(LitemallOrderStatus.PAID));
        LitemallCjDisputeAggregate dispute = new LitemallCjDisputeAggregate();
        dispute.setDisputeId(7);
        dispute.setOrderId(ORDER);
        dispute.setUserId(USER);
        dispute.setResolution(CjDisputeResolution.REJECTED);
        when(disputeRepository.findById(7)).thenReturn(Optional.of(dispute));

        assertThrows(LitemallCjDisputeException.class, () -> service.cancelDispute(USER, ORDER, 7));
        verify(disputeFacade, never()).cancel(any(), any());
    }
}
