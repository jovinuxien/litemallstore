package org.linlinjava.litemall.order.domain.model.commands.wallet;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LitemallRechargeCreateCommand {

    private Integer userId;
    private BigDecimal price;
    private BigDecimal givePrice;
    private String rechargeType;
    private String orderId;
}
