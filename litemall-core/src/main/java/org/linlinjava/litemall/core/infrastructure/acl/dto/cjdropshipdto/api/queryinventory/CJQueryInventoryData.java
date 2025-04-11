package org.linlinjava.litemall.core.infrastructure.acl.dto.cjdropshipdto.api.queryinventory;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class CJQueryInventoryData {
    @JsonProperty("areaEn")
    private String areaEn;
    @JsonProperty("areaId")
    private Integer areaId;
    @JsonProperty("countryCode")
    private String countryCode;
    @JsonProperty("totalInventoryNum")
    private Integer totalInventoryNum;
    @JsonProperty("factoryInventoryNum")
    private Integer FactoryInventoryNUm;
    @JsonProperty("storageNum")
    private String CountryNameEn;

}
