package org.linlinjava.litemall.order.domain.model.valueobjects;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

public class LitemallAddressId {

    private Integer id;
    public LitemallAddressId(Integer id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Address ID must be a positive integer.");
        }
        this.id = id;
    }
    public Integer getId() {
        return id;
    }
}
