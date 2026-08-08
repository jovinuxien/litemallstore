package org.linlinjava.litemall.order.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.linlinjava.litemall.order.application.internal.LitemallGrouponServiceLayer;
import org.linlinjava.litemall.order.application.internal.LitemallOrderServiceImpl;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallBillAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.commands.wallet.LitemallWalletCreditCommand;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.order.domain.service.order.LitemallOrderOperationResult;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link LitemallOrderOrchestratorService#autoRefundForExpiredGroup}: the Wave-21
 * GROUP_EXPIRED auto-refund rides the EXISTING refund transitions and
 * tender-parity settlement — a PAID wallet order is credited exactly its recorded
 * debit; an already-REFUNDED order is a confirmed idempotent no-op (no second
 * credit); a CREATED order is refused (the listener cancels those instead).
 */
@ExtendWith(MockitoExtension.class)
class LitemallOrderGroupExpiredRefundTest {

    @Mock
    private LitemallOrderServiceImpl orderServiceImpl;
    @Mock
    private LitemallOrderRepository orderRepository;
    @Mock
    private LitemallGrouponServiceLayer grouponServiceLayer;
    @Mock
    private LitemallIWalletService walletService;
    @Mock
    private org.linlinjava.litemall.order.application.internal.cj.CjFulfillmentService cjFulfillmentService;

    @InjectMocks
    private LitemallOrderOrchestratorService orchestrator;

    @BeforeEach
    void wireFieldInjectedDeps() {
        ReflectionTestUtils.setField(orchestrator, "walletService", walletService);
        ReflectionTestUtils.setField(orchestrator, "cjFulfillmentService", cjFulfillmentService);
    }

    private LitemallOrderAggregate order(int id, LitemallOrderStatus status, String payId, String actualPrice) {
        LitemallOrderAggregate order = new LitemallOrderAggregate();
        order.setOrderId(new LitemallOrderId(id));
        order.setUserId(new LitemallUserId(42));
        order.setOrderSn("SN" + id);
        order.setOrderStatus(status);
        order.setActualPrice(new LitemallMoney(new BigDecimal(actualPrice)));
        order.setPayId(payId);
        order.setPinkId(400);
        return order;
    }

    private LitemallBillAggregate paymentDebit(String amount) {
        LitemallBillAggregate bill = new LitemallBillAggregate();
        bill.setAmount(new LitemallMoney(new BigDecimal(amount)));
        return bill;
    }

    @Test
    void paidWalletOrder_requestsRefund_thenCreditsExactlyTheRecordedDebit() {
        LitemallOrderId orderId = new LitemallOrderId(60);
        // First read: still PAID (the guard + request step); after requestRefund the
        // same-transaction re-read sees REFUND_REQUEST (as the row UPDATE does live).
        when(orderRepository.findById(orderId)).thenReturn(
                Optional.of(order(60, LitemallOrderStatus.PAID, "WALLET", "35.00")),
                Optional.of(order(60, LitemallOrderStatus.REFUND_REQUEST, "WALLET", "35.00")));
        when(walletService.findOrderPaymentDebit(42, "60")).thenReturn(Optional.of(paymentDebit("35.00")));
        when(walletService.hasOrderRefundCredit(42, "60")).thenReturn(false);

        LitemallOrderOperationResult result = orchestrator.autoRefundForExpiredGroup(
                orderId, "Group-buy did not fill before it expired — automatic cancellation");

        assertTrue(result.isSuccess());
        verify(orderServiceImpl).requestRefund(eq(orderId), anyString());
        ArgumentCaptor<LitemallWalletCreditCommand> credit =
                ArgumentCaptor.forClass(LitemallWalletCreditCommand.class);
        verify(walletService).credit(credit.capture());
        assertEquals(0, new BigDecimal("35.00").compareTo(credit.getValue().getAmount()));
        ArgumentCaptor<LitemallMoney> refunded = ArgumentCaptor.forClass(LitemallMoney.class);
        verify(orderServiceImpl).refundOrder(eq(orderId), refunded.capture());
        assertEquals(0, new BigDecimal("35.00").compareTo(refunded.getValue().getAmount()));
    }

    @Test
    void alreadyRefundedOrder_isConfirmedNoOp_withoutSecondCredit() {
        LitemallOrderId orderId = new LitemallOrderId(61);
        when(orderRepository.findById(orderId)).thenReturn(
                Optional.of(order(61, LitemallOrderStatus.REFUNDED, "WALLET", "35.00")));

        LitemallOrderOperationResult result = orchestrator.autoRefundForExpiredGroup(orderId, "reason");

        assertTrue(result.isSuccess());
        verify(orderServiceImpl, never()).requestRefund(any(), anyString());
        verify(orderServiceImpl, never()).refundOrder(any(), any());
        verify(walletService, never()).credit(any());
    }

    @Test
    void createdOrder_isRefused_theListenerCancelsThoseInstead() {
        LitemallOrderId orderId = new LitemallOrderId(62);
        when(orderRepository.findById(orderId)).thenReturn(
                Optional.of(order(62, LitemallOrderStatus.CREATED, null, "35.00")));

        LitemallOrderOperationResult result = orchestrator.autoRefundForExpiredGroup(orderId, "reason");

        assertFalse(result.isSuccess());
        verify(orderServiceImpl, never()).requestRefund(any(), anyString());
        verify(walletService, never()).credit(any());
    }
}
