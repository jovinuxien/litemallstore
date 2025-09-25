package org.linlinjava.litemall.goods.domain.model.valueobjects.user;

public class LitemallUserId {

    private final Integer id;

    public LitemallUserId(Integer id) {
        if(id == null || id <= 0) {
            throw new IllegalArgumentException("Goods ID must be a positive integer.");
        }
        this.id = id;
    }

    public Integer getId() {
        return id;
    }
}
