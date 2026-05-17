package org.linlinjava.litemall.loyalty.domain.model.valueobjects;

import lombok.Getter;

@Getter
public class LitemallSignId {
    private final Integer id;

    public LitemallSignId(Integer id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Sign-in ID must be a positive integer.");
        }
        this.id = id;
    }
}
