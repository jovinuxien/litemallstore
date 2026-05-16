package org.linlinjava.litemall.wallet.domain.events.wallet;

import lombok.Getter;
import org.linlinjava.litemall.core.events.LitemallDomainEvent;
import org.linlinjava.litemall.wallet.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.wallet.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.wallet.domain.model.valueobjects.wallet.LitemallExtractId;

@Getter
public class LitemallExtractRequestedEvent extends LitemallDomainEvent {

    private final LitemallExtractId extractId;
    private final LitemallUserId userId;
    private final LitemallMoney amount;

    public LitemallExtractRequestedEvent(LitemallExtractId extractId, LitemallUserId userId,
                                          LitemallMoney amount) {
        super("EXTRACT_REQUESTED");
        this.extractId = extractId;
        this.userId = userId;
        this.amount = amount;
    }
}
