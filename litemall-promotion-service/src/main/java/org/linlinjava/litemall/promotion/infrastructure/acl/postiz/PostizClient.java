package org.linlinjava.litemall.promotion.infrastructure.acl.postiz;

import org.linlinjava.litemall.promotion.infrastructure.acl.postiz.dto.PostizCreatePostRequest;
import org.linlinjava.litemall.promotion.infrastructure.acl.postiz.dto.PostizCreatedPostDto;
import org.linlinjava.litemall.promotion.infrastructure.acl.postiz.dto.PostizIntegrationDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * Feign client over the Postiz public API (Wave 17). Base URL from
 * {@code litemall.postiz.base-url} (includes the {@code /api/public/v1}
 * segment); the fallback default is never reached — {@link PostizAdapter}
 * guards every call on {@code PostizProperties.isConfigured()}. Auth is the
 * BARE api key in the {@code Authorization} header (no {@code Bearer} prefix),
 * added by {@link PostizFeignConfig}. Wrapped by {@link PostizAdapter} so
 * application code depends only on {@code PostizPort}, never on this client.
 */
@FeignClient(
        name = "postiz",
        url = "${litemall.postiz.base-url:http://localhost:4007/api/public/v1}",
        configuration = PostizFeignConfig.class)
public interface PostizClient {

    /** The org's connected integrations (channels). */
    @GetMapping("/integrations")
    List<PostizIntegrationDto> listIntegrations();

    /**
     * Create one post targeting N channels in a single call; returns
     * {@code [{postId, integration}]} pairs. Validation 400s are structured
     * ({@code {statusCode, provider, name, message}}) and surface as
     * FeignExceptions the adapter decodes.
     */
    @PostMapping("/posts")
    List<PostizCreatedPostDto> createPost(@RequestBody PostizCreatePostRequest request);
}
