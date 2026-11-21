package org.linlinjava.litemall.order.application.internal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.core.system.SystemConfig;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * F14 of plan-order-lifecycle-e2e.md: the admin-editable system setting decides the
 * auto-confirm window; the yml value is only the fallback.
 */
class AutoConfirmOrderSchedulerTest {

    private final LitemallOrderRepository orderRepository = mock(LitemallOrderRepository.class);
    private final LitemallOrderServiceImpl orderService = mock(LitemallOrderServiceImpl.class);

    @AfterEach
    void clearSystemConfig() {
        SystemConfig.setConfigs(new HashMap<>());
    }

    private AutoConfirmOrderScheduler scheduler(int fallbackDays) {
        AutoConfirmOrderScheduler s = new AutoConfirmOrderScheduler(orderRepository, orderService);
        ReflectionTestUtils.setField(s, "autoConfirmDaysFallback", fallbackDays);
        return s;
    }

    @Test
    void systemSettingWins_whenSet() {
        Map<String, String> configs = new HashMap<>();
        configs.put("litemall_order_unconfirm", "7");
        SystemConfig.setConfigs(configs);

        assertThat(scheduler(15).autoConfirmDays()).isEqualTo(7);
    }

    @Test
    void fallbackApplies_whenTheSettingIsAbsent() {
        SystemConfig.setConfigs(new HashMap<>());

        assertThat(scheduler(15).autoConfirmDays()).isEqualTo(15);
    }

    @Test
    void sweep_usesTheEffectiveWindow_andConfirmsEachDueOrderInItsOwnCall() {
        Map<String, String> configs = new HashMap<>();
        configs.put("litemall_order_unconfirm", "7");
        SystemConfig.setConfigs(configs);
        LitemallOrderAggregate a = new LitemallOrderAggregate();
        a.setOrderId(new LitemallOrderId(1));
        LitemallOrderAggregate b = new LitemallOrderAggregate();
        b.setOrderId(new LitemallOrderId(2));
        when(orderRepository.queryUnconfirm(7)).thenReturn(List.of(a, b));
        doThrow(new RuntimeException("race")).when(orderService).autoConfirmOrder(new LitemallOrderId(1));

        scheduler(15).sweep();

        verify(orderRepository).queryUnconfirm(7);
        verify(orderService).autoConfirmOrder(new LitemallOrderId(2)); // the first failure did not stop the sweep
    }

    // ---- CJ orders close on the carrier's word (2026-11-21) ----

    private final org.linlinjava.litemall.order.infrastructure.services.acl.facades.CjTrackingFacade tracking =
            mock(org.linlinjava.litemall.order.infrastructure.services.acl.facades.CjTrackingFacade.class);

    private AutoConfirmOrderScheduler cjScheduler(int capDays) {
        Map<String, String> configs = new HashMap<>();
        configs.put("litemall_order_unconfirm", "7");
        SystemConfig.setConfigs(configs);
        AutoConfirmOrderScheduler s = new AutoConfirmOrderScheduler(orderRepository, orderService, tracking);
        ReflectionTestUtils.setField(s, "autoConfirmDaysFallback", 15);
        ReflectionTestUtils.setField(s, "cjMaxDays", capDays);
        return s;
    }

    private LitemallOrderAggregate cjOrder(int id, String shipSn, int shippedDaysAgo) {
        LitemallOrderAggregate o = new LitemallOrderAggregate();
        o.setOrderId(new LitemallOrderId(id));
        o.setSource(LitemallOrderAggregate.SOURCE_CJ);
        o.setShipSn(shipSn);
        o.setShipTime(java.time.LocalDateTime.now().minusDays(shippedDaysAgo));
        return o;
    }

    private static org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.CjTrackingSnapshot status(
            String trackingStatus) {
        return new org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.CjTrackingSnapshot(
                "T", "CJPacket", "CN", "SE", "8", null, trackingStatus, null, null);
    }

    @Test
    void cjOrderStillInTransit_staysShipped_pastTheWindow() {
        // The prod order 11 case: shipped 8 days ago, parcel at a courier facility.
        when(orderRepository.queryUnconfirm(7)).thenReturn(List.of(cjOrder(11, "EQK1", 8)));
        when(tracking.trackInfo("EQK1")).thenReturn(java.util.Optional.of(status("Arrived Courier Facility")));

        cjScheduler(45).sweep();

        verify(orderService, org.mockito.Mockito.never()).autoConfirmOrder(new LitemallOrderId(11));
    }

    @Test
    void cjOrderReportedDelivered_isConfirmed() {
        when(orderRepository.queryUnconfirm(7)).thenReturn(List.of(cjOrder(12, "EQK2", 9)));
        when(tracking.trackInfo("EQK2")).thenReturn(java.util.Optional.of(status("Delivered")));

        cjScheduler(45).sweep();

        verify(orderService).autoConfirmOrder(new LitemallOrderId(12));
    }

    @Test
    void cjUnreachableOrNoTrackingNumber_waits_thenClosesAtTheCap() {
        when(orderRepository.queryUnconfirm(7)).thenReturn(List.of(
                cjOrder(13, "EQK3", 20), cjOrder(14, null, 20), cjOrder(15, "EQK5", 46)));
        when(tracking.trackInfo(org.mockito.ArgumentMatchers.anyString())).thenReturn(java.util.Optional.empty());

        cjScheduler(45).sweep();

        verify(orderService, org.mockito.Mockito.never()).autoConfirmOrder(new LitemallOrderId(13));
        verify(orderService, org.mockito.Mockito.never()).autoConfirmOrder(new LitemallOrderId(14));
        verify(orderService).autoConfirmOrder(new LitemallOrderId(15)); // past the 45-day cap
    }

    @Test
    void capAtOrBelowTheWindow_turnsTheDeliveryCheckOff() {
        when(orderRepository.queryUnconfirm(7)).thenReturn(List.of(cjOrder(16, "EQK6", 8)));

        cjScheduler(0).sweep();

        verify(orderService).autoConfirmOrder(new LitemallOrderId(16));
        verify(tracking, org.mockito.Mockito.never()).trackInfo(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void localOrder_closesOnTheWindow_andNeverAsksCj() {
        LitemallOrderAggregate local = cjOrder(17, "LOCAL1", 8);
        local.setSource(LitemallOrderAggregate.SOURCE_LOCAL);
        when(orderRepository.queryUnconfirm(7)).thenReturn(List.of(local));

        cjScheduler(45).sweep();

        verify(orderService).autoConfirmOrder(new LitemallOrderId(17));
        verify(tracking, org.mockito.Mockito.never()).trackInfo(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deliveredMeansTheStatusStartsWithDelivered_nothingLooser() {
        assertThat(status("Delivered").isDelivered()).isTrue();
        assertThat(status("delivered to recipient").isDelivered()).isTrue();
        assertThat(status("Out for delivery").isDelivered()).isFalse();
        assertThat(status("Undelivered").isDelivered()).isFalse();
        assertThat(status("Delivery attempted").isDelivered()).isFalse();
        assertThat(status(null).isDelivered()).isFalse();
        var byHop = status("Arrived Courier Facility");
        byHop.setHops(List.of(new org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj
                .CjTrackingSnapshot.Hop("2026-11-22 10:00:00", "MALMO", "Delivered")));
        assertThat(byHop.isDelivered()).isTrue();
    }
}
