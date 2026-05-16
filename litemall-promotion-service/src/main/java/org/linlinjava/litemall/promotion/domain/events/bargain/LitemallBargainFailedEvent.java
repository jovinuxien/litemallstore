package org.linlinjava.litemall.promotion.domain.events.bargain;

import org.linlinjava.litemall.core.events.LitemallDomainEvent;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallBargainId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallBargainUserId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserId;

public class LitemallBargainFailedEvent extends LitemallDomainEvent {

    private final LitemallBargainUserId bargainUserId;
    private final LitemallUserId userId;
    private final LitemallBargainId bargainId;

    public LitemallBargainFailedEvent(LitemallBargainUserId bargainUserId, LitemallUserId userId,
                                       LitemallBargainId bargainId) {
        super("BARGAIN_FAILED");
        this.bargainUserId = bargainUserId;
        this.userId = userId;
        this.bargainId = bargainId;
    }

    public LitemallBargainUserId getBargainUserId() { return bargainUserId; }
    public LitemallUserId getUserId() { return userId; }
    public LitemallBargainId getBargainId() { return bargainId; }
}
