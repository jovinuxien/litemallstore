package org.linlinjava.litemall.order.application;

import org.linlinjava.litemall.order.domain.model.agregates.LitemallBillAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallWalletAggregate;
import org.linlinjava.litemall.order.domain.model.commands.wallet.LitemallWalletCreditCommand;
import org.linlinjava.litemall.order.domain.model.commands.wallet.LitemallWalletDebitCommand;

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

    /**
     * The wallet debit recorded when the given order was paid — the captured
     * amount a refund may return. Empty when the order was not wallet-paid.
     *
     * @param userId   the buyer
     * @param orderRef the order id as recorded on the bill's linkId
     */
    java.util.Optional<LitemallBillAggregate> findOrderPaymentDebit(Integer userId, String orderRef);

    /**
     * Whether a refund credit for the given order already exists on the ledger
     * (idempotency guard for refund approval retries/replays).
     */
    boolean hasOrderRefundCredit(Integer userId, String orderRef);
}
