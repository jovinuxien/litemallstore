package org.linlinjava.litemall.order.domain.model.agregates;

import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.core.events.LitemallDomainEvent;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.order.domain.model.valueobjects.wallet.LitemallWalletId;

import java.util.ArrayList;
import java.util.List;

/**
 * Wallet aggregate root.
 * Maps wallet balance to the litemall_user table (now_money field for balance,
 * brokerage_price field for brokerage balance).
 */
@Getter
@Setter
public class LitemallWalletAggregate {

    private LitemallWalletId walletId;
    private LitemallUserId userId;
    private LitemallMoney balance;
    private LitemallMoney brokerageBalance;
    private List<LitemallDomainEvent> domainEvents = new ArrayList<>();

    public LitemallWalletAggregate() {
    }

    public LitemallWalletAggregate(LitemallWalletId walletId, LitemallUserId userId,
                                    LitemallMoney balance, LitemallMoney brokerageBalance) {
        this.walletId = walletId;
        this.userId = userId;
        this.balance = balance;
        this.brokerageBalance = brokerageBalance;
    }

    /**
     * Credit the wallet (add funds).
     *
     * @param amount the amount to credit
     */
    public void credit(LitemallMoney amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Credit amount cannot be null");
        }
        this.balance = this.balance.add(amount);
    }

    /**
     * Debit the wallet (deduct funds).
     *
     * @param amount the amount to debit
     */
    public void debit(LitemallMoney amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Debit amount cannot be null");
        }
        if (!hasEnoughBalance(amount)) {
            throw new IllegalStateException("Insufficient wallet balance");
        }
        this.balance = this.balance.subtract(amount);
    }

    /**
     * Check if the wallet has sufficient balance.
     *
     * @param amount the amount to check
     * @return true if balance is sufficient
     */
    public boolean hasEnoughBalance(LitemallMoney amount) {
        return this.balance.isGreaterThanOrEqual(amount);
    }

    public void addDomainEvent(LitemallDomainEvent event) {
        this.domainEvents.add(event);
    }

    public List<LitemallDomainEvent> getDomainEvents() {
        return new ArrayList<>(domainEvents);
    }

    public void clearDomainEvents() {
        this.domainEvents.clear();
    }
}
