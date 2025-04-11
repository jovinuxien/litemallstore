package org.linlinjava.litemall.core.infrastructure.acl.dto.cjdropshipdto.api.product;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class CJProduct  {
    @JsonProperty("pid")
    private String pid; // Product ID, e.g., "04A22450-67F0-4617-A132-E7AE7F8963B0"

    @JsonProperty("productName")
    private String productName; // Product name, e.g., "[\"猫耳朵卫衣\",\"定制卫衣\",\"个性化定制\"]"

    @JsonProperty("productNameEn")
    private String productNameEn; // Product name in English, e.g., "Personalized Belly-baring Cat Ear Hoody Coat"

    @JsonProperty("productSku")
    private String productSku; // Product SKU, e.g., "CJNSSYWY01847"

    @JsonProperty("productImage")
    private String productImage; // Product image URL, e.g., "https://cc-west-usa.oss-us-west-1.aliyuncs.com/20210129/2167381084610.png"

    @JsonProperty("productWeight")
    private String productWeight; // Product weight, e.g., 0

    @JsonProperty("productType")
    private String productType; // Product type, e.g., null

    @JsonProperty("productUnit")
    private String productUnit; // Product unit, e.g., "unit(s)"

    @JsonProperty("sellPrice")
    private String sellPrice; // Product price, e.g., 11.85

    @JsonProperty("categoryId")
    private String categoryId; // Category ID, e.g., "5E656DFB-9BAE-44DD-A755-40AFA2E0E686"

    @JsonProperty("categoryName")
    private String categoryName; // Category name, e.g., "Women's Clothing / Tops & Sets / Hoodies & Sweatshirts"

    @JsonProperty("sourceFrom")
    private int sourceFrom; // Source of the product, e.g., 0

    @JsonProperty("remark")
    private String remark; // Additional remarks, e.g., ""

    @JsonProperty("createTime")
    private String createTime; // Creation time, e.g., null
}
