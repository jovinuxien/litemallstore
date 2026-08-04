package org.linlinjava.litemall.promotion.application.ports;

import java.util.List;
import java.util.Map;

/**
 * Outbound port for the Wave-17 Postiz publishing vertical: the connected
 * channel list and the one-call-per-product schedule handoff. Implemented in
 * {@code infrastructure/acl/postiz} over Postiz's public API — application code
 * never touches the Feign client directly.
 *
 * <p>Every method throws {@link PostizGatewayException} on failure: transport
 * errors carry only a message; Postiz's structured validation 400s
 * ({@code {provider, name, message}}) carry the provider so the caller can
 * surface the rejection verbatim per channel.
 */
public interface PostizPort {

    /** The org's connected Postiz integrations (channels), unfiltered. */
    List<PostizChannel> channels();

    /**
     * Create ONE scheduled Postiz post targeting N channels — a single API call
     * (one throttle hit) whose validation is atomic: a 400 on any channel fails
     * the whole call.
     *
     * @param dateUtcIso UTC ISO-8601 instant, e.g. {@code 2026-08-05T14:00:00.000Z}
     * @return one entry per target channel with the created Postiz post id
     */
    List<ScheduledPost> schedulePost(String dateUtcIso, List<ChannelPost> targets);

    record PostizChannel(String integrationId, String identifier, String name,
                         String picture, boolean disabled) {
    }

    /** @param settings provider-required settings map (may be empty, never null) */
    record ChannelPost(String integrationId, String content, String imageUrl,
                       Map<String, Object> settings) {
    }

    record ScheduledPost(String integrationId, String postizPostId) {
    }
}
