package org.linlinjava.litemall.goods.domain.model.valueobjects.goods.category.user;

public class LitemallRoleId {

    private final Integer id;

    public LitemallRoleId(Integer id) {
        if(id == null || id <= 0) {
            throw new IllegalArgumentException("Goods ID must be a positive integer.");
        }
        this.id = id;
    }

    public Integer getId() {
        return id;
    }
}
