package org.linlinjava.litemall.order.interfaces.dtos.address;

import org.linlinjava.litemall.order.domain.model.agregates.LitemallAddressAggregate;

/**
 * Outbound address shape consumed by the customer SPA ({@code IAddress}):
 * {@code { id, name, tel, province, city, county, addressDetail, areaCode,
 * postalCode, isDefault }}.
 */
public class AddressDtoResponse {

    private Integer id;
    private String name;
    private String tel;
    private String province;
    private String city;
    private String county;
    private String addressDetail;
    private String areaCode;
    private String postalCode;
    private String countryCode;
    private Boolean isDefault;

    public static AddressDtoResponse from(LitemallAddressAggregate a) {
        AddressDtoResponse dto = new AddressDtoResponse();
        dto.id = a.getAddressId() != null ? a.getAddressId().getId() : null;
        dto.name = a.getName();
        dto.tel = a.getTel();
        dto.province = a.getProvince();
        dto.city = a.getCity();
        dto.county = a.getCounty();
        dto.addressDetail = a.getAddressDetail();
        dto.areaCode = a.getAreaCode();
        dto.postalCode = a.getPostalCode();
        dto.countryCode = a.getCountryCode();
        dto.isDefault = a.getIsDefault();
        return dto;
    }

    public Integer getId() { return id; }
    public String getName() { return name; }
    public String getTel() { return tel; }
    public String getProvince() { return province; }
    public String getCity() { return city; }
    public String getCounty() { return county; }
    public String getAddressDetail() { return addressDetail; }
    public String getAreaCode() { return areaCode; }
    public String getPostalCode() { return postalCode; }
    public String getCountryCode() { return countryCode; }
    public Boolean getIsDefault() { return isDefault; }
}
