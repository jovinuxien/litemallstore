package org.linlinjava.litemall.order.application.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.linlinjava.litemall.order.application.LitemallOrderOrchestratorService;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.commands.LitemallOrderCancelCommand;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallAfterSaleStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The root cause of the 2026-08-27 sweep loop: cancelling an order left its
 * unpaid-timeout task row behind. Only the two PAY paths retired the task, so a
 * cancelled order was swept — and rejected — every 60s for as long as the service
 * ran. The cancel path now retires it too.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UnpaidTaskCancelledOnOrderCancelTest {

    @Mock
    private LitemallOrderServiceImpl orderServiceImpl;
    @Mock
    private LitemallOrderRepository orderRepository;
    @Mock
    private UnpaidOrderTaskScheduler unpaidOrderTaskScheduler;

    @InjectMocks
    private LitemallOrderOrchestratorService orchestrator;

    private LitemallOrderAggregate order(LitemallOrderStatus status) {
        LitemallOrderAggregate order = new LitemallOrderAggregate();
        order.setOrderId(new LitemallOrderId(8));
        order.setUserId(new LitemallUserId(99));
        order.setOrderSn("20260723691950");
        order.setOrderStatus(status);
        order.setAfterSaleStatus(LitemallAfterSaleStatus.STATUS_INIT);
        order.setActualPrice(new LitemallMoney(new BigDecimal("12.34")));
        return order;
    }

    private void wire(LitemallOrderAggregate order) {
        ReflectionTestUtils.setField(orchestrator, "unpaidOrderTaskScheduler", unpaidOrderTaskScheduler);
        when(orderServiceImpl.getOrderAggregate(any())).thenReturn(Optional.of(order));
    }

    private LitemallOrderCancelCommand cancelCommand() {
        return new LitemallOrderCancelCommand(new LitemallOrderId(8), new LitemallUserId(99), "changed my mind");
    }

    @Test
    void cancellingAnUnpaidOrder_retiresItsUnpaidTimeoutTask() {
        wire(order(LitemallOrderStatus.CREATED));

        orchestrator.cancelOrder(cancelCommand());

        verify(unpaidOrderTaskScheduler).cancel(argThat(id -> id.getId().equals(8)));
    }

    @Test
    void aPaidOrderIsNotCancellableHere_soNoTaskIsTouched() {
        // Cancel is REFUSED for PAID: LitemallOrderHandleOption grants a paid order
        // refund, not cancel. Nothing needs retiring anyway — the pay path already
        // did it. (Note LitemallOrderStatusQuery.canBeCanceled claims CREATED||PAID;
        // the dispatcher does not use it, so the two disagree on paper only.)
        wire(order(LitemallOrderStatus.PAID));

        orchestrator.cancelOrder(cancelCommand());

        verify(unpaidOrderTaskScheduler, never()).cancel(any());
        verify(orderServiceImpl, never()).cancelOrder(any(), any());
    }

    @Test
    void aRefusedCancel_touchesNoTask() {
        // SHIPPED cannot be cancelled: the handler returns invalid-state before any
        // cleanup runs, so nothing may be retired.
        wire(order(LitemallOrderStatus.SHIPPED));

        orchestrator.cancelOrder(cancelCommand());

        verify(unpaidOrderTaskScheduler, never()).cancel(any());
        verify(orderServiceImpl, never()).cancelOrder(any(), any());
    }
}
