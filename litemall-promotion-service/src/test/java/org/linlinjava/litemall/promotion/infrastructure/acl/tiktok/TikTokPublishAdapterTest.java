package org.linlinjava.litemall.promotion.infrastructure.acl.tiktok;

import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.promotion.application.ports.SocialPublishPort.SocialPublishCommand;
import org.linlinjava.litemall.promotion.application.ports.SocialPublishPort.SocialPublishResult;
import org.linlinjava.litemall.promotion.infrastructure.acl.tiktok.dto.TikTokPublishResponse;
import org.linlinjava.litemall.promotion.infrastructure.configuration.SocialProperties;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the TikTok ACL adapter — the video gate, the in-body error
 * envelope (TikTok logical failures ride a 200), and the fail-soft contract.
 */
class TikTokPublishAdapterTest {

    private static class RecordingTikTokClient implements TikTokContentClient {
        Map<String, Object> lastBody;
        String errorCode = "ok";
        boolean throwTransport = false;

        @Override
        @SuppressWarnings("unchecked")
        public TikTokPublishResponse initVideoPublish(String bearerToken, Map<String, Object> body) {
            if (throwTransport) {
                throw new RuntimeException("tiktok down");
            }
            lastBody = body;
            TikTokPublishResponse response = new TikTokPublishResponse();
            TikTokPublishResponse.Error error = new TikTokPublishResponse.Error();
            error.setCode(errorCode);
            error.setMessage("spam_risk_too_many_posts".equals(errorCode) ? "too many posts" : "");
            response.setError(error);
            if ("ok".equals(errorCode)) {
                TikTokPublishResponse.Data data = new TikTokPublishResponse.Data();
                data.setPublishId("v_pub_123");
                response.setData(data);
            }
            return response;
        }
    }

    private SocialProperties configuredProps() {
        SocialProperties props = new SocialProperties();
        props.getTiktok().setEnabled(true);
        props.getTiktok().setAccessToken("act-token");
        return props;
    }

    private SocialPublishCommand command(String videoUrl) {
        return new SocialPublishCommand(7, "caption", null, videoUrl, "http://s/p/7?utm_source=tiktok");
    }

    @Test
    void disabledAdapterFailsSoftWithClearError() {
        TikTokPublishAdapter adapter = new TikTokPublishAdapter(new RecordingTikTokClient(), new SocialProperties());
        SocialPublishResult result = adapter.publish(command("http://video"));
        assertFalse(result.success());
        assertTrue(result.error().contains("disabled"), result.error());
    }

    @Test
    void videoLessGoodsIsGated() {
        TikTokPublishAdapter adapter = new TikTokPublishAdapter(new RecordingTikTokClient(), configuredProps());
        SocialPublishResult result = adapter.publish(command(null));
        assertFalse(result.success());
        assertTrue(result.error().contains("requires a video"), result.error());
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishesViaPullFromUrl() {
        RecordingTikTokClient client = new RecordingTikTokClient();
        TikTokPublishAdapter adapter = new TikTokPublishAdapter(client, configuredProps());
        SocialPublishResult result = adapter.publish(command("http://video.mp4"));
        assertTrue(result.success());
        assertEquals("v_pub_123", result.externalPostId());
        Map<String, Object> sourceInfo = (Map<String, Object>) client.lastBody.get("source_info");
        assertEquals("PULL_FROM_URL", sourceInfo.get("source"));
        assertEquals("http://video.mp4", sourceInfo.get("video_url"));
        Map<String, Object> postInfo = (Map<String, Object>) client.lastBody.get("post_info");
        assertEquals("SELF_ONLY", postInfo.get("privacy_level"), "unaudited-app default");
    }

    @Test
    void inBodyErrorEnvelopeBecomesFailedResult() {
        RecordingTikTokClient client = new RecordingTikTokClient();
        client.errorCode = "spam_risk_too_many_posts";
        TikTokPublishAdapter adapter = new TikTokPublishAdapter(client, configuredProps());
        SocialPublishResult result = adapter.publish(command("http://video.mp4"));
        assertFalse(result.success());
        assertTrue(result.error().contains("spam_risk_too_many_posts"), result.error());
    }

    @Test
    void transportErrorBecomesFailedResult() {
        RecordingTikTokClient client = new RecordingTikTokClient();
        client.throwTransport = true;
        TikTokPublishAdapter adapter = new TikTokPublishAdapter(client, configuredProps());
        SocialPublishResult result = adapter.publish(command("http://video.mp4"));
        assertFalse(result.success());
        assertTrue(result.error().contains("tiktok down"), result.error());
    }
}
