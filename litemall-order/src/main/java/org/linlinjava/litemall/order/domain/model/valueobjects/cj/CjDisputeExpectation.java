package org.linlinjava.litemall.order.domain.model.valueobjects.cj;

/**
 * What the customer wants out of a CJ dispute. Domain-side name for CJ's
 * {@code expectType} integer (translated at the ACL boundary, never exposed as 1/2).
 */
public enum CjDisputeExpectation {
    REFUND((short) 1),
    REISSUE((short) 2);

    private final short cjCode;

    CjDisputeExpectation(short cjCode) {
        this.cjCode = cjCode;
    }

    public short getCjCode() {
        return cjCode;
    }

    public static CjDisputeExpectation fromCjCode(Short code) {
        if (code == null) {
            return null;
        }
        for (CjDisputeExpectation value : values()) {
            if (value.cjCode == code) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown CJ expectType: " + code);
    }
}
