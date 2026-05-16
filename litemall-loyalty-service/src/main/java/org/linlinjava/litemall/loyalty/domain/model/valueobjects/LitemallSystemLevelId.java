package org.linlinjava.litemall.loyalty.domain.model.valueobjects;

import lombok.Getter;

@Getter
public class LitemallSystemLevelId {
    private final Integer id;

    public LitemallSystemLevelId(Integer id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("System level ID must be a positive integer.");
        }
        this.id = id;
    }
}