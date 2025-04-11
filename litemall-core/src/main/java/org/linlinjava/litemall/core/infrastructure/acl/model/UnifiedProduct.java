package org.linlinjava.litemall.core.infrastructure.acl.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Getter
@Setter
public class UnifiedProduct {

    // Code identifiers for product
    private String id; // Prefix with "CJ_" for CJ products or "LOCAL_" for local
    private String sku;
    private String goodsSn;

    // Names and descriptions
    private String name;
    private String nameEn;
    private String brief;
    private String detail;
    private String keywords;
    private String description;


    // Classifications
    private String categoryId;
    private String brandId;



    // Pricing
    private BigDecimal sellPrice;
    private BigDecimal counterPrice;
    private BigDecimal retailPrice;
    private BigDecimal suggestedPrice;

    // Media
    private String picUrl;


    // Physical Attributes
    private Double productWeight;
    private Double packingWeight;
    private Double weight;


    // Product Characteristics
    private String productType;
    private String materialEn;
    private String packingEn;

    // Status Flags
    private boolean onSale;
    private boolean isNew;
    private boolean isHot;
    private String status;
    private boolean deleted;

    // Supplier Info

    // Timestamps
    private LocalDateTime createTime;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;

    private String source; // "local" or "cj_dropshipping"
}
