package org.linlinjava.litemall.order.application.internal.cj;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.linlinjava.litemall.order.application.internal.LitemallOrderServiceImpl;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderStatusHistoryRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderStatusChange;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.CjDropshipOrderFacade;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.CjCallOutcome;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.CjOrderSnapshot;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CjLifecycleService}: each advance() pass records the CJ status hop exactly once
 * (dedup on {@code cj_order_status}), drives at most one CJ mutation (confirm / payBalance,
 * the latter config-gated), maps SHIPPED/DELIVERED into the local guarded transitions with
 * CJ's tracking number, and degrades to a no-op when CJ gives no usable answer.
 */
@ExtendWith(MockitoExtension.class)
class CjLifecycleServiceTest {

    private static final LitemallOrderId ORDER_ID = new LitemallOrderId(61);

    @Mock
    private LitemallOrderRepository orderRepository;
    @Mock
    private LitemallOrderStatusHistoryRepository statusHistoryRepository;
    @Mock
    private LitemallOrderServiceImpl orderServiceImpl;
    @Mock
    private CjDropshipOrderFacade cjOrderFacade;
    @Mock
    private CjFulfilmentIncidentService incidents;

    private CjLifecycleService service(boolean autoPayBalance) {
        return new CjLifecycleService(orderRepository, statusHistoryRepository, orderServiceImpl,
                cjOrderFacade, incidents, autoPayBalance);
    }

    private LitemallOrderAggregate cjOrder(LitemallOrderStatus localStatus, String lastSeenCjStatus) {
        LitemallOrderAggregate order = new LitemallOrderAggregate();
        order.setOrderId(ORDER_ID);
        order.setUserId(new LitemallUserId(42));
        order.setOrderStatus(localStatus);
        order.setSource(LitemallOrderAggregate.SOURCE_CJ);
        order.setCjOrderId("cj-id-1");
        order.setCjOrderStatus(lastSeenCjStatus);
        return order;
    }

    private CjOrderSnapshot snapshot(String cjStatus, String trackNumber, String provider) {
        return new CjOrderSnapshot("cj-id-1", cjStatus, null, trackNumber, provider, "CJPacket Ordinary");
    }

    @Test
    void createdDraft_recordsHopAndConfirms() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(cjOrder(LitemallOrderStatus.PAID, null)));
        when(cjOrderFacade.fetchOrderDetail("cj-id-1")).thenReturn(Optional.of(snapshot("CREATED", null, null)));
        when(cjOrderFacade.confirmOrderOutcome("cj-id-1")).thenReturn(CjCallOutcome.accepted());

        service(true).advance(ORDER_ID);

        verify(cjOrderFacade).confirmOrderOutcome("cj-id-1");
        verify(orderRepository).updateCjOrderStatus(ORDER_ID, "CREATED");
        ArgumentCaptor<LitemallOrderStatusChange> hop = ArgumentCaptor.forClass(LitemallOrderStatusChange.class);
        verify(statusHistoryRepository).record(hop.capture());
        assertEquals(CjLifecycleService.CHANGE_TYPE_CJ_SYNC, hop.getValue().getChangeType());
        assertTrue(hop.getValue().getChangeMessage().contains("CREATED"));
        assertEquals("system", hop.getValue().getOperator());
    }

    @Test
    void unpaid_paysFromBalance_whenAutoPayEnabled() {
        when(orderRepository.findById(ORDER_ID))
                .thenReturn(Optional.of(cjOrder(LitemallOrderStatus.PAID, "CREATED")));
        when(cjOrderFacade.fetchOrderDetail("cj-id-1")).thenReturn(Optional.of(snapshot("UNPAID", null, null)));
        when(cjOrderFacade.payBalanceOutcome("cj-id-1")).thenReturn(CjCallOutcome.accepted());

        service(true).advance(ORDER_ID);

        verify(cjOrderFacade).payBalanceOutcome("cj-id-1");
        verify(orderRepository).updateCjOrderStatus(ORDER_ID, "UNPAID");
    }

    @Test
    void unpaid_waitsForManualPayment_whenAutoPayDisabled() {
        when(orderRepository.findById(ORDER_ID))
                .thenReturn(Optional.of(cjOrder(LitemallOrderStatus.PAID, "CREATED")));
        when(cjOrderFacade.fetchOrderDetail("cj-id-1")).thenReturn(Optional.of(snapshot("UNPAID", null, null)));

        service(false).advance(ORDER_ID);

        verify(cjOrderFacade, never()).payBalanceOutcome(any());
    }

    @Test
    void unchangedCjStatus_recordsNoDuplicateHop() {
        when(orderRepository.findById(ORDER_ID))
                .thenReturn(Optional.of(cjOrder(LitemallOrderStatus.PAID, "UNSHIPPED")));
        when(cjOrderFacade.fetchOrderDetail("cj-id-1"))
                .thenReturn(Optional.of(snapshot("UNSHIPPED", null, null)));

        service(true).advance(ORDER_ID);

        verify(statusHistoryRepository, never()).record(any());
        verify(orderRepository, never()).updateCjOrderStatus(any(), any());
    }

    @Test
    void shippedAtCj_shipsLocallyWithCjTrackingNumber() {
        when(orderRepository.findById(ORDER_ID))
                .thenReturn(Optional.of(cjOrder(LitemallOrderStatus.PAID, "UNSHIPPED")));
        when(cjOrderFacade.fetchOrderDetail("cj-id-1"))
                .thenReturn(Optional.of(snapshot("SHIPPED", "CJPKL123", "YunExpress")));

        service(true).advance(ORDER_ID);

        verify(orderServiceImpl).shipOrder(ORDER_ID, "YunExpress", "CJPKL123", "system");
        verify(orderRepository).updateCjOrderStatus(ORDER_ID, "SHIPPED");
    }

    @Test
    void shippedAtCj_localAlreadyShipped_isIdempotent() {
        when(orderRepository.findById(ORDER_ID))
                .thenReturn(Optional.of(cjOrder(LitemallOrderStatus.SHIPPED, "SHIPPED")));
        when(cjOrderFacade.fetchOrderDetail("cj-id-1"))
                .thenReturn(Optional.of(snapshot("SHIPPED", "CJPKL123", "YunExpress")));

        service(true).advance(ORDER_ID);

        verify(orderServiceImpl, never()).shipOrder(any(), any(), any(), any());
    }

    @Test
    void deliveredAtCj_fromPaid_shipsThenAutoConfirms() {
        when(orderRepository.findById(ORDER_ID))
                .thenReturn(Optional.of(cjOrder(LitemallOrderStatus.PAID, "SHIPPED")));
        when(cjOrderFacade.fetchOrderDetail("cj-id-1"))
                .thenReturn(Optional.of(snapshot("DELIVERED", "CJPKL123", "YunExpress")));

        service(true).advance(ORDER_ID);

        verify(orderServiceImpl).shipOrder(ORDER_ID, "YunExpress", "CJPKL123", "system");
        verify(orderServiceImpl).autoConfirmOrder(ORDER_ID);
    }

    @Test
    void cjUnreachable_isANoop() {
        when(orderRepository.findById(ORDER_ID))
                .thenReturn(Optional.of(cjOrder(LitemallOrderStatus.PAID, "CREATED")));
        when(cjOrderFacade.fetchOrderDetail("cj-id-1")).thenReturn(Optional.empty());

        service(true).advance(ORDER_ID);

        verify(statusHistoryRepository, never()).record(any());
        verify(orderRepository, never()).updateCjOrderStatus(any(), any());
        verify(cjOrderFacade, never()).confirmOrderOutcome(any());
    }

    @Test
    void nonCjOrPlacedlessOrder_isIgnored() {
        LitemallOrderAggregate local = cjOrder(LitemallOrderStatus.PAID, null);
        local.setSource(LitemallOrderAggregate.SOURCE_LOCAL);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(local));

        service(true).advance(ORDER_ID);

        verify(cjOrderFacade, never()).fetchOrderDetail(any());
    }

    // ------------------------------------------------------------------
    // Lifecycle package B: nothing fails silently any more
    // ------------------------------------------------------------------

    @Test
    void unpaid_payBalanceRefused_isReportedAsAnIncident_notSwallowed() {
        when(orderRepository.findById(ORDER_ID))
                .thenReturn(Optional.of(cjOrder(LitemallOrderStatus.PAID, "CREATED")));
        when(cjOrderFacade.fetchOrderDetail("cj-id-1")).thenReturn(Optional.of(snapshot("UNPAID", null, null)));
        when(cjOrderFacade.payBalanceOutcome("cj-id-1")).thenReturn(CjCallOutcome.rejected("1602: insufficient balance"));

        service(true).advance(ORDER_ID);

        verify(incidents).onLifecycleMutationFailure(any(), org.mockito.ArgumentMatchers.eq("payBalance"),
                org.mockito.ArgumentMatchers.eq("1602: insufficient balance"));
    }

    @Test
    void created_confirmRefused_isReportedAsAnIncident() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(cjOrder(LitemallOrderStatus.PAID, null)));
        when(cjOrderFacade.fetchOrderDetail("cj-id-1")).thenReturn(Optional.of(snapshot("CREATED", null, null)));
        when(cjOrderFacade.confirmOrderOutcome("cj-id-1")).thenReturn(CjCallOutcome.rejected("locked"));

        service(true).advance(ORDER_ID);

        verify(incidents).onLifecycleMutationFailure(any(), org.mockito.ArgumentMatchers.eq("confirm"),
                org.mockito.ArgumentMatchers.eq("locked"));
    }

    @Test
    void cancelledAtCj_onTheTransition_raisesTheIncidentOnce() {
        when(orderRepository.findById(ORDER_ID))
                .thenReturn(Optional.of(cjOrder(LitemallOrderStatus.PAID, "UNSHIPPED")));
        when(cjOrderFacade.fetchOrderDetail("cj-id-1")).thenReturn(Optional.of(snapshot("CANCELLED", null, null)));

        service(true).advance(ORDER_ID);

        verify(incidents).onCjCancelledAfterPayment(any());
        verify(orderRepository).updateCjOrderStatus(ORDER_ID, "CANCELLED");
    }

    @Test
    void cancelledAtCj_alreadySeen_doesNotRepeatTheIncident() {
        when(orderRepository.findById(ORDER_ID))
                .thenReturn(Optional.of(cjOrder(LitemallOrderStatus.PAID, "CANCELLED")));
        when(cjOrderFacade.fetchOrderDetail("cj-id-1")).thenReturn(Optional.of(snapshot("CANCELLED", null, null)));

        service(true).advance(ORDER_ID);

        verify(incidents, never()).onCjCancelledAfterPayment(any());
    }

    /** F10: CJ says SHIPPED with no number yet — ship with a NULL number, not "". */
    @Test
    void shippedAtCj_withoutTrackingNumber_shipsWithNullNumber() {
        when(orderRepository.findById(ORDER_ID))
                .thenReturn(Optional.of(cjOrder(LitemallOrderStatus.PAID, "UNSHIPPED")));
        when(cjOrderFacade.fetchOrderDetail("cj-id-1"))
                .thenReturn(Optional.of(snapshot("SHIPPED", "", "YunExpress")));

        service(true).advance(ORDER_ID);

        verify(orderServiceImpl).shipOrder(ORDER_ID, "YunExpress", null, "system");
    }

    /** F11: a refund is under review and CJ ships anyway — the admin must see it on the timeline. */
    @Test
    void shippedAtCj_whileRefundRequested_recordsAWarningHop_andDoesNotShipLocally() {
        when(orderRepository.findById(ORDER_ID))
                .thenReturn(Optional.of(cjOrder(LitemallOrderStatus.REFUND_REQUEST, "UNSHIPPED")));
        when(cjOrderFacade.fetchOrderDetail("cj-id-1"))
                .thenReturn(Optional.of(snapshot("SHIPPED", "CJPKL123", "YunExpress")));

        service(true).advance(ORDER_ID);

        verify(orderServiceImpl, never()).shipOrder(any(), any(), any(), any());
        ArgumentCaptor<LitemallOrderStatusChange> hops = ArgumentCaptor.forClass(LitemallOrderStatusChange.class);
        verify(statusHistoryRepository, org.mockito.Mockito.times(2)).record(hops.capture());
        assertTrue(hops.getAllValues().stream()
                .anyMatch(h -> h.getChangeMessage().contains("while a refund request is open")));
    }
}
