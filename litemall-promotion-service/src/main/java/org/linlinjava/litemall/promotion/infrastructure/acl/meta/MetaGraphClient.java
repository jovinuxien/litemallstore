package org.linlinjava.litemall.promotion.infrastructure.acl.meta;

import org.linlinjava.litemall.promotion.infrastructure.acl.meta.dto.MetaObjectResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Feign client over the Meta Graph API (Wave-6 social posting). One business
 * app serves both surfaces: Facebook Page publishing and Instagram
 * content publishing (container → publish). Host from
 * {@code litemall.promotion.social.meta.base-url} (version-pinned, overridable);
 * auth is the page access token passed per-call — no interceptor, since the
 * same client publishes to the page and the IG account. Wrapped by the
 * {@code Meta*PublishAdapter} ACLs so application code depends only on
 * {@code SocialPublishPort}, never on this client.
 *
 * <p>Graph API accepts parameters on the query string for POSTs; error
 * responses are non-2xx JSON envelopes surfacing as FeignExceptions, which the
 * adapters turn into failed ledger rows (fail-soft).
 */
@FeignClient(
        name = "meta-graph",
        url = "${litemall.promotion.social.meta.base-url:https://graph.facebook.com/v19.0}")
public interface MetaGraphClient {

    /** Photo post on the page feed: caption + image fetched by Meta from the URL. */
    @PostMapping("/{pageId}/photos")
    MetaObjectResponse publishPagePhoto(@PathVariable("pageId") String pageId,
                                        @RequestParam("url") String imageUrl,
                                        @RequestParam("caption") String caption,
                                        @RequestParam("access_token") String accessToken);

    /** Plain feed post (no image): message + link (Meta renders the link preview). */
    @PostMapping("/{pageId}/feed")
    MetaObjectResponse publishPageFeed(@PathVariable("pageId") String pageId,
                                       @RequestParam("message") String message,
                                       @RequestParam("link") String link,
                                       @RequestParam("access_token") String accessToken);

    /** IG step 1: create a media container for the image + caption. */
    @PostMapping("/{igUserId}/media")
    MetaObjectResponse createIgMediaContainer(@PathVariable("igUserId") String igUserId,
                                              @RequestParam("image_url") String imageUrl,
                                              @RequestParam("caption") String caption,
                                              @RequestParam("access_token") String accessToken);

    /** IG step 2: publish the container. */
    @PostMapping("/{igUserId}/media_publish")
    MetaObjectResponse publishIgMedia(@PathVariable("igUserId") String igUserId,
                                      @RequestParam("creation_id") String creationId,
                                      @RequestParam("access_token") String accessToken);
}
