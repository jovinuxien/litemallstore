package org.linlinjava.litemall.order.domain.model.valueobjects.cj;

/**
 * How CJ finally resolved a dispute. Domain-side name for CJ's {@code finallyDeal}
 * integer (translated at the ACL boundary). Absent while the dispute is undecided.
 */
public enum CjDisputeResolution {
    REFUND((short) 1),
    REISSUE((short) 2),
    REJECTED((short) 3);

    private final short cjCode;

    CjDisputeResolution(short cjCode) {
        this.cjCode = cjCode;
    }

    public short getCjCode() {
        return cjCode;
    }

    public static CjDisputeResolution fromCjCode(Short code) {
        if (code == null) {
            return null;
        }
        for (CjDisputeResolution value : values()) {
            if (value.cjCode == code) {
                return value;
            }
        }
        // CJ may add resolution codes; an unknown one must not break the read path.
        return null;
    }
}
