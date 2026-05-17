package org.linlinjava.litemall.wallet.application;

import org.linlinjava.litemall.wallet.domain.model.agregates.LitemallBillAggregate;
import org.linlinjava.litemall.wallet.domain.model.agregates.LitemallWalletAggregate;
import org.linlinjava.litemall.wallet.domain.model.commands.LitemallWalletCreditCommand;
import org.linlinjava.litemall.wallet.domain.model.commands.LitemallWalletDebitCommand;

import java.util.List;

/**
 * Wallet service interface defining core wallet operations.
 */
public interface LitemallIWalletService {

    /**
     * Credit funds to a user wallet.
     *
     * @param command the credit command
     * @return the updated wallet aggregate
     */
    LitemallWalletAggregate credit(LitemallWalletCreditCommand command);

    /**
     * Debit funds from a user wallet.
     *
     * @param command the debit command
     * @return the updated wallet aggregate
     */
    LitemallWalletAggregate debit(LitemallWalletDebitCommand command);

    /**
     * Retrieve a user's wallet.
     *
     * @param userId the user ID
     * @return the wallet aggregate
     */
    LitemallWalletAggregate getWallet(Integer userId);

    /**
     * Retrieve the billing history for a user.
     *
     * @param userId the user ID
     * @return list of bill aggregates
     */
    List<LitemallBillAggregate> getBills(Integer userId);
}
