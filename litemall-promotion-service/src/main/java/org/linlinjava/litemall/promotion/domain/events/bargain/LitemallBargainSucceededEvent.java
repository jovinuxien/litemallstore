package org.linlinjava.litemall.promotion.domain.events.bargain;

import org.linlinjava.litemall.core.events.LitemallDomainEvent;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallBargainId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallBargainUserId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserId;

public class LitemallBargainSucceededEvent extends LitemallDomainEvent {

    private final LitemallBargainUserId bargainUserId;
    private final LitemallUserId userId;
    private final LitemallBargainId bargainId;
    private final LitemallMoney finalPrice;

    public LitemallBargainSucceededEvent(LitemallBargainUserId bargainUserId, LitemallUserId userId,
                                          LitemallBargainId bargainId, LitemallMoney finalPrice) {
        super("BARGAIN_SUCCEEDED");
        this.bargainUserId = bargainUserId;
        this.userId = userId;
        this.bargainId = bargainId;
        this.finalPrice = finalPrice;
    }

    public LitemallBargainUserId getBargainUserId() { return bargainUserId; }
    public LitemallUserId getUserId() { return userId; }
    public LitemallBargainId getBargainId() { return bargainId; }
    public LitemallMoney getFinalPrice() { return finalPrice; }
}
