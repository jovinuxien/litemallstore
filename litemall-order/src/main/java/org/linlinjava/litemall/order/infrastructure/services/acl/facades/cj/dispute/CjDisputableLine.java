package org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.dispute;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * One CJ-order line eligible for dispute, in domain language. {@code lineItemId} is CJ's
 * opaque line reference (echoed back on create); {@code cjVariantId} lets callers join
 * the line to our own order-goods rows via {@code litemall_goods_product.cj_vid}.
 */
@Data
@Builder
public class CjDisputableLine {
    private String lineItemId;
    private String cjVariantId;
    private String productName;
    private String imageUrl;
    private BigDecimal unitPriceUsd;
    private int maxQuantity;
    private boolean disputable;
}
