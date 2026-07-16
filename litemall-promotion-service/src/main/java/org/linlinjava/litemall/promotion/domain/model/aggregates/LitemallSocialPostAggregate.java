package org.linlinjava.litemall.promotion.domain.model.aggregates;

import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallSocialPlatform;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallSocialPostStatus;

import java.time.LocalDateTime;

/**
 * One social publish attempt — a row of the {@code litemall_social_post} ledger
 * (V42). Mostly a typed record: the interesting invariants (guarded status
 * transitions, auto-post dedupe) are enforced at the mapper/SQL level so they
 * hold across concurrent instances; {@link #isRetryable()} mirrors the retry
 * guard for the admin surface.
 */
@Getter
@Setter
public class LitemallSocialPostAggregate {

    private Integer id;
    private Integer goodsId;
    private LitemallSocialPlatform platform;
    private String caption;
    /** Image URL for meta_fb/meta_ig; the goods' video URL for tiktok. */
    private String mediaUrl;
    /** Share URL carrying the shared UTM convention. */
    private String linkUrl;
    private LitemallSocialPostStatus status;
    private String externalPostId;
    private String error;
    /** Admin id, or the literal {@code "auto"} for the deal auto-poster. */
    private String postedBy;
    /** The seckill (flash deal) that triggered an auto post; null for manual posts. */
    private Integer dealId;
    /** True while this is the armed auto row of its deal's current activation. */
    private boolean autoActive;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;

    public static final String POSTED_BY_AUTO = "auto";

    public boolean isRetryable() {
        return status == LitemallSocialPostStatus.FAILED;
    }
}
