package org.linlinjava.litemall.promotion.domain.events.bargain;

import org.linlinjava.litemall.core.events.LitemallDomainEvent;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallBargainUserId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserId;

public class LitemallBargainHelpAppliedEvent extends LitemallDomainEvent {

    private final LitemallBargainUserId bargainUserId;
    private final LitemallUserId helperId;
    private final LitemallMoney helpAmount;
    private final LitemallMoney newPrice;

    public LitemallBargainHelpAppliedEvent(LitemallBargainUserId bargainUserId, LitemallUserId helperId,
                                            LitemallMoney helpAmount, LitemallMoney newPrice) {
        super("BARGAIN_HELP_APPLIED");
        this.bargainUserId = bargainUserId;
        this.helperId = helperId;
        this.helpAmount = helpAmount;
        this.newPrice = newPrice;
    }

    public LitemallBargainUserId getBargainUserId() { return bargainUserId; }
    public LitemallUserId getHelperId() { return helperId; }
    public LitemallMoney getHelpAmount() { return helpAmount; }
    public LitemallMoney getNewPrice() { return newPrice; }
}