package org.linlinjava.litemall.wallet.application.internal;

import lombok.extern.slf4j.Slf4j;
import org.linlinjava.litemall.wallet.domain.events.LitemallDomainEventPublisher;
import org.linlinjava.litemall.wallet.domain.events.wallet.LitemallRechargeCompletedEvent;
import org.linlinjava.litemall.wallet.domain.model.agregates.LitemallRechargeAggregate;
import org.linlinjava.litemall.wallet.domain.model.commands.LitemallRechargeCreateCommand;
import org.linlinjava.litemall.wallet.domain.model.commands.LitemallWalletCreditCommand;
import org.linlinjava.litemall.wallet.domain.model.repositories.LitemallRechargeRepository;
import org.linlinjava.litemall.wallet.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.wallet.domain.model.valueobjects.enums.LitemallRechargeType;
import org.linlinjava.litemall.wallet.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.wallet.domain.model.valueobjects.wallet.LitemallRechargeId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@Slf4j
@Transactional
public class LitemallRechargeServiceLayer {

    private final LitemallRechargeRepository rechargeRepository;
    private final LitemallWalletServiceImpl walletService;

    @Autowired
    private LitemallDomainEventPublisher domainEventPublisher;

    public LitemallRechargeServiceLayer(LitemallRechargeRepository rechargeRepository,
                                         LitemallWalletServiceImpl walletService) {
        this.rechargeRepository = rechargeRepository;
        this.walletService = walletService;
    }

    /**
     * Create a new recharge record.
     *
     * @param command the recharge create command
     * @return the created recharge aggregate
     */
    public LitemallRechargeAggregate createRecharge(LitemallRechargeCreateCommand command) {
        LitemallUserId userId = new LitemallUserId(command.getUserId());
        LitemallMoney price = new LitemallMoney(command.getPrice());
        LitemallMoney givePrice = command.getGivePrice() != null
                ? new LitemallMoney(command.getGivePrice())
                : new LitemallMoney(BigDecimal.ZERO);
        LitemallRechargeType rechargeType = LitemallRechargeType.fromValue(command.getRechargeType());

        LitemallRechargeAggregate recharge = new LitemallRechargeAggregate(
                userId, command.getOrderId(), price, givePrice, rechargeType
        );

        rechargeRepository.add(recharge);

        log.info("Recharge created for userId={}, amount={}, type={}",
                command.getUserId(), command.getPrice(), command.getRechargeType());

        return recharge;
    }

    /**
     * Complete a recharge (mark as paid and credit wallet).
     *
     * @param rechargeId the recharge to complete
     */
    public void completeRecharge(LitemallRechargeId rechargeId) {
        LitemallRechargeAggregate recharge = rechargeRepository.findById(rechargeId)
                .orElseThrow(() -> new IllegalArgumentException("Recharge not found: " + rechargeId.getId()));

        if (Boolean.TRUE.equals(recharge.getPaid())) {
            throw new IllegalStateException("Recharge already completed: " + rechargeId.getId());
        }

        // Mark recharge as paid
        rechargeRepository.markAsPaid(rechargeId);

        // Calculate total amount (price + givePrice)
        LitemallMoney totalAmount = recharge.getPrice().add(recharge.getGivePrice());

        // Credit the wallet
        LitemallWalletCreditCommand creditCommand = new LitemallWalletCreditCommand(
                recharge.getUserId().getId(),
                totalAmount.getAmount(),
                "Recharge",
                "recharge",
                recharge.getRechargeType().getValue(),
                String.valueOf(rechargeId.getId()),
                "Wallet recharge"
        );
        walletService.credit(creditCommand);

        // Publish event
        domainEventPublisher.publish(new LitemallRechargeCompletedEvent(
                rechargeId,
                recharge.getUserId(),
                totalAmount
        ));

        log.info("Recharge completed for rechargeId={}, userId={}, amount={}",
                rechargeId.getId(), recharge.getUserId().getId(), totalAmount.getAmount());
    }
}
