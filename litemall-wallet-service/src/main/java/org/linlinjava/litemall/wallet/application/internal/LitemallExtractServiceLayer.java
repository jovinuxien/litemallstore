package org.linlinjava.litemall.wallet.application.internal;

import lombok.extern.slf4j.Slf4j;
import org.linlinjava.litemall.wallet.domain.events.LitemallDomainEventPublisher;
import org.linlinjava.litemall.wallet.domain.events.wallet.LitemallExtractRequestedEvent;
import org.linlinjava.litemall.wallet.domain.model.agregates.LitemallExtractAggregate;
import org.linlinjava.litemall.wallet.domain.model.agregates.LitemallWalletAggregate;
import org.linlinjava.litemall.wallet.domain.model.commands.LitemallExtractRequestCommand;
import org.linlinjava.litemall.wallet.domain.model.commands.LitemallWalletDebitCommand;
import org.linlinjava.litemall.wallet.domain.model.repositories.LitemallExtractRepository;
import org.linlinjava.litemall.wallet.domain.model.repositories.LitemallWalletRepository;
import org.linlinjava.litemall.wallet.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.wallet.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.wallet.domain.service.wallet.LitemallWalletDomainService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@Transactional
public class LitemallExtractServiceLayer {

    private final LitemallExtractRepository extractRepository;
    private final LitemallWalletRepository walletRepository;
    private final LitemallWalletServiceImpl walletService;
    private final LitemallWalletDomainService walletDomainService;

    @Autowired
    private LitemallDomainEventPublisher domainEventPublisher;

    public LitemallExtractServiceLayer(LitemallExtractRepository extractRepository,
                                        LitemallWalletRepository walletRepository,
                                        LitemallWalletServiceImpl walletService,
                                        LitemallWalletDomainService walletDomainService) {
        this.extractRepository = extractRepository;
        this.walletRepository = walletRepository;
        this.walletService = walletService;
        this.walletDomainService = walletDomainService;
    }

    /**
     * Request an extract (withdrawal) from the wallet.
     *
     * @param command the extract request command
     * @return the created extract aggregate
     */
    public LitemallExtractAggregate requestExtract(LitemallExtractRequestCommand command) {
        LitemallUserId userId = new LitemallUserId(command.getUserId());
        LitemallMoney extractAmount = new LitemallMoney(command.getExtractAmount());

        // Load wallet and validate
        LitemallWalletAggregate wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found for user: " + command.getUserId()));

        walletDomainService.validateExtractRequest(wallet, extractAmount);

        // Compute balance after extract
        LitemallMoney balanceAfter = wallet.getBalance().subtract(extractAmount);

        // Create extract aggregate
        LitemallExtractAggregate extract = new LitemallExtractAggregate(
                userId,
                command.getRealName(),
                command.getExtractType(),
                command.getBankCode(),
                command.getBankAddress(),
                extractAmount,
                balanceAfter
        );
        extractRepository.add(extract);

        // Debit wallet
        LitemallWalletDebitCommand debitCommand = new LitemallWalletDebitCommand(
                command.getUserId(),
                extractAmount.getAmount(),
                "Withdrawal",
                "extract",
                command.getExtractType(),
                null,
                "Wallet withdrawal request"
        );
        walletService.debit(debitCommand);

        // Publish event
        if (extract.getExtractId() != null) {
            domainEventPublisher.publish(new LitemallExtractRequestedEvent(
                    extract.getExtractId(),
                    userId,
                    extractAmount
            ));
        }

        log.info("Extract requested for userId={}, amount={}", command.getUserId(), command.getExtractAmount());
        return extract;
    }
}
