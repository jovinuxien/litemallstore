package org.linlinjava.litemall.gateway.domain.valueobjects.user;

public class LitemallPermissionId {

    private final Integer id;

    public LitemallPermissionId(Integer id) {
        if(id == null || id <= 0) {
            throw new IllegalArgumentException("Goods ID must be a positive integer.");
        }
        this.id = id;
    }

    public Integer getId() {
        return id;
    }
}
