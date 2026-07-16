package org.linlinjava.litemall.promotion.domain.model.valueobjects.enums;

/**
 * Ledger states of a social publish attempt. Transitions are guarded at the
 * mapper level ({@code SocialPostMapper}): {@code DRAFT|FAILED -> POSTED|FAILED};
 * a {@code POSTED} row is immutable history. Only {@code FAILED} rows may be
 * retried.
 */
public enum LitemallSocialPostStatus {

    DRAFT("draft"),
    POSTED("posted"),
    FAILED("failed");

    private final String dbValue;

    LitemallSocialPostStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static LitemallSocialPostStatus fromDbValue(String value) {
        if (value == null) {
            return null;
        }
        for (LitemallSocialPostStatus status : values()) {
            if (status.dbValue.equalsIgnoreCase(value.trim())) {
                return status;
            }
        }
        return null;
    }
}
