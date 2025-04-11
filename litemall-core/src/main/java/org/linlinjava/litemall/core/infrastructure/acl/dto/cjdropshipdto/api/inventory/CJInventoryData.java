package org.linlinjava.litemall.core.infrastructure.acl.dto.cjdropshipdto.api.inventory;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class CJInventoryData {

    @JsonProperty("vid")
    private String vid; // Unique identifier for the product or warehouse

    @JsonProperty("areaId")
    private String areaId; // ID of the warehouse area

    @JsonProperty("areaEn")
    private String areaEn; // English name of the warehouse area

    @JsonProperty("countryCode")
    private String countryCode; // Country code of the warehouse

    @JsonProperty("storageNum")
    private Integer storageNum; // Number of items in stock
}
