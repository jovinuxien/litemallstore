package org.linlinjava.litemall.promotion.domain.events.bargain;

import org.linlinjava.litemall.core.events.LitemallDomainEvent;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallBargainId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallBargainUserId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserId;

public class LitemallBargainSessionCreatedEvent extends LitemallDomainEvent {

    private final LitemallBargainUserId bargainUserId;
    private final LitemallUserId userId;
    private final LitemallBargainId bargainId;
    private final LitemallMoney initialPrice;

    public LitemallBargainSessionCreatedEvent(LitemallBargainUserId bargainUserId, LitemallUserId userId,
                                               LitemallBargainId bargainId, LitemallMoney initialPrice) {
        super("BARGAIN_SESSION_CREATED");
        this.bargainUserId = bargainUserId;
        this.userId = userId;
        this.bargainId = bargainId;
        this.initialPrice = initialPrice;
    }

    public LitemallBargainUserId getBargainUserId() { return bargainUserId; }
    public LitemallUserId getUserId() { return userId; }
    public LitemallBargainId getBargainId() { return bargainId; }
    public LitemallMoney getInitialPrice() { return initialPrice; }
}
