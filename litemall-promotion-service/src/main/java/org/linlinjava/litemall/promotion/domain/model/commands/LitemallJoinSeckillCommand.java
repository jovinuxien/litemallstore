package org.linlinjava.litemall.promotion.domain.model.commands;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallSeckillId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserId;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LitemallJoinSeckillCommand {

    private LitemallUserId userId;
    private LitemallSeckillId seckillId;
    private Integer quantity;
}
