package org.linlinjava.litemall.promotion.domain.model.commands;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallBargainId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallBargainUserId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserId;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LitemallHelpBargainCommand {

    private LitemallUserId helperId;
    private LitemallBargainUserId bargainUserId;
    private LitemallBargainId bargainId;
}
