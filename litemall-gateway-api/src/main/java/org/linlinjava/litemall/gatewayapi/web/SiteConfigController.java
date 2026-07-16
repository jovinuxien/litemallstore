package org.linlinjava.litemall.gatewayapi.web;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Runtime SPA bootstrap config (Wave-6 Task A). The customer SPA fetches this
 * once at startup to decide whether to inject the Matomo tracker: both values
 * present ⇒ inject; either null (the committed default) ⇒ the bundle stays
 * byte-identical in behavior — no matomo script, no tracker requests.
 *
 * <p>Lives under {@code /auth} purely for plumbing: that prefix is served
 * locally by edge controllers (not routed downstream) and is already proxied
 * by the SPA dev-server, so dev and prod need no extra wiring. The payload is
 * public, non-sensitive config (the Matomo auth token is NOT exposed — that is
 * promotion's server-side Reporting-API credential; the browser tracker
 * endpoint needs none).
 */
@RestController
@RequestMapping("/auth")
public class SiteConfigController {

    private final String matomoUrl;
    private final String matomoSiteId;
    private final int matomoGoodsDimension;

    public SiteConfigController(
            @Value("${litemall.tracking.matomo.base-url:}") String matomoUrl,
            @Value("${litemall.tracking.matomo.site-id:}") String matomoSiteId,
            @Value("${litemall.tracking.matomo.goods-dimension:1}") int matomoGoodsDimension) {
        this.matomoUrl = blankToNull(matomoUrl);
        this.matomoSiteId = blankToNull(matomoSiteId);
        this.matomoGoodsDimension = matomoGoodsDimension;
    }

    @GetMapping("/site-config")
    public Map<String, Object> siteConfig() {
        Map<String, Object> data = new HashMap<>();
        data.put("matomoUrl", matomoUrl);
        data.put("matomoSiteId", matomoSiteId);
        data.put("matomoGoodsDimension", matomoGoodsDimension);
        return ApiResponse.ok(data);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
