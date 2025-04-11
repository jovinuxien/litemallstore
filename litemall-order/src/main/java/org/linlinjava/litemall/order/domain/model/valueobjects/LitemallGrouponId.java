package org.linlinjava.litemall.order.domain.model.valueobjects;

import lombok.Getter;

@Getter
public class LitemallGrouponId {

    private final Integer id;
    public LitemallGrouponId(Integer id) {
        if(id == null || id <= 0) {
            throw new IllegalArgumentException("Groupon ID must be a positive integer.");
        }

        this.id = id;
    }
}
