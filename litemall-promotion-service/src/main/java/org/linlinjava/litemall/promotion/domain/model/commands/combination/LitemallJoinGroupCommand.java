package org.linlinjava.litemall.promotion.domain.model.commands.combination;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCombinationPinkId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserId;

/** Customer command to join an open group by its leader slot id. */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LitemallJoinGroupCommand {

    private LitemallUserId userId;
    private LitemallCombinationPinkId pinkId;
}
