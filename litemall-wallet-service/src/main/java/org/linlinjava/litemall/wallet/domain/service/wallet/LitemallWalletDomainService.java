package org.linlinjava.litemall.wallet.domain.service.wallet;

import org.linlinjava.litemall.wallet.domain.model.agregates.LitemallBillAggregate;
import org.linlinjava.litemall.wallet.domain.model.agregates.LitemallWalletAggregate;
import org.linlinjava.litemall.wallet.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.wallet.domain.model.valueobjects.enums.LitemallBillDirection;

import java.math.BigDecimal;

/**
 * Domain service for wallet operations.
 * Encapsulates pure domain logic that spans multiple aggregates.
 */
public class LitemallWalletDomainService {

    /**
     * Validate that wallet has sufficient balance for a debit operation.
     *
     * @param wallet the wallet aggregate
     * @param amount the requested debit amount
     * @throws IllegalStateException if balance is insufficient
     */
    public void validateSufficientBalance(LitemallWalletAggregate wallet, LitemallMoney amount) {
        if (wallet == null) {
            throw new IllegalArgumentException("Wallet cannot be null");
        }
        if (!wallet.hasEnoughBalance(amount)) {
            throw new IllegalStateException(
                    "Insufficient balance. Current balance: " + wallet.getBalance().getAmount()
                    + ", requested: " + amount.getAmount()
            );
        }
    }

    /**
     * Create a bill aggregate for a credit operation.
     *
     * @param wallet     the wallet after credit
     * @param creditAmount the credited amount
     * @param title      the bill title
     * @param category   the bill category
     * @param type       the bill type
     * @param linkId     the linked entity ID
     * @param mark       a descriptive mark
     * @return the new bill aggregate
     */
    public LitemallBillAggregate createCreditBill(LitemallWalletAggregate wallet,
                                                   LitemallMoney creditAmount,
                                                   String title, String category,
                                                   String type, String linkId, String mark) {
        return new LitemallBillAggregate(
                wallet.getUserId(),
                linkId,
                LitemallBillDirection.CREDIT,
                title,
                category,
                type,
                creditAmount,
                wallet.getBalance(),
                mark
        );
    }

    /**
     * Create a bill aggregate for a debit operation.
     *
     * @param wallet      the wallet after debit
     * @param debitAmount the debited amount
     * @param title       the bill title
     * @param category    the bill category
     * @param type        the bill type
     * @param linkId      the linked entity ID
     * @param mark        a descriptive mark
     * @return the new bill aggregate
     */
    public LitemallBillAggregate createDebitBill(LitemallWalletAggregate wallet,
                                                  LitemallMoney debitAmount,
                                                  String title, String category,
                                                  String type, String linkId, String mark) {
        return new LitemallBillAggregate(
                wallet.getUserId(),
                linkId,
                LitemallBillDirection.DEBIT,
                title,
                category,
                type,
                debitAmount,
                wallet.getBalance(),
                mark
        );
    }

    /**
     * Calculate the minimum extractable amount (business rule).
     *
     * @return minimum extract amount
     */
    public LitemallMoney minimumExtractAmount() {
        return new LitemallMoney(BigDecimal.ONE);
    }

    /**
     * Validate that an extract amount is acceptable.
     *
     * @param wallet        the user wallet
     * @param extractAmount the amount to extract
     */
    public void validateExtractRequest(LitemallWalletAggregate wallet, LitemallMoney extractAmount) {
        validateSufficientBalance(wallet, extractAmount);
        if (extractAmount.getAmount().compareTo(minimumExtractAmount().getAmount()) < 0) {
            throw new IllegalArgumentException(
                    "Extract amount must be at least " + minimumExtractAmount().getAmount()
            );
        }
    }
}