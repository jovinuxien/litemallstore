package org.linlinjava.litemall.db.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * CJ Dropshipping product snapshot row (table {@code litemall_cj_product}).
 *
 * <p>The {@link #pid} is the RAW CJ product id (a UUID), not the {@code cj_<pid>} OCS document id —
 * so it can be used directly to fetch CJ product detail and to reference the product when placing a
 * CJ order. The list/JSON-typed columns ({@code category_names}, {@code category_ids},
 * {@code variants_json}, {@code attributes_json}) are persisted as JSON text and parsed by the
 * goods-management layer; this is a plain POJO with no MyBatis Generator {@code Example} support.
 */
public class LitemallCjProduct {

    private String pid;
    private String source;
    private String title;
    private BigDecimal price;
    private BigDecimal discountPrice;
    private String description;
    private String imageUrl;
    private String brand;
    private String categoryNames;
    private String categoryIds;
    private String variantsJson;
    private String attributesJson;
    private String imagesJson;
    private String detailHtml;
    private LocalDateTime enrichedTime;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private Boolean deleted;
    // Ranking signals (V31), captured during detail enrichment and copied onto the promoted
    // litemall_goods row. listedNum + createTime ride the detail response (free); reviewCount +
    // rating come from a paced CJ productComments call folded into the same enrichment loop.
    private Integer listedNum;
    private Integer reviewCount;
    private BigDecimal rating;
    private LocalDateTime cjCreateTime;
    private LocalDateTime reviewsSyncedTime;

    public String getPid() {
        return pid;
    }

    public void setPid(String pid) {
        this.pid = pid;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getDiscountPrice() {
        return discountPrice;
    }

    public void setDiscountPrice(BigDecimal discountPrice) {
        this.discountPrice = discountPrice;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getCategoryNames() {
        return categoryNames;
    }

    public void setCategoryNames(String categoryNames) {
        this.categoryNames = categoryNames;
    }

    public String getCategoryIds() {
        return categoryIds;
    }

    public void setCategoryIds(String categoryIds) {
        this.categoryIds = categoryIds;
    }

    public String getVariantsJson() {
        return variantsJson;
    }

    public void setVariantsJson(String variantsJson) {
        this.variantsJson = variantsJson;
    }

    public String getAttributesJson() {
        return attributesJson;
    }

    public void setAttributesJson(String attributesJson) {
        this.attributesJson = attributesJson;
    }

    public String getImagesJson() {
        return imagesJson;
    }

    public void setImagesJson(String imagesJson) {
        this.imagesJson = imagesJson;
    }

    public String getDetailHtml() {
        return detailHtml;
    }

    public void setDetailHtml(String detailHtml) {
        this.detailHtml = detailHtml;
    }

    public LocalDateTime getEnrichedTime() {
        return enrichedTime;
    }

    public void setEnrichedTime(LocalDateTime enrichedTime) {
        this.enrichedTime = enrichedTime;
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

    public Integer getListedNum() {
        return listedNum;
    }

    public void setListedNum(Integer listedNum) {
        this.listedNum = listedNum;
    }

    public Integer getReviewCount() {
        return reviewCount;
    }

    public void setReviewCount(Integer reviewCount) {
        this.reviewCount = reviewCount;
    }

    public BigDecimal getRating() {
        return rating;
    }

    public void setRating(BigDecimal rating) {
        this.rating = rating;
    }

    public LocalDateTime getCjCreateTime() {
        return cjCreateTime;
    }

    public void setCjCreateTime(LocalDateTime cjCreateTime) {
        this.cjCreateTime = cjCreateTime;
    }

    public LocalDateTime getReviewsSyncedTime() {
        return reviewsSyncedTime;
    }

    public void setReviewsSyncedTime(LocalDateTime reviewsSyncedTime) {
        this.reviewsSyncedTime = reviewsSyncedTime;
    }
}
