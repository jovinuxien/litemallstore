package org.linlinjava.litemall.db.domain;

import java.time.LocalDateTime;

/**
 * Hand-written entity for the Postiz publish ledger ({@code litemall_postiz_post},
 * V50). NOT MyBatis-Generator output — litemall-db is hand-maintained; keep this
 * class and {@code PostizPostMapper.xml} in sync when columns change.
 *
 * <p>{@code integrationId} is the Postiz channel id (a cuid string);
 * {@code channelIdentifier} its provider identifier ({@code facebook | x | ...}).
 * {@code status} is {@code scheduled | failed} — Postiz owns the post lifecycle
 * after acceptance, so the ledger records the handoff. {@code scheduleTime} is
 * stored as UTC (Postiz forces TZ=UTC).
 */
public class LitemallPostizPost {

    private Integer id;
    private Integer goodsId;
    private Integer categoryId;
    private String integrationId;
    private String channelIdentifier;
    private String postizPostId;
    private LocalDateTime scheduleTime;
    private String status;
    private String error;
    private String postedBy;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private Boolean deleted;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getGoodsId() { return goodsId; }
    public void setGoodsId(Integer goodsId) { this.goodsId = goodsId; }

    public Integer getCategoryId() { return categoryId; }
    public void setCategoryId(Integer categoryId) { this.categoryId = categoryId; }

    public String getIntegrationId() { return integrationId; }
    public void setIntegrationId(String integrationId) { this.integrationId = integrationId; }

    public String getChannelIdentifier() { return channelIdentifier; }
    public void setChannelIdentifier(String channelIdentifier) { this.channelIdentifier = channelIdentifier; }

    public String getPostizPostId() { return postizPostId; }
    public void setPostizPostId(String postizPostId) { this.postizPostId = postizPostId; }

    public LocalDateTime getScheduleTime() { return scheduleTime; }
    public void setScheduleTime(LocalDateTime scheduleTime) { this.scheduleTime = scheduleTime; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public String getPostedBy() { return postedBy; }
    public void setPostedBy(String postedBy) { this.postedBy = postedBy; }

    public LocalDateTime getAddTime() { return addTime; }
    public void setAddTime(LocalDateTime addTime) { this.addTime = addTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }
}
