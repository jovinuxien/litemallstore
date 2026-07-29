package org.linlinjava.litemall.db.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Per-L1-category margin override (table {@code litemall_category_margin}, V46;
 * PK = category_id, the L1 root).
 *
 * <p>Retail = cost × margin. An existing row overrides the global
 * {@code spring.cjdropship.pricing.margin} for every goods whose category resolves to
 * this L1 root; overrides take effect at the nightly reprice sites. Rows are
 * hard-deleted on removal — no soft-delete column by design. Plain POJO,
 * hand-maintained, no MyBatis Generator {@code Example} support.
 */
public class LitemallCategoryMargin {

    private Integer categoryId;
    private BigDecimal margin;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;

    public Integer getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Integer categoryId) {
        this.categoryId = categoryId;
    }

    public BigDecimal getMargin() {
        return margin;
    }

    public void setMargin(BigDecimal margin) {
        this.margin = margin;
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
}
