package org.linlinjava.litemall.db.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Scored flash-deal proposal for a daily CJ arrival
 * (table {@code litemall_deal_candidate}, V45; UNIQUE goods_id + day).
 *
 * <p>Proposals are written by the Wave-12 inventory flow's scorer and only ever become
 * deals when an admin approves them ({@code proposed} → {@code approved} creates the
 * {@code litemall_seckill} row) or die by {@code dismissed}. Never auto-created deals.
 * Plain POJO, hand-maintained, no MyBatis Generator {@code Example} support.
 */
public class LitemallDealCandidate {

    public static final String STATUS_PROPOSED = "proposed";
    public static final String STATUS_APPROVED = "approved";
    public static final String STATUS_DISMISSED = "dismissed";

    private Integer id;
    private Integer goodsId;
    private LocalDate day;
    private String tier;
    private BigDecimal score;
    private BigDecimal suggestedDealPrice;
    private String reasons;
    private String status;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private Boolean deleted;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
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

    public BigDecimal getSuggestedDealPrice() {
        return suggestedDealPrice;
    }

    public void setSuggestedDealPrice(BigDecimal suggestedDealPrice) {
        this.suggestedDealPrice = suggestedDealPrice;
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
