package org.linlinjava.litemall.order.application.internal.cj;

import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.order.domain.events.LitemallDomainEventPublisher;
import org.linlinjava.litemall.order.domain.events.order.LitemallOrderLocalCarrierAssignedEvent;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderStatusHistoryRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderStatusChange;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.CjTrackingFacade;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.CjTrackingSnapshot;
import org.linlinjava.litemall.order.infrastructure.services.cj.CjTokenService;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * The one-time "your local carrier is X" notice: raised the first time CJ tracking names
 * a last-mile carrier, never twice, and without spending CJ calls on orders already told.
 */
class LocalCarrierNoticeTest {

    private final LitemallOrderRepository orderRepository = mock(LitemallOrderRepository.class);
    private final LitemallOrderStatusHistoryRepository history = mock(LitemallOrderStatusHistoryRepository.class);
    private final LitemallDomainEventPublisher publisher = mock(LitemallDomainEventPublisher.class);
    private final CjTrackingFacade tracking = mock(CjTrackingFacade.class);
    private final CjTokenService token = mock(CjTokenService.class);

    private final LocalCarrierNoticeService service = new LocalCarrierNoticeService(history, publisher);

    private LocalCarrierNoticeScheduler scheduler() {
        LocalCarrierNoticeScheduler s = new LocalCarrierNoticeScheduler(orderRepository, tracking, service, token);
        s.paceMs = 0;
        when(token.isEnabled()).thenReturn(true);
        return s;
    }

    private static LitemallOrderAggregate order(int id, String source, String shipSn) {
        LitemallOrderAggregate o = new LitemallOrderAggregate();
        o.setOrderId(new LitemallOrderId(id));
        o.setOrderSn("SN" + id);
        o.setSource(source);
        o.setShipSn(shipSn);
        o.setOrderStatus(LitemallOrderStatus.SHIPPED);
        return o;
    }

    private static CjTrackingSnapshot snapshot(String carrier, String localNumber) {
        CjTrackingSnapshot s = new CjTrackingSnapshot("EQK1", "equick_Standard", "CN", "SE", "8-15", null,
                "In transit", carrier, localNumber);
        s.setLastMileCarrierPhone("0771-40 60 40");
        return s;
    }

    private static LitemallOrderStatusChange hop(int orderId, String type) {
        return new LitemallOrderStatusChange(new LitemallOrderId(orderId), LitemallOrderStatus.SHIPPED,
                LitemallOrderStatus.SHIPPED, type, "x", "system", LocalDateTime.now());
    }

    @Test
    void carrierNamed_writesTheHop_andRaisesTheEventWithTheCarrierDetails() {
        LitemallOrderAggregate o = order(11, "cj", "EQK1");
        when(history.findByOrderId(any())).thenReturn(List.of(hop(11, "ship")));

        assertThat(service.tellIfAssigned(o, snapshot(" CityMail ", "BCM1"))).isTrue();

        ArgumentCaptor<LitemallOrderStatusChange> hop = ArgumentCaptor.forClass(LitemallOrderStatusChange.class);
        verify(history).record(hop.capture());
        assertThat(hop.getValue().getChangeType()).isEqualTo(LocalCarrierNoticeService.CHANGE_TYPE);
        assertThat(hop.getValue().getChangeMessage()).contains("CityMail").contains("BCM1");
        ArgumentCaptor<LitemallOrderLocalCarrierAssignedEvent> event =
                ArgumentCaptor.forClass(LitemallOrderLocalCarrierAssignedEvent.class);
        verify(publisher).publish(event.capture());
        assertThat(event.getValue().getCarrier()).isEqualTo("CityMail");
        assertThat(event.getValue().getLocalTrackNumber()).isEqualTo("BCM1");
        assertThat(event.getValue().getCarrierPhone()).isEqualTo("0771-40 60 40");
        assertThat(event.getValue().getCarrierEmail()).isEmpty();
    }

    @Test
    void noCarrierYet_orNoTrackingAtAll_raisesNothing() {
        LitemallOrderAggregate o = order(11, "cj", "EQK1");

        assertThat(service.tellIfAssigned(o, snapshot(null, null))).isFalse();
        assertThat(service.tellIfAssigned(o, snapshot("  ", "BCM1"))).isFalse();
        assertThat(service.tellIfAssigned(o, null)).isFalse();

        verify(history, never()).record(any());
        verifyNoMoreInteractions(publisher);
    }

    @Test
    void alreadyTold_isNeverToldAgain() {
        LitemallOrderAggregate o = order(11, "cj", "EQK1");
        when(history.findByOrderId(any())).thenReturn(
                List.of(hop(11, "ship"), hop(11, LocalCarrierNoticeService.CHANGE_TYPE)));

        assertThat(service.tellIfAssigned(o, snapshot("CityMail", "BCM1"))).isFalse();

        verify(history, never()).record(any());
        verifyNoMoreInteractions(publisher);
    }

    @Test
    void sweep_asksCjOnlyAboutCjOrdersNotYetTold() {
        LitemallOrderAggregate told = order(1, "cj", "TOLD");
        LitemallOrderAggregate local = order(2, "local", "LOCAL");
        LitemallOrderAggregate waiting = order(3, "cj", "WAIT");
        LitemallOrderAggregate fresh = order(4, "cj", "FRESH");
        when(orderRepository.queryShippedWithTracking()).thenReturn(List.of(told, local, waiting, fresh));
        when(history.findByOrderId(new LitemallOrderId(1)))
                .thenReturn(List.of(hop(1, LocalCarrierNoticeService.CHANGE_TYPE)));
        when(tracking.trackInfo("WAIT")).thenReturn(Optional.of(snapshot(null, null)));
        when(tracking.trackInfo("FRESH")).thenReturn(Optional.of(snapshot("CityMail", "BCM4")));

        scheduler().sweep();

        verify(tracking, never()).trackInfo("TOLD");
        verify(tracking, never()).trackInfo("LOCAL");
        verify(publisher, times(1)).publish(any());
        verify(history, times(1)).record(any());
    }

    @Test
    void sweep_oneOrdersFailureDoesNotStopTheRest_andCjSilenceIsNotAnError() {
        LitemallOrderAggregate broken = order(1, "cj", "BROKEN");
        LitemallOrderAggregate silent = order(2, "cj", "SILENT");
        LitemallOrderAggregate fine = order(3, "cj", "FINE");
        when(orderRepository.queryShippedWithTracking()).thenReturn(List.of(broken, silent, fine));
        when(tracking.trackInfo("BROKEN")).thenThrow(new IllegalStateException("boom"));
        when(tracking.trackInfo("SILENT")).thenReturn(Optional.empty());
        when(tracking.trackInfo("FINE")).thenReturn(Optional.of(snapshot("CityMail", null)));

        scheduler().sweep();

        verify(publisher, times(1)).publish(any());
    }

    @Test
    void sweep_stopsAskingCjAtTheBatchCap() {
        List<LitemallOrderAggregate> many = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            many.add(order(i, "cj", "T" + i));
        }
        when(orderRepository.queryShippedWithTracking()).thenReturn(many);
        when(tracking.trackInfo(anyString())).thenReturn(Optional.empty());
        LocalCarrierNoticeScheduler s = scheduler();
        ReflectionTestUtils.setField(s, "batchSize", 2);

        s.sweep();

        verify(tracking, times(2)).trackInfo(anyString());
    }

    @Test
    void sweep_doesNothing_whenSwitchedOff_orWhenCjIsNotConfigured() {
        LocalCarrierNoticeScheduler off = scheduler();
        ReflectionTestUtils.setField(off, "enabled", false);
        off.sweep();

        LocalCarrierNoticeScheduler noCj = scheduler();
        when(token.isEnabled()).thenReturn(false);
        noCj.sweep();

        verify(orderRepository, never()).queryShippedWithTracking();
        verify(tracking, never()).trackInfo(anyString());
    }
}
