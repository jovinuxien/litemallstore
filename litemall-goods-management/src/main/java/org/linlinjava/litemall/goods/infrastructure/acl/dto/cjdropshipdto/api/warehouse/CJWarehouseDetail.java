package org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.warehouse;

import lombok.Data;

import java.util.List;

/** CJ warehouse/storage info from {@code warehouse/detail?id=<storageId>}. */
@Data
public class CJWarehouseDetail {
    private String id;
    private String name;
    private Integer areaId;
    private String areaCountryCode;
    private String address1;
    private String address2;
    private String contacts;
    private String phone;
    private String city;
    private String province;
    private String zipCode;
    private Integer isSelfPickup; // 1 = self-pickup supported, 0 = not
    private List<CJLogisticsBrand> logisticsBrandList;
}
