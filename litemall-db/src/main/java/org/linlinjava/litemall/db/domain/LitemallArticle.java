package org.linlinjava.litemall.db.domain;

import java.time.LocalDateTime;

/**
 * Article CMS row ({@code litemall_article}, V36). Hand-written slim domain
 * (CjSourcingRequest pattern) — NOT MyBatis-Generator output, no Example class.
 *
 * <p>{@code content} is SANITIZED HTML: goods-management cleans it with a jsoup
 * custom Safelist at write time (clean-and-store), so readers may inject it.
 * {@code viewCount} is bumped via the mapper's atomic
 * {@code view_count = view_count + 1} statement — never read-modify-write.
 * {@code status}: {@code published} | {@code hidden}.
 */
public class LitemallArticle {

    public static final String STATUS_PUBLISHED = "published";
    public static final String STATUS_HIDDEN = "hidden";

    private Integer id;
    private Integer categoryId;
    private String title;
    private String summary;
    /** Cover image URL. */
    private String picUrl;
    /** Sanitized HTML (jsoup custom Safelist, cleaned at write time). */
    private String content;
    /** published | hidden. */
    private String status;
    private Boolean isHot;
    private Boolean isBanner;
    /** Related goods id, 0 = none. */
    private Integer goodsId;
    private Integer viewCount;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private Boolean deleted;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Integer categoryId) {
        this.categoryId = categoryId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getPicUrl() {
        return picUrl;
    }

    public void setPicUrl(String picUrl) {
        this.picUrl = picUrl;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getIsHot() {
        return isHot;
    }

    public void setIsHot(Boolean isHot) {
        this.isHot = isHot;
    }

    public Boolean getIsBanner() {
        return isBanner;
    }

    public void setIsBanner(Boolean isBanner) {
        this.isBanner = isBanner;
    }

    public Integer getGoodsId() {
        return goodsId;
    }

    public void setGoodsId(Integer goodsId) {
        this.goodsId = goodsId;
    }

    public Integer getViewCount() {
        return viewCount;
    }

    public void setViewCount(Integer viewCount) {
        this.viewCount = viewCount;
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
