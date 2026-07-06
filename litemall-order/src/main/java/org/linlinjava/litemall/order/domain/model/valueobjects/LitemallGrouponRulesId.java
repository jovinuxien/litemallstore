package org.linlinjava.litemall.order.domain.model.valueobjects;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

public class LitemallGrouponRulesId {
    private Integer id;

    public LitemallGrouponRulesId(Integer id) {
        // 0 is the "no groupon" sentinel for a plain order (placeOrder normalizes a
        // missing groupon to 0 and guards real use with getId() > 0). Reject only
        // null/negative so plain checkouts don't blow up constructing this VO.
        if (id == null || id < 0) {
            throw new IllegalArgumentException("Groupon rules ID must not be null or negative.");
        }
        this.id = id;
    }
    public Integer getId() {
        return id;
    }
}
