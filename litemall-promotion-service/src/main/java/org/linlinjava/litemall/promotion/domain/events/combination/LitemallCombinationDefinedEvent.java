package org.linlinjava.litemall.promotion.domain.events.combination;

import org.linlinjava.litemall.core.events.LitemallDomainEvent;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCombinationId;

public class LitemallCombinationDefinedEvent extends LitemallDomainEvent {

    private final LitemallCombinationId combinationId;
    private final Integer goodsId;

    public LitemallCombinationDefinedEvent(LitemallCombinationId combinationId, Integer goodsId) {
        super("COMBINATION_DEFINED");
        this.combinationId = combinationId;
        this.goodsId = goodsId;
    }

    public LitemallCombinationId getCombinationId() { return combinationId; }
    public Integer getGoodsId() { return goodsId; }
}
