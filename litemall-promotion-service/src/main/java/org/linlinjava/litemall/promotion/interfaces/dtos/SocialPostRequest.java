package org.linlinjava.litemall.promotion.interfaces.dtos;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** Body of {@code POST /srv/private/admin/social/post} (the composer's submit). */
@Getter
@Setter
public class SocialPostRequest {

    private Integer goodsId;
    /** Blank/null falls back to the templated caption from compose-preview. */
    private String caption;
    /** The picked image (meta_fb/meta_ig); TikTok always publishes the goods' video. */
    private String mediaUrl;
    /** Contract values: {@code meta_fb | meta_ig | tiktok}. */
    private List<String> platforms;
}
