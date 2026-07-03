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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Refund-to-tender settlement on {@link LitemallOrderOrchestratorService#approveRefund}
 * (see {@code docs/plan-refund-tender-parity.md}). Guards the defect where a refund
 * always credited the wallet with the full actual price regardless of how the order
 * was paid: a CARD-paid order must not mint wallet money, a WALLET refund is capped
 * at the recorded debit, and a replayed approval must not credit twice.
 */
@ExtendWith(MockitoExtension.class)
class LitemallOrderRefundTenderTest {

    @Mock
    private LitemallOrderServiceImpl orderServiceImpl;
    @Mock
    private LitemallOrderRepository orderRepository;
    @Mock
    private LitemallGrouponServiceLayer grouponServiceLayer;
    @Mock
    private LitemallIWalletService walletService;

    @InjectMocks
    private LitemallOrderOrchestratorService orchestrator;

    @BeforeEach
    void wireFieldInjectedDeps() {
        // walletService is field-injected (@Autowired), not a constructor arg.
        ReflectionTestUtils.setField(orchestrator, "walletService", walletService);
    }

    private LitemallOrderAggregate refundRequestedOrder(int id, String payId, String actualPrice) {
        LitemallOrderAggregate order = new LitemallOrderAggregate();
        order.setOrderId(new LitemallOrderId(id));
        order.setUserId(new LitemallUserId(42));
        order.setOrderSn("SN" + id);
        order.setOrderStatus(LitemallOrderStatus.REFUND_REQUEST);
        order.setActualPrice(new LitemallMoney(new BigDecimal(actualPrice)));
        order.setPayId(payId);
        return order;
    }

    private LitemallBillAggregate paymentDebit(String amount) {
        LitemallBillAggregate bill = new LitemallBillAggregate();
        bill.setAmount(new LitemallMoney(new BigDecimal(amount)));
        return bill;
    }

    @Test
    void cardPaidOrder_refundDoesNotCreditWallet_butRecordsRefundAmount() {
        LitemallOrderId orderId = new LitemallOrderId(31);
        when(orderRepository.findById(orderId))
                .thenReturn(Optional.of(refundRequestedOrder(31, "CREDIT_CARD:pi_123", "80.00")));
        // No wallet debit was ever taken for a card charge.
        when(walletService.findOrderPaymentDebit(42, "31")).thenReturn(Optional.empty());

        orchestrator.approveRefund(orderId);

        verify(walletService, never()).credit(any());
        ArgumentCaptor<LitemallMoney> refunded = ArgumentCaptor.forClass(LitemallMoney.class);
        verify(orderServiceImpl).refundOrder(eq(orderId), refunded.capture());
        // The PSP reversal covers the captured card amount; refund_amount records it.
        assertEquals(0, new BigDecimal("80.00").compareTo(refunded.getValue().getAmount()));
    }

    @Test
    void walletPaidOrder_refundCreditsExactlyTheRecordedDebit() {
        LitemallOrderId orderId = new LitemallOrderId(32);
        when(orderRepository.findById(orderId))
                .thenReturn(Optional.of(refundRequestedOrder(32, "WALLET", "50.00")));
        when(walletService.findOrderPaymentDebit(42, "32")).thenReturn(Optional.of(paymentDebit("50.00")));
        when(walletService.hasOrderRefundCredit(42, "32")).thenReturn(false);

        orchestrator.approveRefund(orderId);

        ArgumentCaptor<LitemallWalletCreditCommand> credit =
                ArgumentCaptor.forClass(LitemallWalletCreditCommand.class);
        verify(walletService).credit(credit.capture());
        assertEquals(0, new BigDecimal("50.00").compareTo(credit.getValue().getAmount()));
        assertEquals("32", credit.getValue().getLinkId());

        ArgumentCaptor<LitemallMoney> refunded = ArgumentCaptor.forClass(LitemallMoney.class);
        verify(orderServiceImpl).refundOrder(eq(orderId), refunded.capture());
        assertEquals(0, new BigDecimal("50.00").compareTo(refunded.getValue().getAmount()));
    }

    @Test
    void walletRefund_isCappedAtTheCapturedAmount() {
        // The ledger says only 40 was ever taken (e.g. the price changed after pay):
        // the refund must return 40, never the current 50 actual price.
        LitemallOrderId orderId = new LitemallOrderId(33);
        when(orderRepository.findById(orderId))
                .thenReturn(Optional.of(refundRequestedOrder(33, "WALLET", "50.00")));
        when(walletService.findOrderPaymentDebit(42, "33")).thenReturn(Optional.of(paymentDebit("40.00")));
        when(walletService.hasOrderRefundCredit(42, "33")).thenReturn(false);

        orchestrator.approveRefund(orderId);

        ArgumentCaptor<LitemallWalletCreditCommand> credit =
                ArgumentCaptor.forClass(LitemallWalletCreditCommand.class);
        verify(walletService).credit(credit.capture());
        assertEquals(0, new BigDecimal("40.00").compareTo(credit.getValue().getAmount()));

        ArgumentCaptor<LitemallMoney> refunded = ArgumentCaptor.forClass(LitemallMoney.class);
        verify(orderServiceImpl).refundOrder(eq(orderId), refunded.capture());
        assertEquals(0, new BigDecimal("40.00").compareTo(refunded.getValue().getAmount()));
    }

    @Test
    void replayedApproval_doesNotCreditTheWalletTwice() {
        LitemallOrderId orderId = new LitemallOrderId(34);
        when(orderRepository.findById(orderId))
                .thenReturn(Optional.of(refundRequestedOrder(34, "WALLET", "50.00")));
        when(walletService.findOrderPaymentDebit(42, "34")).thenReturn(Optional.of(paymentDebit("50.00")));
        // A credit with the ORDER/REFUND/34 business key is already on the ledger.
        when(walletService.hasOrderRefundCredit(42, "34")).thenReturn(true);

        orchestrator.approveRefund(orderId);

        verify(walletService, never()).credit(any());
        verify(orderServiceImpl).refundOrder(eq(orderId), any());
    }

    @Test
    void legacyOrderWithoutTenderOrCapture_refundsNothing() {
        // Paid before the tender was recorded and no wallet debit on the ledger:
        // nothing was captured, so the status flips with refund_amount = 0.
        LitemallOrderId orderId = new LitemallOrderId(35);
        when(orderRepository.findById(orderId))
                .thenReturn(Optional.of(refundRequestedOrder(35, null, "50.00")));
        when(walletService.findOrderPaymentDebit(42, "35")).thenReturn(Optional.empty());

        orchestrator.approveRefund(orderId);

        verify(walletService, never()).credit(any());
        verify(walletService, never()).hasOrderRefundCredit(anyInt(), anyString());
        ArgumentCaptor<LitemallMoney> refunded = ArgumentCaptor.forClass(LitemallMoney.class);
        verify(orderServiceImpl).refundOrder(eq(orderId), refunded.capture());
        assertEquals(0, BigDecimal.ZERO.compareTo(refunded.getValue().getAmount()));
    }

    @Test
    void legacyWalletPaidOrder_fallsBackToTheLedgerForTenderAndAmount() {
        // No pay_id tender recorded, but the ledger shows a wallet payment debit:
        // treat as wallet-paid and refund the recorded capture.
        LitemallOrderId orderId = new LitemallOrderId(36);
        when(orderRepository.findById(orderId))
                .thenReturn(Optional.of(refundRequestedOrder(36, null, "50.00")));
        when(walletService.findOrderPaymentDebit(42, "36")).thenReturn(Optional.of(paymentDebit("50.00")));
        when(walletService.hasOrderRefundCredit(42, "36")).thenReturn(false);

        orchestrator.approveRefund(orderId);

        ArgumentCaptor<LitemallWalletCreditCommand> credit =
                ArgumentCaptor.forClass(LitemallWalletCreditCommand.class);
        verify(walletService).credit(credit.capture());
        assertEquals(0, new BigDecimal("50.00").compareTo(credit.getValue().getAmount()));
    }
}
