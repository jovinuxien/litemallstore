package org.linlinjava.litemall.order.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.order.application.util.exception.wallet.LitemallInsufficientBalanceException;
import org.linlinjava.litemall.order.domain.events.LitemallDomainEventPublisher;
import org.linlinjava.litemall.order.domain.events.wallet.LitemallWalletDebitedEvent;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallWalletAggregate;
import org.linlinjava.litemall.order.domain.model.commands.wallet.LitemallWalletDebitCommand;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallBillRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallWalletRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.order.domain.model.valueobjects.wallet.LitemallWalletId;
import org.linlinjava.litemall.order.domain.service.wallet.LitemallWalletDomainService;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the wallet-debit path the order payment flow drives when
 * {@code PaymentMethod.WALLET} is selected. Verifies the debit goes
 * aggregate -> domain service -> repository and emits
 * {@link LitemallWalletDebitedEvent}, and that an underfunded wallet raises
 * {@link LitemallInsufficientBalanceException} (no debit, no event).
 */
class LitemallWalletDebitPathTest {

    private LitemallWalletRepository walletRepository;
    private LitemallBillRepository billRepository;
    private LitemallDomainEventPublisher eventPublisher;
    private LitemallWalletServiceImpl walletService;

    @BeforeEach
    void setUp() {
        walletRepository = mock(LitemallWalletRepository.class);
        billRepository = mock(LitemallBillRepository.class);
        eventPublisher = mock(LitemallDomainEventPublisher.class);
        // Real domain service so balance validation actually fires.
        walletService = new LitemallWalletServiceImpl(walletRepository, billRepository,
                new LitemallWalletDomainService());
        ReflectionTestUtils.setField(walletService, "domainEventPublisher", eventPublisher);
    }

    private LitemallWalletAggregate walletWithBalance(String balance) {
        return new LitemallWalletAggregate(
                new LitemallWalletId(7),
                new LitemallUserId(7),
                new LitemallMoney(new BigDecimal(balance)),
                new LitemallMoney(BigDecimal.ZERO));
    }

    private LitemallWalletDebitCommand debit(String amount) {
        return new LitemallWalletDebitCommand(7, new BigDecimal(amount),
                "Order payment", "ORDER", "PAYMENT", "100", "Wallet debit for order");
    }

    @Test
    void debit_sufficientBalance_persistsBillAndEmitsDebitedEvent() {
        when(walletRepository.findByUserId(any())).thenReturn(Optional.of(walletWithBalance("50.00")));

        walletService.debit(debit("30.00"));

        verify(walletRepository).debitBalance(any(), any());
        verify(billRepository).add(any());

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(1)).publish((org.linlinjava.litemall.core.events.LitemallDomainEvent) captor.capture());
        org.junit.jupiter.api.Assertions.assertInstanceOf(LitemallWalletDebitedEvent.class, captor.getValue());
    }

    @Test
    void debit_insufficientBalance_throwsAndDoesNotDebit() {
        when(walletRepository.findByUserId(any())).thenReturn(Optional.of(walletWithBalance("10.00")));

        assertThrows(LitemallInsufficientBalanceException.class, () -> walletService.debit(debit("30.00")));

        verify(walletRepository, never()).debitBalance(any(), any());
        verify(billRepository, never()).add(any());
        verify(eventPublisher, never()).publish(any());
    }
}
