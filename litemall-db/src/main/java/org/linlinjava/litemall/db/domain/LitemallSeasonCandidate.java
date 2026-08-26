package org.linlinjava.litemall.db.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A scored seasonal proposal ({@code litemall_season_candidate}, V64;
 * UNIQUE season_key + goods_id + day).
 *
 * <p>Written by the nightly scorer for EVERY enabled season, not only the running one, so the
 * next season's page is populated before anyone activates it. Rows at {@link #STATUS_AUTO} are
 * what the search index publishes in its multi-valued {@code seasons} field;
 * {@link #STATUS_DISMISSED} is a permanent admin veto that a re-run never overturns.
 *
 * <p>{@code configVersionHash} fingerprints the rule that produced the score, and
 * {@code configSnapshot} carries the effective weights BESIDE it on purpose: a hash alone
 * resolves to nothing once the rule it names has been edited or deleted.
 *
 * <p>Plain POJO, hand-maintained, no MyBatis Generator {@code Example} support.
 */
public class LitemallSeasonCandidate {

    public static final String STATUS_PROPOSED = "proposed";
    public static final String STATUS_AUTO = "auto";
    public static final String STATUS_DISMISSED = "dismissed";
    
    public static final String TIER_HOT = "hot";
    public static final String TIER_FEATURED = "featured";
    public static final String TIER_WATCH = "watch";

    private Integer id;
    private String seasonKey;
    private Integer goodsId;
    private LocalDate day;
    private String tier;
    private BigDecimal score;
    private String reasons;
    private String status;
    private String configVersionHash;
    private String configSnapshot;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private Boolean deleted;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getSeasonKey() {
        return seasonKey;
    }

    public void setSeasonKey(String seasonKey) {
        this.seasonKey = seasonKey;
    }

    public Integer getGoodsId() {
        return goodsId;
    }

    public void setGoodsId(Integer goodsId) {
        this.goodsId = goodsId;
    }

    public LocalDate getDay() {
        return day;
    }

    public void setDay(LocalDate day) {
        this.day = day;
    }

    public String getTier() {
        return tier;
    }

    public void setTier(String tier) {
        this.tier = tier;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }

    public String getReasons() {
        return reasons;
    }

    public void setReasons(String reasons) {
        this.reasons = reasons;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getConfigVersionHash() {
        return configVersionHash;
    }

    public void setConfigVersionHash(String configVersionHash) {
        this.configVersionHash = configVersionHash;
    }

    public String getConfigSnapshot() {
        return configSnapshot;
    }

    public void setConfigSnapshot(String configSnapshot) {
        this.configSnapshot = configSnapshot;
    }

    public LocalDateTime getAddTime() {
        return addTime;
    }

    public void setAddTime(LocalDateTime addTime) {
        this.addTime = addTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public Boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }

}
