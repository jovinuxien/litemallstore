package org.linlinjava.litemall.order.domain.model.valueobjects;

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
