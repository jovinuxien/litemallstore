package org.linlinjava.litemall.order.domain.model.valueobjects.order;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

public class LitemallOrderGoodsId {

    private final Integer id;

    public LitemallOrderGoodsId(Integer id) {
        if(id == null || id <= 0) {
            throw new IllegalArgumentException("Order ID must be a positive integer.");
        }
        this.id = id;
    }

    public Integer getId() {
        return id;
    }
}
