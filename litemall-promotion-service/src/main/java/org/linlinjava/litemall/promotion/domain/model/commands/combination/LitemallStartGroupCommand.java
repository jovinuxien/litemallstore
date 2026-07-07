package org.linlinjava.litemall.promotion.domain.model.commands.combination;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCombinationId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserId;

/** Customer command to start a new group on an active group-buy campaign. */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LitemallStartGroupCommand {

    private LitemallUserId userId;
    private LitemallCombinationId combinationId;
}
