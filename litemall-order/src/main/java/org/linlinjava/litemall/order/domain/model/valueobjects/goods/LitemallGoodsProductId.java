package org.linlinjava.litemall.order.domain.model.valueobjects.goods;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

public class LitemallGoodsProductId {

    private final Integer id;

    public LitemallGoodsProductId(Integer id) {
        if(id == null || id <= 0) {
            throw new IllegalArgumentException("Goods product ID must be a positive integer.");
        }
        this.id = id;
    }

    public Integer getId() {
        return id;
    }

    // Value equality: used as a HashMap key in AggregatesValidationContext, so
    // identity equality would make every product stock lookup miss.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return java.util.Objects.equals(id, ((LitemallGoodsProductId) o).id);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hashCode(id);
    }
}
