package org.linlinjava.litemall.promotion.interfaces.dtos;

import lombok.Builder;
import lombok.Getter;

/**
 * Per-platform outcome of a composer post or a retry. The envelope is always
 * 2xx — {@code status=failed} + {@code error} is the honest per-platform
 * verdict (adapter disabled, missing media, API error), never a 5xx.
 */
@Getter
@Builder
public class SocialPublishResultDtoResponse {

    /** {@code meta_fb | meta_ig | tiktok}. */
    private final String platform;
    /** The ledger row created/updated for this attempt. */
    private final Integer postId;
    /** {@code posted | failed}. */
    private final String status;
    private final String externalPostId;
    private final String error;
}
