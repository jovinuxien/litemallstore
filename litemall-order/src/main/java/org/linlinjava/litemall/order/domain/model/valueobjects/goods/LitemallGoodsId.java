package org.linlinjava.litemall.order.domain.model.valueobjects.goods;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

public class LitemallGoodsId {

    private final Integer id;

    public LitemallGoodsId(Integer id) {
        if(id == null || id <= 0) {
            throw new IllegalArgumentException("Goods ID must be a positive integer.");
        }
        this.id = id;
    }

    public Integer getId() {
        return id;
    }

    // Value equality: used as a HashMap key in AggregatesValidationContext.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return java.util.Objects.equals(id, ((LitemallGoodsId) o).id);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hashCode(id);
    }
}
