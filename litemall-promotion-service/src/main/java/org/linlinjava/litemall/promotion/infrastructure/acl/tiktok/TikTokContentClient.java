package org.linlinjava.litemall.promotion.infrastructure.acl.tiktok;

import org.linlinjava.litemall.promotion.infrastructure.acl.tiktok.dto.TikTokPublishResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

/**
 * Feign client over the TikTok Content Posting API (Wave-6 social posting).
 * Host from {@code litemall.promotion.social.tiktok.base-url}; the OAuth
 * bearer token is passed per call (it is a per-creator token, not app-global).
 * Wrapped by {@link TikTokPublishAdapter} so application code depends only on
 * {@code SocialPublishPort}, never on this client.
 */
@FeignClient(
        name = "tiktok-content",
        url = "${litemall.promotion.social.tiktok.base-url:https://open.tiktokapis.com}")
public interface TikTokContentClient {

    /**
     * Direct-post init with {@code PULL_FROM_URL} — TikTok fetches the video
     * itself, so no upload streaming is needed. Body:
     * {@code {post_info: {title, privacy_level}, source_info: {source: "PULL_FROM_URL", video_url}}}.
     */
    @PostMapping(value = "/v2/post/publish/video/init/",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    TikTokPublishResponse initVideoPublish(@RequestHeader("Authorization") String bearerToken,
                                           @RequestBody Map<String, Object> body);
}
