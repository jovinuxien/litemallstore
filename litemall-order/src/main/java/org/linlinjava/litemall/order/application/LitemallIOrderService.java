package org.linlinjava.litemall.order.application;

import com.google.protobuf.ServiceException;
import org.linlinjava.litemall.order.domain.model.commands.LitemallOrderSubmitResult;
import org.linlinjava.litemall.order.domain.model.commands.LitemallPlaceOrderCommand;

public interface LitemallIOrderService {

    LitemallOrderSubmitResult placeOrder(LitemallPlaceOrderCommand command) throws ServiceException;
}
