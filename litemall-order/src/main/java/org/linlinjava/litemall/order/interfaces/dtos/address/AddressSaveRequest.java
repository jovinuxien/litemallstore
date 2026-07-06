package org.linlinjava.litemall.order.interfaces.dtos.address;

import org.linlinjava.litemall.order.domain.model.agregates.LitemallAddressAggregate;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallAddressId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;

/**
 * Inbound payload for {@code POST /srv/address/save}. A positive {@code id} means
 * update; absent / non-positive means create. The owner is bound from the
 * {@code X-User-Id} header (see {@link #toAggregate}), never from this body.
 */
public class AddressSaveRequest {

    private Integer id;
    private String name;
    private String tel;
    private String province;
    private String city;
    private String county;
    private String addressDetail;
    private String areaCode;
    private String postalCode;
    private Boolean isDefault;

    /**
     * Build the domain aggregate, binding the authoritative owner from the header.
     * The id is attached only when it is a positive existing-row reference.
     */
    public LitemallAddressAggregate toAggregate(LitemallUserId userId) {
        LitemallAddressAggregate a = new LitemallAddressAggregate();
        if (id != null && id > 0) {
            a.setAddressId(new LitemallAddressId(id));
        }
        a.setUserId(userId);
        a.setName(name);
        a.setTel(tel);
        a.setProvince(province);
        a.setCity(city);
        a.setCounty(county);
        a.setAddressDetail(addressDetail);
        a.setAreaCode(areaCode);
        a.setPostalCode(postalCode);
        a.setIsDefault(isDefault != null ? isDefault : Boolean.FALSE);
        return a;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTel() { return tel; }
    public void setTel(String tel) { this.tel = tel; }
    public String getProvince() { return province; }
    public void setProvince(String province) { this.province = province; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getCounty() { return county; }
    public void setCounty(String county) { this.county = county; }
    public String getAddressDetail() { return addressDetail; }
    public void setAddressDetail(String addressDetail) { this.addressDetail = addressDetail; }
    public String getAreaCode() { return areaCode; }
    public void setAreaCode(String areaCode) { this.areaCode = areaCode; }
    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
    public Boolean getIsDefault() { return isDefault; }
    public void setIsDefault(Boolean isDefault) { this.isDefault = isDefault; }
}
