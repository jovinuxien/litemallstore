package org.linlinjava.litemall.order.application;

import org.linlinjava.litemall.order.domain.model.commands.LitemallPlaceOrderCommand;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;

public interface LitemallIOrderService {

    LitemallOrderId placeOrder(LitemallPlaceOrderCommand command);
}
