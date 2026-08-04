package org.linlinjava.litemall.goods.application.tracking;

import java.util.Locale;

/**
 * Coarse User-Agent → device-type bucket at ingest. The raw UA string is used
 * once here and DISCARDED — it is never stored (doc/behavioral-events.md).
 * Buckets are deliberately coarse: mobile | tablet | desktop | bot.
 */
public final class DeviceClassifier {

    public static final String MOBILE = "mobile";
    public static final String TABLET = "tablet";
    public static final String DESKTOP = "desktop";
    public static final String BOT = "bot";

    private DeviceClassifier() {
    }

    public static String classify(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        String ua = userAgent.toLowerCase(Locale.ROOT);
        if (ua.contains("bot") || ua.contains("crawler") || ua.contains("spider")
                || ua.contains("headless") || ua.contains("curl") || ua.contains("python")) {
            return BOT;
        }
        if (ua.contains("ipad") || ua.contains("tablet")
                || (ua.contains("android") && !ua.contains("mobile"))) {
            return TABLET;
        }
        if (ua.contains("mobi") || ua.contains("iphone") || ua.contains("android")) {
            return MOBILE;
        }
        return DESKTOP;
    }
}
