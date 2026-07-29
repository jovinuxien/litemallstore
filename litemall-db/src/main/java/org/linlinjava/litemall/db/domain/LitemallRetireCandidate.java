package org.linlinjava.litemall.db.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Scored retirement (off-sale) proposal for a weak catalog goods
 * (table {@code litemall_retire_candidate}, V46; UNIQUE goods_id + day).
 *
 * <p>Proposals are written by the Wave-14 inventory-flow scorer/governor and only ever
 * act when an admin approves them ({@code proposed} → {@code approved} with an
 * {@code executeOn} date; the daily executor flips due batches off-sale and marks them
 * {@code executed}) or die by {@code dismissed}. Retirement is reversible via the
 * normal admin on-sale toggle. Plain POJO, hand-maintained, no MyBatis Generator
 * {@code Example} support.
 */
public class LitemallRetireCandidate {

    public static final String STATUS_PROPOSED = "proposed";
    public static final String STATUS_APPROVED = "approved";
    public static final String STATUS_DISMISSED = "dismissed";
    public static final String STATUS_EXECUTED = "executed";

    private Integer id;
    private Integer goodsId;
    private LocalDate day;
    private BigDecimal score;
    private String reasons;
    private String status;
    private LocalDate executeOn;
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

    public LocalDate getExecuteOn() {
        return executeOn;
    }

    public void setExecuteOn(LocalDate executeOn) {
        this.executeOn = executeOn;
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
