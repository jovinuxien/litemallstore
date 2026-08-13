package org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.productvariant;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.LenientDoubleDeserializer;

@Data
public class CJProductVariantData {
    @JsonProperty("vid")
    private String vid; // Variant ID

    @JsonProperty("pid")
    private String pid; // Product ID

    @JsonProperty("variantName")
    private String variantName; // Variant name

    @JsonProperty("variantNameEn")
    private String variantNameEn; // Variant name in English

    @JsonProperty("variantSku")
    private String variantSku; // Variant SKU

    @JsonProperty("variantNum")
    private String variantNum; // Variant number

    @JsonProperty("variantStandard")
    private String variantStandard; // Variant dimensions (e.g., "long=5,width=5,height=5")

    @JsonProperty("variantUnit")
    private String variantUnit; // Variant unit (if applicable)

    @JsonProperty("variantProperty")
    private String variantProperty; // Variant properties (if applicable)

    @JsonProperty("variantKey")
    private String variantKey; // Variant key (e.g., "[\"XS\"]")

    @JsonProperty("variantImage")
    private String variantImage; // Variant image URL (CJ docs: string, max 200; often absent)

    @JsonProperty("variantLength")
    private Double variantLength; // Variant length

    @JsonProperty("variantWidth")
    private Double variantWidth; // Variant width

    @JsonProperty("variantHeight")
    private Double variantHeight; // Variant height

    @JsonProperty("variantVolume")
    private Double variantVolume; // Variant volume

    @JsonProperty("variantWeight")
    private Double variantWeight; // Variant weight

    @JsonProperty("variantSellPrice")
    @JsonDeserialize(using = LenientDoubleDeserializer.class)
    private Double variantSellPrice; // Variant sell price (CJ may send a "low-high" range string)

    @JsonProperty("createTime")
    private String createTime; // Creation time (if applicable)
}
