package org.linlinjava.litemall.db.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One CJ Dropshipping product-sourcing request created from the admin surface
 * (table {@code litemall_cj_sourcing_request}, created in V32).
 *
 * <p>Hand-written (not MyBatis-Generator output) and co-located with the generated
 * domains, like {@link LitemallCjDispute}. CJ owns the sourcing lifecycle; this row is
 * the local projection: what we sent to {@code product/sourcing/create}, CJ's
 * {@code cjSourcingId}, and the status fields as last synced via
 * {@code product/sourcing/query}.
 */
public class LitemallCjSourcingRequest {

    private Integer id;
    /** CJ's sourcing id from product/sourcing/create; the sourcing/query key. */
    private String cjSourcingId;
    /** CJ product id the request was created from, when sourced from our catalog. */
    private String cjPid;
    private String productName;
    private String productImage;
    private String productUrl;
    private String remark;
    private BigDecimal price;
    /** CJ sourceStatus code as last synced. */
    private String sourceStatus;
    /** CJ human-readable status as last synced. */
    private String sourceStatusStr;
    /** CJ product id once sourcing succeeds. */
    private String cjProductId;
    /** CJ variant sku once sourcing succeeds. */
    private String cjVariantSku;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private Boolean deleted;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getCjSourcingId() { return cjSourcingId; }
    public void setCjSourcingId(String cjSourcingId) { this.cjSourcingId = cjSourcingId; }

    public String getCjPid() { return cjPid; }
    public void setCjPid(String cjPid) { this.cjPid = cjPid; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getProductImage() { return productImage; }
    public void setProductImage(String productImage) { this.productImage = productImage; }

    public String getProductUrl() { return productUrl; }
    public void setProductUrl(String productUrl) { this.productUrl = productUrl; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public String getSourceStatus() { return sourceStatus; }
    public void setSourceStatus(String sourceStatus) { this.sourceStatus = sourceStatus; }

    public String getSourceStatusStr() { return sourceStatusStr; }
    public void setSourceStatusStr(String sourceStatusStr) { this.sourceStatusStr = sourceStatusStr; }

    public String getCjProductId() { return cjProductId; }
    public void setCjProductId(String cjProductId) { this.cjProductId = cjProductId; }

    public String getCjVariantSku() { return cjVariantSku; }
    public void setCjVariantSku(String cjVariantSku) { this.cjVariantSku = cjVariantSku; }

    public LocalDateTime getAddTime() { return addTime; }
    public void setAddTime(LocalDateTime addTime) { this.addTime = addTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }
}
