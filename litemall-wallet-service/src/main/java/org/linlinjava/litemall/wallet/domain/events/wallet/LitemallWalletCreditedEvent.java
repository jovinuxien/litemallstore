package org.linlinjava.litemall.wallet.domain.events.wallet;

import lombok.Getter;
import org.linlinjava.litemall.core.events.LitemallDomainEvent;
import org.linlinjava.litemall.wallet.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.wallet.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.wallet.domain.model.valueobjects.wallet.LitemallWalletId;

@Getter
public class LitemallWalletCreditedEvent extends LitemallDomainEvent {

    private final LitemallWalletId walletId;
    private final LitemallUserId userId;
    private final LitemallMoney amount;
    private final String billTitle;

    public LitemallWalletCreditedEvent(LitemallWalletId walletId, LitemallUserId userId,
                                        LitemallMoney amount, String billTitle) {
        super("WALLET_CREDITED");
        this.walletId = walletId;
        this.userId = userId;
        this.amount = amount;
        this.billTitle = billTitle;
    }
}
