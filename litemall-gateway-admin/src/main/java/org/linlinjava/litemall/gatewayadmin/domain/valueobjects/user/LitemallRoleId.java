package org.linlinjava.litemall.gatewayadmin.domain.valueobjects.user;

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
