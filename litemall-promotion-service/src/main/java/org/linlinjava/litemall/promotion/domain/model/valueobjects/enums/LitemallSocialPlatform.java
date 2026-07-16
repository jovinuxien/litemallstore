package org.linlinjava.litemall.promotion.domain.model.valueobjects.enums;

/**
 * Social posting platforms (Wave 6). {@code dbValue} is the ledger/API contract
 * value ({@code litemall_social_post.platform} and every admin envelope);
 * {@code utmSource} is the shared UTM convention's {@code utm_source} for links
 * posted on that platform ({@code utm_medium=social} always).
 */
public enum LitemallSocialPlatform {

    META_FB("meta_fb", "facebook", "Facebook Page"),
    META_IG("meta_ig", "instagram", "Instagram"),
    TIKTOK("tiktok", "tiktok", "TikTok");

    private final String dbValue;
    private final String utmSource;
    private final String displayName;

    LitemallSocialPlatform(String dbValue, String utmSource, String displayName) {
        this.dbValue = dbValue;
        this.utmSource = utmSource;
        this.displayName = displayName;
    }

    public String getDbValue() {
        return dbValue;
    }

    public String getUtmSource() {
        return utmSource;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Case-insensitive lookup by the contract value; null for unknown input (caller decides the 400). */
    public static LitemallSocialPlatform fromDbValue(String value) {
        if (value == null) {
            return null;
        }
        for (LitemallSocialPlatform platform : values()) {
            if (platform.dbValue.equalsIgnoreCase(value.trim())) {
                return platform;
            }
        }
        return null;
    }
}
