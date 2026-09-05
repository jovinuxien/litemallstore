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
}
