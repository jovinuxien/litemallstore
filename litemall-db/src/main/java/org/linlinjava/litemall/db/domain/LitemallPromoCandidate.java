package org.linlinjava.litemall.db.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Scored coupon/groupon merchandising proposal
 * (table {@code litemall_promo_candidate}, V53; UNIQUE kind + goods_id + day).
 *
 * <p>Written by the Wave-19 nightly scorer over the inventory-intelligence data.
 * A proposal is only ever acted on by an admin: {@code proposed} → {@code consumed}
 * (the admin created the real coupon/combination through the existing promotion
 * paths; {@code refId} records it) or {@code proposed} → {@code dismissed}.
 * Plain POJO, hand-maintained, no MyBatis Generator {@code Example} support.
 */
public class LitemallPromoCandidate {

    public static final String KIND_COUPON = "coupon";
    public static final String KIND_GROUPON = "groupon";

    public static final String STATUS_PROPOSED = "proposed";
    public static final String STATUS_DISMISSED = "dismissed";
    public static final String STATUS_CONSUMED = "consumed";

    private Integer id;
    private String kind;
    private Integer goodsId;
    private LocalDate day;
    private String tier;
    private BigDecimal score;
    private String suggestion;
    private String reasons;
    private String status;
    private Integer refId;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private Boolean deleted;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
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

    public String getSuggestion() {
        return suggestion;
    }

    public void setSuggestion(String suggestion) {
        this.suggestion = suggestion;
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

    public Integer getRefId() {
        return refId;
    }

    public void setRefId(Integer refId) {
        this.refId = refId;
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

    public Boolean getDeleted() {
        return deleted;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }
}
