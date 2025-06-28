package org.linlinjava.litemall.order.domain.model.valueobjects.user;

import lombok.Getter;

@Getter
public class LitemallUserId {
    private final Integer id;

    public LitemallUserId(Integer id) {
        if(id == null || id <= 0) {
            throw new IllegalArgumentException("User ID must be a positive integer.");
        }
        this.id = id;
    }
}
