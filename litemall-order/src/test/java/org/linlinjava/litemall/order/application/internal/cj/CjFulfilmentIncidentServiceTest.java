package org.linlinjava.litemall.order.application.internal.cj;

import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.order.domain.events.LitemallDomainEventPublisher;
import org.linlinjava.litemall.order.domain.events.cj.LitemallCjFulfilmentCancelledEvent;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderStatusHistoryRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderStatusChange;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Package B of plan-order-lifecycle-e2e.md: what used to be a WARN line becomes a
 * timeline hop, an ops mail, a park, or a customer mail — with the state kept on the
 * order's own timeline so nothing needs a schema change.
 */
class CjFulfilmentIncidentServiceTest {

    private static final LitemallOrderId ORDER = new LitemallOrderId(90);

    private final LitemallOrderRepository orderRepository = mock(LitemallOrderRepository.class);
    private final LitemallOrderStatusHistoryRepository history = mock(LitemallOrderStatusHistoryRepository.class);
    private final LitemallDomainEventPublisher publisher = mock(LitemallDomainEventPublisher.class);
    private final CjOpsNotifier ops = mock(CjOpsNotifier.class);

    private final CjFulfilmentIncidentService service =
            new CjFulfilmentIncidentService(orderRepository, history, publisher, ops, 60, 24, 24);

    private static LitemallOrderAggregate paidCjOrder() {
        LitemallOrderAggregate order = new LitemallOrderAggregate();
        order.setOrderId(ORDER);
        order.setOrderSn("20260905000090");
        order.setOrderStatus(LitemallOrderStatus.PAID);
        order.setSource(LitemallOrderAggregate.SOURCE_CJ);
        order.setCjOrderId("cj-90");
        return order;
    }

    private static LitemallOrderStatusChange hop(String type, String message, LocalDateTime at) {
        return new LitemallOrderStatusChange(ORDER, LitemallOrderStatus.PAID, LitemallOrderStatus.PAID,
                type, message, "system", at);
    }

    private static LitemallOrderStatusChange deferred(LocalDateTime at) {
        return hop(CjPlacementService.CHANGE_TYPE_CJ_PLACEMENT, CjFulfilmentIncidentService.MSG_DEFERRED, at);
    }

    // ------------------------------------------------------------------
    // Retryable placement failures (F8)
    // ------------------------------------------------------------------

    @Test
    void firstFailure_writesTheDeferredHop_noOps() {
        when(history.findByOrderId(ORDER)).thenReturn(List.of());

        var verdict = service.onRetryablePlacementFailure(paidCjOrder(), "CJ 503");

        assertThat(verdict).isEqualTo(CjFulfilmentIncidentService.StallVerdict.RECORDED_FIRST_FAILURE);
        ArgumentCaptor<LitemallOrderStatusChange> hop = ArgumentCaptor.forClass(LitemallOrderStatusChange.class);
        verify(history).record(hop.capture());
        assertThat(hop.getValue().getChangeMessage()).isEqualTo(CjFulfilmentIncidentService.MSG_DEFERRED);
        verifyNoMoreInteractions(ops);
        verify(orderRepository, never()).updateCjOrderStatus(any(), anyString());
    }

    @Test
    void failingForMinutes_justRetries_noSecondHop() {
        when(history.findByOrderId(ORDER)).thenReturn(List.of(deferred(LocalDateTime.now().minusMinutes(10))));

        var verdict = service.onRetryablePlacementFailure(paidCjOrder(), "CJ 503");

        assertThat(verdict).isEqualTo(CjFulfilmentIncidentService.StallVerdict.RETRYING);
        verify(history, never()).record(any());
        verifyNoMoreInteractions(ops);
    }

    @Test
    void failingForOverAnHour_warnsOpsOnce() {
        when(history.findByOrderId(ORDER)).thenReturn(List.of(deferred(LocalDateTime.now().minusMinutes(90))));

        var verdict = service.onRetryablePlacementFailure(paidCjOrder(), "CJ auth failed");

        assertThat(verdict).isEqualTo(CjFulfilmentIncidentService.StallVerdict.WARNED);
        ArgumentCaptor<LitemallOrderStatusChange> hop = ArgumentCaptor.forClass(LitemallOrderStatusChange.class);
        verify(history).record(hop.capture());
        assertThat(hop.getValue().getChangeType()).isEqualTo(CjFulfilmentIncidentService.CHANGE_TYPE_CJ_STALL);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(ops).notify(anyString(), body.capture());
        assertThat(body.getValue()).contains("CJ auth failed").contains("parked after 24 h");
        verify(orderRepository, never()).updateCjOrderStatus(any(), anyString());
    }

    @Test
    void alreadyWarned_doesNotWarnAgain() {
        LocalDateTime since = LocalDateTime.now().minusMinutes(150);
        when(history.findByOrderId(ORDER)).thenReturn(List.of(
                deferred(since),
                hop(CjFulfilmentIncidentService.CHANGE_TYPE_CJ_STALL,
                        CjFulfilmentIncidentService.MSG_WARNING_PREFIX + since + " — ops notified",
                        since.plusMinutes(61))));

        var verdict = service.onRetryablePlacementFailure(paidCjOrder(), "CJ 503");

        assertThat(verdict).isEqualTo(CjFulfilmentIncidentService.StallVerdict.RETRYING);
        verify(history, never()).record(any());
        verifyNoMoreInteractions(ops);
    }

    @Test
    void failingForADay_parksTheOrderUnderTheStalledSentinel_andTellsOps() {
        when(history.findByOrderId(ORDER)).thenReturn(List.of(deferred(LocalDateTime.now().minusHours(25))));

        var verdict = service.onRetryablePlacementFailure(paidCjOrder(), "CJ auth failed");

        assertThat(verdict).isEqualTo(CjFulfilmentIncidentService.StallVerdict.PARKED);
        verify(orderRepository).updateCjOrderStatus(ORDER, CjFulfilmentIncidentService.STATUS_PLACEMENT_STALLED);
        ArgumentCaptor<LitemallOrderStatusChange> hop = ArgumentCaptor.forClass(LitemallOrderStatusChange.class);
        verify(history).record(hop.capture());
        assertThat(hop.getValue().getChangeType()).isEqualTo(CjPlacementService.CHANGE_TYPE_CJ_PLACEMENT_FAILED);
        assertThat(hop.getValue().getChangeMessage()).contains("parked").contains("CJ auth failed");
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(ops).notify(anyString(), body.capture());
        assertThat(body.getValue()).contains("PARKED").contains("Requeue").doesNotContain("UPDATE litemall_order");
    }

    /** A human requeued it: the clock restarts from the NEXT failure, not the old one. */
    @Test
    void requeue_resetsTheStallClock() {
        LocalDateTime longAgo = LocalDateTime.now().minusDays(3);
        when(history.findByOrderId(ORDER)).thenReturn(List.of(
                deferred(longAgo),
                hop(CjPlacementService.CHANGE_TYPE_CJ_PLACEMENT,
                        CjFulfilmentIncidentService.MSG_REQUEUED_PREFIX + " by admin 1", LocalDateTime.now().minusMinutes(1))));

        var verdict = service.onRetryablePlacementFailure(paidCjOrder(), "CJ 503");

        assertThat(verdict).isEqualTo(CjFulfilmentIncidentService.StallVerdict.RECORDED_FIRST_FAILURE);
        verify(orderRepository, never()).updateCjOrderStatus(any(), anyString());
    }

    // ------------------------------------------------------------------
    // Placed, CJ refuses to move it (F5)
    // ------------------------------------------------------------------

    @Test
    void lifecycleMutationFailure_writesHopAndOpsMail_thenStaysQuietForADay() {
        when(history.findByOrderId(ORDER)).thenReturn(List.of());

        service.onLifecycleMutationFailure(paidCjOrder(), "payBalance", "insufficient balance");

        ArgumentCaptor<LitemallOrderStatusChange> hop = ArgumentCaptor.forClass(LitemallOrderStatusChange.class);
        verify(history).record(hop.capture());
        assertThat(hop.getValue().getChangeType()).isEqualTo(CjFulfilmentIncidentService.CHANGE_TYPE_CJ_STALL);
        assertThat(hop.getValue().getChangeMessage()).startsWith("CJ payBalance failed: insufficient balance");
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(ops).notify(anyString(), body.capture());
        assertThat(body.getValue()).contains("insufficient balance").contains("top it up");

        // Same failure ten minutes later: nothing new.
        when(history.findByOrderId(ORDER)).thenReturn(List.of(hop.getValue()));
        service.onLifecycleMutationFailure(paidCjOrder(), "payBalance", "insufficient balance");
        verify(history, times(1)).record(any());
        verify(ops, times(1)).notify(anyString(), anyString());
    }

    @Test
    void lifecycleMutationFailure_forADifferentOperation_isReportedSeparately() {
        when(history.findByOrderId(ORDER)).thenReturn(List.of(
                hop(CjFulfilmentIncidentService.CHANGE_TYPE_CJ_STALL,
                        "CJ payBalance failed: insufficient balance — retrying automatically",
                        LocalDateTime.now().minusMinutes(5))));

        service.onLifecycleMutationFailure(paidCjOrder(), "confirm", "order locked");

        verify(history).record(any());
        verify(ops).notify(eq("CJ confirm failing — order 20260905000090"), anyString());
    }

    // ------------------------------------------------------------------
    // CJ cancelled after payment (F6 / D2)
    // ------------------------------------------------------------------

    @Test
    void cjCancelled_tellsOps_andPublishesTheCustomerEvent_movesNoMoney() {
        service.onCjCancelledAfterPayment(paidCjOrder());

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(ops).notify(anyString(), body.capture());
        assertThat(body.getValue()).contains("NOT touched").contains("customer has been emailed");
        ArgumentCaptor<org.linlinjava.litemall.core.events.LitemallDomainEvent> event =
                ArgumentCaptor.forClass(org.linlinjava.litemall.core.events.LitemallDomainEvent.class);
        verify(publisher).publish(event.capture());
        assertThat(event.getValue()).isInstanceOf(LitemallCjFulfilmentCancelledEvent.class);
        assertThat(((LitemallCjFulfilmentCancelledEvent) event.getValue()).getCjOrderId()).isEqualTo("cj-90");
        verifyNoMoreInteractions(orderRepository);
    }
}
