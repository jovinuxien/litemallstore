package org.linlinjava.litemall.order.application.internal.cj;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.infrastructure.services.cj.CjTokenService;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link CjPlacementSweepScheduler}: skips entirely (no DB read, no CJ traffic) while the CJ
 * ACL is disabled — retention IS the disabled behavior — and, when enabled, hands each due
 * order to {@link CjPlacementService#place} with reconcile-first on, one order's failure
 * never poisoning the rest of the batch.
 */
@ExtendWith(MockitoExtension.class)
class CjPlacementSweepSchedulerTest {

    @Mock
    private LitemallOrderRepository orderRepository;
    @Mock
    private CjPlacementService placementService;
    @Mock
    private CjTokenService cjTokenService;

    private CjPlacementSweepScheduler scheduler() {
        CjPlacementSweepScheduler s =
                new CjPlacementSweepScheduler(orderRepository, placementService, cjTokenService);
        ReflectionTestUtils.setField(s, "batchSize", 10);
        return s;
    }

    @Test
    void disabledCj_skipsWithoutTouchingDbOrPlacement() {
        when(cjTokenService.isEnabled()).thenReturn(false);

        scheduler().sweep();

        verifyNoMoreInteractions(orderRepository, placementService);
    }

    @Test
    void enabledCj_placesEachDueOrder_reconcileFirst() {
        when(cjTokenService.isEnabled()).thenReturn(true);
        when(orderRepository.queryPlaceableCjOrders(10))
                .thenReturn(List.of(new LitemallOrderId(1), new LitemallOrderId(2)));

        scheduler().sweep();

        verify(placementService).place(eq(new LitemallOrderId(1)), eq(true), eq(false));
        verify(placementService).place(eq(new LitemallOrderId(2)), eq(true), eq(false));
    }

    @Test
    void oneOrderBlowingUp_neverPoisonsTheRestOfTheSweep() {
        when(cjTokenService.isEnabled()).thenReturn(true);
        when(orderRepository.queryPlaceableCjOrders(10))
                .thenReturn(List.of(new LitemallOrderId(1), new LitemallOrderId(2)));
        doThrow(new RuntimeException("boom"))
                .when(placementService).place(eq(new LitemallOrderId(1)), anyBoolean(), anyBoolean());

        scheduler().sweep();

        verify(placementService).place(eq(new LitemallOrderId(2)), eq(true), eq(false));
    }
}
