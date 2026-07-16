package org.linlinjava.litemall.promotion.interfaces.dtos;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** One social-post ledger row (the gateway-admin "Social posts" page). */
@Getter
@Builder
public class SocialPostDtoResponse {

    private final Integer id;
    private final Integer goodsId;
    /** {@code meta_fb | meta_ig | tiktok}. */
    private final String platform;
    private final String caption;
    private final String mediaUrl;
    private final String linkUrl;
    /** {@code draft | posted | failed}. */
    private final String status;
    /** Platform post/publish id on success (drives the admin link-out). */
    private final String externalPostId;
    private final String error;
    /** Admin id, or the literal {@code auto} (deal auto-poster badge). */
    private final String postedBy;
    /** Flash deal that triggered an auto post; null for manual posts without a live deal. */
    private final Integer dealId;
    private final LocalDateTime addTime;
    private final LocalDateTime updateTime;
}
