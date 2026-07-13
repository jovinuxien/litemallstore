package org.linlinjava.litemall.db.domain;

import java.time.LocalDateTime;

/**
 * Article CMS category ({@code litemall_article_category}, V36). Hand-written
 * slim domain (CjSourcingRequest pattern) — NOT MyBatis-Generator output, no
 * Example class. Flat list (declared deviation from crmeb's shared category
 * tree). See litemall-goods-management/docs/handoff-content-endpoints.md.
 */
public class LitemallArticleCategory {

    private Integer id;
    private String name;
    private Integer sortOrder;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private Boolean deleted;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
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
