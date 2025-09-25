package org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.productdetail;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.productvariant.CJProductVariantData;

import java.util.List;


@Data
public class CJProductDetailData {
    @JsonProperty("pid")
    private String pid; // Product ID

    @JsonProperty("productName")
    //private List<String> productName; // Product name (list of names)
    private String productName; // Product name (list of names)

    @JsonProperty("productNameEn")
    private String productNameEn; // Product name in English

    @JsonProperty("productSku")
    private String productSku; // Product SKU

    @JsonProperty("productImage")
    private String productImage; // Product image URL

    @JsonProperty("productWeight")
    private String productWeight; // Product weight

    @JsonProperty("productUnit")
    private String productUnit; // Product unit (e.g., "unit(s)")

    @JsonProperty("productType")
    private String productType; // Product type (e.g., "ORDINARY_PRODUCT")

    @JsonProperty("categoryId")
    private String categoryId; // Category ID

    @JsonProperty("categoryName")
    private String categoryName; // Category name

    @JsonProperty("entryCode")
    private String entryCode; // Entry code

    @JsonProperty("entryName")
    private String entryName; // Entry name

    @JsonProperty("entryNameEn")
    private String entryNameEn; // Entry name in English

    @JsonProperty("materialName")
    private String materialName; // Material name (list of names)

    @JsonProperty("materialNameEn")
    private String materialNameEn; // Material name in English (list of names)

    @JsonProperty("materialKey")
    private String materialKey; // Material key (list of keys)

    @JsonProperty("packingWeight")
    private String packingWeight; // Packing weight

    @JsonProperty("packingName")
    private String packingName; // Packing name (list of names)

    @JsonProperty("packingNameEn")
    private String packingNameEn; // Packing name in English (list of names)

    @JsonProperty("packingKey")
    private String packingKey; // Packing key (list of keys)

    @JsonProperty("productKey")
    private String productKey; // Product key (list of keys)

    @JsonProperty("productKeyEn")
    private String productKeyEn; // Product key in English

    @JsonProperty("sellPrice")
    private Double sellPrice; // Sell price

    @JsonProperty("sourceFrom")
    private Integer sourceFrom; // Source from (e.g., 1)

    @JsonProperty("description")
    private String description; // Product description

    @JsonProperty("suggestSellPrice")
    private String suggestSellPrice; // Suggested sell price range

    @JsonProperty("listedNum")
    private Integer listedNum; // Number of listings

    @JsonProperty("status")
    private String status; // Product status

    @JsonProperty("supplierName")
    private String supplierName; // Supplier name

    @JsonProperty("supplierId")
    private String supplierId; // Supplier ID

    @JsonProperty("productVariants")
    private CJProductVariantData variantData; // Variant data

    @JsonProperty("createTime")
    private String createTime; // Creation time

}
