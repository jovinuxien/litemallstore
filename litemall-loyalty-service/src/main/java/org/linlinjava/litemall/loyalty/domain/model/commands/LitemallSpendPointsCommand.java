package org.linlinjava.litemall.loyalty.domain.model.commands;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LitemallSpendPointsCommand {
    private Integer userId;
    private int points;
    private String title;
    private String linkId;
}
