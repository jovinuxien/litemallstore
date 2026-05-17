package org.linlinjava.litemall.loyalty.domain.model.commands;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LitemallCheckLevelUpCommand {
    private Integer userId;
}
