package org.linlinjava.litemall.goods.domain.model.valueobjects.goods.user;

public class LitemallAdminId {

    private final Integer id;

    public LitemallAdminId(Integer id) {
        if(id == null || id <= 0) {
            throw new IllegalArgumentException("Goods ID must be a positive integer.");
        }
        this.id = id;
    }

    public Integer getId() {
        return id;
    }
}
