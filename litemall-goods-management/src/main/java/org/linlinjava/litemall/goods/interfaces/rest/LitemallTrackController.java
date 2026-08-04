package org.linlinjava.litemall.goods.interfaces.rest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.db.domain.LitemallConsentRecord;
import org.linlinjava.litemall.goods.application.tracking.DeviceClassifier;
import org.linlinjava.litemall.goods.application.tracking.IngestContext;
import org.linlinjava.litemall.goods.application.tracking.TrackEventDto;
import org.linlinjava.litemall.goods.application.tracking.TrackIngestService;
import org.linlinjava.litemall.goods.utils.UserContext;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * First-party behavioral event ingest (Phase 0 — doc/behavioral-events.md).
 *
 * <p>Public POST at the edge (the second sanctioned anonymous POST after the
 * Stripe webhook; user-approved 2026-08-04). Trust model: identity comes ONLY
 * from the edge-injected {@code X-Visitor-Id}/{@code X-Session-Id} headers
 * (inbound spoofs are stripped edge-side) and the edge-verified
 * {@code X-User-Id} via {@link UserContext} — never from the body. The edge
 * injects {@code X-Visitor-Id} only under a granted consent, so its presence
 * is the consent proof: batches without it are dropped, silently — tracking
 * never surfaces errors to the storefront.
 */
@RestController
@RequestMapping("/srv/track")
public class LitemallTrackController {

    private final TrackIngestService ingestService;

    public LitemallTrackController(TrackIngestService ingestService) {
        this.ingestService = ingestService;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CollectBody {
        private List<TrackEventDto> events;

        public List<TrackEventDto> getEvents() {
            return events;
        }

        public void setEvents(List<TrackEventDto> events) {
            this.events = events;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ConsentBody {
        private String choice;
        private String scope;
        private Long occurredAt;

        public String getChoice() {
            return choice;
        }

        public void setChoice(String choice) {
            this.choice = choice;
        }

        public String getScope() {
            return scope;
        }

        public void setScope(String scope) {
            this.scope = scope;
        }

        public Long getOccurredAt() {
            return occurredAt;
        }

        public void setOccurredAt(Long occurredAt) {
            this.occurredAt = occurredAt;
        }
    }

    @PostMapping("/collect")
    public Object collect(@RequestBody CollectBody body,
                          @RequestHeader(value = "X-Visitor-Id", required = false) String visitorId,
                          @RequestHeader(value = "X-Session-Id", required = false) String sessionId,
                          @RequestHeader(value = "CF-IPCountry", required = false) String cfCountry,
                          @RequestHeader(value = "User-Agent", required = false) String userAgent,
                          @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
        List<TrackEventDto> events = body == null ? null : body.getEvents();
        int size = events == null ? 0 : events.size();
        String visitor = TrackIngestService.normalizeUuid(visitorId);
        if (visitor == null) {
            // No edge-attested visitor identity = no consent proof. Silent drop by contract.
            return ResponseUtil.ok(Map.of("accepted", 0, "dropped", size));
        }
        TrackIngestService.IngestResult result =
                ingestService.offerBatch(events, context(visitor, sessionId, cfCountry, userAgent, acceptLanguage));
        return ResponseUtil.ok(Map.of("accepted", result.accepted(), "dropped", result.dropped()));
    }

    /**
     * Lawful-basis audit of the consent choice. Unlike {@code /collect} this
     * accepts a missing visitor id — under strict prior consent a denial never
     * had identity minted, and the denial itself is still worth recording.
     */
    @PostMapping("/consent")
    public Object consent(@RequestBody ConsentBody body,
                          @RequestHeader(value = "X-Visitor-Id", required = false) String visitorId,
                          @RequestHeader(value = "CF-IPCountry", required = false) String cfCountry,
                          @RequestHeader(value = "User-Agent", required = false) String userAgent,
                          @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
        String choice = body == null ? null : body.getChoice();
        if (LitemallConsentRecord.CHOICE_GRANTED.equals(choice) || LitemallConsentRecord.CHOICE_DENIED.equals(choice)) {
            ingestService.offerConsent(choice,
                    body.getScope() == null ? LitemallConsentRecord.SCOPE_ANALYTICS : body.getScope(),
                    body.getOccurredAt(),
                    context(visitorId, null, cfCountry, userAgent, acceptLanguage));
        }
        return ResponseUtil.ok();
    }

    private IngestContext context(String visitorId, String sessionId,
                                  String cfCountry, String userAgent, String acceptLanguage) {
        return new IngestContext(visitorId, sessionId, UserContext.getUserIdAsInt(),
                normalizeCountry(cfCountry), DeviceClassifier.classify(userAgent), firstLanguageTag(acceptLanguage));
    }

    /** CF-IPCountry is a 2-letter code or "XX"/"T1" for unknown/Tor — store real codes only. */
    private static String normalizeCountry(String cfCountry) {
        if (cfCountry == null) {
            return null;
        }
        String code = cfCountry.trim().toUpperCase();
        return code.length() == 2 && code.chars().allMatch(Character::isLetter) && !"XX".equals(code) ? code : null;
    }

    private static String firstLanguageTag(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return null;
        }
        String first = acceptLanguage.split(",")[0].split(";")[0].trim();
        return first.isEmpty() ? null : first.substring(0, Math.min(first.length(), 8));
    }
}
