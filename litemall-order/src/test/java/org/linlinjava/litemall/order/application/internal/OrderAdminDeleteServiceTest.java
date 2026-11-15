package org.linlinjava.litemall.order.application.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.linlinjava.litemall.order.application.internal.OrderAdminDeleteService.Result;
import org.linlinjava.litemall.order.application.internal.OrderAdminDeleteService.Status;
import org.linlinjava.litemall.order.application.internal.cj.CjPlacementMode;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderStatusHistoryRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderStatusChange;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link OrderAdminDeleteService}: finished orders delete on the customer's own rule; a paid
 * order deletes only when it is out of the placement sweep's reach AND its order number is
 * repeated; everything else is a typed refusal that writes nothing.
 */
@ExtendWith(MockitoExtension.class)
class OrderAdminDeleteServiceTest {

    private static final LitemallOrderId ID = new LitemallOrderId(7);
    private static final String SN = "20260802000007";

    @Mock
    private LitemallOrderRepository orderRepository;
    @Mock
    private LitemallOrderStatusHistoryRepository historyRepository;

    private OrderAdminDeleteService service(String mode) {
        return new OrderAdminDeleteService(orderRepository, historyRepository, new CjPlacementMode(mode));
    }

    private LitemallOrderAggregate order(LitemallOrderStatus status) {
        LitemallOrderAggregate order = new LitemallOrderAggregate();
        order.setOrderId(ID);
        order.setOrderSn(SN);
        order.setOrderStatus(status);
        order.setSource(LitemallOrderAggregate.SOURCE_CJ);
        order.setDeleted(false);
        when(orderRepository.findById(ID)).thenReturn(Optional.of(order));
        return order;
    }

    private void assertNothingWritten() {
        verify(orderRepository, never()).softDeleteIfStatus(any(), any());
        verify(historyRepository, never()).record(any());
    }

    @Test
    void finishedStatusesDeleteWithoutConfirmation() {
        for (LitemallOrderStatus status : new LitemallOrderStatus[]{LitemallOrderStatus.CANCELED,
                LitemallOrderStatus.SYSTEM_CANCELED, LitemallOrderStatus.REFUNDED,
                LitemallOrderStatus.DELIVERED, LitemallOrderStatus.AUTO_DELIVERED}) {
            order(status);
            when(orderRepository.softDeleteIfStatus(ID, status)).thenReturn(1);
            Result result = service("manual").delete(7, null, "1");
            assertEquals(Status.DELETED, result.status(), status.name());
        }
    }

    @Test
    void deleteLeavesAHopNamingTheAdmin() {
        order(LitemallOrderStatus.CANCELED);
        when(orderRepository.softDeleteIfStatus(ID, LitemallOrderStatus.CANCELED)).thenReturn(1);

        service("manual").delete(7, null, " 12 ");

        ArgumentCaptor<LitemallOrderStatusChange> hop = ArgumentCaptor.forClass(LitemallOrderStatusChange.class);
        verify(historyRepository).record(hop.capture());
        assertEquals(OrderAdminDeleteService.CHANGE_TYPE_ADMIN_DELETE, hop.getValue().getChangeType());
        assertEquals("Deleted by admin 12", hop.getValue().getChangeMessage());
        assertEquals("admin:12", hop.getValue().getOperator());
    }

    @Test
    void unpaidAndShippedOrdersAreRefused() {
        for (LitemallOrderStatus status : new LitemallOrderStatus[]{LitemallOrderStatus.CREATED,
                LitemallOrderStatus.SHIPPED}) {
            order(status);
            Result result = service("manual").delete(7, SN, "1");
            assertEquals(Status.NOT_DELETABLE, result.status(), status.name());
            assertTrue(result.message().contains(status.name()));
        }
        assertNothingWritten();
    }

    @Test
    void paidOrderNeedsItsOrderNumberRepeated() {
        order(LitemallOrderStatus.PAID);
        assertEquals(Status.CONFIRM_REQUIRED, service("manual").delete(7, null, "1").status());
        assertEquals(Status.CONFIRM_REQUIRED, service("manual").delete(7, "20260802000008", "1").status());
        assertNothingWritten();
    }

    @Test
    void paidUnapprovedOrderDeletesUnderTheManualGate() {
        order(LitemallOrderStatus.PAID);
        when(orderRepository.softDeleteIfStatus(ID, LitemallOrderStatus.PAID)).thenReturn(1);

        Result result = service("manual").delete(7, " " + SN + " ", "1");

        assertEquals(Status.DELETED, result.status());
        ArgumentCaptor<LitemallOrderStatusChange> hop = ArgumentCaptor.forClass(LitemallOrderStatusChange.class);
        verify(historyRepository).record(hop.capture());
        assertTrue(hop.getValue().getChangeMessage().contains("no money moved"));
    }

    @Test
    void refundRequestedOrderDeletesWithConfirmation() {
        order(LitemallOrderStatus.REFUND_REQUEST);
        when(orderRepository.softDeleteIfStatus(ID, LitemallOrderStatus.REFUND_REQUEST)).thenReturn(1);
        assertEquals(Status.DELETED, service("manual").delete(7, SN, "1").status());
    }

    @Test
    void approvedOrderTheSweepCouldPlaceIsRefused() {
        order(LitemallOrderStatus.PAID).setCjPlacementApprovedTime(LocalDateTime.now());
        assertEquals(Status.QUEUED_FOR_CJ, service("manual").delete(7, SN, "1").status());
        assertNothingWritten();
    }

    @Test
    void autoModeRefusesEveryUnparkedPaidCjOrder() {
        order(LitemallOrderStatus.PAID);
        assertEquals(Status.QUEUED_FOR_CJ, service("auto").delete(7, SN, "1").status());
        assertNothingWritten();
    }

    @Test
    void parkedOrderDeletesEvenWhenApproved() {
        LitemallOrderAggregate order = order(LitemallOrderStatus.PAID);
        order.setCjPlacementApprovedTime(LocalDateTime.now());
        order.setCjOrderStatus("PLACEMENT_REJECTED");
        when(orderRepository.softDeleteIfStatus(ID, LitemallOrderStatus.PAID)).thenReturn(1);
        assertEquals(Status.DELETED, service("manual").delete(7, SN, "1").status());
    }

    @Test
    void orderAlreadyAtCjIsRefused() {
        order(LitemallOrderStatus.PAID).setCjOrderId("CJ123");
        Result result = service("manual").delete(7, SN, "1");
        assertEquals(Status.PLACED_AT_CJ, result.status());
        assertTrue(result.message().contains("CJ123"));
        assertNothingWritten();
    }

    @Test
    void missingAndAlreadyDeletedOrdersAreTyped() {
        when(orderRepository.findById(ID)).thenReturn(Optional.empty());
        assertEquals(Status.NOT_FOUND, service("manual").delete(7, null, "1").status());

        order(LitemallOrderStatus.CANCELED).setDeleted(true);
        assertEquals(Status.ALREADY_DELETED, service("manual").delete(7, null, "1").status());
        assertNothingWritten();
    }

    @Test
    void lostRaceWritesNoHop() {
        order(LitemallOrderStatus.CANCELED);
        when(orderRepository.softDeleteIfStatus(ID, LitemallOrderStatus.CANCELED)).thenReturn(0);
        assertEquals(Status.LOST_RACE, service("manual").delete(7, null, "1").status());
        verify(historyRepository, never()).record(any());
    }
}
