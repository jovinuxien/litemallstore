package org.linlinjava.litemall.promotion.domain.model.commands;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallBargainUserId;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LitemallCheckBargainStatusCommand {

    private LitemallBargainUserId bargainUserId;
}
