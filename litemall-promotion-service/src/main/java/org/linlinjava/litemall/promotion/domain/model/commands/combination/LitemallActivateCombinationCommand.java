package org.linlinjava.litemall.promotion.domain.model.commands.combination;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCombinationId;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LitemallActivateCombinationCommand {

    private LitemallCombinationId combinationId;
}
