package org.linlinjava.litemall.order.application.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.linlinjava.litemall.order.application.LitemallOrderOrchestratorService;
import org.linlinjava.litemall.order.application.util.exception.payment.LitemallRefundFailedException;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.linlinjava.litemall.order.domain.model.util.LitemallOrderHandleOption;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.order.domain.service.order.LitemallOrderOperationResult;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The Wave-21 GROUP_EXPIRED order policy (USER DECISION 2026-08-08): a PAID order
 * in the failed group is auto-cancelled + refunded through the EXISTING
 * tender-parity refund path; an unpaid order is just system-cancelled; replayed
 * events are idempotent (already-refunded/cancelled orders are skipped); one
 * order's failure never stops the rest of the batch.
 */
@ExtendWith(MockitoExtension.class)
class GroupExpiredOrderProcessorTest {

    @Mock
    private LitemallOrderRepository orderRepository;
    @Mock
    private LitemallOrderServiceImpl orderService;
    @Mock
    private LitemallOrderOrchestratorService orchestratorService;

    private GroupExpiredOrderProcessor processor() {
        return new GroupExpiredOrderProcessor(orderRepository, orderService, orchestratorService);
    }

    private static LitemallOrderAggregate order(int id, LitemallOrderStatus status) {
        LitemallOrderAggregate order = new LitemallOrderAggregate();
        order.setOrderId(new LitemallOrderId(id));
        order.setUserId(new LitemallUserId(99));
        order.setOrderStatus(status);
        return order;
    }

    private static LitemallOrderOperationResult refundOk(int orderId) {
        return LitemallOrderOperationResult.refundSuccess(
                new LitemallOrderId(orderId), LitemallOrderStatus.PAID,
                LitemallOrderHandleOption.forStatus(LitemallOrderStatus.REFUNDED));
    }

    @Test
    void paidOrder_isRefundedThroughTheExistingRefundPath() {
        when(orderRepository.findByPinkId(1)).thenReturn(List.of(order(10, LitemallOrderStatus.PAID)));
        when(orchestratorService.autoRefundForExpiredGroup(eq(new LitemallOrderId(10)), anyString()))
                .thenReturn(refundOk(10));

        processor().processExpiredGroup(List.of(1));

        verify(orchestratorService).autoRefundForExpiredGroup(
                eq(new LitemallOrderId(10)), eq(GroupExpiredOrderProcessor.REASON));
        verify(orderService, never()).autoCancelOrder(any(), anyString());
    }

    @Test
    void unpaidOrder_isJustCancelled_noRefund() {
        when(orderRepository.findByPinkId(2)).thenReturn(List.of(order(11, LitemallOrderStatus.CREATED)));

        processor().processExpiredGroup(List.of(2));

        verify(orderService).autoCancelOrder(
                eq(new LitemallOrderId(11)), eq(GroupExpiredOrderProcessor.REASON));
        verify(orchestratorService, never()).autoRefundForExpiredGroup(any(), anyString());
    }

    @Test
    void alreadyRefundedOrCancelled_isIdempotentSkip() {
        when(orderRepository.findByPinkId(3)).thenReturn(List.of(
                order(12, LitemallOrderStatus.REFUNDED),
                order(13, LitemallOrderStatus.SYSTEM_CANCELED)));

        processor().processExpiredGroup(List.of(3));

        verify(orchestratorService, never()).autoRefundForExpiredGroup(any(), anyString());
        verify(orderService, never()).autoCancelOrder(any(), anyString());
    }

    @Test
    void refundRequestOrder_resumesSettlement() {
        // An earlier half-run (or a customer-opened request on the doomed group)
        // resumes at the settlement step.
        when(orderRepository.findByPinkId(4)).thenReturn(
                List.of(order(14, LitemallOrderStatus.REFUND_REQUEST)));
        when(orchestratorService.autoRefundForExpiredGroup(eq(new LitemallOrderId(14)), anyString()))
                .thenReturn(refundOk(14));

        processor().processExpiredGroup(List.of(4));

        verify(orchestratorService).autoRefundForExpiredGroup(
                eq(new LitemallOrderId(14)), anyString());
    }

    @Test
    void oneFailingRefund_neverStopsTheRestOfTheBatch() {
        when(orderRepository.findByPinkId(5)).thenReturn(List.of(order(15, LitemallOrderStatus.PAID)));
        when(orderRepository.findByPinkId(6)).thenReturn(List.of(order(16, LitemallOrderStatus.PAID)));
        when(orchestratorService.autoRefundForExpiredGroup(eq(new LitemallOrderId(15)), anyString()))
                .thenThrow(new LitemallRefundFailedException("PSP said no"));
        when(orchestratorService.autoRefundForExpiredGroup(eq(new LitemallOrderId(16)), anyString()))
                .thenReturn(refundOk(16));

        assertDoesNotThrow(() -> processor().processExpiredGroup(List.of(5, 6)));

        verify(orchestratorService).autoRefundForExpiredGroup(eq(new LitemallOrderId(16)), anyString());
    }

    @Test
    void slotWithoutLocalOrder_isANoOp() {
        when(orderRepository.findByPinkId(7)).thenReturn(List.of());

        assertDoesNotThrow(() -> processor().processExpiredGroup(List.of(7)));

        verify(orchestratorService, never()).autoRefundForExpiredGroup(any(), anyString());
        verify(orderService, never()).autoCancelOrder(any(), anyString());
    }
}
