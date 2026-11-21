package org.linlinjava.litemall.order.application.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Builds the external "follow your parcel" link shown on the order page and in the shipped
 * mail. One template for the whole service ({@code litemall.order.tracking-url-template},
 * {@code {trackNumber}} placeholder); a blank template or a blank number yields no link,
 * and callers then simply omit it.
 */
@Component
public class TrackingLinkBuilder {

    static final String DEFAULT_TEMPLATE = "https://t.17track.net/en#nums={trackNumber}";
    private static final String PLACEHOLDER = "{trackNumber}";

    private final String template;

    public TrackingLinkBuilder(
            @Value("${litemall.order.tracking-url-template:" + DEFAULT_TEMPLATE + "}") String template) {
        this.template = template == null ? "" : template.trim();
    }

    /** The link for this tracking number, or null when there is nothing to link to. */
    public String urlFor(String trackNumber) {
        if (trackNumber == null || trackNumber.isBlank() || !template.contains(PLACEHOLDER)) {
            return null;
        }
        return template.replace(PLACEHOLDER, URLEncoder.encode(trackNumber.trim(), StandardCharsets.UTF_8));
    }
}
