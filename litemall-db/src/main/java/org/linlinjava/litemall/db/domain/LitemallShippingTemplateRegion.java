package org.linlinjava.litemall.db.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One region-pricing row of a freight template (table
 * {@code litemall_shipping_templates_region}, created in V8; {@code country_code} /
 * {@code province_name} added in V34).
 *
 * <p>Hand-written (not MyBatis-Generator output), like {@link LitemallCjDispute}.
 * Rows match on (countryCode, provinceName): countryCode '*' = any country,
 * provinceName NULL = whole country. The legacy {@code province} JSON blob is kept
 * mapped but unused by the V34 matcher.
 */
public class LitemallShippingTemplateRegion {

    private Integer id;
    /** litemall_shipping_templates.id */
    private Integer tempId;
    /** V34: ISO country code this row applies to, '*' = any. */
    private String countryCode;
    /** V34: free-text province/state name (case-insensitive match), NULL = whole country. */
    private String provinceName;
    /** Legacy province JSON blob (pre-V34 matcher); retained untouched. */
    private String province;
    /** First unit count (pieces / kg / m3 depending on template type). */
    private BigDecimal first;
    /** Price for the first units. */
    private BigDecimal firstPrice;
    /** Additional unit count (column continue_p; `continue` is reserved in Java). */
    private BigDecimal continueP;
    /** Price per additional unit block. */
    private BigDecimal continuePrice;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private Boolean deleted;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getTempId() { return tempId; }
    public void setTempId(Integer tempId) { this.tempId = tempId; }

    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }

    public String getProvinceName() { return provinceName; }
    public void setProvinceName(String provinceName) { this.provinceName = provinceName; }

    public String getProvince() { return province; }
    public void setProvince(String province) { this.province = province; }

    public BigDecimal getFirst() { return first; }
    public void setFirst(BigDecimal first) { this.first = first; }

    public BigDecimal getFirstPrice() { return firstPrice; }
    public void setFirstPrice(BigDecimal firstPrice) { this.firstPrice = firstPrice; }

    public BigDecimal getContinueP() { return continueP; }
    public void setContinueP(BigDecimal continueP) { this.continueP = continueP; }

    public BigDecimal getContinuePrice() { return continuePrice; }
    public void setContinuePrice(BigDecimal continuePrice) { this.continuePrice = continuePrice; }

    public LocalDateTime getAddTime() { return addTime; }
    public void setAddTime(LocalDateTime addTime) { this.addTime = addTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }
}
