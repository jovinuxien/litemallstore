package org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.sourcing;

import lombok.Data;

/** One sourcing request's status as returned by CJ {@code product/sourcing/query}. */
@Data
public class CJSourcingQueryItem {
    private String sourceId;
    private String sourceNumber;
    private String productId;
    private String variantId;
    private String shopId;
    private String shopName;
    private String sourceStatus;
    private String sourceStatusStr;
    private String cjProductId;
    private String cjVariantSku;
}
