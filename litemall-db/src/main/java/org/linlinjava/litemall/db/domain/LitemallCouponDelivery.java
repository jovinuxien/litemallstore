package org.linlinjava.litemall.db.domain;

import java.time.LocalDateTime;

/**
 * Targeted coupon delivery run (table {@code litemall_coupon_delivery}, V58,
 * Wave 22 — coupon roadmap Phase 4).
 *
 * <p>Written once per admin "deliver to segment" sweep: {@code segmentJson}
 * records the criteria (recencyDays / minFrequency / minMonetary over paid
 * orders), {@code matched}/{@code granted}/{@code skipped} record the outcome.
 * Preview runs never write a row. Plain POJO, hand-maintained, no MyBatis
 * Generator {@code Example} support (same discipline as
 * {@link LitemallPromoCandidate}).
 */
public class LitemallCouponDelivery {

    private Integer id;
    private Integer couponId;
    private String segmentJson;
    private Integer matched;
    private Integer granted;
    private Integer skipped;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private Boolean deleted;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getCouponId() {
        return couponId;
    }

    public void setCouponId(Integer couponId) {
        this.couponId = couponId;
    }

    public String getSegmentJson() {
        return segmentJson;
    }

    public void setSegmentJson(String segmentJson) {
        this.segmentJson = segmentJson;
    }

    public Integer getMatched() {
        return matched;
    }

    public void setMatched(Integer matched) {
        this.matched = matched;
    }

    public Integer getGranted() {
        return granted;
    }

    public void setGranted(Integer granted) {
        this.granted = granted;
    }

    public Integer getSkipped() {
        return skipped;
    }

    public void setSkipped(Integer skipped) {
        this.skipped = skipped;
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

    @Override
    public String toString() {
        return "LitemallCouponDelivery{id=" + id
                + ", couponId=" + couponId
                + ", segmentJson=" + segmentJson
                + ", matched=" + matched
                + ", granted=" + granted
                + ", skipped=" + skipped
                + "}";
    }
}
