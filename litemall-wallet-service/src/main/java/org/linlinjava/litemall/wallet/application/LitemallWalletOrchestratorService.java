package org.linlinjava.litemall.wallet.application;

import lombok.extern.slf4j.Slf4j;
import org.linlinjava.litemall.wallet.application.internal.LitemallExtractServiceLayer;
import org.linlinjava.litemall.wallet.application.internal.LitemallRechargeServiceLayer;
import org.linlinjava.litemall.wallet.application.internal.LitemallWalletServiceImpl;
import org.linlinjava.litemall.wallet.domain.model.agregates.LitemallBillAggregate;
import org.linlinjava.litemall.wallet.domain.model.agregates.LitemallExtractAggregate;
import org.linlinjava.litemall.wallet.domain.model.agregates.LitemallRechargeAggregate;
import org.linlinjava.litemall.wallet.domain.model.agregates.LitemallWalletAggregate;
import org.linlinjava.litemall.wallet.domain.model.commands.LitemallExtractRequestCommand;
import org.linlinjava.litemall.wallet.domain.model.commands.LitemallRechargeCreateCommand;
import org.linlinjava.litemall.wallet.domain.model.commands.LitemallWalletCreditCommand;
import org.linlinjava.litemall.wallet.domain.model.commands.LitemallWalletDebitCommand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
@Transactional
public class LitemallWalletOrchestratorService {

    private final LitemallWalletServiceImpl walletServiceImpl;
    private final LitemallRechargeServiceLayer rechargeServiceLayer;
    private final LitemallExtractServiceLayer extractServiceLayer;

    public LitemallWalletOrchestratorService(LitemallWalletServiceImpl walletServiceImpl,
                                              LitemallRechargeServiceLayer rechargeServiceLayer,
                                              LitemallExtractServiceLayer extractServiceLayer) {
        this.walletServiceImpl = walletServiceImpl;
        this.rechargeServiceLayer = rechargeServiceLayer;
        this.extractServiceLayer = extractServiceLayer;
    }

    public enum WalletAction {
        CREDIT, DEBIT, RECHARGE, EXTRACT
    }

    /**
     * Main entry point for all wallet actions.
     */
    public Object performWalletAction(WalletAction action, Object actionData) {
        switch (action) {
            case CREDIT:
                return walletServiceImpl.credit((LitemallWalletCreditCommand) actionData);
            case DEBIT:
                return walletServiceImpl.debit((LitemallWalletDebitCommand) actionData);
            case RECHARGE:
                return rechargeServiceLayer.createRecharge((LitemallRechargeCreateCommand) actionData);
            case EXTRACT:
                return extractServiceLayer.requestExtract((LitemallExtractRequestCommand) actionData);
            default:
                throw new IllegalArgumentException("Unknown wallet action: " + action);
        }
    }

    /**
     * Credit a user's wallet.
     */
    public LitemallWalletAggregate creditWallet(LitemallWalletCreditCommand command) {
        return (LitemallWalletAggregate) performWalletAction(WalletAction.CREDIT, command);
    }

    /**
     * Debit a user's wallet.
     */
    public LitemallWalletAggregate debitWallet(LitemallWalletDebitCommand command) {
        return (LitemallWalletAggregate) performWalletAction(WalletAction.DEBIT, command);
    }

    /**
     * Create a recharge (top-up) for a user.
     */
    public LitemallRechargeAggregate createRecharge(LitemallRechargeCreateCommand command) {
        return (LitemallRechargeAggregate) performWalletAction(WalletAction.RECHARGE, command);
    }

    /**
     * Request a withdrawal (extract) for a user.
     */
    public LitemallExtractAggregate requestExtract(LitemallExtractRequestCommand command) {
        return (LitemallExtractAggregate) performWalletAction(WalletAction.EXTRACT, command);
    }

    /**
     * Get wallet for a user.
     */
    @Transactional(readOnly = true)
    public LitemallWalletAggregate getWallet(Integer userId) {
        return walletServiceImpl.getWallet(userId);
    }

    /**
     * Get bills for a user.
     */
    @Transactional(readOnly = true)
    public List<LitemallBillAggregate> getBills(Integer userId) {
        return walletServiceImpl.getBills(userId);
    }
}
