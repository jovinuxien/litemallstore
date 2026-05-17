package org.linlinjava.litemall.loyalty.domain.model.valueobjects.enums;

import lombok.Getter;

@Getter
public enum LitemallLevelStatus {
    ACTIVE((byte) 1),
    INACTIVE((byte) 0);

    private final Byte code;

    LitemallLevelStatus(Byte code) {
        this.code = code;
    }

    public static LitemallLevelStatus fromCode(Byte code) {
        for (LitemallLevelStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown level status code: " + code);
    }
}
