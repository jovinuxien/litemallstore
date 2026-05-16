package org.linlinjava.litemall.loyalty.domain.model.valueobjects;

import lombok.Getter;

@Getter
public class LitemallPointsId {
    private final Integer id;

    public LitemallPointsId(Integer id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Points record ID must be a positive integer.");
        }
        this.id = id;
    }
}
