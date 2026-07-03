package org.linlinjava.litemall.order.application.internal;

import lombok.extern.slf4j.Slf4j;
import org.linlinjava.litemall.order.application.LitemallIWalletService;
import org.linlinjava.litemall.order.application.util.exception.wallet.LitemallInsufficientBalanceException;
import org.linlinjava.litemall.order.domain.events.LitemallDomainEventPublisher;
import org.linlinjava.litemall.order.domain.events.wallet.LitemallWalletCreditedEvent;
import org.linlinjava.litemall.order.domain.events.wallet.LitemallWalletDebitedEvent;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallBillAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallWalletAggregate;
import org.linlinjava.litemall.order.domain.model.commands.wallet.LitemallWalletCreditCommand;
import org.linlinjava.litemall.order.domain.model.commands.wallet.LitemallWalletDebitCommand;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallBillRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallWalletRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallBillDirection;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.order.domain.service.wallet.LitemallWalletDomainService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
@Transactional
public class LitemallWalletServiceImpl implements LitemallIWalletService {

    private final LitemallWalletRepository walletRepository;
    private final LitemallBillRepository billRepository;
    private final LitemallWalletDomainService walletDomainService;

    @Autowired
    private LitemallDomainEventPublisher domainEventPublisher;

    public LitemallWalletServiceImpl(LitemallWalletRepository walletRepository,
                                      LitemallBillRepository billRepository,
                                      LitemallWalletDomainService walletDomainService) {
        this.walletRepository = walletRepository;
        this.billRepository = billRepository;
        this.walletDomainService = walletDomainService;
    }

    @Override
    public LitemallWalletAggregate credit(LitemallWalletCreditCommand command) {
        LitemallUserId userId = new LitemallUserId(command.getUserId());
        LitemallMoney amount = new LitemallMoney(command.getAmount());

        // Find or load wallet
        LitemallWalletAggregate wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found for user: " + command.getUserId()));

        // Perform credit on aggregate (enforces the domain invariant)
        wallet.credit(amount);

        // Persist the new balance (atomic UPDATE)
        walletRepository.creditBalance(userId, amount);

        // Re-read the authoritative post-update balance so the bill ledger and the
        // returned aggregate reflect the DB, not a possibly-stale in-memory value.
        LitemallMoney balanceAfter = walletRepository.getBalance(userId);
        wallet.setBalance(balanceAfter);

        // Create bill record
        LitemallBillAggregate bill = new LitemallBillAggregate(
                userId,
                command.getLinkId(),
                LitemallBillDirection.CREDIT,
                command.getTitle(),
                command.getCategory(),
                command.getType(),
                amount,
                balanceAfter,
                command.getMark()
        );
        billRepository.add(bill);

        // Publish domain event
        domainEventPublisher.publish(new LitemallWalletCreditedEvent(
                wallet.getWalletId(),
                userId,
                amount,
                command.getTitle()
        ));

        log.info("Wallet credited for userId={}, amount={}", command.getUserId(), command.getAmount());
        return wallet;
    }

    @Override
    public LitemallWalletAggregate debit(LitemallWalletDebitCommand command) {
        LitemallUserId userId = new LitemallUserId(command.getUserId());
        LitemallMoney amount = new LitemallMoney(command.getAmount());

        // Find or load wallet
        LitemallWalletAggregate wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found for user: " + command.getUserId()));

        // Validate sufficient balance
        walletDomainService.validateSufficientBalance(wallet, amount);

        // Perform debit on aggregate (enforces the domain invariant)
        wallet.debit(amount);

        // Persist the new balance via the atomic, overdraw-guarded UPDATE. If a
        // concurrent debit won the race the guard rejects this one — surface it as
        // the typed insufficient-balance exception, not a raw IllegalStateException.
        try {
            walletRepository.debitBalance(userId, amount);
        } catch (IllegalStateException e) {
            throw new LitemallInsufficientBalanceException(
                    "Insufficient balance for user " + command.getUserId());
        }

        // Re-read the authoritative post-update balance for the bill ledger + response.
        LitemallMoney balanceAfter = walletRepository.getBalance(userId);
        wallet.setBalance(balanceAfter);

        // Create bill record
        LitemallBillAggregate bill = new LitemallBillAggregate(
                userId,
                command.getLinkId(),
                LitemallBillDirection.DEBIT,
                command.getTitle(),
                command.getCategory(),
                command.getType(),
                amount,
                balanceAfter,
                command.getMark()
        );
        billRepository.add(bill);

        // Publish domain event
        domainEventPublisher.publish(new LitemallWalletDebitedEvent(
                wallet.getWalletId(),
                userId,
                amount,
                command.getTitle()
        ));

        log.info("Wallet debited for userId={}, amount={}", command.getUserId(), command.getAmount());
        return wallet;
    }

    @Override
    @Transactional(readOnly = true)
    public LitemallWalletAggregate getWallet(Integer userId) {
        LitemallUserId userIdVO = new LitemallUserId(userId);
        return walletRepository.findByUserId(userIdVO)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found for user: " + userId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<LitemallBillAggregate> getBills(Integer userId) {
        LitemallUserId userIdVO = new LitemallUserId(userId);
        return billRepository.findByUserId(userIdVO);
    }

    @Override
    public java.util.Optional<LitemallBillAggregate> findOrderPaymentDebit(Integer userId, String orderRef) {
        return billRepository.findOrderPaymentDebit(new LitemallUserId(userId), orderRef);
    }

    @Override
    public boolean hasOrderRefundCredit(Integer userId, String orderRef) {
        return billRepository.orderRefundCreditExists(new LitemallUserId(userId), orderRef);
    }
}
