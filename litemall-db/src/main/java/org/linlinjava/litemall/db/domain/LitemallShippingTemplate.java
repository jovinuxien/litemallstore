package org.linlinjava.litemall.db.domain;

import java.time.LocalDateTime;

/**
 * One freight template (table {@code litemall_shipping_templates}, created in V8,
 * {@code is_default} added in V34).
 *
 * <p>Hand-written (not MyBatis-Generator output) and co-located with the generated
 * domains, like {@link LitemallCjDispute}. Goods bind via
 * {@code litemall_goods.temp_id}; 0 falls back to the single default template.
 */
public class LitemallShippingTemplate {

    private Integer id;
    private String name;
    /** Billing type: 1 = by piece, 2 = by weight (kg), 3 = by volume (m3). */
    private Integer type;
    /** Whether free-shipping threshold rows apply (litemall_shipping_templates_free). */
    private Integer appoint;
    /** V34: default template for goods with temp_id=0 (at most one row set). */
    private Boolean isDefault;
    private Integer sort;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private Boolean deleted;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getType() { return type; }
    public void setType(Integer type) { this.type = type; }

    public Integer getAppoint() { return appoint; }
    public void setAppoint(Integer appoint) { this.appoint = appoint; }

    public Boolean getIsDefault() { return isDefault; }
    public void setIsDefault(Boolean isDefault) { this.isDefault = isDefault; }

    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }

    public LocalDateTime getAddTime() { return addTime; }
    public void setAddTime(LocalDateTime addTime) { this.addTime = addTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }
}
