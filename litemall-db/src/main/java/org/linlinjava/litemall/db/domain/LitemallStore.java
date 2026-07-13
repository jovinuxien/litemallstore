package org.linlinjava.litemall.db.domain;

import java.time.LocalDateTime;

/**
 * One physical pickup store (table {@code litemall_store}, created in V35).
 *
 * <p>Hand-written (not MyBatis-Generator output) and co-located with the generated
 * domains, like {@link LitemallCjDispute}. Customers pick a visible store at checkout
 * when {@code delivery_type='pickup'}; write-off staff belong to a store implicitly
 * (no per-store account binding in this trim).
 */
public class LitemallStore {

    private Integer id;
    private String name;
    private String intro;
    private String phone;
    /** Region-level address (city/district). */
    private String address;
    /** Street-level address. */
    private String detailedAddress;
    /** Logo / storefront image URL. */
    private String logo;
    private String latitude;
    private String longitude;
    /** e.g. "Mon-Sun 09:00-18:00". */
    private String businessHours;
    /** Visible to customers on the pickup-store picker. */
    private Boolean isShow;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private Boolean deleted;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getIntro() { return intro; }
    public void setIntro(String intro) { this.intro = intro; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getDetailedAddress() { return detailedAddress; }
    public void setDetailedAddress(String detailedAddress) { this.detailedAddress = detailedAddress; }

    public String getLogo() { return logo; }
    public void setLogo(String logo) { this.logo = logo; }

    public String getLatitude() { return latitude; }
    public void setLatitude(String latitude) { this.latitude = latitude; }

    public String getLongitude() { return longitude; }
    public void setLongitude(String longitude) { this.longitude = longitude; }

    public String getBusinessHours() { return businessHours; }
    public void setBusinessHours(String businessHours) { this.businessHours = businessHours; }

    public Boolean getIsShow() { return isShow; }
    public void setIsShow(Boolean isShow) { this.isShow = isShow; }

    public LocalDateTime getAddTime() { return addTime; }
    public void setAddTime(LocalDateTime addTime) { this.addTime = addTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }
}
