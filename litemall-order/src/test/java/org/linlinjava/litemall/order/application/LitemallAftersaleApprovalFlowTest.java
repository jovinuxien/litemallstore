package org.linlinjava.litemall.order.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.linlinjava.litemall.order.application.internal.LitemallGrouponServiceLayer;
import org.linlinjava.litemall.order.application.internal.LitemallOrderServiceImpl;
import org.linlinjava.litemall.order.application.util.exception.order.LitemallAftersaleException;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallAftersaleAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallBillAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.commands.wallet.LitemallWalletCreditCommand;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallAftersaleRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderStatusHistoryRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallAfterSaleStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.order.domain.service.order.LitemallOrderOperationResult;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Aftersale admin decisions on the orchestrator: approve drives the order through
 * the existing refund transitions and settles to the paying tender capped at
 * min(requested, paid, captured); reject leaves the order's status and money
 * untouched; a decided application cannot be decided again.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LitemallAftersaleApprovalFlowTest {

    private static final LitemallOrderId ORDER_ID = new LitemallOrderId(77);
    private static final LitemallUserId BUYER = new LitemallUserId(42);

    @Mock
    private LitemallOrderServiceImpl orderServiceImpl;
    @Mock
    private LitemallOrderRepository orderRepository;
    @Mock
    private LitemallGrouponServiceLayer grouponServiceLayer;
    @Mock
    private LitemallAftersaleRepository aftersaleRepository;
    @Mock
    private LitemallOrderStatusHistoryRepository statusHistoryRepository;
    @Mock
    private LitemallIWalletService walletService;

    private LitemallOrderOrchestratorService orchestrator;

    private LitemallOrderAggregate order;
    private LitemallAftersaleAggregate aftersale;

    @BeforeEach
    void wire() {
        orchestrator = new LitemallOrderOrchestratorService(
                orderServiceImpl, orderRepository, grouponServiceLayer);
        ReflectionTestUtils.setField(orchestrator, "aftersaleRepository", aftersaleRepository);
        ReflectionTestUtils.setField(orchestrator, "statusHistoryRepository", statusHistoryRepository);
        ReflectionTestUtils.setField(orchestrator, "walletService", walletService);
        // approveAftersale runs the Wave-3 CJ-side delete hook (no-op for local orders)
        // and the Wave-5 brokerage clawback — both field-injected.
        ReflectionTestUtils.setField(orchestrator, "cjFulfillmentService",
                org.mockito.Mockito.mock(org.linlinjava.litemall.order.application.internal.cj.CjFulfillmentService.class));
        ReflectionTestUtils.setField(orchestrator, "brokerageService",
                org.mockito.Mockito.mock(org.linlinjava.litemall.order.application.internal.BrokerageService.class));

        order = new LitemallOrderAggregate();
        order.setOrderId(ORDER_ID);
        order.setUserId(BUYER);
        order.setOrderSn("SN-77");
        order.setOrderStatus(LitemallOrderStatus.DELIVERED);
        order.setActualPrice(new LitemallMoney(new BigDecimal("85")));
        order.setPayId("WALLET");

        aftersale = LitemallAftersaleAggregate.apply(order, BUYER,
                (short) 2, "wrong size", new BigDecimal("50"), null, null);
        aftersale.setId(5);
        aftersale.setAftersaleSn("SN-77-A1");

        when(aftersaleRepository.findById(5)).thenReturn(Optional.of(aftersale));
        when(orderRepository.findById(any())).thenReturn(Optional.of(order));
    }

    @Test
    void approve_walletTender_refundsMinOfRequestedPaidCaptured() {
        // Captured at pay time: 85. Requested: 50 → wallet credit must be 50.
        LitemallBillAggregate debit = mock(LitemallBillAggregate.class);
        when(debit.getAmount()).thenReturn(new LitemallMoney(new BigDecimal("85")));
        when(walletService.findOrderPaymentDebit(eq(42), anyString())).thenReturn(Optional.of(debit));
        when(walletService.hasOrderRefundCredit(eq(42), anyString())).thenReturn(false);

        LitemallOrderOperationResult result = orchestrator.approveAftersale(5);

        assertTrue(result.isSuccess());
        // DELIVERED order first enters the refund flow, then completes it.
        verify(orderServiceImpl).requestRefund(eq(ORDER_ID), anyString());
        ArgumentCaptor<LitemallMoney> refund = ArgumentCaptor.forClass(LitemallMoney.class);
        verify(orderServiceImpl).refundOrder(eq(ORDER_ID), refund.capture());
        assertEquals(0, refund.getValue().getAmount().compareTo(new BigDecimal("50")));
        ArgumentCaptor<LitemallWalletCreditCommand> credit =
                ArgumentCaptor.forClass(LitemallWalletCreditCommand.class);
        verify(walletService).credit(credit.capture());
        assertEquals(0, credit.getValue().getAmount().compareTo(new BigDecimal("50")));
        // Aftersale row reached its terminal refunded state and was persisted.
        assertEquals(LitemallAfterSaleStatus.STATUS_REFUND, aftersale.getStatus());
        verify(aftersaleRepository).update(aftersale);
        verify(orderRepository).updateAfterSaleStatus(ORDER_ID,
                LitemallAfterSaleStatus.STATUS_REFUND.getCode());
    }

    @Test
    void approve_alreadyRefundedCredit_isIdempotentOnTheLedger() {
        LitemallBillAggregate debit = mock(LitemallBillAggregate.class);
        when(debit.getAmount()).thenReturn(new LitemallMoney(new BigDecimal("85")));
        when(walletService.findOrderPaymentDebit(eq(42), anyString())).thenReturn(Optional.of(debit));
        when(walletService.hasOrderRefundCredit(eq(42), anyString())).thenReturn(true);

        orchestrator.approveAftersale(5);

        // Duplicate-credit guard: the ledger already holds the refund credit.
        verify(walletService, never()).credit(any());
    }

    @Test
    void reject_leavesOrderStatusAndMoneyAlone() {
        LitemallOrderOperationResult result = orchestrator.rejectAftersale(5, "photos unclear");

        assertTrue(result.isSuccess());
        assertEquals(LitemallAfterSaleStatus.STATUS_REJECT, aftersale.getStatus());
        verify(aftersaleRepository).update(aftersale);
        verify(orderRepository).updateAfterSaleStatus(ORDER_ID,
                LitemallAfterSaleStatus.STATUS_REJECT.getCode());
        // The order itself is untouched: no refund flow, no money movement.
        verify(orderServiceImpl, never()).requestRefund(any(), anyString());
        verify(orderServiceImpl, never()).refundOrder(any(), any());
        verify(walletService, never()).credit(any());
        assertEquals(LitemallOrderStatus.DELIVERED, order.getOrderStatus());
    }

    @Test
    void decide_twice_isRefused() {
        aftersale.reject();

        assertThrows(LitemallAftersaleException.class, () -> orchestrator.approveAftersale(5));
        assertThrows(LitemallAftersaleException.class, () -> orchestrator.rejectAftersale(5, null));
        verify(orderServiceImpl, never()).refundOrder(any(), any());
    }

    @Test
    void approve_unknownAftersale_isNotFoundResult() {
        when(aftersaleRepository.findById(anyInt())).thenReturn(Optional.empty());
        assertTrue(!orchestrator.approveAftersale(999).isSuccess());
    }
}
