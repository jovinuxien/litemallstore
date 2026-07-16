package org.linlinjava.litemall.db.domain;

import java.time.LocalDateTime;

/**
 * Hand-written entity for the social publish ledger ({@code litemall_social_post},
 * V42). NOT MyBatis-Generator output — litemall-db is hand-maintained; keep this
 * class and {@code SocialPostMapper.xml} in sync when columns change (the
 * {@code now_money} lesson).
 *
 * <p>{@code platform} is one of {@code meta_fb | meta_ig | tiktok}; {@code status}
 * one of {@code draft | posted | failed}. {@code postedBy} is the admin id, or the
 * literal {@code "auto"} for the flash-deal auto-poster, in which case
 * {@code dealId}/{@code autoActive} carry the restart-safe dedupe state.
 */
public class LitemallSocialPost {

    private Integer id;
    private Integer goodsId;
    private String platform;
    private String caption;
    private String mediaUrl;
    private String linkUrl;
    private String status;
    private String externalPostId;
    private String error;
    private String postedBy;
    private Integer dealId;
    private Boolean autoActive;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private Boolean deleted;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getGoodsId() { return goodsId; }
    public void setGoodsId(Integer goodsId) { this.goodsId = goodsId; }

    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }

    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }

    public String getMediaUrl() { return mediaUrl; }
    public void setMediaUrl(String mediaUrl) { this.mediaUrl = mediaUrl; }

    public String getLinkUrl() { return linkUrl; }
    public void setLinkUrl(String linkUrl) { this.linkUrl = linkUrl; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getExternalPostId() { return externalPostId; }
    public void setExternalPostId(String externalPostId) { this.externalPostId = externalPostId; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public String getPostedBy() { return postedBy; }
    public void setPostedBy(String postedBy) { this.postedBy = postedBy; }

    public Integer getDealId() { return dealId; }
    public void setDealId(Integer dealId) { this.dealId = dealId; }

    public Boolean getAutoActive() { return autoActive; }
    public void setAutoActive(Boolean autoActive) { this.autoActive = autoActive; }

    public LocalDateTime getAddTime() { return addTime; }
    public void setAddTime(LocalDateTime addTime) { this.addTime = addTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }
}
