package org.linlinjava.litemall.order.domain.model.valueobjects.goods;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

public class LitemallCategoryId {

    private final Integer id;

    public LitemallCategoryId(Integer id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Category id must not be null or empty");
        }
        this.id = id;
    }
    public Integer getId() {
        return id;
    }
}
