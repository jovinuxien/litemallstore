package org.linlinjava.litemall.promotion.infrastructure.acl.meta;

import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.promotion.application.ports.SocialPublishPort.SocialPublishCommand;
import org.linlinjava.litemall.promotion.application.ports.SocialPublishPort.SocialPublishResult;
import org.linlinjava.litemall.promotion.infrastructure.acl.meta.dto.MetaObjectResponse;
import org.linlinjava.litemall.promotion.infrastructure.configuration.SocialProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the Meta ACL adapters — fail-soft contract (disabled /
 * unconfigured / API error → failed result, never a throw), photo-vs-feed
 * routing, and the IG container → publish flow. Recording fake client, no
 * Mockito (module convention).
 */
class MetaPublishAdaptersTest {

    private static class RecordingMetaClient implements MetaGraphClient {
        String lastCall;
        String lastCaption;
        boolean fail = false;

        private MetaObjectResponse respond(String id, String postId) {
            if (fail) {
                throw new RuntimeException("graph down");
            }
            MetaObjectResponse r = new MetaObjectResponse();
            r.setId(id);
            r.setPostId(postId);
            return r;
        }

        @Override
        public MetaObjectResponse publishPagePhoto(String pageId, String imageUrl, String caption, String token) {
            lastCall = "photo";
            lastCaption = caption;
            return respond("111", "222_333");
        }

        @Override
        public MetaObjectResponse publishPageFeed(String pageId, String message, String link, String token) {
            lastCall = "feed";
            lastCaption = message;
            return respond("222_444", null);
        }

        @Override
        public MetaObjectResponse createIgMediaContainer(String igUserId, String imageUrl, String caption, String token) {
            lastCall = "ig-container";
            lastCaption = caption;
            return respond("555", null);
        }

        @Override
        public MetaObjectResponse publishIgMedia(String igUserId, String creationId, String token) {
            lastCall = "ig-publish:" + creationId;
            return respond("666", null);
        }
    }

    private SocialProperties configuredProps() {
        SocialProperties props = new SocialProperties();
        props.getMeta().setEnabled(true);
        props.getMeta().setPageId("page-1");
        props.getMeta().setPageAccessToken("token");
        props.getMeta().setIgUserId("ig-1");
        return props;
    }

    private SocialPublishCommand command(String imageUrl) {
        return new SocialPublishCommand(7, "caption", imageUrl, null, "http://s/p/7?utm_source=facebook");
    }

    @Test
    void disabledAdapterFailsSoftWithClearError() {
        RecordingMetaClient client = new RecordingMetaClient();
        MetaFacebookPublishAdapter adapter = new MetaFacebookPublishAdapter(client, new SocialProperties());
        SocialPublishResult result = adapter.publish(command("http://img"));
        assertFalse(result.success());
        assertTrue(result.error().contains("disabled"), result.error());
    }

    @Test
    void facebookPostsPhotoWhenImagePicked_linkRidesTheCaption() {
        RecordingMetaClient client = new RecordingMetaClient();
        MetaFacebookPublishAdapter adapter = new MetaFacebookPublishAdapter(client, configuredProps());
        SocialPublishResult result = adapter.publish(command("http://img"));
        assertTrue(result.success());
        assertEquals("photo", client.lastCall);
        assertEquals("222_333", result.externalPostId(), "post_id preferred over object id");
        assertTrue(client.lastCaption.contains("utm_source=facebook"), "share link woven into the caption");
    }

    @Test
    void facebookFallsBackToFeedPostWithoutImage() {
        RecordingMetaClient client = new RecordingMetaClient();
        MetaFacebookPublishAdapter adapter = new MetaFacebookPublishAdapter(client, configuredProps());
        SocialPublishResult result = adapter.publish(command(null));
        assertTrue(result.success());
        assertEquals("feed", client.lastCall);
        assertEquals("222_444", result.externalPostId());
    }

    @Test
    void facebookApiErrorBecomesFailedResult() {
        RecordingMetaClient client = new RecordingMetaClient();
        client.fail = true;
        MetaFacebookPublishAdapter adapter = new MetaFacebookPublishAdapter(client, configuredProps());
        SocialPublishResult result = adapter.publish(command("http://img"));
        assertFalse(result.success());
        assertTrue(result.error().contains("graph down"), result.error());
    }

    @Test
    void errorMessagesNeverLeakTheAccessToken() {
        RecordingMetaClient client = new RecordingMetaClient() {
            @Override
            public MetaObjectResponse publishPagePhoto(String pageId, String imageUrl, String caption, String token) {
                throw new RuntimeException(
                        "[400] during [POST] to [https://graph.facebook.com/v19.0/1/photos?caption=x&access_token=EAASECRETTOKEN123]");
            }
        };
        MetaFacebookPublishAdapter adapter = new MetaFacebookPublishAdapter(client, configuredProps());
        SocialPublishResult result = adapter.publish(command("http://img"));
        assertFalse(result.success());
        assertFalse(result.error().contains("EAASECRETTOKEN123"), "token redacted from the ledger error");
        assertTrue(result.error().contains("access_token=***"), result.error());
    }

    @Test
    void instagramRequiresAnImage() {
        RecordingMetaClient client = new RecordingMetaClient();
        MetaInstagramPublishAdapter adapter = new MetaInstagramPublishAdapter(client, configuredProps());
        SocialPublishResult result = adapter.publish(command(null));
        assertFalse(result.success());
        assertTrue(result.error().contains("requires an image"), result.error());
    }

    @Test
    void instagramPublishesTheCreatedContainer() {
        RecordingMetaClient client = new RecordingMetaClient();
        MetaInstagramPublishAdapter adapter = new MetaInstagramPublishAdapter(client, configuredProps());
        SocialPublishResult result = adapter.publish(command("http://img"));
        assertTrue(result.success());
        assertEquals("ig-publish:555", client.lastCall, "publishes the container created in step 1");
        assertEquals("666", result.externalPostId());
    }
}
