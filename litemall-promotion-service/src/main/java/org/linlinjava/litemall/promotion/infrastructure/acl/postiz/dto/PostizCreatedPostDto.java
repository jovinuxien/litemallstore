package org.linlinjava.litemall.promotion.infrastructure.acl.postiz.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

/**
 * One element of the {@code POST /public/v1/posts} response array:
 * {@code {postId, integration}} — the created Postiz post id paired with the
 * integration (channel) id it targets.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public class PostizCreatedPostDto {

    private String postId;

    /** The integration (channel) id this post was created for. */
    private String integration;
}
