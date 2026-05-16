package org.linlinjava.litemall.loyalty.domain.model.valueobjects;

import lombok.Getter;

@Getter
public class LitemallUserLevelRecordId {
    private final Integer id;

    public LitemallUserLevelRecordId(Integer id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("User level record ID must be a positive integer.");
        }
        this.id = id;
    }
}