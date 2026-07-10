package org.linlinjava.litemall.promotion.domain.model.valueobjects.enums;

/**
 * Lifecycle of an algorithmic-targeting promotion campaign. DRAFT campaigns are
 * admin-only and may be evaluated for preview; ACTIVE campaigns deliver their
 * targeting assignments (PromotionTargetedEvent); COMPLETED/PAUSED are terminal
 * / suspended.
 */
public enum LitemallCampaignStatus {

    DRAFT(0, "Draft"),
    ACTIVE(1, "Active"),
    COMPLETED(2, "Completed"),
    PAUSED(3, "Paused");

    private final int code;
    private final String displayName;

    LitemallCampaignStatus(int code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public int getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static LitemallCampaignStatus fromCode(int code) {
        for (LitemallCampaignStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown LitemallCampaignStatus code: " + code);
    }
}
