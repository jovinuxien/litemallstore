package org.linlinjava.litemall.promotion.domain.model.aggregates;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallBargainHelpId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallBargainId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallBargainUserId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserId;

@Getter
@Setter
@Builder
public class LitemallBargainHelpAggregate {

    private LitemallBargainHelpId helpId;
    private LitemallUserId helperId;
    private LitemallBargainId bargainId;
    private LitemallBargainUserId bargainUserId;
    private LitemallMoney helpAmount;
}
