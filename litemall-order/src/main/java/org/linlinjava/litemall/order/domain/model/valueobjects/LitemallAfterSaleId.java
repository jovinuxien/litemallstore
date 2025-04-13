package org.linlinjava.litemall.order.domain.model.valueobjects;

public class LitemallAfterSaleId {

    private final Integer id;

    public LitemallAfterSaleId(Integer id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("After sale id must not be null or empty");
        }
        this.id = id;
    }

    public Integer getId() {
        return id;
    }
}
