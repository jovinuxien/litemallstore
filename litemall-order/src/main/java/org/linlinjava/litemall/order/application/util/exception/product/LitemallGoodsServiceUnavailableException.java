package org.linlinjava.litemall.order.application.util.exception.product;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

/**
 * Raised by the goods ACL ({@code LitemallGoodsFacade}) when goods-management
 * cannot give an authoritative answer for a checkout-critical read/reserve
 * (service down, circuit open, error response). Propagating this aborts the
 * placement transaction so no order is created and no stock is decremented.
 */
public class LitemallGoodsServiceUnavailableException extends RuntimeException {

    public LitemallGoodsServiceUnavailableException(String message) {
        super("Goods service unavailable: " + message);
    }

    public LitemallGoodsServiceUnavailableException(String message, Throwable cause) {
        super("Goods service unavailable: " + message, cause);
    }
}
