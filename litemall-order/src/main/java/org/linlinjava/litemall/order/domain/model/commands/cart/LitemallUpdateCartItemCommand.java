package org.linlinjava.litemall.order.domain.model.commands.cart;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LitemallUpdateCartItemCommand {
    private Integer userId;
    private Integer cartId;
    private Integer number;
    private Integer productId;
    private String[] specifications;
}
