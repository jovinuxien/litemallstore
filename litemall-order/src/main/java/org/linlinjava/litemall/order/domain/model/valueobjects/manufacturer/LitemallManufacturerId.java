package org.linlinjava.litemall.order.domain.model.valueobjects.manufacturer;

public class LitemallManufacturerId {

    private final Integer id;

    public LitemallManufacturerId(Integer id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Manufacturer id must not be null or empty");
        }
        this.id = id;
    }

    public Integer getId() {
        return id;
    }
}
