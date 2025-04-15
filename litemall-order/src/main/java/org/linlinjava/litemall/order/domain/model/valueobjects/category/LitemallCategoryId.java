package org.linlinjava.litemall.order.domain.model.valueobjects.category;

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
