package org.linlinjava.litemall.order.application.internal;

import lombok.extern.slf4j.Slf4j;
import org.linlinjava.litemall.order.application.LitemallIWalletService;
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

        // Perform credit on aggregate
        wallet.credit(amount);

        // Persist the new balance
        walletRepository.creditBalance(userId, amount);

        // Create bill record
        LitemallBillAggregate bill = new LitemallBillAggregate(
                userId,
                command.getLinkId(),
                LitemallBillDirection.CREDIT,
                command.getTitle(),
                command.getCategory(),
                command.getType(),
                amount,
                wallet.getBalance(),
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

        // Perform debit on aggregate
        wallet.debit(amount);

        // Persist the new balance
        walletRepository.debitBalance(userId, amount);

        // Create bill record
        LitemallBillAggregate bill = new LitemallBillAggregate(
                userId,
                command.getLinkId(),
                LitemallBillDirection.DEBIT,
                command.getTitle(),
                command.getCategory(),
                command.getType(),
                amount,
                wallet.getBalance(),
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
}
