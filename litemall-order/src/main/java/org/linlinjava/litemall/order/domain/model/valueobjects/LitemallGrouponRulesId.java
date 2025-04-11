package org.linlinjava.litemall.order.domain.model.valueobjects;

public class LitemallGrouponRulesId {
    private Integer id;

    public LitemallGrouponRulesId(Integer id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Groupon rules ID must be a positive integer.");
        }
        this.id = id;
    }
    public Integer getId() {
        return id;
    }
}
