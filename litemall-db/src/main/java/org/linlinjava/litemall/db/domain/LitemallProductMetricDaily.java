package org.linlinjava.litemall.db.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Per-goods daily inventory/margin/engagement metric row
 * (table {@code litemall_product_metric_daily}, V45; PK = goods_id + day).
 *
 * <p>Written by the Wave-12 inventory flow, one idempotent upsert per goods per day, from
 * the product's arrival date onward. {@code cost}/{@code marginPct} stay NULL while the
 * wholesale cost has not been captured yet — consumers must render them as null, never 0%.
 * Plain POJO, hand-maintained, no MyBatis Generator {@code Example} support.
 */
public class LitemallProductMetricDaily {

    private Integer goodsId;
    private LocalDate day;
    private BigDecimal retailPrice;
    private BigDecimal cost;
    private BigDecimal marginPct;
    private Integer stockTotal;
    private Boolean available;
    private Integer views;
    private Integer salesQty;
    private LocalDateTime updateTime;

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

    public BigDecimal getRetailPrice() {
        return retailPrice;
    }

    public void setRetailPrice(BigDecimal retailPrice) {
        this.retailPrice = retailPrice;
    }

    public BigDecimal getCost() {
        return cost;
    }

    public void setCost(BigDecimal cost) {
        this.cost = cost;
    }

    public BigDecimal getMarginPct() {
        return marginPct;
    }

    public void setMarginPct(BigDecimal marginPct) {
        this.marginPct = marginPct;
    }

    public Integer getStockTotal() {
        return stockTotal;
    }

    public void setStockTotal(Integer stockTotal) {
        this.stockTotal = stockTotal;
    }

    public Boolean getAvailable() {
        return available;
    }

    public void setAvailable(Boolean available) {
        this.available = available;
    }

    public Integer getViews() {
        return views;
    }

    public void setViews(Integer views) {
        this.views = views;
    }

    public Integer getSalesQty() {
        return salesQty;
    }

    public void setSalesQty(Integer salesQty) {
        this.salesQty = salesQty;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
